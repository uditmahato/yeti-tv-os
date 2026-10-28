# Yeti TV OS — Developer Guide

Notes for working on Yeti TV OS: how to connect to a box, build and test, how the launcher is put together, and
the things about the hardware that are easy to get wrong.

## Reference hardware

| | |
|---|---|
| SoC | Amlogic S905W2 (quad Cortex-A35, 32-bit userspace) |
| Base | Android 11 (Android TV), `userdebug` build, SELinux permissive |
| Video | HDMI output up to 4K60; the Android UI renders at 1920×1080 |
| Network | 1T1R Wi-Fi, 10/100 Ethernet; no Bluetooth |
| Google | Play Store and Google Play services preinstalled |

**Screen size:** the HDMI signal is 4K but apps draw at 1080p. Always size the UI from
`getResources().getDisplayMetrics()`. `getRealMetrics()` reports the 4K output and makes everything twice too big.

## Connecting to a box

USB (A-to-A cable) needs **Developer options → OTG mode** switched on. Network adb listens on port 5555 once the box is
online.

```bash
adb devices
adb root          # userdebug: everything after this runs as root
```

After a reboot the box disappears from adb for up to a minute while USB comes back up — wait for `adb devices`
rather than assuming it hung.

## Build and test loop

```powershell
powershell -ExecutionPolicy Bypass -File tools\dev.ps1           # build, install, screenshot every screen
powershell -ExecutionPolicy Bypass -File tools\dev.ps1 -NoBuild  # reinstall the last build
```

`dev.ps1` installs the APK, AOT-compiles it (`cmd package compile -m speed` — the home screen's cold start is the last
step of boot), grants the permissions below, makes Yeti TV OS the home app and saves screenshots to `shots/`.
Check the screenshots before calling a UI change done. `shots/` is not committed: it contains Wi-Fi names, IP and
MAC addresses.

Gradle needs JDK 17 (Android Studio's bundled JDK is fine). The lint warning *ExpiredTargetSdkVersion* is expected.

### Signing

Release builds are signed with the key named in `keystore.properties` (see `keystore.properties.example`). Neither
that file nor the keystore is committed. Every update must use the same key, otherwise `adb install -r` fails with a
signature mismatch and the launcher has to be uninstalled first.

### Why `targetSdk` is 28

The Wi-Fi picker uses `WifiManager.addNetwork`, `enableNetwork` and `setWifiEnabled`, which Android blocks for apps
targeting API 29+. Raising `targetSdk` breaks the picker.

### Permissions granted over adb

| Permission | Used by |
|---|---|
| `ACCESS_FINE_LOCATION` | Wi-Fi scan results and the connected network name |
| `WRITE_SETTINGS` (app-op) | Remote click sounds, 24-hour clock |
| `WRITE_SECURE_SETTINGS` | Automatic date & time / time zone |
| `INTERACT_ACROSS_USERS` | Resolution changes (the vendor library broadcasts the change to all users) |

```bash
adb shell appops set tv.yeti.launcher WRITE_SETTINGS allow
adb shell pm grant tv.yeti.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant tv.yeti.launcher android.permission.INTERACT_ACROSS_USERS
adb shell pm grant tv.yeti.launcher android.permission.ACCESS_FINE_LOCATION
```

Without them the affected toggles hand off to Android TV Settings instead of failing.

## Launcher architecture

Source: `app/src/main/java/tv/yeti/launcher/`.

| Class | Role |
|---|---|
| `Ui` | Design system and screen scaling. Layouts are designed on a 1920×1080 canvas inside 5% TV-safe margins; `Ui.s(px)` converts design pixels to real pixels. Focus styles (`focusEffect`, `pillFocus`), the drawn Wi-Fi icon. |
| `MainActivity` | Home: top bar (logo, Wi-Fi, Ethernet when connected, settings, clock) → hero (greeting, status, contextual button) → *Your apps* → *Get more to watch* (hidden when nothing is missing) → *Settings* row. No scrolling. Home key returns focus to the hero. Long-press OK or Menu on an app: Open / App info / Uninstall. |
| `BootIntro` | Boot hand-off: splash identical to the boot animation, then a slide to the home screen. |
| `SettingsPage` | Base for the settings pages: summary card on the left, list of action / toggle / slider rows on the right, rebuilt on every resume. |
| `NetworkActivity`, `DisplayActivity`, `SoundActivity`, `DateTimeActivity`, `AppSettingsActivity` | The settings pages. Each ends with a link to the matching Android TV Settings screen. |
| `WifiActivity`, `Wifi` | Wi-Fi picker: on/off, connection details, live scan list, password entry above the on-screen keyboard. |
| `Amlogic` | Display controls through the vendor library (below). |
| `AppsActivity`, `Apps` | All-apps grid; app listing, launching and the hidden-package list. |
| `AboutActivity` | Version, credit and live device information. The credit *Udit Kumar Mahato — Founder, Yeti TV* must stay. |

### Two settings apps

The base firmware has two settings apps:

- `com.android.tv.settings` — the full Android TV settings. **Always open this one.**
- `com.android.settings` — the stock firmware's simplified five-tile screen. It also answers generic
  `android.settings.*` intents, so a plain `startActivity(new Intent(Settings.ACTION_…))` lands there. Don't remove
  it (other system screens depend on it); just never route to it.

`Ui.openSettings(...)` resolves an action only among Android TV Settings' own activities and starts the match
explicitly, falling back to its main screen. `Ui.openTvSettings(context, class)` opens a specific screen.

## Display controls

`Amlogic.java` calls the vendor library `droidlogic.software.core` (`/vendor/framework/droidlogic.jar`) by
reflection, so the app still runs on boxes without it — the controls just hide. Its `SystemControlManager` talks to
the root `systemcontrol` service, which does the privileged work.

**Brightness, contrast, colour** write the amvecm *post-blend* registers, which affect the whole picture (menus and
video):

| Slider (0–100, 50 = standard) | Register | Range |
|---|---|---|
| Brightness | `/sys/class/amvecm/brightness2` | −255…255 |
| Contrast | `/sys/class/amvecm/contrast2` | −127…127 |
| Colour | `/sys/class/amvecm/saturation_hue_post` (`"sat hue"`) | −128…128 |

Don't use the vendor's `SetBrightness()` / `brightness1`: those only adjust the video layer, so nothing visible
changes on the home screen. The registers reset at boot, so the launcher stores the values and re-applies them in
`MainActivity.onCreate`.

**Resolution and screen size** persist in the u-boot environment:

- `OutputModeManager.setBestMode(null)` = automatic, `setBestMode("1080p60hz")` = fixed mode. Writes
  `outputmode`, `hdmimode`, `is.bestmode` and `<mode>_deepcolor`.
- `DisplayPositionManager.zoomByPercent(80…100)` writes `<resolution>_x/_y/_w/_h`.

A new resolution must be confirmed within 15 seconds or it reverts (Back also reverts). The timer is static so it
still fires if the page closes. Before changing these code paths, back up the `env` partition and diff the
variables before and after a test.

All of this works because SELinux is permissive on this firmware (the calls log an `untrusted_app` denial). An
enforcing firmware would need a platform-signed or privileged build.

## Branding and boot

| Stage | Source | Notes |
|---|---|---|
| Power-on logo | u-boot reads `/logo_files/bootup.bmp` from the `odm_ext_a` partition, falls back to the `logo` partition | 1920×1080, 16-bit RGB565 BI_BITFIELDS, exactly 4,147,272 bytes. Source: `branding/yeti_bootup.bmp`. |
| Boot animation | `/oem/media/bootanimation.zip` (overrides `/system/media/`) | Generated by `branding/make_bootanimation.py`. |
| Launcher splash | `drawable/splash.xml` + `drawable-nodpi/splash_logo.png` | Same frame as the boot animation. |

Android never scales boot animation frames, so the animation is full 1920×1080 and every frame is the power-on logo
plus a "Starting Yeti TV OS" line — the logo never moves between stages. 15 fps keeps 1080p PNG decoding smooth on
the A35. The launcher's window background is the same frame, so there is no black flash when the animation stops;
`BootIntro` then slides the splash away and brings the home rows in from the right. It only runs within three
minutes of boot; to replay it:

```bash
adb shell am start -n tv.yeti.launcher/.MainActivity --ez intro true
adb shell settings put global animator_duration_scale 10   # slow motion for screenshots; set back to 1 after
```

The `oem` partition is plain ext4 and can be remounted read-write to replace the animation:

```bash
adb push branding/bootanimation.zip /data/local/tmp/
adb shell "mount -o remount,rw /oem && cp /data/local/tmp/bootanimation.zip /oem/media/ && chmod 644 /oem/media/bootanimation.zip && sync && mount -o remount,ro /oem"
adb shell sha256sum /oem/media/bootanimation.zip
```

Boot time (kernel start to screen on) is about 22 seconds, ~20 of them Android's own start-up on this CPU.

## Google Play

- The Play Store must be the **Android TV build** (version tag `[8]`). The phone build refuses to run on a
  TV-only device ("This version of the app isn't compatible…"). Install the TV build over the preinstalled one with
  `adb install -r`; Google's signature must match. Undo: `pm uninstall-system-updates com.android.vending`.
- **Downloads stuck on "Pending":** Play Store jobs need an unmetered connection. Hotspots and some routers advertise
  themselves as metered. Fix per network: `adb shell cmd netpolicy set metered-network "<SSID>" false`.
- The base firmware isn't Play Protect certified, and registering the device's GSF ID with Google doesn't change that
  label. Google sign-in and most apps work; apps that require certification (Netflix) don't install.
- Don't clear `com.google.android.gsf` data: it regenerates the GSF ID.

## Working on the device firmware safely

The bootloader is locked, but `vbmeta` has verification disabled, so the dynamic partitions are plain read-only
ext4 (they can't be remounted read-write). There is no USB recovery mode on these boards: **a bricked box stays
bricked.**

- Take a full partition backup with SHA-256 checksums before any partition work, and keep it off the box.
- Before writing a partition, check it still matches the backup; after writing, verify the result by SHA-256.
- Don't write `bootloader*`, `mmcblk0boot*`, `env`, `vbmeta*`, `boot_*` or `super` by hand.
- The base system's platform certificate contains the stock brand string. Byte-patching that string inside `super`
  breaks every system app signature and bootloops the box.
- Stock apps can be removed for the user (`pm uninstall --user 0`) and restored with `pm install-existing`.

## Known issues

- The Wi-Fi picker can only enable or forget networks it saved itself; networks saved by Android TV Settings are
  handed back to Settings.
- Ethernet details have only been tested with the cable unplugged.
- The all-apps screen still uses the older square tiles.
- The remote's dedicated app buttons are mapped by a stock system service to the stock apps' intents; they need
  remapping now that those apps are gone.
- The clock can show the firmware build date until the box gets network time.
