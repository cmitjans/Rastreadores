package cl.cmitjans.rastreadores;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * VPN local que solo intercepta las consultas DNS (las preguntas "¿dónde está tal dominio?").
 *
 * - El resto del tráfico NO pasa por aquí: va directo a internet como siempre.
 * - Cada consulta se anota (qué app preguntó por qué dominio) y se reenvía tal cual al servidor DNS
 *   de tu red (el de tu Wi‑Fi o tu compañía móvil). La respuesta vuelve sin modificar.
 * - Nada se envía a ningún otro servidor ni se guarda en archivos.
 */
public class RastreoVpn extends VpnService {
    static final String ACTION_STOP = "cl.cmitjans.rastreadores.STOP";
    private static final String CHANNEL = "rastreo";
    private static final int NOTIF_ID = 1;

    /** Direcciones inventadas dentro de la VPN: el teléfono cree que el servidor DNS está en FAKE_DNS. */
    private static final String VPN_ADDRESS = "10.111.222.1";
    private static final String FAKE_DNS = "10.111.222.2";

    private static volatile boolean running;

    private ParcelFileDescriptor tun;
    private Thread reader;
    private ExecutorService pool;
    private FileOutputStream out;
    private List<InetAddress> upstream = new ArrayList<>();
    private final Map<Integer, String> labels = new HashMap<>();
    private long lastNotif;

    static boolean isRunning() { return running; }

    static void stop(Context c) {
        c.startService(new Intent(c, RastreoVpn.class).setAction(ACTION_STOP));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_STICKY;
        createChannel();
        startInForeground();
        upstream = findUpstreamDns();
        try {
            Builder b = new Builder()
                    .setSession("Rastreadores")
                    .addAddress(VPN_ADDRESS, 32)
                    .addDnsServer(FAKE_DNS)
                    .addRoute(FAKE_DNS, 32)          // solo el tráfico hacia el DNS falso entra a la VPN
                    .setBlocking(true);
            try { b.addDisallowedApplication(getPackageName()); } catch (PackageManager.NameNotFoundException ignored) { }
            if (Build.VERSION.SDK_INT >= 29) b.setMetered(false);
            tun = b.establish();
        } catch (RuntimeException e) {
            tun = null;
        }
        if (tun == null) { shutdown(); stopSelf(); return START_NOT_STICKY; }

        running = true;
        out = new FileOutputStream(tun.getFileDescriptor());
        pool = Executors.newFixedThreadPool(8);
        reader = new Thread(this::readLoop, "rastreo-dns");
        reader.start();
        return START_STICKY;
    }

    private void readLoop() {
        FileInputStream in = new FileInputStream(tun.getFileDescriptor());
        byte[] buf = new byte[32767];
        while (running) {
            int n;
            try { n = in.read(buf); } catch (IOException e) { break; }
            if (n <= 0) continue;
            DnsPacket.Query q = DnsPacket.parse(buf, n);
            if (q == null) continue;
            pool.execute(() -> handle(q));
        }
    }

    private void handle(DnsPacket.Query q) {
        int uid = ownerUid(q);
        Registro.add(uid, label(uid), q.name);
        updateNotification();

        byte[] reply = forward(q.dns);
        if (reply == null) return;
        byte[] packet = DnsPacket.response(q, reply, reply.length);
        synchronized (this) {
            try { if (running) out.write(packet); } catch (IOException ignored) { }
        }
    }

    /** Reenvía la consulta al servidor DNS real de tu red y devuelve su respuesta sin tocarla. */
    private byte[] forward(byte[] query) {
        for (InetAddress server : upstream) {
            try (DatagramSocket s = new DatagramSocket()) {
                protect(s); // que este socket no vuelva a entrar a la VPN
                s.setSoTimeout(4000);
                s.send(new DatagramPacket(query, query.length, server, 53));
                byte[] r = new byte[4096];
                DatagramPacket p = new DatagramPacket(r, r.length);
                s.receive(p);
                byte[] res = new byte[p.getLength()];
                System.arraycopy(r, 0, res, 0, p.getLength());
                return res;
            } catch (IOException ignored) {
                // prueba el siguiente servidor
            }
        }
        return null;
    }

    /** Servidores DNS de la red real (Wi‑Fi o datos), sin contar esta VPN. */
    private List<InetAddress> findUpstreamDns() {
        List<InetAddress> list = new ArrayList<>();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network active = cm.getActiveNetwork();
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc == null || nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
            if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp == null) continue;
            List<InetAddress> dns = lp.getDnsServers();
            if (n.equals(active)) list.addAll(0, dns); else list.addAll(dns);
        }
        // Respaldo si la red no informa sus servidores: Cloudflare y Google.
        try {
            list.add(InetAddress.getByName("1.1.1.1"));
            list.add(InetAddress.getByName("8.8.8.8"));
        } catch (IOException ignored) { }
        return list;
    }

    /** Pregunta a Android qué app abrió esta conexión (solo funciona desde Android 10). */
    private int ownerUid(DnsPacket.Query q) {
        if (Build.VERSION.SDK_INT < 29) return -1;
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            return cm.getConnectionOwnerUid(OsConstants.IPPROTO_UDP,
                    new InetSocketAddress(InetAddress.getByAddress(q.srcIp), q.srcPort),
                    new InetSocketAddress(InetAddress.getByAddress(q.dstIp), q.dstPort));
        } catch (Exception e) {
            return -1;
        }
    }

    private String label(int uid) {
        synchronized (labels) {
            String l = labels.get(uid);
            if (l != null) return l;
            l = resolveLabel(uid);
            labels.put(uid, l);
            return l;
        }
    }

    private String resolveLabel(int uid) {
        if (uid < 0) return "Desconocida";
        if (uid == 0) return "Sistema (root)";
        if (uid == 1000) return "Sistema Android";
        PackageManager pm = getPackageManager();
        String[] pkgs = pm.getPackagesForUid(uid);
        if (pkgs == null || pkgs.length == 0) return "Sistema (uid " + uid + ")";
        String first;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkgs[0], 0);
            first = pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            first = pkgs[0];
        }
        return pkgs.length > 1 ? first + " y " + (pkgs.length - 1) + " más" : first;
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Registro activo",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), fl);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, RastreoVpn.class).setAction(ACTION_STOP), fl);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Registrando conexiones")
                .setContentText(Registro.total() + " consultas, " + Registro.trackerTotal() + " a rastreadores")
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(null, "Detener", stop).build())
                .build();
    }

    private void startInForeground() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotification() {
        long now = System.currentTimeMillis();
        if (now - lastNotif < 2000) return;
        lastNotif = now;
        try { getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification()); }
        catch (RuntimeException ignored) { }
    }

    private void shutdown() {
        running = false;
        if (pool != null) pool.shutdownNow();
        try { if (tun != null) tun.close(); } catch (IOException ignored) { }
        tun = null;
        if (reader != null) reader.interrupt();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @Override
    public void onRevoke() {
        shutdown();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }
}
