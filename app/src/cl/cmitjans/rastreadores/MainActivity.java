package cl.cmitjans.rastreadores;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Pantalla principal: encender el registro y ver qué app se conecta a qué. */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 18, 22);
    private static final int CARD = Color.rgb(30, 34, 41);
    private static final int TEXT = Color.rgb(232, 236, 240);
    private static final int MUTED = Color.rgb(150, 160, 172);
    private static final int ACCENT = Color.rgb(110, 162, 239);
    private static final int WARN = Color.rgb(240, 140, 98);
    private static final int WARN_BG = Color.rgb(64, 36, 26);
    private static final int REQ_VPN = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private Button toggle;
    private TextView status;
    private Button tabApps, tabRecent;
    private LinearLayout list;
    private boolean showRecent;
    private int detailUid = Integer.MIN_VALUE;

    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 2000); }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Fallos.instalar(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(28));

        TextView title = text("Rastreadores", 28, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView sub = text("Muestra a qué servidores intenta conectarse cada app, y marca los de publicidad y "
                + "análisis conocidos. Solo ve los nombres de los servidores, nunca el contenido de lo que se envía.", 14, MUTED);
        sub.setPadding(0, dp(6), 0, dp(16));
        root.addView(sub);

        String fallo = Fallos.ultimo(this);
        if (fallo != null) root.addView(tarjetaFallo(fallo), gap());

        toggle = button("Iniciar registro", ACCENT);
        toggle.setOnClickListener(v -> {
            if (RastreoVpn.isRunning()) { RastreoVpn.stop(this); handler.postDelayed(this::refresh, 300); }
            else startVpn();
        });
        root.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        status = text("", 14, MUTED);
        status.setPadding(0, dp(10), 0, dp(14));
        root.addView(status);

        LinearLayout tabs = new LinearLayout(this);
        tabApps = button("Por app", CARD);
        tabRecent = button("Recientes", CARD);
        tabApps.setOnClickListener(v -> { showRecent = false; detailUid = Integer.MIN_VALUE; refresh(); });
        tabRecent.setOnClickListener(v -> { showRecent = true; detailUid = Integer.MIN_VALUE; refresh(); });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(44), 1);
        half.rightMargin = dp(6);
        tabs.addView(tabApps, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(44), 1);
        half2.leftMargin = dp(6);
        tabs.addView(tabRecent, half2);
        root.addView(tabs);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(14), 0, 0);
        root.addView(list);

        Button clear = button("Borrar registro", CARD);
        clear.setTextColor(MUTED);
        clear.setOnClickListener(v -> { Registro.clear(); detailUid = Integer.MIN_VALUE; refresh(); });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        clp.topMargin = dp(18);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(clear, clp);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(tick); }

    @Override
    public void onBackPressed() {
        if (detailUid != Integer.MIN_VALUE) { detailUid = Integer.MIN_VALUE; refresh(); }
        else super.onBackPressed();
    }

    private void startVpn() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }
        Intent consent = VpnService.prepare(this);
        if (consent != null) startActivityForResult(consent, REQ_VPN);
        else onActivityResult(REQ_VPN, RESULT_OK, null);
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        if (req == REQ_VPN && result == RESULT_OK) {
            startForegroundService(new Intent(this, RastreoVpn.class));
            handler.postDelayed(this::refresh, 500);
        }
    }

    private void refresh() {
        boolean on = RastreoVpn.isRunning();
        toggle.setText(on ? "Detener registro" : "Iniciar registro");
        tint(toggle, on ? CARD : ACCENT);
        toggle.setTextColor(on ? TEXT : Color.rgb(13, 21, 32));
        String desde = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(Registro.since()));
        status.setText((on ? "Registrando desde las " + desde : "Detenido")
                + " · " + Registro.total() + " consultas · " + Registro.trackerTotal() + " a rastreadores");
        tint(tabApps, !showRecent ? ACCENT : CARD);
        tabApps.setTextColor(!showRecent ? Color.rgb(13, 21, 32) : TEXT);
        tint(tabRecent, showRecent ? ACCENT : CARD);
        tabRecent.setTextColor(showRecent ? Color.rgb(13, 21, 32) : TEXT);

        list.removeAllViews();
        if (detailUid != Integer.MIN_VALUE) showDetail();
        else if (showRecent) showRecent();
        else showApps();
    }

    private void showApps() {
        List<Registro.App> apps = Registro.apps();
        if (apps.isEmpty()) { empty(); return; }
        for (Registro.App a : apps) {
            LinearLayout c = card();
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(a.label, 16, TEXT);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (a.trackerHits > 0) top.addView(pill(a.trackerHits + " a rastreadores"));
            c.addView(top);
            String line = a.total + " consultas a " + a.dominios.size() + " servidores";
            List<String> comps = a.companies();
            if (!comps.isEmpty()) line += "\nEmpresas: " + TextUtils.join(", ", comps);
            TextView info = text(line, 13, MUTED);
            info.setPadding(0, dp(4), 0, 0);
            c.addView(info);
            final int uid = a.uid;
            c.setOnClickListener(v -> { detailUid = uid; refresh(); });
            list.addView(c, gap());
        }
    }

    private void showDetail() {
        Registro.App a = Registro.app(detailUid);
        if (a == null) { detailUid = Integer.MIN_VALUE; showApps(); return; }
        Button back = button("← Volver", CARD);
        back.setOnClickListener(v -> { detailUid = Integer.MIN_VALUE; refresh(); });
        list.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        TextView h = text(a.label, 20, TEXT);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, dp(12), 0, dp(10));
        list.addView(h);
        for (Registro.Dominio d : Registro.dominios(a)) {
            LinearLayout c = card();
            TextView n = text(d.name, 14, TEXT);
            n.setTypeface(Typeface.MONOSPACE);
            c.addView(n);
            String l = d.count + (d.count == 1 ? " vez" : " veces") + " · última a las " + hora.format(new Date(d.last));
            if (d.tracker != null) l = d.tracker.category + " · " + d.tracker.company + "\n" + l;
            TextView t = text(l, 13, d.tracker != null ? WARN : MUTED);
            t.setPadding(0, dp(4), 0, 0);
            c.addView(t);
            list.addView(c, gap());
        }
    }

    private void showRecent() {
        List<Registro.Evento> ev = Registro.recientes(150);
        if (ev.isEmpty()) { empty(); return; }
        for (Registro.Evento e : ev) {
            LinearLayout c = card();
            TextView top = text(hora.format(new Date(e.time)) + "  ·  " + e.app, 13, MUTED);
            c.addView(top);
            TextView d = text(e.domain, 14, e.tracker != null ? WARN : TEXT);
            d.setTypeface(Typeface.MONOSPACE);
            d.setPadding(0, dp(2), 0, 0);
            c.addView(d);
            if (e.tracker != null) {
                TextView t = text(e.tracker.category + " · " + e.tracker.company, 12, WARN);
                c.addView(t);
            }
            list.addView(c, gap());
        }
    }

    private void empty() {
        TextView t = text(RastreoVpn.isRunning()
                ? "Esperando conexiones… Abre otras apps y vuelve: aparecerán aquí."
                : "Toca \"Iniciar registro\". Android te pedirá permiso para crear una VPN: es local, el tráfico no sale a ningún servidor externo.", 14, MUTED);
        t.setPadding(0, dp(8), 0, 0);
        list.addView(t);
    }

    /** Muestra el último error guardado, para poder copiarlo y enviarlo. */
    private View tarjetaFallo(String fallo) {
        LinearLayout c = card();
        TextView h = text("La app se cerró por un error la última vez", 16, WARN);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(h);
        TextView ex = text("Copia este detalle y envíalo para poder corregirlo. Solo contiene datos técnicos del error.", 13, MUTED);
        ex.setPadding(0, dp(4), 0, dp(8));
        c.addView(ex);
        TextView det = text(fallo, 11, TEXT);
        det.setTypeface(Typeface.MONOSPACE);
        det.setTextIsSelectable(true);
        det.setMaxLines(14);
        c.addView(det);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(10), 0, 0);
        Button copy = button("Copiar", ACCENT);
        copy.setTextColor(Color.rgb(13, 21, 32));
        copy.setOnClickListener(v -> {
            android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Error de Rastreadores", fallo));
            copy.setText("Copiado");
        });
        Button dismiss = button("Descartar", BG);
        dismiss.setOnClickListener(v -> { Fallos.borrar(this); c.setVisibility(View.GONE); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.rightMargin = dp(8);
        row.addView(copy, lp);
        row.addView(dismiss, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        c.addView(row);
        return c;
    }

    // --- pequeñas ayudas de diseño ---

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(12));
        c.setBackground(bg);
        return c;
    }

    private TextView pill(String s) {
        TextView t = text(s, 12, WARN);
        t.setPadding(dp(8), dp(2), dp(8), dp(2));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(WARN_BG);
        bg.setCornerRadius(dp(10));
        t.setBackground(bg);
        return t;
    }

    private Button button(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(TEXT);
        b.setPadding(dp(16), 0, dp(16), 0);
        tint(b, color);
        return b;
    }

    private void tint(View v, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(24));
        v.setBackground(bg);
    }

    private LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
