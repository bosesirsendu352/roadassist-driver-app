package com.roadassist.driver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Driver's job list. Refreshes every 15 s while open. */
public class MainActivity extends Activity {
    private LinearLayout list;
    private TextView linkusState;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { load(); h.postDelayed(this, 15000); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Prefs.paired(this)) {
            startActivity(new Intent(this, PairActivity.class));
            finish();
            return;
        }
        LinearLayout root = Ui.column(this);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 36), p, p);

        LinearLayout top = Ui.row(this);
        LinearLayout who = Ui.column(this);
        who.addView(Ui.text(this, "Hi " + Prefs.name(this), 24, Ui.INK, true));
        linkusState = Ui.text(this, Linkus.state(), 13, Ui.MUTED, false);
        who.addView(linkusState);
        top.addView(who, new LinearLayout.LayoutParams(0, -2, 1));
        TextView menu = Ui.text(this, "⋮", 26, Ui.MUTED, true);
        menu.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 4), 0);
        menu.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Driver " + Prefs.name(this) + " · ext " + Prefs.ext(this))
                .setItems(new String[]{"Refresh", "Unpair this phone"}, (d, which) -> {
                    if (which == 0) { Linkus.ensureLogin(this); load(); }
                    else {
                        stopService(new Intent(this, DutyService.class));
                        Prefs.clear(this);
                        startActivity(new Intent(this, PairActivity.class));
                        finish();
                    }
                }).show());
        top.addView(menu);
        root.addView(top);

        root.addView(Ui.outline(this, "☎  Dialpad · call agent / extension / emergency", v -> startActivity(new Intent(this, DialActivity.class))));

        TextView hdr = Ui.text(this, "My jobs", 15, Ui.MUTED, true);
        hdr.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 8));
        root.addView(hdr);
        list = Ui.column(this);
        root.addView(list);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.addView(root);
        setContentView(sv);

        askPermissions();
        startForegroundService(new Intent(this, DutyService.class));
    }

    private void askPermissions() {
        List<String> need = new ArrayList<>();
        need.add(android.Manifest.permission.RECORD_AUDIO);
        need.add(android.Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT >= 31) need.add("android.permission.BLUETOOTH_CONNECT");
        if (Build.VERSION.SDK_INT >= 33) need.add("android.permission.POST_NOTIFICATIONS");
        List<String> ask = new ArrayList<>();
        for (String s : need) if (checkSelfPermission(s) != PackageManager.PERMISSION_GRANTED) ask.add(s);
        if (!ask.isEmpty()) requestPermissions(ask.toArray(new String[0]), 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list == null) return;
        Linkus.setListener(st -> linkusState.setText(st));
        linkusState.setText(Linkus.state());
        Linkus.ensureLogin(this);
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
        Linkus.setListener(null);
    }

    private void load() {
        Api.run(this, "GET", "/app/jobs", null, (res, err) -> {
            if (isFinishing()) return;
            if (err != null) {
                if (res != null && res.optInt("_status") == 401) {  // driver removed / re-created in CRM
                    Prefs.clear(this);
                    startActivity(new Intent(this, PairActivity.class));
                    finish();
                    return;
                }
                showMessage(err);
                return;
            }
            JSONArray jobs = res.optJSONArray("jobs");
            list.removeAllViews();
            if (jobs == null || jobs.length() == 0) { showMessage("No open jobs. New jobs from the CRM appear here."); return; }
            for (int i = 0; i < jobs.length(); i++) list.addView(jobCard(jobs.optJSONObject(i)));
        });
    }

    private void showMessage(String s) {
        list.removeAllViews();
        LinearLayout c = Ui.card(this);
        TextView t = Ui.text(this, s, 15, Ui.MUTED, false);
        t.setGravity(Gravity.CENTER);
        c.addView(t);
        list.addView(c);
    }

    private LinearLayout jobCard(JSONObject j) {
        LinearLayout c = Ui.card(this);
        LinearLayout r = Ui.row(this);
        r.addView(Ui.text(this, "Job #" + j.optInt("id") + " · " + j.optString("concern"), 18, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(Ui.text(this, Ui.status(j.optString("status")), 13, Ui.TEAL, true));
        c.addView(r);
        c.addView(Ui.text(this, "Customer " + j.optString("customer") + " (hidden)", 13, Ui.MUTED, false));
        boolean hasLoc = !j.isNull("lat") && !j.isNull("lng");
        String loc = hasLoc ? "📍 " + (j.optString("location").isEmpty() ? "Location shared" : j.optString("location"))
                : "📍 Waiting for the customer's location";
        TextView lt = Ui.text(this, loc, 15, hasLoc ? Ui.INK : Ui.AMBER, false);
        lt.setPadding(0, Ui.dp(this, 6), 0, 0);
        c.addView(lt);
        c.setOnClickListener(v -> startActivity(new Intent(this, JobActivity.class).putExtra("job", j.toString())));
        return c;
    }
}
