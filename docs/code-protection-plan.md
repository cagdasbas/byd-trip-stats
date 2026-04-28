# Code Protection Plan

This app remains free, but sensitive BYD discovery details should not be easy to mine from the public repo or release APK.

## Public Repo

- Keep UI, charts, trip and charging repositories, database schema, backups, app update flow, and generic ABRP/MQTT client code public.
- Keep direct BYD telemetry as a free feature because it is now the app's core data source.
- Avoid publishing raw probe logs, speculative feature-ID maps, vendor log tags, privileged-shell experiments, or READ_LOGS mining notes.

## Private Module Boundary

- Put future high-risk implementation in an ignored optional module named `private-telemetry`.
- The root Gradle settings include `:private-telemetry` only when the folder exists locally.
- The app depends on `:private-telemetry` only when that module exists, so public builds keep working without private files.
- The current local stub is a Kotlin/JVM jar module loaded through a reflection bridge; this avoids Android-library release-task issues while still packaging private code into local builds.
- Vendor-log filters and log-line classifiers are the first code moved behind the bridge; public builds keep the READ_LOGS path disabled when the private module is absent.
- Lab-only local bridge probing is also behind the private module, so public source does not expose its socket probes or handshake details.
- Drive/regen discovery getter tables are private too; public code keeps only the generic reflection loop and confirmed production mode handling.
- Optional supplemental feature groups for unresolved signal/statistic/climate candidates are private; public builds simply skip those diagnostics when the module is absent.
- Dormant sidecar/provider/car-property discovery shims have been removed from public source; recreate them only inside the private module if they are needed again.
- Keep private implementation behind a small public-facing API before moving large code, so production behavior stays stable.

## Sensitive Code Candidates

- Speculative BYD raw feature-ID maps and hidden getter lists that are not required for stable production telemetry.
- Lab-only probe loops and vendor log-mining parsers.
- Any local ADB, shell, privileged-shell, or UID 1000 discovery flow.
- Autostart and keepalive bypass details that are not already public Android behavior.
- Exact raw-to-signal mappings discovered by live probing if they expose non-public behavior.
- Confirmed production telemetry mappings still live in the public data source for now; hiding those would require moving most of the standalone telemetry runtime behind the private boundary.

## Release APK Rules

- Build public APKs from the `release` variant, not `debug`.
- Release builds use R8 minification and resource shrinking.
- Release builds strip `android.util.Log.*` calls via `app/proguard-rules.pro`.
- Do not publish R8 mapping files.
- Do not include lab/probe build markers in release notes or screenshots.

## Migration Checklist

- Move one sensitive area at a time, starting with lab/probe code that is not required for stable telemetry.
- Keep production telemetry behavior unchanged after each move.
- Run only local compile/JVM checks unless device testing is explicitly requested.
- Verify release builds still install and start without missing reflection classes.
- Keep a private note mapping public telemetry fields to private raw sources.
