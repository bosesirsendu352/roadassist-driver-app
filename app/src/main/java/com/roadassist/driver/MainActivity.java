package com.roadassist.driver;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.List;

/**
 * The driver app is the CRM's driver web app (/d) in a WebView. The web app registers on the Yeastar PBX as the
 * driver's extension (Linkus WebRTC SDK over HTTPS 443), so calls go phone → PBX → customer, showing the pilot number.
 */
public class MainActivity extends Activity {
    private static final int REQ_PERMS = 7;
    private WebView web;
    private PermissionRequest pendingMic;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0f172a"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " RoadAssistApp/" + BuildConfig.VERSION_NAME);

        web.addJavascriptInterface(new Bridge(), "RoadAssist");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String crmHost = Uri.parse(Prefs.crmUrl(MainActivity.this)).getHost();
                if ("tel".equals(u.getScheme())) { startActivity(new Intent(Intent.ACTION_DIAL, u)); return true; }
                if (crmHost != null && crmHost.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;                                   // maps / navigation open outside the app
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest req) {
                runOnUiThread(() -> {
                    if (hasMic()) req.grant(req.getResources());
                    else { pendingMic = req; askPermissions(); }
                });
            }
        });

        askPermissions();
        if (b != null) web.restoreState(b); else web.loadUrl(startUrl(getIntent()));
        if (Prefs.paired(this)) startDuty();
    }

    private String startUrl(Intent i) {
        StringBuilder u = new StringBuilder(Prefs.crmUrl(this)).append("/d#");
        if (Prefs.paired(this)) u.append("t=").append(Prefs.token(this)).append('&');
        int job = i == null ? 0 : i.getIntExtra("jobId", 0);
        if (job > 0) u.append("job=").append(job);
        return u.toString();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        int job = i.getIntExtra("jobId", 0);
        if (job > 0 && web != null)
            web.evaluateJavascript("window.RoadAssistOpenJob&&window.RoadAssistOpenJob(" + job + ")", null);
    }

    private boolean hasMic() { return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }

    private void askPermissions() {
        List<String> need = new ArrayList<>();
        if (!hasMic()) need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            need.add("android.permission.POST_NOTIFICATIONS");
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (pendingMic != null) {
            if (hasMic()) pendingMic.grant(pendingMic.getResources()); else pendingMic.deny();
            pendingMic = null;
        }
        if (hasMic() && Prefs.paired(this)) startDuty();
    }

    private void startDuty() {
        if (!hasMic()) return;   // a microphone-type foreground service needs the mic permission first
        try { startForegroundService(new Intent(this, DutyService.class)); } catch (Exception ignored) {}
    }

    /** Called from the web app (window.RoadAssist) after pairing. */
    private class Bridge {
        @JavascriptInterface
        public void paired(String token, String name, String ext) {
            Prefs.savePairing(MainActivity.this, Prefs.crmUrl(MainActivity.this), token, name, ext);
            runOnUiThread(MainActivity.this::startDuty);
        }
        @JavascriptInterface
        public void unpaired() {
            Prefs.clear(MainActivity.this);
            runOnUiThread(() -> stopService(new Intent(MainActivity.this, DutyService.class)));
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else moveTaskToBack(true);   // keep the extension online
    }

    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); if (web != null) web.saveState(o); }
}
