package com.roadassist.driver;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeastar.linkus.constant.YlsConstant;
import com.yeastar.linkus.service.call.YlsCallManager;
import com.yeastar.linkus.service.call.vo.CallStateVo;
import com.yeastar.linkus.service.call.vo.InCallVo;
import com.yeastar.linkus.utils.SoundManager;

/**
 * Call screen (Linkus SDK). Real phone numbers are never displayed:
 * any number of 7+ digits is shown as "Customer (number hidden)".
 */
public class CallActivity extends Activity implements App.CallListener {
    /** Optional label set by JobActivity before dialling. */
    public static volatile String label = null;

    private TextView who, state, timer;
    private Button answer, decline, mute, speaker, hangup;
    private long connectedAt = 0;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable clock = new Runnable() {
        @Override public void run() {
            if (connectedAt > 0) {
                long s = (SystemClock.elapsedRealtime() - connectedAt) / 1000;
                timer.setText(String.format("%02d:%02d", s / 60, s % 60));
            }
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Color.parseColor("#0B3B37"));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 24);
        root.setPadding(p, Ui.dp(this, 90), p, p);

        who = Ui.text(this, "", 24, Color.WHITE, true);
        who.setGravity(Gravity.CENTER);
        root.addView(who);
        state = Ui.text(this, "", 16, Color.parseColor("#99F6E4"), false);
        state.setGravity(Gravity.CENTER);
        state.setPadding(0, Ui.dp(this, 8), 0, 0);
        root.addView(state);
        timer = Ui.text(this, "", 20, Color.WHITE, false);
        timer.setGravity(Gravity.CENTER);
        timer.setPadding(0, Ui.dp(this, 12), 0, 0);
        root.addView(timer);
        TextView note = Ui.text(this, "🔒 Customer number hidden", 13, Color.parseColor("#A7C4C1"), false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, Ui.dp(this, 6), 0, 0);
        root.addView(note);

        LinearLayout spacer = new LinearLayout(this);
        root.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));

        answer = Ui.button(this, "Answer", Color.parseColor("#16A34A"), v -> act("answer"));
        decline = Ui.button(this, "Decline", Ui.RED, v -> act("decline"));
        mute = Ui.button(this, "Mute", Color.parseColor("#1F5C56"), v -> act("mute"));
        speaker = Ui.button(this, "Speaker", Color.parseColor("#1F5C56"), v -> act("speaker"));
        hangup = Ui.button(this, "End call", Ui.RED, v -> act("hangup"));
        root.addView(answer);
        root.addView(decline);
        root.addView(mute);
        root.addView(speaker);
        root.addView(hangup);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        App.callListener = this;
        refresh(null);
        h.post(clock);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(clock);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (App.callListener == this) App.callListener = null;
        if (!Linkus.inCall()) label = null;
    }

    private InCallVo current() {
        try { return YlsCallManager.getInstance().getFirstCall(); } catch (Throwable t) { return null; }
    }

    @Override
    public void onCallState(CallStateVo vo) { runOnUiThread(() -> refresh(vo)); }

    @Override
    public void onFinished() { runOnUiThread(this::endSoon); }

    private void endSoon() {
        state.setText("Call ended");
        h.postDelayed(() -> { if (!Linkus.inCall()) finish(); }, 900);
    }

    private void refresh(CallStateVo ev) {
        InCallVo c = current();
        if (c == null) {
            if (ev != null && ev.getStatus() == YlsConstant.SIP_DISCONNECT) {
                int code = ev.getStatusCode();
                state.setText(code == 486 ? "Busy" : code == 480 || code == 408 ? "No answer" : "Call ended");
                h.postDelayed(this::finish, 1200);
            } else if (!Linkus.inCall()) {
                endSoon();
            }
            return;
        }
        // Name: never show an external number.
        String num = c.getCallNumber();
        String name = c.getCallName();
        String shown;
        if (label != null) shown = Linkus.isExternal(label) ? "Number ending " + label.replaceAll("[^0-9]", "").substring(Math.max(0, label.replaceAll("[^0-9]", "").length() - 3)) : label;
        else if (Linkus.isExternal(num) || Linkus.isExternal(name)) shown = "Customer";
        else if (num != null && num.equals(Linkus.dialCode())) shown = "Customer";
        else shown = (name != null && !name.isEmpty() && !name.equals(num)) ? name : "Job alert (" + num + ")";
        who.setText(shown);

        int st = ev != null && ev.getCallId() == c.getCallId() ? ev.getStatus() : c.getCallState();
        boolean incomingRinging = !c.isCallOut() && !c.isAnswer() && st != YlsConstant.SIP_CONFIRMED;
        boolean connected = st == YlsConstant.SIP_CONFIRMED || (c.isAnswer() && st != YlsConstant.SIP_DISCONNECT);

        if (connected && connectedAt == 0) connectedAt = SystemClock.elapsedRealtime();
        if (incomingRinging) state.setText("Incoming call…");
        else if (connected) state.setText(c.isHold() ? "On hold" : "Connected");
        else if (st == YlsConstant.SIP_DISCONNECT) state.setText("Call ended");
        else state.setText("Calling… the PBX is connecting you");

        answer.setVisibility(incomingRinging ? android.view.View.VISIBLE : android.view.View.GONE);
        decline.setVisibility(incomingRinging ? android.view.View.VISIBLE : android.view.View.GONE);
        mute.setVisibility(incomingRinging ? android.view.View.GONE : android.view.View.VISIBLE);
        speaker.setVisibility(incomingRinging ? android.view.View.GONE : android.view.View.VISIBLE);
        hangup.setVisibility(incomingRinging ? android.view.View.GONE : android.view.View.VISIBLE);
        mute.setText(c.isMute() ? "Unmute" : "Mute");
        boolean spk = false;
        try { spk = SoundManager.getInstance().isSpeakerOn(); } catch (Throwable ignored) {}
        speaker.setText(spk ? "Speaker: on" : "Speaker: off");
    }

    private void act(String what) {
        InCallVo c = current();
        if (c == null) { finish(); return; }
        YlsCallManager m = YlsCallManager.getInstance();
        try {
            switch (what) {
                case "answer": m.answerCall(c.getCallId()); break;
                case "decline": m.answerBusy(this, c.getCallId()); break;
                case "hangup": m.hangUpCall(this, c.getCallId()); break;
                case "mute": m.mute(c); break;
                case "speaker":
                    SoundManager sm = SoundManager.getInstance();
                    sm.changeAudioRoute(sm.isSpeakerOn() ? YlsConstant.AUDIO_EARPIECE : YlsConstant.AUDIO_SPEAKER, this);
                    break;
            }
        } catch (Throwable t) {
            state.setText("Error: " + t.getMessage());
        }
        h.postDelayed(() -> refresh(null), 300);
    }

    @Override
    public void onBackPressed() {
        if (!Linkus.inCall()) super.onBackPressed();
        else moveTaskToBack(true);   // keep the call running
    }
}
