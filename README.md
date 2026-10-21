# JOS VPN India — Android Studio starter

Target: Android 13/14 and newer compatible devices (minSdk 26).

## What is included
- Kotlin Android app UI
- WireGuard tunnel library integration
- Import a WireGuard `.conf` profile
- Connect / disconnect controls
- Per-app VPN routing: selected apps can be included in the VPN tunnel
- JioHotstar launcher button
- GitHub Actions workflow to build a debug APK

## Critical limitations
1. **No VPN server is included.** You must obtain a valid WireGuard configuration from a VPN provider that offers an India endpoint, or set up your own server in India. The configuration contains private keys; keep it secret.
2. **India location is not automatic.** The imported WireGuard profile determines the server location. Confirm the endpoint is actually in India.
3. **Per-app routing is not a full firewall.** Apps not selected for the tunnel may use their normal connection; this project does not block all their internet access. Android's public VPN APIs do not provide a simple app-level packet-blocking firewall when another VPN tunnel is already handling traffic. Do not label this as a complete firewall.
4. **Hotstar access is not guaranteed.** JioHotstar may enforce regional, account, subscription, or VPN restrictions. Use the service in accordance with its terms and applicable law.
5. This is a starter project. Test it on a non-critical device before relying on it.

## Open in Android Studio
1. Extract the ZIP.
2. Open the `JOSVPN-India-Android` folder in Android Studio.
3. Use JDK 17.
4. Allow Gradle to sync.
5. Run on an Android 13/14 device.

## Build APK using GitHub Actions
1. Create or open a GitHub repository.
2. Upload all files and folders from this project to the repository root.
3. Ensure `.github/workflows/build-apk.yml` is committed.
4. Open **Actions** → **Build JOS VPN India APK** → **Run workflow**.
5. After success, download the `JOSVPN-India-debug-apk` artifact and extract `app-debug.apk`.

## Import a profile
Get a WireGuard `.conf` file from your VPN provider or your own server. In the app, tap **Import India WireGuard .conf**, select the file, then tap **Connect VPN** and approve Android's VPN permission.

Never share the `.conf` file publicly because it contains private key material.
