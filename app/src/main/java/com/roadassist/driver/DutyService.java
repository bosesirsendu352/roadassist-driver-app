package com.roadassist.driver;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * "On duty" foreground service: checks the CRM every 20 s for new jobs / customer locations, posting a notification.
 */
public class DutyService extends Service {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() { check(); h.postDelayed(this, 20000); }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, App.CH_DUTY)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("On duty")
                .setContentText("Extension " + Prefs.ext(this) + " online · waiting for jobs")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(1, n);
        h.removeCallbacks(poll);
        h.post(poll);
        return START_STICKY;
    }

    private void check() {
        if (!Prefs.paired(this)) { stopSelf(); return; }
        Api.run(this, "GET", "/app/jobs", null, (res, err) -> {
            if (err != null || res == null) return;
            JSONArray a = res.optJSONArray("jobs");
            if (a == null) return;
            String seen = Prefs.seen(this);
            StringBuilder now = new StringBuilder();
            for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.optJSONObject(i);
                int id = j.optInt("id");
                boolean loc = !j.isNull("lat");
                String key = "|" + id + (loc ? "L" : "") + "|";
                now.append(key);
                if (seen.contains(key)) continue;
                boolean knownJob = seen.contains("|" + id + "|") || seen.contains("|" + id + "L|");
                String title = !knownJob ? "New job #" + id + " · " + j.optString("concern") : "Customer location received · job #" + id;
                String text = loc ? (j.optString("location").isEmpty() ? "Tap to see the map" : j.optString("location"))
                        : "Waiting for the customer's location";
                notifyJob(id, title, text, j);
            }
            Prefs.setSeen(this, now.toString());
        });
    }

    private void notifyJob(int id, String title, String text, JSONObject j) {
        Intent i = new Intent(this, MainActivity.class).putExtra("jobId", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, id, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, App.CH_JOBS)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(100 + id, n);
    }

    @Override
    public void onDestroy() {
        h.removeCallbacks(poll);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
