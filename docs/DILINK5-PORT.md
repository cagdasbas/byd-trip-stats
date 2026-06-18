# DiLink-5 (Sealion 7) port — design & status

Goal: extend trip-stats beyond DiLink-3 to DiLink-5 (Sealion 7, `ro.vehicle.type=Di5.0_DXF_W`,
Android 11/SDK 30). Grounded in the firmware analysis in the `byd-apps` repo
(`device-dump/FINDINGS-dilink5-live-data.md`, `FINDINGS-sdk-divergence-d3-d5.md`,
`FINDINGS-aaos-carproperty-path.md`).

## How trip-stats reads the car today (DiLink-3)

Pure reflection over the BYD SDK:
- `BydVehicleDataSource.tryDynamicDevice(className, …)` → `Class.forName(className)` +
  `getInstance(ctx)` for each device.
- Listeners: it subclasses the **abstract** per-device listener — `object : AbsBYDAutoChargingListener() {…}`,
  `AbsBYDAutoGearboxListener`, `AbsBYDAutoTyreListener` — and calls `registerListener(it)`.
  Some devices instead get a `java.lang.reflect.Proxy` over an **interface** (`listenerInterfaceName`).
- Statistic (SoC, mileage, energy): the feature-ID path — `trySubscribeStatisticFeatures`,
  `pollStatisticFeature(device, featureId)`, `dispatchStatisticFeatureEvent` — i.e. the generic
  `get(featureId)` register interface.
- The vendored classes in `app/src/main/java/android/hardware/bydauto/**` are **thin stubs**
  (`getInstance` returns null, getters return 0). They exist only so the app **compiles**; at
  runtime DiLink-3 supplies the real classes on the **boot classpath**, which (parent-first
  class loading) shadow the bundled stubs.

## Why this breaks on DiLink-5

On DiLink-5 the `android.hardware.bydauto.*` classes are **NOT on the boot classpath** — they
are bundled per-app (confirmed: present in `com.byd.data.collect`, absent from every framework
jar). Consequences for trip-stats as-is:
1. `Class.forName("…BYDAutoChargingDevice")` resolves to the **bundled thin stub** →
   `getInstance` returns null → device "unavailable". Devices with no stub (e.g. statistic) →
   `ClassNotFoundException`.
2. `object : AbsBYDAutoChargingListener()` extends the **thin stub**, not the real abstract
   class → even if registered, no callbacks.
3. The statistic feature-ID path is doubly broken on D5: the generic `get(devType,fid)` returns
   defaults (all 0) and a Proxy-over-interface listener registers but delivers **zero callbacks**
   (both verified on the car via byd-probe). DiLink-5 live statistic data requires subclassing
   the **typed** `AbsBYDAutoStatisticListener` (`onElecPercentageChanged`=SoC,
   `onTotalMileageValueChanged`, …) — see `byd-apps/device-dump/FINDINGS-dilink5-live-data.md`.

Also note signature drift: `getTotalMileageValue()` is `int` on D3 but **`float`** on D5 (Dalvik
resolves by name+return-type → `NoSuchMethodError`). Statistic on D3 is poll-only (6 getters, no
listener); D5 reshapes it (~50 getters + the typed listener).

## Gating dependency (confirm on the car FIRST, via byd-probe)

The DiLink-5 bydauto runtime references `com.ts.lib.caradapter.*`, which is **not bundled** in
`com.byd.data.collect` and not in any pulled framework jar, and `data.collect` declares no
`<uses-library>`. **Open question:** can a normal sideloaded app load `com.ts.lib.caradapter` at
runtime? The `byd-probe` DiLink-5 variant ("LISTEN D5" button) answers this directly:
- success (`[LIVE] SoC% = …`) → the listener path works → this port is viable as designed.
- `NoClassDefFoundError: com.ts.lib.caradapter` → the SDK needs a shared lib we must locate
  (which component exports it) before any app — trip-stats included — can use it.

**Do not start the runtime data-path changes until byd-probe confirms this.**

## Proposed architecture (post-confirmation)

Mirror the byd-probe variant approach using **Gradle product flavors** so one codebase ships both:

- `dilink3` flavor (default, unchanged): current thin stubs, current behavior. Zero risk to
  existing users.
- `dilink5` flavor:
  - bundles the **real DiLink-5 bydauto classes** (from `com.byd.data.collect`) as a runtime
    library so `Class.forName`/`getInstance` and the abstract-listener subclasses resolve to the
    real implementations. (Get them onto the compile+runtime classpath as a jar — e.g.
    `dex2jar` the dex, add via `dilink5Implementation files(...)`; the OEM artifact stays out of
    git, regenerated from the device APK, same policy as `byd-apps/device-dump`.)
  - adds a **statistic typed-listener** path: `object : AbsBYDAutoStatisticListener()` overriding
    `onElecPercentageChanged`/`onTotalMileageValueChanged`/`onEVMileageValueChanged`/… → feed the
    existing `dispatchStatisticFeatureEvent`/snapshot pipeline (map callbacks to the same internal
    fields the D3 feature-ID path fills).
  - D5-correct signatures (`getTotalMileageValue():float`, etc.).
- Runtime gate: `DiLink5Platform.isDiLink5` (`Build.VERSION.SDK_INT >= 30` &&
  `SystemProperties ro.vehicle.type` starts with `Di5`) selects the D5 code paths; the flavor
  selects the SDK classes. Keep the AAOS `CarPropertyManager` path OUT (needs
  `CAR_VENDOR_EXTENSION` = signature|privileged — closed to sideloaded apps; see findings).

## Concrete next steps

1. **(blocked on car)** Run byd-probe DiLink-5 build on the Sealion 7; resolve the
   `com.ts.lib.caradapter` question. If it needs a shared lib, find the exporter.
2. Add the `dilink3`/`dilink5` product flavors (dilink3 = exact current build; verify the
   release APK is byte-for-byte behavior-identical).
3. Move the thin stubs to the `dilink3` source set; wire the real D5 SDK into `dilink5`.
4. Implement the D5 statistic typed-listener; route into the existing snapshot pipeline.
5. Validate on the car; then flip the "Only DiLink 3 supported" gates
   (`CarConfig.kt:610`, `InitializationScreen.kt:97`) for the Sealion 7.

Status: **design only.** No runtime code changed pending step 1.

---

# IMPLEMENTATION PLAN (grounded in on-car capture 2026-06-18)

Data source: `byd-apps/device-dump/captures/probe-log-2026-06-18.txt` (drive + charge,
SoC 35→55%, DC charge ~47 kW). The SAVE-JSON files are empty (CAPTURE logs to text, not the
`scan` map) — use the log.

## Confirmed ABRP field → DiLink-5 source

| ABRP `/tlm/send` | DiLink-5 source | notes |
|---|---|---|
| `soc` | `BYDAutoStatisticDevice.getElecPercentageValue()` / `onElecPercentageChanged` | % ✓ |
| `est_battery_range` | `getElecDrivingRangeValue()` / `onElecDrivingRangeChanged` | km ✓ (==ByStandard) |
| `capacity` | `getEVRemainingBatteryPower()` ÷ (soc/100) ≈ **70.5 kWh** | steady across SoC → hardcode 70.5 in CarConfig for the TR variant; usable energy now = `getEVRemainingBatteryPower()` (kWh) |
| `speed` | `BYDAutoSpeedDevice.getSpeedValue()` (float km/h) | ✓ (`getCurrentSpeed` = int) |
| `is_charging` | `BYDAutoChargingDevice.onChargerStateChanged`==1 / `onChargingPowerChanged`>0 | ✓ |
| `power` (charging) | `-onChargingPowerChanged` (kW; **filter >150** — 359.4 spikes) | ✓ negative = into battery |
| `power` (driving) | **no direct getter** — derive (see below) | ⚠ motor device didn't bind |
| `odometer` | `getTotalMileageValue()` / `onTotalMileageValueChanged` | km ✓ |
| `is_dcfc` | infer: charging & power > ~25 kW ⇒ DC | `getChargingType()` returns 0 (unpopulated) |
| `batt_temp` | **unavailable** (`getChargeBatteryTemp()`=0) | omit |
| `voltage`,`current` | **unavailable** (return 0) | omit |
| `soh` | not surfaced | omit / CarConfig estimate |
| `ext_temp`,`lat/lon/elev/heading` | Android (already in trip-stats), not BYD SDK | unchanged |

Notes: `getRemainingBatteryPower()` ≈ SoC% (redundant). Fuel/HEV getters are sentinels
(0xFFFFF / 2046 / 255) on this BEV — ignore. `statistic` getters return 0 until a listener is
registered (registering primes the TS adapter) — register early.

## Driving-power options (pick during impl)
1. Derive: `power_kW ≈ -Δ(getEVRemainingBatteryPower)/Δt × 3600`, smoothed over ~10–30 s
   (energy steps are ~0.6–0.8 kWh, so instantaneous is bursty — use a moving average).
2. Investigate offline: does any non-`motor` device expose instantaneous kW (trip-stats reads
   "live power" on DiLink-3 — check which getter, and whether a different MOTOR perm name binds
   on D5). 
3. Acceptable fallback: omit `power` while driving (soc+speed+is_charging still a valid ABRP feed).

## Build / architecture (unchanged from design above, now de-risked)
- Gradle product flavors `dilink3` (current, untouched) / `dilink5`.
- `dilink5` bundles the real DiLink-5 bydauto SDK (statistic + charging classes; via the
  `byd-apps/apps/byd-probe/stubs-dilink5` signatures + the real dex). Register **statistic +
  charging** typed listeners (both confirmed delivering); poll `speed` getter.
- Runtime gate `ro.vehicle.type` startsWith `Di5` (or SDK 30) selects the D5 path.
- Wire the D5 snapshot into the EXISTING ABRP/MQTT pipeline (`AbrpConnectionManager`) — no feed
  changes needed; only the data-source layer differs.

## Step order
1. Add `dilink3`/`dilink5` flavors; confirm `dilink3` builds identical to today.
2. Bundle D5 SDK into `dilink5` (dex2jar the bydauto classes → `dilink5Implementation`, or reuse
   the probe's stub-compile + bundled-dex approach).
3. D5 data source: register statistic + charging listeners → fill the existing telemetry snapshot
   (soc, range, usable kWh, odometer, charge power/state, speed).
4. Capacity 70.5 kWh + driving-power derivation (option 1) + is_dcfc inference.
5. Add the TR Sealion 7 to CarConfig; flip the D3-only gates for `ro.vehicle.type=Di5*`.
6. On-car validate (one drive + one charge) against ABRP; iterate.

Status: **plan ready, data-backed.** Implementation not started (awaiting go-ahead).
