package com.roadassist.driver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Dialpad. Company extensions / agent: the PBX rings this phone first, then connects the number
 * (shows the company number). Emergency numbers open the phone's own dialer (always works, no PBX needed).
 */
public class DialActivity extends Activity {
    private TextView display;
    private final StringBuilder num = new StringBuilder();
    private String agent = "6400";
    private LinearLayout emergencyRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.column(this);
        int p = Ui.dp(this, 16);
        root.setPadding(p, Ui.dp(this, 36), p, p);

        TextView back = Ui.text(this, "‹ My jobs", 15, Ui.TEAL, true);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(Ui.text(this, "Dialpad", 24, Ui.INK, true));
        root.addView(Ui.text(this, "Calls go through the company PBX as your extension. Emergency numbers use your phone directly.", 13, Ui.MUTED, false));

        display = Ui.text(this, "", 32, Ui.INK, true);
        display.setGravity(Gravity.CENTER);
        display.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 8));
        root.addView(display);

        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "⌫"}};
        for (String[] row : keys) {
            LinearLayout r = Ui.row(this);
            for (String k : row) {
                Button key = Ui.outline(this, k, v -> press(k));
                key.setTextSize(22);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1);
                lp.setMargins(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
                key.setLayoutParams(lp);
                r.addView(key);
            }
            root.addView(r);
        }

        Button call = Ui.button(this, "📞  Call", Ui.TEAL, v -> dial(num.toString()));
        root.addView(call);
        Button agentBtn = Ui.button(this, "🎧  Call an agent", Ui.INK, v -> dial(agent));
        root.addView(agentBtn);

        TextView eh = Ui.text(this, "Emergency (uses your phone directly)", 13, Ui.MUTED, true);
        eh.setPadding(0, Ui.dp(this, 18), 0, 0);
        root.addView(eh);
        emergencyRow = Ui.row(this);
        root.addView(emergencyRow);
        setEmergency(new String[][]{{"999", "Police"}, {"998", "Ambulance"}, {"997", "Fire"}});

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.addView(root);
        setContentView(sv);

        // Agent number + emergency list come from the CRM
        Api.run(this, "GET", "/app/me", null, (res, err) -> {
            if (err != null || res == null) return;
            agent = res.optString("agent", agent);
            JSONArray em = res.optJSONArray("emergency");
            if (em != null && em.length() > 0) {
                String[][] list = new String[em.length()][2];
                for (int i = 0; i < em.length(); i++) {
                    JSONObject o = em.optJSONObject(i);
                    list[i][0] = o.optString("number");
                    list[i][1] = o.optString("label");
                }
                setEmergency(list);
            }
        });
    }

    private void setEmergency(String[][] list) {
        emergencyRow.removeAllViews();
        for (String[] e : list) {
            Button btn = Ui.button(this, e[1] + "\n" + e[0], Ui.RED, v -> {
                // Opens the phone's dialer with the number filled in; the driver presses call.
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + e[0])));
            });
            btn.setTextSize(14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1);
            lp.setMargins(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), 0);
            btn.setLayoutParams(lp);
            emergencyRow.addView(btn);
        }
    }

    private void press(String k) {
        if ("⌫".equals(k)) { if (num.length() > 0) num.setLength(num.length() - 1); }
        else if (num.length() < 20) num.append(k);
        display.setText(num.toString());
    }

    private void dial(String number) {
        if (number == null || number.trim().isEmpty()) return;
        if (Linkus.ready() && !Linkus.inCall()) {
            // Call as the driver's Yeastar extension (VoIP). The PBX applies the extension's call permissions.
            CallActivity.label = number;
            com.yeastar.linkus.service.call.YlsCallManager.getInstance().makeNewCall(number.trim(), true);
            App.openCallScreen(this);
            return;
        }
        JSONObject req = new JSONObject();
        try { req.put("number", number); } catch (Exception ignored) {}
        Api.run(this, "POST", "/app/dial", req, (res, err) -> {
            if (err != null) {
                new AlertDialog.Builder(this).setTitle("Call not started").setMessage(err).setPositiveButton("OK", null).show();
                return;
            }
            new AlertDialog.Builder(this).setTitle("📞 Your phone will ring now")
                    .setMessage("Answer the call from the company number to be connected to " + number + ".")
                    .setPositiveButton("OK", null).show();
        });
    }
}
