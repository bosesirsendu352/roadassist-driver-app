package com.roadassist.driver;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/** One-time setup: the agent gives the driver the 8-character code from the CRM "Drivers" page. */
public class PairActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.column(this);
        int p = Ui.dp(this, 20);
        root.setPadding(p, Ui.dp(this, 48), p, p);
        root.setBackgroundColor(Ui.BG);

        root.addView(Ui.text(this, "RoadAssist Driver", 26, Ui.INK, true));
        TextView sub = Ui.text(this, "Enter the driver code from the CRM (Drivers page).", 15, Ui.MUTED, false);
        sub.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 18));
        root.addView(sub);

        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "Driver code", 13, Ui.MUTED, true));
        EditText code = new EditText(this);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setHint("e.g. k7m2xq9p");
        code.setTextSize(20);
        card.addView(code);

        card.addView(Ui.text(this, "CRM address", 13, Ui.MUTED, true));
        EditText crm = new EditText(this);
        crm.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        crm.setText(Prefs.crmUrl(this));
        crm.setTextSize(14);
        card.addView(crm);

        TextView err = Ui.text(this, "", 14, Ui.RED, false);
        card.addView(err);
        root.addView(card);

        Button go = Ui.button(this, "Connect", Ui.TEAL, null);
        go.setOnClickListener(v -> {
            String c = code.getText().toString().trim();
            String base = crm.getText().toString().trim().replaceAll("/+$", "");
            if (c.length() < 8) { err.setText("The code has 8 characters."); return; }
            go.setEnabled(false);
            err.setText("");
            Api.runIo(() -> {
                String error = null;
                JSONObject r = null;
                try {
                    r = Api.call(base, null, "POST", "/app/pair", new JSONObject().put("code", c));
                    if (!r.optBoolean("ok")) error = r.optString("error", "Invalid code");
                } catch (Exception e) {
                    error = "Can't reach the CRM. Check the address and internet.";
                }
                final String fe = error;
                final JSONObject fr = r;
                runOnUiThread(() -> {
                    go.setEnabled(true);
                    if (fe != null) { err.setText(fe); return; }
                    Prefs.savePairing(this, base, fr.optString("token"), fr.optString("name"), fr.optString("ext"));
                    Linkus.ensureLogin(this);
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
            });
        });
        root.addView(go);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Ui.BG);
        sv.addView(root);
        setContentView(sv);
    }
}
