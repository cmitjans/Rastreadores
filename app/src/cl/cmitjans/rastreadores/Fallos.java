package cl.cmitjans.rastreadores;

import android.content.Context;
import android.os.Build;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Si la app falla, guarda el detalle técnico del error para mostrarlo la próxima vez que la abras.
 * Queda solo en el teléfono; tú decides si lo copias y lo compartes.
 */
final class Fallos {
    private static final String PREFS = "fallos";
    private static final String KEY = "ultimo";

    private static boolean instalado;

    private Fallos() {}

    /** Captura cualquier error que cerraría la app. */
    static synchronized void instalar(Context ctx) {
        if (instalado) return;
        instalado = true;
        final Context app = ctx.getApplicationContext();
        final Thread.UncaughtExceptionHandler previo = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((th, e) -> {
            guardarAhora(app, e, th.getName());
            if (previo != null) previo.uncaughtException(th, e);
        });
    }

    static void guardar(Context ctx, Throwable e) {
        guardarAhora(ctx.getApplicationContext(), e, Thread.currentThread().getName());
    }

    private static void guardarAhora(Context ctx, Throwable e, String hilo) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String texto = "Rastreadores " + version(ctx) + " · Android " + Build.VERSION.RELEASE
                    + " (API " + Build.VERSION.SDK_INT + ") · " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                    + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                    + " · hilo " + hilo + "\n\n" + sw;
            if (texto.length() > 6000) texto = texto.substring(0, 6000);
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, texto).commit();
        } catch (Throwable ignored) { }
    }

    static String ultimo(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
    }

    static void borrar(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    private static String version(Context ctx) {
        try { return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }
}
