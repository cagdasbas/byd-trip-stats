# Running BYD Trip Stats on DiLink 5.0 (e.g. Sealion 7)

DiLink-5 head units (Sealion 7 and other newer BYDs, Android 11+) are supported by a separate
`dilink5` build flavor. The OEM `bydauto` SDK is BYD's proprietary binary, so it is **never
committed, bundled, or extracted to disk by this app**: the repo ships only hand-written,
signature-only stubs (for compilation), and at runtime the app resolves the real classes by
referencing the `com.byd.data.collect` system app that's already installed on your car — nothing
is ever copied out of it.

Because of that, there is no prebuilt DiLink-5 APK on the Releases page — you build it yourself
once (it's quick, no extra tooling needed). The DiLink-3 APK on Releases does **not** work on
DiLink-5.

## What you need

- A DiLink-5 car (DiLink 5.0 / Android 11, arm64 — e.g. Sealion 7)
- `adb` connected to the head unit (WiFi ADB is fine), for install only
- A JDK **17 or 21** and the Android SDK (the repo builds with Gradle)

## 1. Build the DiLink-5 APK

```bash
./gradlew assembleDilink5Release      # signed release (needs a keystore in local.properties)
# or, no keystore setup needed:
./gradlew assembleDilink5Debug
```

Output: `app/build/outputs/apk/dilink5/<release|debug>/byd-trip-stats-*-d5.apk`

## 2. Install and first-run

```bash
adb install app/build/outputs/apk/dilink5/release/byd-trip-stats-*-d5.apk
```

On first launch:

1. A one-time **ADB authorization** prompt lets the app grant itself the permissions it needs
   over its bundled local `adb` channel (accept the "Allow USB debugging" dialog on the car).
2. An **"Allow vehicle data access?"** dialog appears. On DiLink-5 the app must relax one
   head-unit system setting (the hidden-API restriction, scoped to the BYD `com.ts.*` and
   `dalvik.system` namespaces) so it can bind the OEM SDK. Tap **Allow** — the app applies the
   setting and restarts itself automatically.
   - Decline it and you'll get a confirmation ("are you sure?") before it takes effect; the app
     still runs but shows no vehicle data. You can turn it on later under **Settings → Vehicle
     data access**.
3. Telemetry populates: SoC, range, speed, per-wheel tyre pressure/temperature, drive mode,
   12 V, ambient temperature, HV pack voltage/current and power.

## Notes

- The hidden-API exemption is a device-global setting that **resets on reboot**; the app
  re-applies it (only when missing) on the next launch after you've consented once.
- Tyre **temperatures** update only while driving — the TPMS temp sensors sleep when parked.
- Only the signature-only stubs (`app/src/dilink3/.../bydauto`, `dilink5-stubsrc/`) live in the
  repo. Nothing extracted from `com.byd.data.collect` is ever written to disk by this app — the
  classes are resolved live from its already-installed apk on every launch.
