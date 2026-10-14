package cl.cmitjans.rastreadores;

import java.util.HashMap;
import java.util.Map;

/**
 * Lista corta de dominios de publicidad y análisis conocidos, con la empresa a la que pertenecen.
 * Un dominio cuenta si coincide exactamente o es un subdominio (por ejemplo "x.doubleclick.net").
 * Es una lista de prueba: las listas completas (Exodus, DuckDuckGo) tienen miles de entradas.
 */
final class Trackers {
    static final String ADS = "Publicidad";
    static final String ANALYTICS = "Análisis";

    static final class Info {
        final String company, category;
        Info(String company, String category) { this.company = company; this.category = category; }
    }

    private static final Map<String, Info> LIST = new HashMap<>();

    private static void add(String company, String category, String... domains) {
        Info i = new Info(company, category);
        for (String d : domains) LIST.put(d, i);
    }

    static {
        add("Google", ADS, "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                "admob.com", "adservice.google.com", "pagead2.googlesyndication.com", "googletagservices.com");
        add("Google", ANALYTICS, "google-analytics.com", "app-measurement.com", "googletagmanager.com",
                "crashlytics.com", "firebase-settings.crashlytics.com", "firebaselogging-pa.googleapis.com");
        add("Meta (Facebook)", ADS, "an.facebook.com", "connect.facebook.net");
        add("Meta (Facebook)", ANALYTICS, "graph.facebook.com", "app-measurement.facebook.com");
        add("Amazon", ADS, "amazon-adsystem.com", "aax.amazon-adsystem.com");
        add("TikTok (ByteDance)", ADS, "ads.tiktok.com", "analytics.tiktok.com", "business-api.tiktok.com",
                "pangle.io", "pangleglobal.com");
        add("Snap", ADS, "tr.snapchat.com", "sc-analytics.appspot.com");
        add("Microsoft", ADS, "ads.linkedin.com", "bat.bing.com", "clarity.ms");
        add("Xiaomi", ADS, "api.ad.xiaomi.com", "globalapi.ad.xiaomi.com", "sdkconfig.ad.xiaomi.com",
                "sdkconfig.ad.intl.xiaomi.com", "ad.mi.com", "ad.xiaomi.com");
        add("Xiaomi", ANALYTICS, "tracking.miui.com", "data.mistat.xiaomi.com", "data.mistat.intl.xiaomi.com",
                "tracking.intl.miui.com");
        add("AppsFlyer", ANALYTICS, "appsflyer.com", "appsflyersdk.com");
        add("Adjust", ANALYTICS, "adjust.com", "adjust.io", "adjust.world");
        add("Branch", ANALYTICS, "branch.io", "api2.branch.io");
        add("Unity", ADS, "unityads.unity3d.com", "auction.unityads.unity3d.com", "config.uca.cloud.unity3d.com");
        add("AppLovin", ADS, "applovin.com", "applvn.com");
        add("ironSource", ADS, "ironsrc.com", "ironsrc.mobi", "supersonicads.com");
        add("Vungle (Liftoff)", ADS, "vungle.com", "liftoff.io");
        add("Chartboost", ADS, "chartboost.com");
        add("InMobi", ADS, "inmobi.com");
        add("Mintegral", ADS, "rayjump.com", "mintegral.net", "mtgglobals.com");
        add("Digital Turbine", ADS, "fyber.com", "inner-active.mobi");
        add("AdColony", ADS, "adcolony.com");
        add("StartApp", ADS, "startappservice.com", "start.io");
        add("Tapjoy", ADS, "tapjoy.com", "tapjoyads.com");
        add("Criteo", ADS, "criteo.com", "criteo.net");
        add("Taboola", ADS, "taboola.com");
        add("Outbrain", ADS, "outbrain.com");
        add("Xandr", ADS, "adnxs.com");
        add("PubMatic", ADS, "pubmatic.com");
        add("Magnite", ADS, "rubiconproject.com");
        add("OpenX", ADS, "openx.net");
        add("Index Exchange", ADS, "casalemedia.com");
        add("Yahoo", ADS, "ads.yahoo.com", "analytics.yahoo.com");
        add("comScore", ANALYTICS, "scorecardresearch.com", "comscore.com");
        add("Flurry", ANALYTICS, "flurry.com");
        add("Mixpanel", ANALYTICS, "mixpanel.com");
        add("Amplitude", ANALYTICS, "amplitude.com");
        add("Segment", ANALYTICS, "segment.io", "segment.com");
        add("Braze", ANALYTICS, "braze.com", "appboy.com");
        add("OneSignal", ANALYTICS, "onesignal.com");
        add("Hotjar", ANALYTICS, "hotjar.com");
        add("New Relic", ANALYTICS, "newrelic.com", "nr-data.net");
        add("Sentry", ANALYTICS, "sentry.io");
        add("Bugsnag", ANALYTICS, "bugsnag.com");
        add("Yandex", ANALYTICS, "appmetrica.yandex.net", "mc.yandex.ru");
        add("Huawei", ANALYTICS, "metrics.data.hicloud.com", "logservice.hicloud.com");
        add("Samsung", ADS, "samsungads.com");
        add("Kochava", ANALYTICS, "kochava.com");
        add("Singular", ANALYTICS, "singular.net");
        add("CleverTap", ANALYTICS, "clevertap-prod.com", "wzrkt.com");
    }

    /** Devuelve la empresa y categoría si el dominio es un rastreador conocido; null si no. */
    static Info match(String domain) {
        String d = domain;
        while (true) {
            Info i = LIST.get(d);
            if (i != null) return i;
            int dot = d.indexOf('.');
            if (dot < 0) return null;
            d = d.substring(dot + 1);
            if (d.indexOf('.') < 0) return null; // no compara contra "com" solo
        }
    }

    static int size() { return LIST.size(); }
}
