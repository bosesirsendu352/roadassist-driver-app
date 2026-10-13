package com.roadassist.driver;

import android.content.Context;
import android.content.SharedPreferences;

/** Pairing data saved on the phone. The customer's number is never stored here. */
public final class Prefs {
    private Prefs() {}
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("driver", Context.MODE_PRIVATE); }

    public static String crmUrl(Context c) { return sp(c).getString("crm", BuildConfig.CRM_URL); }
    public static String token(Context c) { return sp(c).getString("token", ""); }
    public static String name(Context c) { return sp(c).getString("name", ""); }
    public static String ext(Context c) { return sp(c).getString("ext", ""); }
    public static boolean paired(Context c) { return token(c).length() == 24; }

    public static void savePairing(Context c, String crm, String token, String name, String ext) {
        sp(c).edit().putString("crm", crm).putString("token", token).putString("name", name).putString("ext", ext).apply();
    }
    public static void clear(Context c) { sp(c).edit().clear().apply(); }

    /** Job ids + location time already notified, so the driver is only alerted for new things. */
    public static String seen(Context c) { return sp(c).getString("seen", ""); }
    public static void setSeen(Context c, String v) { sp(c).edit().putString("seen", v).apply(); }
}
