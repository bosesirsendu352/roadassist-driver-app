package com.roadassist.driver;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.yeastar.linkus.service.call.YlsCallManager;
import com.yeastar.linkus.service.callback.RequestCallback;
import com.yeastar.linkus.service.login.YlsLoginManager;

import org.json.JSONObject;

/**
 * Linkus SDK login for the driver's extension.
 * The CRM creates the login signature (PBX OpenAPI sign/create) so the driver never needs a PBX password.
 */
public final class Linkus {
    private Linkus() {}

    public interface Listener { void onState(String state); }

    private static volatile String state = "Not connected";
    private static volatile boolean busy = false;
    private static volatile String dialCode = "";
    private static Listener listener;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static String state() { return state; }
    public static String dialCode() { return dialCode; }
    public static void setListener(Listener l) { listener = l; }
    public static boolean ready() {
        try { return YlsLoginManager.getInstance().isLoginEd(); } catch (Throwable t) { return false; }
    }

    static void set(String s) {
        state = s;
        MAIN.post(() -> { if (listener != null) listener.onState(s); });
    }

    /** Make sure the SDK is logged in; safe to call often. */
    public static void ensureLogin(Context ctx) {
        if (busy || !Prefs.paired(ctx)) return;
        if (ready()) {
            if (!state.startsWith("Connected")) set("Connected · ext " + Prefs.ext(ctx));
            if (!dialCode.isEmpty()) return;      // still fetch the dial code once below
        }
        busy = true;
        final Context app = ctx.getApplicationContext();
        if (!ready()) set("Connecting to PBX…");
        Api.run(app, "GET", "/app/me", null, (res, err) -> {
            if (err != null || res == null) { busy = false; set("CRM: " + err); return; }
            dialCode = res.optString("dial", "");
            if (ready()) { busy = false; set("Connected · ext " + res.optString("ext")); return; }
            String sign = res.isNull("sign") ? "" : res.optString("sign", "");
            if (sign.isEmpty()) { busy = false; set("Calls not set up: " + res.optString("sign_error", "no Linkus SDK signature")); return; }
            final String ext = res.optString("ext");
            final java.util.List<String[]> targets = new java.util.ArrayList<>();
            org.json.JSONArray ta = res.optJSONArray("targets");
            if (ta != null) for (int i = 0; i < ta.length(); i++) {
                JSONObject t = ta.optJSONObject(i);
                if (t != null) targets.add(new String[]{t.optString("host"), String.valueOf(t.optInt("port", 8111))});
            }
            if (targets.isEmpty()) {
                JSONObject l = res.optJSONObject("linkus");
                targets.add(new String[]{l != null ? l.optString("host", "") : "", String.valueOf(l != null ? l.optInt("port", 8111) : 8111)});
            }
            tryTarget(app, ext, sign, targets, 0, new StringBuilder());
        });
    }

    /** Try each PBX address in turn until one logs in; every result is reported to the CRM log. */
    private static void tryTarget(Context app, String ext, String sign, java.util.List<String[]> targets, int i, StringBuilder fails) {
        if (i >= targets.size()) {
            busy = false;
            set("PBX login failed: " + fails);
            return;
        }
        final String host = targets.get(i)[0];
        final int port = Integer.parseInt(targets.get(i)[1]);
        final String where = host + ":" + port;
        set("Connecting to PBX (" + where + ")…");
        try {
            YlsLoginManager.getInstance().loginBlock(app, ext, sign, "", port, host, port, new RequestCallback<Boolean>() {
                @Override public void onSuccess(Boolean ok) {
                    busy = false;
                    log(app, "login OK via " + where);
                    set("Connected · ext " + ext);
                }
                @Override public void onFailed(int code) {
                    String m = "code " + code + " @ " + where + " " + reason(code);
                    log(app, "login failed: " + m);
                    fails.append(fails.length() > 0 ? " | " : "").append(code).append(" @ ").append(where);
                    MAIN.post(() -> tryTarget(app, ext, sign, targets, i + 1, fails));
                }
                @Override public void onException(Throwable e) {
                    String m = e.getClass().getSimpleName() + ": " + e.getMessage() + " @ " + where;
                    log(app, "login exception: " + m);
                    fails.append(fails.length() > 0 ? " | " : "").append("error @ ").append(where);
                    MAIN.post(() -> tryTarget(app, ext, sign, targets, i + 1, fails));
                }
            });
        } catch (Throwable t) {
            log(app, "login crash: " + t.getClass().getSimpleName() + ": " + t.getMessage() + " @ " + where);
            fails.append(fails.length() > 0 ? " | " : "").append("crash @ ").append(where);
            MAIN.post(() -> tryTarget(app, ext, sign, targets, i + 1, fails));
        }
    }

    /** Send a diagnostic line to the CRM (Driver app log page). */
    public static void log(Context ctx, String text) {
        try {
            Api.run(ctx, "POST", "/app/log", new JSONObject().put("text", "[" + android.os.Build.MODEL + "] " + text), (r, e) -> {});
        } catch (Exception ignored) {}
    }

    static String reason(int code) {
        switch (code) {
            case 1: return "– can't reach the PBX address/port";
            case -5: return "– PBX did not answer the login";
            case 403: return "– signature rejected";
            case 405: return "– Linkus is disabled for this extension";
            case 407: return "– account locked";
            case 416: return "– IP blocked by the PBX";
            default: return "";
        }
    }

    public static boolean inCall() {
        try { return YlsCallManager.getInstance().isInCall(); } catch (Throwable t) { return false; }
    }

    /** Is this a real phone number (customer) rather than an extension / call-flow code? */
    public static boolean isExternal(String n) {
        if (n == null) return false;
        return n.replaceAll("[^0-9]", "").length() >= 7;
    }
}
