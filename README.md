# RoadAssist Driver (Android, Linkus SDK)

Driver app for the RoadAssist CRM. It runs on the Yeastar **Linkus SDK** (the Linkus calling engine in your own app).

* **My jobs**: jobs assigned in the CRM, with a notification for each new job and when the customer's location arrives.
* **Customer location**: a map inside the app plus a **Navigate** button that opens Google Maps.
* **Call customer (masked)**: calls through the PBX. The app asks the CRM to prepare the call, then dials call flow 6902, which connects to the customer. The customer's number never reaches the phone; any 7+ digit number is shown as "Customer".
* **Job alert calls**: when the agent clicks Assign, the CRM's job-alert call rings in this app.
* **Status**: On the way / Arrived / Job completed, written back to the CRM timeline.

## 1. PBX (one time)
1. **Integrations → Linkus SDK**: turn it on, then note the **AccessID** and **AccessKey**. A push certificate is optional for this app, which stays connected through its "On duty" notification.
2. In Cloudflare, under **roadassist-crm → Settings → Variables**, add:
   * `LINKUS_SDK_ID`, the AccessID (text)
   * `LINKUS_SDK_KEY`, the AccessKey (**secret**)
   * Optional: `LINKUS_HOST` / `LINKUS_PORT` if the app can't reach the PBX at the defaults (the YS_BASE host and port 8111).

## 2. Build the APK (free)
**Option A: GitHub (no install).** Push this folder to a GitHub repository. The *Build driver APK* action runs on its own; download **RoadAssistDriver-apk** from the run's Artifacts. Public repos are free, and private repos get 2,000 free minutes a month (one build takes about 5 minutes).

**Option B: Android Studio.** Open this folder and choose *Build → Build APK(s)*.

## 3. Driver phone
1. Install the APK. Allow "install unknown apps" for your browser or file manager.
2. In the CRM, open **Drivers** and give the driver their 8-character **Android app code**.
3. Open the app, enter the code, then allow microphone and notifications.
4. The header should say **Connected · ext 1003**.

Note: the same extension can't be logged in to both this app and the normal Linkus mobile app at once; the newer login takes over.
