package com.roadassist.driver;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

/** Driver app: jobs, customer location, and a masked "Call customer" button (the PBX bridges the call). */
public class App extends Application {
    public static final String CH_JOBS = "jobs";
    public static final String CH_DUTY = "duty";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel jobs = new NotificationChannel(CH_JOBS, "New jobs", NotificationManager.IMPORTANCE_HIGH);
        jobs.setDescription("New job or customer location from the CRM");
        nm.createNotificationChannel(jobs);
        nm.createNotificationChannel(new NotificationChannel(CH_DUTY, "On duty", NotificationManager.IMPORTANCE_LOW));
    }
}
