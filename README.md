# RoadAssist Driver (Android)

Driver app for the RoadAssist CRM. **No Linkus app or PBX login on the phone.**

* **My jobs**: jobs assigned in the CRM, with a notification for each new job and when the customer's location arrives.
* **Customer location**: a map inside the app plus a **Navigate** button that opens Google Maps.
* **Call customer**: the CRM asks the Yeastar PBX to ring the driver's own mobile first, then the customer. Both see only the company pilot number, and the customer's number never reaches the phone.
* **Status**: On the way / Arrived / Job completed, written back to the CRM.

## Driver setup
1. Install the APK from Releases → latest.
2. Enter the 8-character code from the CRM **Drivers** page.
3. The driver's **mobile number must be filled in** on the CRM Drivers page; that's the phone the PBX rings.

## PBX setup (one time)
* The outbound route used by the bridge (permission of the extension in `BRIDGE_PERM_EXT`, default 1001) must allow calls to mobiles, and the trunk caller ID must be the company pilot number.
* Optional callbacks: an inbound call flow on the pilot number with an HTTP Request to `CRM/api/route?caller=<caller>` (X-Api-Key). A 200 reply is the number to transfer to; a 404 means continue to the AI receptionist.
