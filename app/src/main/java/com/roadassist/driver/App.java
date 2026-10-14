package com.roadassist.driver;

import android.app.ActivityManager;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

import com.yeastar.linkus.service.base.YlsBaseManager;
import com.yeastar.linkus.service.base.YlsInitConfig;
import com.yeastar.linkus.service.call.YlsCallManager;
import com.yeastar.linkus.service.call.vo.CallStateVo;
import com.yeastar.linkus.service.callback.ActionCallback;
import com.yeastar.linkus.service.callback.CallStateCallback;
import com.yeastar.linkus.service.callback.SdkCallback;

public class App extends Application {
    public static final String CH_JOBS = "jobs";
    public static final String CH_DUTY = "duty";

    /** Current call screen listener (set by CallActivity). */
    public interface CallListener { void onCallState(CallStateVo vo); void onFinished(); }
    public static volatile CallListener callListener;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!isMainProcess()) return;
        createChannels();

        // Linkus SDK must be initialised once, in the main process.
        String path = YlsBaseManager.getInstance().getProjectPath(this);
        YlsInitConfig config = new YlsInitConfig.Builder(path).key("roadassist").supportCallWaiting(false).build();
        YlsBaseManager.getInstance().initYlsSDK(this, config);

        YlsBaseManager.getInstance().setSdkCallback(new SdkCallback() {
            @Override public void onCdrChange(int syncResult) {}
            @Override public void onLogout(int type) { Linkus.set("Logged out by PBX (" + type + ")"); }
            @Override public void onReconnectSuccess() { Linkus.set("Connected · ext " + Prefs.ext(App.this)); }
        });

        YlsCallManager.getInstance().setCallStateCallback(new CallStateCallback() {
            @Override public void onCallStateChange(CallStateVo vo) {
                CallListener l = callListener;
                if (l != null) l.onCallState(vo);
            }
            @Override public void onNetWorkLevelChange(int callId, int level) {}
            @Override public void onConnectChange() {}
            @Override public void onRecordChange(boolean recording) {}
        });

        YlsCallManager.getInstance().setActionCallback(new ActionCallback() {
            @Override public void onNewCall() { openCallScreen(App.this); }   // incoming call (e.g. CRM job alert)
            @Override public void onFinishCall() {
                CallListener l = callListener;
                if (l != null) l.onFinished();
            }
            @Override public void onCallWaiting() {}
            @Override public void onMissCallClick() {}
            @Override public void onStopMicroPhoneService() {}
            @Override public void onDismissPopupView() {}
            @Override public void onNotifyAudioChange() {}
            @Override public void onMissCallArrive(String caller) {}
        });

        if (Prefs.paired(this)) Linkus.ensureLogin(this);
    }

    public static void openCallScreen(Context c) {
        Intent i = new Intent(c, CallActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        c.startActivity(i);
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel jobs = new NotificationChannel(CH_JOBS, "New jobs", NotificationManager.IMPORTANCE_HIGH);
        jobs.setDescription("New job or customer location from the CRM");
        nm.createNotificationChannel(jobs);
        NotificationChannel duty = new NotificationChannel(CH_DUTY, "On duty", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(duty);
    }

    private boolean isMainProcess() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null || am.getRunningAppProcesses() == null) return true;
        int pid = Process.myPid();
        for (ActivityManager.RunningAppProcessInfo p : am.getRunningAppProcesses()) {
            if (p.pid == pid) return getPackageName().equals(p.processName);
        }
        return true;
    }
}
