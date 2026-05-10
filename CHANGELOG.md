# Changelog

All notable changes to **BYD Trip Stats** will be documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [2.3.0] - 2026-May-10

### Added

- **Delete local backups from within the app** — each entry in the Local Backup list now has a delete button (trash icon). Tapping it shows a confirmation dialog then permanently removes the file from all locations (Downloads, internal ADB copy) in a single operation.
- **BYD Atto 2 Boost** — 51.1 kWh Blade LFP (100S estimated), FWD, 130 kW, 344 km WLTP.
- **BYD M6 Standard 120kW** — 55.4 kWh Blade LFP (102S), DC charging 89 kW.
- **BYD M6 Superior 150kW** — 71.8 kWh Blade LFP (132S), DC charging 115 kW. The existing M6 Superior entry has been renamed to "BYD M6 Superior 100kW" to distinguish the two; its stored ID is unchanged so existing car selections are preserved.
- **BYD Seal 6 Premium 95kW** — 56.64 kWh Blade LFP (118S, 150Ah × 377.6V), FWD, DC charging 103 kW, 425 km WLTC.
- **BYD Seal 6 Premium 160kW** — same 56.64 kWh pack (118S), RWD, 160 kW rear motor.
- **Distance shows no decimals in split-screen mode** — the Distance power metric no longer displays a digit after the decimal (e.g., "12.3") when the app resides in split-screen. Integer format is used to avoid crowding.
- **Added AUD (Australian Dollar) currency option** — the electricity cost tariff now supports A$ in addition to €, £, and $.

### Changed

- **Battery temperature removed from Battery stat card** — the value is unreliable across firmwares. Cars without a valid battery temp source (e.g. Seal Excellence) were displaying firmware sentinel values (186÷3=62°C, 195÷3=65°C, etc.) that could not be reliably distinguished from real readings without a stable ambient reference. The data continues to be recorded and displayed in the charging detail chart where it is more trustworthy.
- **rawJson no longer duplicates dedicated column data** — 25 fields already persisted in typed DB columns (soc, speed, gear, latitude, longitude, altitude, tyre pressures, tyre temperatures, cell voltages, battery total voltage, 12V voltage, soh, engine speeds, electric range, soc panel) have been removed from the per-point JSON blob. New data points are ~700 bytes smaller per row (~33% reduction). Fields that have no dedicated column (drive\_mode, regen\_mode, car\_on, statistic\_\*, instrument temps, bodywork diagnostics, etc.) are retained.

### Fixed

- **Battery cell temperature from statistic device auto-detects the encoding** — the BYD SDK returns `cellTAvg` / `cellTMin` / `cellTMax` in one of two encodings depending on firmware: direct °C on some firmwares, 1/3 °C units on others. Applying the wrong interpretation produced either an underread (e.g., 11.5°C when ambient was 32°C) or an overread (e.g., 62°C when ambient was 25°C). The fix uses the in-car ambient temperature as a reference and picks the interpretation whose deviation from ambient is physically plausible (within ~30°C parked/driving, ~50°C during DC fast charging).
- **Battery temperature sentinels suppressed when ambient is unavailable** — `resolveStatisticTempCelsius` was returning the divided value (raw ÷ 3) even when no ambient reference was available to validate it. Firmware sentinels like 186 (→ 186÷3=62°C) passed through undetected if the instrument device had not yet reported outdoor temperature. The function now returns null until ambient is known.
- **Service self-stop tripwire no longer fires on fresh start** — `lastFeatureEventElapsedMs` defaults to 0, which previously made the "SDK silent for 10 minutes" check evaluate to true immediately on first launch (any uptime exceeded the threshold before any SDK events arrived). The service would self-stop before SDK devices could register, blocking all telemetry until the user physically drove the car. The tripwire now requires at least one real SDK event before the silence threshold is evaluated.
- **Off-state idle no longer thrashes the service overnight** — three independent periodic restart sources (`ServiceWatchdogWorker` 15-min, `ServiceRestarterJobService` periodic 15-min, and `ServiceRestartReceiver` boot-action kicks) were re-starting the telemetry service throughout the night, undoing the carOff+notCharging self-stop and keeping the wake lock + WiFi lock held for ~33% of the overnight period. A new persistent `ServiceIdleState` flag is set when the service self-stops; every periodic restart source now reads the flag and skips the restart while idle. `BootReceiver` no longer re-arms restart kicks on `ACC_OFF` / `POWER_DISCONNECTED`. The off-state wake-lock duty cycle drops from ~33% to ~5%, eliminating the overnight 12V drain.
- **Service idle self-stop now truly stops the service** — the previous implementation called `stopSelf()` but did not set `intentionalStop = true`, causing `onDestroy` to immediately reschedule a restart via `scheduleSelfRestart`. The service was effectively cycling: stop → restart → 5 min → stop → restart, running all night and continuously publishing MQTT snapshots. Setting `intentionalStop = true` before `stopSelf()` breaks the loop; the service stays stopped until the next 90-minute alarm fires intentionally.
- **MQTT idle publish cadence corrected** — as a consequence of the above fix, MQTT now publishes a single snapshot every ~90 minutes while the car is off and idle (from the `OffStateKeepaliveReceiver` alarm), rather than every 5 minutes from the unintended restart loop. The MQTT settings hint has been updated to reflect this: "Idle: snapshot every ~90 min".
- **BYD SDK temperature sentinel values unconditionally rejected** — `resolveStatisticTempCelsius` now rejects raw values of 195 (`TEMPERATURE_INVALID`) and -60 (`TEMPERATURE_MIN`) before any ambient comparison logic. Previously these sentinel values could pass through when no ambient reference was yet available, producing spurious readings such as 65°C (195 ÷ 3) on the Seal Excellence firmware.
- **Live trip tracking card consumption matches finalized trip stats** — the energy counter in the trip tracking card now uses the battery discharge counter delta (`currentTotalDischarge − sessionStartTotalDischarge`), the same source used by finalized trip statistics. The previous motor power integration approach excluded AC, 12V auxiliary, and other non-motor loads, causing a systematic under-read of 10–20% when climate control was active.
- **Live trip tracking card average speed matches finalized trip stats** — average speed is now derived from the pure odometer delta (`currentOdometer − sessionStartOdometer`), matching the `endOdometer − startOdometer` formula used by finalized trips. The integration-based distance estimate is retained as a fallback only when the odometer has not yet advanced.
- **Backup deletion for "Download (file)" entries now works on Android 10+** — backups created by older app versions that wrote directly to the filesystem had no MediaStore record, so `File.delete()` and MediaStore-by-name lookups both silently failed. The deletion logic now follows a 4-step escalation: direct `File.delete()` → MediaStore lookup by file path (`DATA` column) → MediaStore lookup by display name → insert the file into MediaStore to gain ownership, then delete via the returned content URI.

### Maintenance

- **User-triggered "Trim database" action** in the Local Backup screen (new "Maintenance" section). Replaces the earlier auto-compaction-on-startup approach so the user runs it at their convenience and gets explicit feedback. Five phases run sequentially with live progress:
  - **A. Strip redundant rawJson fields** from every row in `trip_data_points` and `charging_data_points` (the 25 keys already persisted as typed columns).
  - **B. Clear rawJson** to `{}` for trip and charging points older than **45 days** — the diagnostic payload is no longer needed for historical data and recovers the largest amount of space.
  - **C. Downsample old trip points to 1 row per minute** for trips older than **60 days**. The native sampling rate of ~1 row per 30 s is preserved for recent trips; older trips keep `MIN(id)` per `(tripId, minute)` bucket. Distance, energy, and time-series shape remain accurate; chart smoothness is slightly reduced for very old trips.
  - **D. Delete charging data points for AC sessions older than 45 days.** AC vs DC is inferred from `charging_sessions.peakKw < 25 kW`; DC fast-charging sessions are kept entirely (rare and interesting to review). The session summary row (kWh added, peak kW, duration, etc.) is preserved for both.
  - **E. VACUUM via raw SQLite** — the telemetry service is briefly stopped, Room is closed, and `PRAGMA wal_checkpoint(TRUNCATE)` + `VACUUM` are run on a direct SQLite connection (the database file would otherwise stay locked by Room's connection pool, leaving freed pages unreclaimed). The app then auto-restarts so Room reopens cleanly — same pattern used after a database restore.
  - The result and timestamp are persisted across app restarts; the section header always shows "Last trimmed: …" with a one-line summary of the rows affected. Logged with tag `DatabaseTrimmer`. Expected to recover the bulk of the 500 MB+ accumulation since v2.1.0.

---

## [2.2.0] - 2026-May-08

### Added

- **Imperial / Metric unit system** — users can now choose between Metric (km, km/h, kWh/100km) and Imperial (mi, mph, kWh/100mi) in Settings → Preferences. The default is determined by the device locale (UK → Imperial, all others → Metric) and can be changed at any time with a two-button selector; the active choice is highlighted in the primary colour. The preference persists across restarts.
  - Odometer and speed values are telemetry-sourced and already arrive in the correct unit for the vehicle's market (BYD UK cars report miles and mph natively), so no conversion is applied to those — only the unit label changes.
  - Calculated distances (trip distance, monthly totals, range projection) and efficiencies (kWh/100km → kWh/100mi) are converted at display time; stored data always remains in metric.
  - The unit system is respected across the Dashboard, Trip History, Trip Detail, Trip Compare, Trip Goals, Seasonal Analysis, and all charts (Speed, Instant Consumption, Consumption history, Range Projection).
- **Live trip stats in the Trip Tracking card** — while a trip is being recorded, the card shows a real-time centre column: elapsed time (HH:MM:SS, continuous — does not pause during brief car-off stops within the 30-minute resume window), average speed, total energy used (kWh), and average consumption (kWh/100km or kWh/100mi). The card stays at a fixed height; the stats appear between the gear/status section on the left and the Auto toggle/Stop button on the right. All values respect the active unit system.
- **SoC overlay on the 12V / HV battery history chart** — the 48-hour battery monitoring dialog now overlays the HV State of Charge as a dashed purple line on a dedicated right-hand axis (0–100 %), making it easy to correlate 12V drain events with HV top-up cycles. SoC is sampled and persisted alongside the existing voltage readings. The dialog title was updated to "HV / 12V Batteries - Last 48 Hours" to reflect that both battery systems are now shown.
- **BYD Tang EV support** — the pure-electric Tang (AWD, 108.8 kWh Blade LFP, 530 km WLTP) is now available as a selectable BEV profile alongside the existing Tang DM-i.
- **In-app changelog viewer** — tapping "Changelog / What's new" in Settings → About now opens a scrollable in-app dialog instead of launching the browser.

### Changed

- **`enginePower` type corrected to `Int`** — the BMS reports motor power as a whole-number kW value; the model field was previously widened to `Double` unnecessarily. Decimal precision is only meaningful for `chargingPower`, which originates from a separate telemetry path and remains `Double`. All redundant `.toInt()` / `.toDouble()` conversions and the now-unreachable `.isFinite()` guard have been removed.
- **Off-state keepalive redesigned** — the redundant in-service 3-minute MCU wake loop has been removed; only the alarm-based `OffStateKeepaliveReceiver` remains. Its interval has been increased from 4 minutes to 90 minutes, allowing the MCU to genuinely sleep between pokes (~75 min idle per cycle) and significantly reducing overnight 12V / HV standby drain. `POWER_CONNECTED` remains the primary wake-up trigger for charging detection, so a charge session started by plugging in is detected immediately; the 90-minute alarm is a fallback backstop only. During active off-state charging sessions, WiFi is kept alive via a targeted keepalive every 10 minutes so that charging telemetry is not interrupted when the car is off.
- **Service self-stops when idle** — after the car has been off and not charging for 5 minutes, the telemetry service stops itself and releases the CPU wake lock, allowing the infotainment to enter deep sleep. This eliminates the overnight HV→12V DC-DC cycling that was observed as a steady SoC drain while parked. The 90-minute alarm restarts the service briefly for a periodic snapshot, then the self-stop fires again. Charging sessions are unaffected: the service stays alive for the full duration of any active charge.
- **Idle poll interval extended to 5 minutes** — when the car is off and not charging, the telemetry loop now ticks every 5 minutes instead of every 30 seconds, reducing unnecessary CPU wake-ups and repository calls. The AC/slow-charging case retains 30-second polling for SoC granularity. The MQTT settings hint has been updated to reflect the new publish cadence: driving at the configured interval, charging every 30 s, idle: service sleeps (no publish).
- **SCREEN_OFF keepalive removed** — the dynamically registered `SCREEN_OFF` receiver that called `McuWakeHelper.keepAlive()` on every screen-off event has been removed. It was firing at the exact moment the system tried to sleep, resetting the MCU WiFi countdown and preventing deep sleep. WiFi continuity during charging is now handled solely by the targeted 10-minute keepalive in the telemetry loop.
- **12V history chart Y-axis fixed** — the voltage axis is now always 12.0–14.0 V (previously 11.0–14.0 V). The SoC right-hand axis ticks were updated from five labels (0/25/50/75/100 %) to three (33/66/100 %) to match the three voltage gridlines.

### Fixed

- **BYD M6 cell count** — Now the number of cells is correct, which means SoH will be calculated correctly
- **12V chart crosshair showing `%.2f` literally** — the 12V voltage value in the crosshair tooltip was not being formatted; it now correctly displays the numeric value (e.g. `13.70 V`).

---

## [2.1.1] - 2026-May-06

### Added

- **BYD ATTO 2 support** — new BEV trims added to the car catalog: Active and Comfort.
- **BYD M6 support** — the M6 is now available as a selectable BEV profile.
- **Public compatibility probe export** — the compatibility probe can now be saved as `Download/BydTripStats/compat_probe.json` locally instead of only being generated in the app cache and sent via private telegram bot.

### Changed

- **SoH handling** — SoH estimates are now capped at 100% before they reach the dashboard, MQTT, and history due to `bodyworkBatteryCapacity` being rough or slightly inflated number and `remainingKwh / soc` could overshoot when one of the inputs would be coarse or stale
- **Probe export flow** — the Settings screen now only reports success after the public Downloads file has actually been written.
- **Regen mode detection** — additional firmware paths are now used to surface drive/regen modes on more car models beyond the original supported set.
- **Vehicle analysis naming** — the trip analysis screen now uses the broader `vehicleAnalysis` label instead of `phevAnalysis`.
- **AWD icon rendering** — the drivetrain icon in the dashboard was switched to a sharper bitmap path, improving clarity on the car head unit.

### Fixed

- **Compatibility probe save** — the saved probe file now uses the same Downloads path as the app’s other backup/export flows, so it actually appears in the file manager on supported BYD head units.

### Reverted changes

- **Road slope×** — the value is no longer divided by 10 at the read (change implemented at 2.1.0)

---

## [2.1.0] - 2026-May-01

### Added

- **BYD Seagull / Dolphin Surf** — three new BEV trims added to the car catalog: Active, Boost and Comfort.
- **BYD Seal 5 / Sealion 5 DM-i** — two new PHEV trims added to the car catalog: Comfort and Design.
- **48-hour live 12V history chart** — tapping the dashboard `HV / 12V` stat card now opens a rolling 48-hour chart of the auxiliary battery voltage, recorded from live telemetry in a dedicated history buffer so users can inspect 12V health and overnight drain behaviour independently of trip or charging-session history.

### Changed

- **Battery stat card** — title is now always "Battery"; value shows "SoH: X.X%" with one decimal place; subtitle shows the average battery temperature ("Temp: X.X °C") when available.
- **PHEV false-positive detection** — sentinel values (≥ 100,000) returned by BEV firmware for fuel-system fields are now excluded from the PHEV signal count; `getEnergyMode` is also excluded for confirmed BEV models, preventing BEV cars from being mis-classified as PHEVs during the compatibility probe sweep.
- **Motor RPM detailed chart** — the fullscreen/detailed view now uses peak-only bucketing at 480-point resolution (4× the condensed thumbnail) so the front motor shows a natural flat-zero baseline with real engagement spikes, rather than an artificially dense spike pattern. The condensed dashboard thumbnail is unchanged.
- **AWD motor stat card** — subtitle now shows total drive power only (e.g. "AWD · 187 kW") instead of a fabricated per-axle split that assumed a fixed front/rear ratio regardless of driving conditions.
- **Duration format** — minutes are now shown as "min" instead of "m" across trip history, trip detail, charging history, charging detail, and trip compare to avoid ambiguity with meters. Hours format updated accordingly (e.g. "1h 19min").
- **Drive/regen mode timeline** — each lane (Drive mode, Regen mode) is now individually gated on its own data availability. Previously, if one mode had data but the other did not, the mode-less lane rendered as a solid grey bar. Now each lane is only shown when it has at least one recorded data point with a known mode value.
- **Background poll rate** — when the car is off, the telemetry poll interval is reduced from 1 s to 30 s, significantly reducing CPU active time and 12V battery drain during overnight parking and long AC charging sessions. An intermediate 5 s tier applies when the SDK has sent events within the last 60 s (e.g. a remotely-awoken car sitting in Park), keeping mode changes visible promptly without continuous fast polling. DC fast charging (above 23 kW) retains 1 s polling for accurate power curve resolution. The service remains always-on as a foreground service; no data or functionality is lost. The delay now precedes the poll work and is based on the previous tick's state, eliminating any unnecessary fast poll on a car-on to car-off transition and maximising idle sleep time.
- **Regen / Driving Dynamics stat card** — renamed from "Regen / Driving Mode"; road grade (slope in degrees) is now shown in the subtitle when available, relocated from the Environment card where it did not fit contextually.
- **Drive/regen mode prompt** — the "Regen / Driving Dynamics" stat card on the dashboard now shows a hint when the car is on but either mode has not yet been detected, prompting the user to tap a mode on the car display. Trip Detail also shows a persistent info chip above the mode timeline when a saved trip is missing drive or regen mode data, with a message explaining how to enable recording on future trips.

### Fixed

- **Drivetrain mode label (AWD / FWD / RWD) always blank** — on some firmware builds the drivetrain state getter returns 0 when polled outside an active event callback. The app now falls back to the car catalog drivetrain type when the getter returns 0, so the motor stat card shows the correct AWD / FWD / RWD label throughout the drive.
- **Road slope reading 10× too steep** — the raw SDK value is in tenths of a degree but was being treated as whole degrees, so a barely noticeable descent would display as e.g. −6° instead of −0.6°. The value is now correctly divided by 10 at the read site and displayed with one decimal place.
- **Range projection chart changes after navigating away and back** — when returning to the dashboard from another screen (or after the app process was recreated), the range projection chart could gain an orange "Projected (actual)" line that was not visible during the live drive session before navigation. This happened because the trip restore path always assigned a projected value to every data point, including those below the calibration threshold. The restore path now leaves unstabilised points without a projected value, matching live session behaviour.

---

## [2.0.0] - 2026-Apr-28

### Added

- **Standalone telemetry runtime** — the app now reads vehicle data directly from the BYD DiLink SDK without requiring Electro or an MQTT broker. All dashboard, trip recording, and charging session features run from the built-in runtime path.
- **Physics-based energy breakdown** — Trip Detail Analysis tab shows how trip energy splits across rolling resistance, aerodynamic drag, elevation gradient (climb / descent), and auxiliary losses (12 V system, HVAC, residual). The model uses Crr = 0.0074 (calibrated against real trips, consistent with ISO 28580 for LFP EV tyres), CdA and kerb mass from the car catalog, and drivetrain efficiency η = 0.88. Components are scaled proportionally so they always sum to the measured consumed energy.
- **ABRP integration** — live telemetry can be forwarded to A Better Route Planner via the Link Generic API using a user-provided token. Includes a test-upload action and token visibility toggle. Opt-in only — disabled by default.
- **Outbound MQTT integration** — live telemetry can be published to any external MQTT broker (e.g. HiveMQ or HomeAssistant) using an Electro-compatible JSON schema, a configurable interval, and a test-publish action. Opt-in only — disabled by default.
- **Connections tab** — dedicated Settings screen for managing ABRP and MQTT integrations, each with a summary card and last-sync timestamp.
- **Drive and regen mode analytics** — drive mode (Normal / Eco / Sport) and regen mode are recorded with every trip data point, shown as a colour-coded timeline chart in Trip Detail, and summarised in the Analysis tab.
- **Combined Power + SoC charging chart** — Charging Detail now includes a dual-axis view with an X-axis toggle between elapsed time and SoC percentage, making DC taper shape easy to read.
- **Per-trip custom DC charging cost** — Trip Detail can override the home-tariff cost estimate with the actual amount paid at a public charger.
- **Environmentals card** — the dashboard temperature card now shows ambient temperature alongside PM2.5 indoor/outdoor readings where the car exposes them.
- **App Diagnostics monitor** — a toggleable section in Settings → Data showing live CPU, RAM, thread count, and uptime with 60-second history charts and an integrated ADB shell runner for diagnostics.
- **Pre-update database backup** — a local database snapshot is created automatically before any APK update is installed.
- **Autostart management shortcut** — the post-update autostart reminder can deep-link directly into BYD’s native Disable Autostart screen where the firmware exposes it.
- **CarCatalog physics fields** — all catalog entries now include `estimatedKerbMassKg`, `cdA`, and an `isPhev` flag. PHEV entries also carry `phevUsableBatteryKwh` for correct EV-range projection.
- **PHEV range projection** — for PHEV models the range projection combines estimated EV range (from usable battery capacity) with the BMS fuel range, giving a combined EV+ICE projection. The chart ceiling adapts to the combined range rather than the EV-only WLTP figure.
- **VehicleCompatibilityProbe** — runtime probe that records which SDK getter methods are available on the connected car, used for diagnostics and future compatibility reporting.

### Changed

- **Telemetry pipeline** — dashboard, trip recording, charging sessions, and all stat cards are now fed by the standalone runtime path. Electro/HiveMQ is no longer involved in normal operation.
- **Charging data quality** — charging power, SoC, HV voltage, and cell min/max now come from verified live in-car signals rather than heuristic reconstruction. Peak and average power in Charging Detail derive from real recorded samples.
- **Battery temperature** — shown as the midpoint of cell min/max when no confirmed pack-average source is available, rather than a potentially wrong single-sensor value.
- **Odometer** — uses the verified decimal mileage source where available, eliminating the `.0` truncation visible on some firmware builds.
- **Distance card** — during a trip that continues across a short engine-off break, the dashboard shows both the current engine-on segment distance and the cumulative trip distance separately.
- **Automatic stop behaviour** — manually stopping an auto-detected trip now warns the user and switches tracking back to manual mode until re-enabled, preventing accidental re-arm.
- **Consumption charts** — outlier buckets outside 9–35 kWh/100 km are filtered; legends show the live average; the selected-duration average is plotted alongside the car reference line.
- **Consumption thumbnail** — the dashboard chart thumbnail previews the monthly consumption line instead of the noisier daily view.
- **Trip timeline sampling** — long trips are sampled evenly across the full duration instead of showing only the earliest events.
- **Tariff editor** — currency symbol entry uses a styled dropdown of common symbols instead of free-text input.
- **Tyre dashboard** — temperature is now shown and tyre unit label text enlarged for readability on the DiLink display.
- **Dashboard animation control** — motion-heavy effects (liquid battery, energy flow canvas) can be disabled from Preferences. Disabling animations reduces CPU usage by up to 80% on DiLink firmware.
- **Foreground notification** — shows a counting-up elapsed timer ("1:23") anchored to service start instead of a wall-clock timestamp that reset to "0m" on every update. Status text now shows car name, SoC, and charging power only — gear, speed, and motor power removed.
- **Range projection on late Activity open** — if the app is opened after a trip has already started, the range chart now reconstructs historical points from the database and shows both the BMS and projection lines from trip start, not from when the app was opened.
- **Range projection stability on return from background** — switching to CarPlay or home and returning no longer resets the chart. `currentTripId` is now kept alive in the ViewModel scope regardless of Activity visibility, preventing the race where a transiently null trip ID caused the chart to restart from the current position.
- **Update install flow** — silent PackageInstaller sessions are used where available so granted runtime permissions survive the update.
- **Release build** — R8 minification and resource shrinking enabled; `dontwarn` rules added for optional HiveMQ/Netty classes.

### Fixed

- **Charging icon / `isCharging` detection** — charging-active state now follows real charging evidence rather than stale fallbacks, eliminating false pulse animations.
- **Charging session flapping** — brief telemetry gaps no longer generate spurious `0 m` junk sessions.
- **Trip auto-start after process recreation** — dashboard state and service binding now survive DiLink activity churn more reliably.
- **Battery voltage and charging-power mapping** — HV pack voltage and charging-power signal paths corrected against verified car telemetry.
- **Trip detail chart layout** — overlapping Y-axis labels and overly dense instantaneous-consumption ticks reduced.
- **Settings back navigation** — returning from nested settings pages now restores the correct top-level tab.
- **Trip metrics on active trips** — max speed, max power, and max regen update on every telemetry packet, not only when a throttled data point is written to the database.
- **Engine-off trip continuation** — trips correctly continue across engine-off breaks shorter than the configured window without ending prematurely on the return to P.

### Removed

- **Electro/MQTT runtime dependency** — the app no longer requires an Electro topic, an embedded broker, or any MQTT connection for normal operation.
- **Legacy bridge code** — the embedded MQTT broker, the Electro-era external MQTT client, and all compatibility-only broker/topic parameters have been removed.
- **Obsolete Electro-era UI copy** — legacy MQTT-facing prompts and stale Electro-only wording removed throughout.

---

## [1.4.2] - 2026-Mar-29

### Added

- **Seal U** — support is added for Seal U Design and Seal U Comfort.

### Fixed

- **Live Charging Session Visibility** — resolved an issue in the Charging History screen where active/live charging sessions were filtered out and hidden; active sessions are now correctly displayed and pinned to the top of the list.
- **Range projection spikes** — during sustained regen or heavy braking the projected range would spike to 500+ km then crash back down. Root cause: negative energy values (`enginePower < 0`) were being subtracted from the rolling consumption window, making the denominator collapse toward zero. Regen is already captured in the SoC-based numerator — the fix excludes negative samples from the rolling window so only forward driving energy contributes to the Wh/km estimate.
- **App returns to "no trip" state after using CarPlay** — when DiLink killed and recreated the Activity while the user was in CarPlay, the new Activity instance bound to `MqttService` asynchronously and late, leaving a window where `observeMqttServiceState()` had not been called and the UI showed disconnected / not in trip. Fixed by moving bind to `onStart` and unbind to `onStop` (standard Android lifecycle pattern). The foreground service, trip recording, and MQTT connection were unaffected throughout.
- **"IVI system does not support this operation" popup on update** — `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission triggered a system dialog that DiLink's IVI OS rejects with this error. Removed both the manifest permission declaration and the `requestBatteryOptimizationExemption()` call. The existing `PARTIAL_WAKE_LOCK` in `MqttService` provides equivalent keep-alive behaviour without requiring a system dialog.

### Changed

- **FAQ** — added note clarifying that trips recorded in areas without signal (e.g. underground garages) may take up to 3 minutes to appear in history. The watchdog closes the active trip after 3 minutes of telemetry silence, anchored to the last received packet before signal was lost.

---

## [1.4.1] - 2026-Mar-24

### Added

- **Background Trip State Restoration** — the `DashboardViewModel` now reconstructs its live graph and efficiency counters from the database upon app resume, preventing data loss when the system kills the process in the background.

### Fixed

- **Unsupported Battery Optimization** — resolved issues with background service termination on certain DiLink firmware versions.

---

## [1.4.0] - 2026-Mar-23

### Added

- **Immediate Power-On Restart** — plugging in a charger now broadcasts `ACTION_POWER_CONNECTED`, which immediately revives the MQTT stack via the `BootReceiver` rather than waiting for the 15-minute watchdog.
- **Autostart Reminder** — after each app update (which resets DiLink's autostart permission), the app now detects the version change and displays a one-time reminder dialog asking the user to re-enable Autostart in car settings. An explicit warning also appears on the initial setup screen.
- **Battery Degradation Screen** — tap the Battery Health stat card on the dashboard to open a dedicated SoH-over-time view. Plots average SoH per trip, a least-squares trend line, a dashed projection line, and a "Projected @ 80%" year label. Four summary cards: current SoH, lowest recorded, decline rate (%/yr), and projected 80% year. Health interpretation legend with BYD Seal warranty note (70% / 8yr / 250 000 km). X-axis labels shown only on first point and on any point where SoH drops, keeping the axis uncluttered.
- **Seasonal Consumption Analysis** — new screen accessible from Trip History toolbar (☀️ icon). Groups all completed trips by meteorological season and shows avg kWh/100km per season as a colour-coded bar chart (🌱☀️🍂❄️) with reference line from `CarConfig`. Each season card shows total distance, kWh consumed, avg battery temp, and delta vs reference. Auto-generated winter-penalty insight when both winter and summer data are present.
- **Trip Goals & Personal Bests** — new screen accessible from Trip History toolbar (🏆 icon). Personal bests: lowest ever efficiency, longest single trip, longest consecutive daily driving streak. Goals: set a consumption target (avg of last 5 trips) and/or a monthly distance target; animated progress bars update in real time. Goals persist via `SharedPreferences`.
- **Cost Tracking** — tap the € icon in the Trip History toolbar to set your electricity tariff (price/kWh + currency symbol). Trip cost is displayed inline in the energy consumed chip (`4.40 kWh (€0.62)`). A collapsible monthly cost summary card shows up to 12 months of history. Trip Detail also shows cost as a row in the stats card.
- **Hybrid Charging Session Recording** — two complementary mechanisms now cover all charging scenarios:
  - *Car ON*: real-time session opened when `chargingPower > 0`, data points recorded every second, session closed immediately when charging stops or car turns off. Live Power / SoC / Voltage / Temperature charts available in Charging Detail.
  - *Car OFF*: SoC + timestamp persisted to `SharedPreferences` on every telemetry packet. On next car wake-up, if SoC increased by ≥ 1% (≥ 0.3 kWh), a synthetic session is created with accurate kWh from the delta. Covers overnight charging, timed charging, and remote charging with zero background process requirement.
- **Multi-model compatibility** — `VehicleTelemetry` fields absent on FWD-only models (Atto 3, Dolphin) now carry safe defaults so `kotlinx.serialization` never throws `MissingFieldException` on a missing key. The motor stat card adapts its title and kW subtitle to `Drivetrain.FWD` / `RWD` / `AWD`.
- **14 Heatmaps** — four new heatmaps added: Tyre Pressure vs Consumption, SoC vs Regen Efficiency, Speed vs Battery Temperature, Cell Voltage Spread vs SoC. The Cell Voltage Spread chart uses a widened y-tick column (70 dp) to prevent label overlap with the rotated axis label.

### Changed

- **Charging History UI** — summary card replaces "Peak ever kW" with "Avg SoC gain %". Synthetic (car-off) session cards show a "⚡ Reconstructed" badge and omit peak/avg kW chips. Real-time (car-on) sessions display an active badge and support live Power / SoC / Voltage / Temperature charts in the detail view.
- **Charging Detail charts** — tabs now show a contextual empty state for synthetic sessions explaining that live charts are only available for car-on sessions, rather than a generic "Not enough data" message.
- **MQTT — persistent session** — `MqttClientManager` now uses a fixed client ID (`"BydTripStats"`), `cleanSession(false)`, and QoS 1 subscription. Combined with "Retain published values" in Electro, this ensures the broker delivers the latest state immediately on reconnect rather than waiting for the next publish interval.
- **Service watchdog** — removed `NetworkType.CONNECTED` constraint so the watchdog fires every 15 minutes regardless of network availability.
- **`carConfig` load order** — `MqttService` now loads car config synchronously before starting the MQTT connection, eliminating a race condition where the first charging packet could arrive before `carConfig` was set.

---

## [1.3.0] - 2026-Mar-20

### Added

- **Charging Session Management** — implemented full support for deleting single and multiple charging sessions from the Charging History screen.
- **Bulk Deletion** — long-press a completed session to enter selection mode, then tap the trash icon to delete multiple sessions at once.
- **Electro Documentation** — added recommended MQTT intervals (1s Car On, 30s Car Off) to the Initialization screen, Network tab, and FAQ.
- **Service Watchdog** — a periodic WorkManager job (every 15 minutes) checks that the embedded MQTT broker and MQTT client are alive and restarts them if the OS had killed the process. Ensures charging telemetry is received even when the car is off and the head unit is under memory pressure.
- **Charging Chart Styling** — charging session charts (Power, SoC, Voltage, Temperature) now match the trip detail aesthetic: `primaryContainer` card background, styled borders, and an interactive crosshair showing precise value, clock time, and elapsed charge duration on drag.

### Changed

- **Atomic Transactions** — `ChargingRepository` and `TripRepository` now use `database.withTransaction` to ensure that mass deletions of sessions and data points are fully atomic and performant.
- **Car-Off Charging Reliability** — removed the 10s car-off watcher shutdown. The MQTT background service now remains active during charging even when the car is turned off, ensuring continuous charging curve capture.
- **Debounce Optimization** — standardized the charging detection debounce to 60 seconds (1 minute), allowing a generous margin for the new 30s recommended Electro car-off interval.

---

## [1.2.2] - 2026-Mar-19

### Added

- **Auto-Update Mechanism** — the app now automatically checks for newer versions and silently updates itself when the vehicle is parked and no tracking is active.
- **Video Documentation** — added a YouTube presentation link and thumbnail to the README for onboarding new users.

### Fixed

- **Charging Session Tracking** — resolved an edge case where AC/DC charging sessions would fail to finalize and continue tracking indefinitely.

---

## [1.2.1] - 2026-Mar-18

### Fixed

- **Post-shutdown CPU spike** — MQTT service now stops automatically after 10 s of `car_on = 0`, preventing the reconnection storm against a dead broker that caused ~71% CPU usage in the minutes following car shutdown. The watcher is guarded by `!isInTrip` so it never interrupts an active trip.
- **Background animation overhead** — `LiquidFillBattery` wave, glow, and bolt-pulse infinite transitions now snap to static values when the lifecycle state is below `RESUMED`. `EnergyFlowCanvas` flow animation applies the same gate. Both were previously cycling at 60 fps regardless of whether the screen was visible.
- **Compose recomposition rate** — added `debounce(500L)` to the `combine(isInTrip, currentTelemetry)` collector, capping dashboard redraws at twice per second instead of once per every 1 s MQTT packet.
- **Unused parameters removed** from `TripRepository.thinOldDataPoints()` — `olderThanMonths` and `keepEverySeconds` were dead code after the tiered thinning policy was introduced in 1.2.0.

---

## [1.2.0] - 2026-Mar-17

### Added

- **Charging session tracking** — automatic detection of AC and DC charging sessions via `chargingPower` field. Each session records SoC start/end, kWh added, peak and average kW, battery temperature rise, and HV voltage. Detection uses a time-based debounce (60 s for DC ≥ 20 kW, 12 min for AC) anchored to `lastChargingTelemetry` so `endTime` reflects actual charge completion rather than debounce fire time.
- **Charging history screen** — dedicated screen (battery icon on dashboard) listing all sessions with duration, SoC arc, kWh added, and peak kW. Active sessions shown with a live badge.
- **Charging detail screen** — four-tab detail view per session: Overview (summary metrics), Power (charge curve — DC taper shape), SoC (fill over time), Voltage (HV bus rise), Temperature (avg + cell min/max).
- **Trip comparison** — select 2–3 completed trips in Trip History (long-press → checkboxes) and tap the compare icon. Opens a bottom sheet with:
  - **Summary tab** — side-by-side metric table with winner highlighting per row.
  - **Charts tab** — overlaid Speed, Power, Consumption, SoC, and Elevation lines normalised to 0–100% trip distance so trips of different lengths compare cleanly.
  - **Routes tab** — all routes drawn on a shared OSM map in distinct colors. Per-trip eye toggle hides/shows individual trips across all three tabs.
- **Battery voltage chart** (trip detail) — dual-axis: HV bus (left, V) and cell min/max band (right, V). Cell spread highlighted in crosshair — key early indicator of cell imbalance.
- **Tyre pressure chart** (trip detail) — all four wheels over time, with inline bar/PSI/kPa unit switcher that shares state with the dashboard tyre icon preference. Dynamic Y-axis step and padding adapt per unit.
- **Instantaneous consumption chart** (trip detail) — raw `power/speed×100` kWh/100 km with 5-point rolling average overlay. Filters to speed > 5 km/h; zero line marks the regen/drive boundary.
- **Panel SoC line in SoC chart** — `soc_panel` rendered as a second line (violet) alongside BMS SoC (blue) with legend. Only shown when the trip has non-zero panel values (v2+ telemetry).
- **Electro telemetry v2 fields** — `VehicleTelemetry` now maps: tyre temperatures (×4), `soc_panel`, `car_locked`, `any_door_opened`, `fuel_percentage`, `fuel_driving_range_km`. Tyre temps and `soc_panel` promoted to first-class `TripDataPointEntity` columns.
- **Database migration 1 → 2** — adds 5 columns to `trip_data_points`, creates `charging_sessions` and `charging_data_points` tables with proper foreign keys and indices.

### Changed

- **`toRawJson()` writes only non-default values** — empty driving points now store `{}` (2 bytes) instead of the static 38-byte string, reducing the `rawJson` column by ~94% on BEV trips. PHEV fields gated by `isPhev` flag.
- **Database maintenance worker** — schedule changed from monthly to weekly. Checkpoint changed from `FULL` to `TRUNCATE` (actually reclaims WAL disk space). Thinning policy replaced with a four-tier age-based policy: < 7 days untouched, 7–30 days → 1 point/2 s, 30–90 days → 1 point/10 s, > 90 days → 1 point/30 s (edit on 1.4.0: This changes to 1 point /15 s instead).
- **Range projection** now uses `selectedCarConfig.batteryKwh` instead of the hardcoded Seal Excellence constant, making projections accurate for Dolphin and Atto 3 owners.

### Fixed

- `MotorRpmChart` `LegendDot` visibility conflict with `OsmRouteMap` — each chart file now carries its own `private` definition.
- `TripCompareSheet` routes tab map touch handling — replaced `pointerInteropFilter` (which consumed events before MapView received them) with `setOnTouchListener` + `requestDisallowInterceptTouchEvent`, allowing correct pan and pinch-to-zoom. OSM built-in zoom buttons removed (`Visibility.NEVER`) to free the bottom legend area.
- `TripCompareSheet` routes tab height overflow during sheet drag animation — `Box(Modifier.weight(1f).clipToBounds())` applied at two levels prevents the map from escaping the sheet boundary.

---

## [1.1.0] - 2025-Mar-13

### Added

- **Initialization screen** — first-run setup screen that prompts the user to select their BYD model and enter their Electro MQTT topic before the app becomes usable. Settings are persisted atomically so neither value can be missing after setup.
- **Car catalog (`CarConfig`)** — structured per-car configuration covering drivetrain, battery capacity, WLTP range, reference consumption (kWh/100 km, sourced from ev-database.org), and recommended front/rear tyre pressures. Supported models at launch:
  - BYD Seal Dynamic (RWD, 61.4 kWh)
  - BYD Seal Premium (RWD, 82.5 kWh)
  - BYD Seal Excellence (AWD, 82.5 kWh)
  - BYD Dolphin Active / Boost (FWD, 44.9 kWh)
  - BYD Dolphin Extended / Comfort / Design (FWD, 60.4 kWh)
  - BYD ATTO 3 (FWD, 60.4 kWh)
- **In-app car switcher** — tapping the car name in the dashboard top bar opens a dialog to change the selected model without going through settings.

### Changed

- **Motor RPM card (dashboard stats)** is now drivetrain-aware:
  - FWD cars show "Front Motor — N RPM" only.
  - RWD cars show "Rear Motor — N RPM" only.
  - AWD (Seal Excellence) shows both axles with a proportional live kW estimate (front 160 kW / rear 230 kW split, confirmed per BYD factory specs; total 390 kW combined).
- **Motor RPM chart (trip detail)** shows only the motor(s) present on the selected car. The legend and crosshair tooltip adapt accordingly (FWD: front only; RWD: rear only; AWD: both).
- **Heatmaps tab (trip detail)** — motor-related heatmaps adapt to drivetrain:
  - "Motor RPM vs Speed" title and axis label reflect the driven axle(s).
  - "Front vs Rear Motor RPM" torque-split heatmap is only shown for AWD cars.
- **Tyre pressure indicators** now use the recommended pressures from the selected car's config (previously hardcoded to Seal Excellence values). Alarm thresholds (±0.2 bar) remain car-agnostic.
- **Consumption chart reference line** uses the selected car's reference consumption from the catalog instead of a hardcoded constant.
- **Range projection chart** caps the projected range at the selected car's WLTP figure instead of a hardcoded 520 km.
- **MQTT connection status** in the Settings Network tab is now split into three distinct visual states with matching icons:
  - `SyncProblem` (red) — connection error with error message.
  - `Sync` (green) — connected and actively receiving telemetry.
  - `SyncDisabled` (muted) — disconnected or no data yet.
  - Same three states are mirrored in the dashboard top bar icon.

### Fixed

- `PreferencesManager.getMqttSettings()` would hang indefinitely due to using `collect` (infinite terminal) instead of `first()` on the DataStore Flow.
- First-run race condition: saving the car selection and the MQTT topic as two sequential DataStore writes could cause the topic write to be skipped if recomposition navigated away from the initialization screen between the two writes. Both are now written in a single atomic `edit` block via `saveInitialSetup()`.

---

## [1.0.0] - 2025-Mar-08

### Added

- Initial release.
- Live MQTT telemetry dashboard (speed, power, SoC, range, gear, tyre pressures, battery stats, motor RPM).
- Automatic and manual trip recording with gear-based auto-detection (D/R → start, P → stop).
- Trip history with per-trip charts: speed, power, SoC, altitude, energy consumption, motor RPM, route map.
- Trip detail heatmaps: power vs speed, consumption vs speed, regen vs speed, RPM vs speed, battery temp vs power, acceleration vs speed, SOC vs consumption, time-of-day vs speed, gradient vs consumption.
- Range projection chart with BMS estimate reference line and power-integrated realistic projection.
- Weekly / monthly / yearly consumption charts with car average reference line.
- Liquid-fill battery indicator with charging animation.
- Energy flow canvas with animated directional arrows (acceleration / regen / charging states).
- Tyre pressure indicators with per-axle alarm thresholds and BAR / PSI / kPa unit switcher.
- Local backup to `Download/BydTripStats/` (SQLite).
- Telegram bot backup with manual send and scheduled auto-backup (daily / weekly / monthly).
- CSV and JSON trip export.
- Embedded MQTT broker on port 1883 for local Electro integration.
- Boot receiver and foreground service to keep MQTT alive without manual app restarts.
- DatabaseMaintenanceWorker for automatic old-data pruning.
- Dark-themed Material 3 UI optimised for the BYD DiLink 11 in-car infotainment display.
