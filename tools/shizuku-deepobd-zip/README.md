# Deep OBD ZIP Installer

Android utility using Shizuku UserService.

It finds the newest `.zip` in `/storage/emulated/0/Download` and extracts it to:

`/storage/emulated/0/Android/data/de.holeschak.bmw_deep_obd/files/<ZIP name>/`

For example `MyConfig.zip` becomes `.../files/MyConfig/`.

## APK

GitHub Actions builds a debug APK automatically. Open the repository's **Actions** tab, choose **Build Shizuku Deep OBD APK**, open the latest successful run, and download the artifact `ShizukuDeepOBDZip-debug-apk`.
