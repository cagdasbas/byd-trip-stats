# Changelog

All notable changes to **BYD Trip Stats** will be documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
- **Database maintenance worker** — schedule changed from monthly to weekly. Checkpoint changed from `FULL` to `TRUNCATE` (actually reclaims WAL disk space). Thinning policy replaced with a four-tier age-based policy: < 7 days untouched, 7–30 days → 1 point/2 s, 30–90 days → 1 point/10 s, > 90 days → 1 point/30 s.
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