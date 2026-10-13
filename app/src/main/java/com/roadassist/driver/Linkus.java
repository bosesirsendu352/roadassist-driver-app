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
            JSONObject l = res.optJSONObject("linkus");
            String host = l != null ? l.optString("host", "") : "";
            int port = l != null ? l.optInt("port", 8111) : 8111;
            String lhost = l != null ? l.optString("local_host", "") : "";
            int lport = l != null ? l.optInt("local_port", 8111) : 8111;
            final String ext = res.optString("ext");
            try {
                YlsLoginManager.getInstance().loginBlock(app, ext, sign, lhost, lport, host, port, new RequestCallback<Boolean>() {
                    @Override public void onSuccess(Boolean ok) {
                        busy = false;
                        set("Connected · ext " + ext);
                    }
                    @Override public void onFailed(int code) {
                        busy = false;
                        set("PBX login failed (" + code + ") " + reason(code));
                    }
                    @Override public void onException(Throwable e) {
                        busy = false;
                        set("PBX login error: " + e.getMessage());
                    }
                });
            } catch (Throwable t) {
                busy = false;
                set("PBX login error: " + t.getMessage());
            }
        });
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
