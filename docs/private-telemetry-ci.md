# Private Telemetry CI

The public repo can build without the private telemetry module, but the full release APK needs `private-telemetry/` to be present at build time.

## Recommended setup

1. Create a separate private GitHub repo for the `private-telemetry/` module.
2. Keep the module layout identical to the local folder used by Gradle.
3. Add a read-only SSH deploy key to that private repo.
4. Add these repository secrets to the public repo:
   - `PRIVATE_TELEMETRY_REPO_SSH_URL`
   - `PRIVATE_TELEMETRY_DEPLOY_KEY`
5. Let the release workflow clone the private repo into `private-telemetry/` before `./gradlew assembleRelease`.
6. Use `workflow_dispatch` in GitHub Actions to test the full build before publishing a tag.

## Local development

For local work, place the private repo at the project root as `private-telemetry/`. Gradle will include it automatically when the folder exists.

## Notes

- The public repository stays buildable on its own.
- The release workflow should fail fast if the private module secrets are missing, so you do not accidentally publish an incomplete APK.
- The private repo should not contain generated build outputs.
