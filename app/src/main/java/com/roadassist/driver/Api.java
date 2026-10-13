package com.roadassist.driver;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny client for the CRM driver API (/app/*). */
public final class Api {
    private Api() {}
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Cb { void done(JSONObject res, String error); }

    /** Blocking call (use from a background thread). */
    public static JSONObject call(String base, String token, String method, String path, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base.replaceAll("/+$", "") + path).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", "application/json");
        if (token != null && !token.isEmpty()) c.setRequestProperty("X-Driver-Token", token);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String txt = "";
        if (is != null) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
            txt = bo.toString("UTF-8");
        }
        c.disconnect();
        JSONObject j;
        try { j = new JSONObject(txt); } catch (Exception e) { j = new JSONObject().put("ok", false).put("error", "HTTP " + code); }
        if (code >= 400 && !j.has("error")) j.put("error", "HTTP " + code);
        if (code >= 400) j.put("ok", false);
        j.put("_status", code);
        return j;
    }

    /** Async call with the saved pairing; callback runs on the main thread. */
    public static void run(Context ctx, String method, String path, JSONObject body, Cb cb) {
        final Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            JSONObject res = null;
            String err = null;
            try {
                res = call(Prefs.crmUrl(app), Prefs.token(app), method, path, body);
                if (!res.optBoolean("ok", false)) err = res.optString("error", "Request failed");
            } catch (Exception e) {
                err = "No connection to the CRM (" + e.getClass().getSimpleName() + ")";
            }
            final JSONObject r = res;
            final String er = err;
            MAIN.post(() -> cb.done(r, er));
        });
    }

    public static void runIo(Runnable r) { IO.execute(r); }
}
