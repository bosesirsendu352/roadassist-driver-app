package com.roadassist.driver;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** One job: summary, customer location on a map, navigation, masked call, status. */
public class JobActivity extends Activity {
    private JSONObject job;
    private LinearLayout body;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { reload(); h.postDelayed(this, 20000); }
    };
    private String shownLoc = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { job = new JSONObject(getIntent().getStringExtra("job")); } catch (Exception e) { finish(); return; }
        body = Ui.column(this);
        int p = Ui.dp(this, 16);
        body.setPadding(p, Ui.dp(this, 36), p, p);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.addView(body);
        setContentView(sv);
        render();
    }

    @Override protected void onResume() { super.onResume(); h.postDelayed(refresh, 20000); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(refresh); }

    private boolean hasLoc() { return !job.isNull("lat") && !job.isNull("lng"); }

    /** Pick up a location the customer shares after the job was opened. */
    private void reload() {
        Api.run(this, "GET", "/app/jobs", null, (res, err) -> {
            if (err != null || isFinishing()) return;
            JSONArray a = res.optJSONArray("jobs");
            if (a == null) return;
            for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.optJSONObject(i);
                if (j.optInt("id") == job.optInt("id")) {
                    boolean changed = !j.optString("lat").equals(job.optString("lat")) || !j.optString("status").equals(job.optString("status"));
                    job = j;
                    if (changed) render();
                    return;
                }
            }
        });
    }

    private void render() {
        body.removeAllViews();
        TextView back = Ui.text(this, "‹ My jobs", 15, Ui.TEAL, true);
        back.setOnClickListener(v -> finish());
        back.setPadding(0, 0, 0, Ui.dp(this, 10));
        body.addView(back);
        body.addView(Ui.text(this, "Job #" + job.optInt("id") + " · " + job.optString("concern"), 24, Ui.INK, true));
        body.addView(Ui.text(this, Ui.status(job.optString("status")) + " · Customer " + job.optString("customer") + " (number hidden)", 14, Ui.MUTED, false));

        // ---- Masked call ----
        LinearLayout call = Ui.card(this);
        ((LinearLayout.LayoutParams) call.getLayoutParams()).topMargin = Ui.dp(this, 14);
        Button callBtn = Ui.button(this, "📞  Call customer", Ui.TEAL, null);
        callBtn.setOnClickListener(v -> callCustomer(callBtn));
        call.addView(callBtn);
        call.addView(Ui.text(this, "The call goes through the company PBX. The customer sees the company number and you never see theirs.", 13, Ui.MUTED, false));
        body.addView(call);

        // ---- Location ----
        LinearLayout loc = Ui.card(this);
        loc.addView(Ui.text(this, "Customer location", 13, Ui.MUTED, true));
        if (hasLoc()) {
            double lat = job.optDouble("lat"), lng = job.optDouble("lng");
            String txt = job.optString("location");
            loc.addView(Ui.text(this, txt.isEmpty() ? "Pin shared on WhatsApp" : txt, 17, Ui.INK, true));
            long t = job.optLong("location_time", 0);
            if (t > 0) loc.addView(Ui.text(this, "Shared " + Ui.time(t), 13, Ui.MUTED, false));
            loc.addView(map(lat, lng));
            loc.addView(Ui.button(this, "🧭  Navigate", Ui.INK, v -> navigate(lat, lng)));
            shownLoc = lat + "," + lng;
        } else {
            loc.addView(Ui.text(this, "The customer hasn't shared a location yet. This screen updates by itself when they do.", 15, Ui.AMBER, false));
        }
        body.addView(loc);

        // ---- Summary ----
        String s = job.optString("summary");
        if (!s.isEmpty()) {
            LinearLayout sum = Ui.card(this);
            sum.addView(Ui.text(this, "Call summary", 13, Ui.MUTED, true));
            sum.addView(Ui.text(this, s, 15, Ui.INK, false));
            body.addView(sum);
        }

        // ---- Status ----
        LinearLayout st = Ui.card(this);
        st.addView(Ui.text(this, "Update the CRM", 13, Ui.MUTED, true));
        st.addView(Ui.outline(this, "On the way", v -> status("onroute")));
        st.addView(Ui.outline(this, "Arrived", v -> status("arrived")));
        st.addView(Ui.button(this, "Job completed", Ui.INK, v -> status("done")));
        body.addView(st);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView map(double lat, double lng) {
        WebView w = new WebView(this);
        WebSettings ws = w.getSettings();
        ws.setJavaScriptEnabled(true);
        w.setWebViewClient(new WebViewClient());   // keep it inside the app
        String bbox = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", lng - 0.008, lat - 0.005, lng + 0.008, lat + 0.005);
        String url = String.format(Locale.US, "https://www.openstreetmap.org/export/embed.html?bbox=%s&layer=mapnik&marker=%.6f,%.6f", bbox, lat, lng);
        w.loadUrl(url);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 230));
        lp.topMargin = Ui.dp(this, 10);
        w.setLayoutParams(lp);
        return w;
    }

    private void navigate(double lat, double lng) {
        Uri nav = Uri.parse(String.format(Locale.US, "google.navigation:q=%.6f,%.6f", lat, lng));
        Intent i = new Intent(Intent.ACTION_VIEW, nav).setPackage("com.google.android.apps.maps");
        if (i.resolveActivity(getPackageManager()) == null) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f", lat, lng, lat, lng)));
        }
        try { startActivity(i); } catch (Exception e) { Toast.makeText(this, "No maps app installed", Toast.LENGTH_LONG).show(); }
    }

    private void callCustomer(Button btn) {
        if (Linkus.inCall()) { App.openCallScreen(this); return; }
        final boolean voip = Linkus.ready();
        btn.setEnabled(false);
        btn.setText("Connecting…");
        JSONObject req = new JSONObject();
        try { req.put("ticket", job.optInt("id")); if (voip) req.put("via", "app"); } catch (Exception ignored) {}
        Api.run(this, "POST", "/app/call", req, (res, err) -> {
            btn.setEnabled(true);
            btn.setText("📞  Call customer");
            if (err != null) {
                new android.app.AlertDialog.Builder(this).setTitle("Call not started").setMessage(err).setPositiveButton("OK", null).show();
                return;
            }
            if (voip && !res.optString("dial").isEmpty()) {
                // In-app call as the driver's Yeastar extension -> PBX masked-call flow -> customer (company number shown)
                CallActivity.label = "Job #" + job.optInt("id") + " · Customer " + job.optString("customer");
                com.yeastar.linkus.service.call.YlsCallManager.getInstance().makeNewCall(res.optString("dial"), true);
                App.openCallScreen(this);
                return;
            }
            new android.app.AlertDialog.Builder(this)
                    .setTitle("📞 Your phone will ring now")
                    .setMessage("The app isn't connected to the PBX right now, so the company line is calling your mobile instead. Answer it to be connected to the customer.")
                    .setPositiveButton("OK", null).show();
        });
    }

    private void status(String s) {
        JSONObject req = new JSONObject();
        try { req.put("ticket", job.optInt("id")).put("status", s); } catch (Exception ignored) {}
        Api.run(this, "POST", "/app/status", req, (res, err) -> {
            if (err != null) { Toast.makeText(this, err, Toast.LENGTH_LONG).show(); return; }
            Toast.makeText(this, "CRM updated", Toast.LENGTH_SHORT).show();
            if ("done".equals(s)) { finish(); return; }
            JSONObject j = res.optJSONObject("job");
            if (j != null) { job = j; render(); }
        });
    }
}
