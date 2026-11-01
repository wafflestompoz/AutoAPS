# Releasing

GitHub Actions (`.github/workflows/build.yml`) builds every push and pull request: it runs the `core` unit tests and uploads a debug APK as a workflow artifact.

## Publish a release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts` and merge to `main`.
2. Tag the commit and push the tag:
   ```sh
   git tag v0.1.0
   git push origin v0.1.0
   ```
3. The workflow builds `AutoAPS-0.1.0.apk`, writes a SHA-256 checksum, and publishes a GitHub Release with both files and the safety notice in the release text.

## Signing (set up once)

Without signing secrets, releases use a debug-signed APK. It installs fine, but users would have to uninstall it before they could install a properly signed build later. So set up signing before the first public release.

1. Create a keystore and keep it, and its passwords, somewhere safe. If you lose it you can never ship an update that installs over existing copies.
   ```sh
   keytool -genkeypair -v -keystore autoaps-release.jks -alias autoaps \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. In the repo on GitHub, open **Settings > Secrets and variables > Actions** and add:
   - `SIGNING_KEYSTORE_BASE64`: the output of `base64 -w0 autoaps-release.jks`
   - `SIGNING_STORE_PASSWORD`
   - `SIGNING_KEY_ALIAS`: `autoaps`
   - `SIGNING_KEY_PASSWORD`
