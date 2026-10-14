package cl.cmitjans.rastreadores;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Guarda en memoria lo que se ha visto desde que se encendió el registro.
 * No se escribe en ningún archivo ni sale del teléfono; se borra al cerrar la app o al tocar "Borrar".
 */
final class Registro {
    static final class Dominio {
        final String name;
        final Trackers.Info tracker;
        int count;
        long last;
        Dominio(String name) { this.name = name; this.tracker = Trackers.match(name); }
    }

    static final class App {
        final int uid;
        final String label;
        int total, trackerHits;
        long last;
        final Map<String, Dominio> dominios = new HashMap<>();
        App(int uid, String label) { this.uid = uid; this.label = label; }

        int trackerDomains() {
            int n = 0;
            for (Dominio d : dominios.values()) if (d.tracker != null) n++;
            return n;
        }

        List<String> companies() {
            List<String> out = new ArrayList<>();
            for (Dominio d : dominios.values())
                if (d.tracker != null && !out.contains(d.tracker.company)) out.add(d.tracker.company);
            Collections.sort(out);
            return out;
        }
    }

    static final class Evento {
        final long time; final String app, domain; final Trackers.Info tracker;
        Evento(long time, String app, String domain, Trackers.Info tracker) {
            this.time = time; this.app = app; this.domain = domain; this.tracker = tracker;
        }
    }

    private static final Map<Integer, App> apps = new HashMap<>();
    private static final ArrayDeque<Evento> recientes = new ArrayDeque<>();
    private static final int MAX_RECIENTES = 300;
    private static int total, trackerTotal;
    private static long since = System.currentTimeMillis();

    private Registro() {}

    static synchronized void add(int uid, String label, String domain) {
        long now = System.currentTimeMillis();
        App a = apps.get(uid);
        if (a == null) { a = new App(uid, label); apps.put(uid, a); }
        Dominio d = a.dominios.get(domain);
        if (d == null) { d = new Dominio(domain); a.dominios.put(domain, d); }
        d.count++; d.last = now;
        a.total++; a.last = now;
        total++;
        if (d.tracker != null) { a.trackerHits++; trackerTotal++; }
        recientes.addFirst(new Evento(now, a.label, domain, d.tracker));
        while (recientes.size() > MAX_RECIENTES) recientes.removeLast();
    }

    /** Apps ordenadas: primero las que más contactan rastreadores. */
    static synchronized List<App> apps() {
        List<App> list = new ArrayList<>(apps.values());
        list.sort((x, y) -> x.trackerHits != y.trackerHits ? y.trackerHits - x.trackerHits : y.total - x.total);
        return list;
    }

    static synchronized App app(int uid) { return apps.get(uid); }

    static synchronized List<Dominio> dominios(App a) {
        List<Dominio> list = new ArrayList<>(a.dominios.values());
        list.sort((x, y) -> {
            boolean tx = x.tracker != null, ty = y.tracker != null;
            if (tx != ty) return tx ? -1 : 1;
            return y.count - x.count;
        });
        return list;
    }

    static synchronized List<Evento> recientes(int max) {
        List<Evento> out = new ArrayList<>();
        for (Evento e : recientes) { if (out.size() >= max) break; out.add(e); }
        return out;
    }

    static synchronized int total() { return total; }
    static synchronized int trackerTotal() { return trackerTotal; }
    static synchronized long since() { return since; }

    static synchronized void clear() {
        apps.clear(); recientes.clear(); total = 0; trackerTotal = 0; since = System.currentTimeMillis();
    }
}
