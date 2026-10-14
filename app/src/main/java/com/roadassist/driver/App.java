package com.roadassist.driver;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public class App extends Application {
    public static final String CH_JOBS = "jobs";
    public static final String CH_DUTY = "duty";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel jobs = new NotificationChannel(CH_JOBS, "New jobs", NotificationManager.IMPORTANCE_HIGH);
        jobs.setDescription("New job or customer location from the CRM");
        nm.createNotificationChannel(jobs);
        nm.createNotificationChannel(new NotificationChannel(CH_DUTY, "On duty", NotificationManager.IMPORTANCE_LOW));
    }
}
