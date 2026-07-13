# Running BYD Trip Stats on DiLink 5.0 (e.g. Sealion 7)

DiLink-5 head units (Sealion 7 and other newer BYDs, Android 11+) are supported by a
separate `dilink5` build flavor. Unlike DiLink-3, the OEM `bydauto` SDK classes are **not**
on the head unit's boot classpath on DiLink-5 — they have to be bundled into the app. That
SDK is BYD's proprietary binary, so it is **never committed or redistributed**: the repo ships
only hand-written, signature-only stubs (for compilation), and **you regenerate the real SDK
locally from a system app that is already on your own car.**

Because of that, there is no prebuilt DiLink-5 APK on the Releases page — you build it yourself
once (it's quick). The DiLink-3 APK on Releases does **not** work on DiLink-5.

## What you need

- A DiLink-5 car (DiLink 5.0 / Android 11, arm64 — e.g. Sealion 7)
- `adb` connected to the head unit (WiFi ADB is fine)
- A JDK **17 or 21** and the Android SDK (the repo builds with Gradle)
- [`dex2jar`](https://github.com/pxb1988/dex2jar) on your `PATH`
- `unzip`, `jar` (from the JDK)

## 1. Regenerate `dilink5-sdk.jar` from your own car (stays local, gitignored)

The bydauto SDK lives inside the system app `com.byd.data.collect`, already installed on your car.
Connect adb, then run the helper — it pulls that apk, converts it with dex2jar, and keeps only the
`android/hardware/bydauto/*` classes:

```bash
adb connect <head-unit-ip>:5555
tools/make-dilink5-sdk-jar.sh            # pulls com.byd.data.collect from the car automatically
# → writes app/libs/dilink5-sdk.jar  (gitignored — do not commit or share it)
```

Already have the apk on disk? Pass it and adb isn't needed:

```bash
tools/make-dilink5-sdk-jar.sh /path/to/com.byd.data.collect.apk
```

## 2. Build the DiLink-5 APK

```bash
./gradlew assembleDilink5Release      # signed release (needs a keystore in local.properties)
# or, no keystore setup needed:
./gradlew assembleDilink5Debug
```

Output: `app/build/outputs/apk/dilink5/<release|debug>/byd-trip-stats-*-d5.apk`

> Without `dilink5-sdk.jar` present the build still succeeds (that's how upstream CI verifies
> the flavor compiles), but the resulting APK reads **no** vehicle data. Steps 1–2 are what make
> it functional.

## 3. Install and first-run

```bash
adb install app/build/outputs/apk/dilink5/release/byd-trip-stats-*-d5.apk
```

On first launch:

1. A one-time **ADB authorization** prompt lets the app grant itself the permissions it needs
   over its bundled local `adb` channel (accept the "Allow USB debugging" dialog on the car).
2. An **"Allow vehicle data access?"** dialog appears. On DiLink-5 the app must relax one
   head-unit system setting (the hidden-API restriction, scoped to the BYD `com.ts.*` vehicle
   libraries) so the bundled SDK can bind. Tap **Allow**.
   - Decline it and the app still runs but shows no vehicle data. You can turn it on later under
     **Settings → Vehicle data access**.
3. Telemetry populates: SoC, range, speed, per-wheel tyre pressure/temperature, drive mode,
   12 V, ambient temperature, HV pack voltage/current and power.

## Notes

- The hidden-API exemption is a device-global setting that **resets on reboot**; the app
  re-applies it (only when missing) on the next launch after you've consented once.
- Tyre **temperatures** update only while driving — the TPMS temp sensors sleep when parked.
- Keep `app/libs/dilink5-sdk.jar` local. It is BYD's binary; only the signature-only stubs
  (`app/src/dilink3/.../bydauto`, `dilink5-stubsrc/`) live in the repo.
