# Yeti TV OS

Yeti TV OS is the home screen and system experience of Yeti TV boxes. It replaces the stock launcher with a
Google TV style home, adds its own settings pages, a Wi-Fi picker and picture controls, and gives the box a
branded boot that runs from the power-on logo straight into the home screen.

Created by **Udit Kumar Mahato, Founder, Yeti TV**.

![Home](docs/screenshots/home.png)

## Contents

- [Features](#features)
  - [Home screen](#home-screen)
  - [Branded boot](#branded-boot)
  - [Wi-Fi](#wi-fi)
  - [Network & internet](#network--internet)
  - [Display](#display)
  - [Sound](#sound)
  - [Date & time](#date--time)
  - [Apps](#apps)
  - [All apps](#all-apps)
  - [About](#about)
  - [Remote control and focus](#remote-control-and-focus)
  - [Design system and screen scaling](#design-system-and-screen-scaling)
- [Screenshots](#screenshots)
- [Supported hardware](#supported-hardware)
- [Install](#install)
- [Build from source](#build-from-source)
- [Permissions](#permissions)
- [Boot animation](#boot-animation)
- [Project layout](#project-layout)
- [Known limitations](#known-limitations)

## Features

### Home screen

The home screen is one fixed page with no scrolling, laid out for a TV viewed from across the room.

- **Top bar.** Yeti TV logo on the left. On the right: a Wi-Fi pill showing the connected network name and a
  drawn signal-strength icon (it opens the Wi-Fi picker), an Ethernet pill that only appears while a cable is
  connected, a settings button and a live clock that follows the system 12/24-hour setting.
- **Hero panel.** Today's date, a greeting that changes with the time of day (morning, afternoon, evening) and a
  live connection line: connected over Ethernet, connected to a named Wi-Fi network, connected but without
  internet, or offline. The main button changes with that state: **Connect to Wi-Fi** when the box is offline,
  **Get apps** (opens the Play Store) when it is online. A second button opens **All apps**. The hero uses the
  height left over by the rows below, so it grows when a row is hidden.
- **Your apps.** Up to six apps as 16:9 cards, installed streaming apps first, followed by everything else in
  alphabetical order, and an **All apps** card at the end with the number of installed apps. Apps that ship a TV
  banner show that banner. Apps without one get a card tinted with the dominant colour of their icon (computed on
  the device from a 24×24 sample of the icon, saturated pixels weighted higher), with the icon centred. The app's
  name fades in under the card while it is focused.
- **Get more to watch.** Cards for popular streaming apps that are not installed yet (YouTube, Netflix, Prime
  Video, VLC, a TV browser), each in the service's own accent colour. Selecting one opens its Play Store page. The
  whole row hides itself once every app in it is installed.
- **Settings row.** Pills for Wi-Fi, Network, Display, Sound, Date & time, Apps, All settings and About. All of
  them open Yeti TV OS's own pages except **All settings**, which opens the full Android TV settings.
- **Live updates.** The page refreshes itself when apps are installed, updated or removed, when Wi-Fi or Ethernet
  connect or drop, when signal strength changes and every minute for the greeting and clock.
- **App options.** Long-press OK, or press Menu, on any app card to get **Open**, **App info** and
  **Uninstall** (Uninstall is not offered for built-in apps).

### Branded boot

The box shows the same picture from the moment it is switched on until the home screen appears, with no change
in logo size and no black frame in between.

- **Power-on logo.** A 1920×1080 Yeti TV logo shown by the bootloader while the system starts.
- **Boot animation.** Built from that exact logo frame, pixel for pixel, at the full 1920×1080 resolution.
  Android draws boot animations at their native size without scaling, so a smaller animation would make the logo
  appear to shrink and reappear; matching the frame avoids that. The only movement is a loading line under the
  logo: three dots that pulse in turn and the text "Starting Yeti TV OS". The animation runs at 15 frames per second
  as a one-second loop and is 2.3 MB in total, so it decodes smoothly on a quad-core Cortex-A35.
- **Launcher splash.** The home screen's window background is the same logo on the same navy colour, so when
  Android stops the boot animation and shows the launcher, nothing visibly changes.
- **Hand-off animation.** After a fresh boot, the launcher keeps the dots pulsing for at least 0.7 seconds, then
  slides the splash off to the left while fading it out, and brings the home rows in from the right one after
  another (55 ms apart, 600 ms each, decelerating). Focus lands on the main hero button when it finishes. This only
  plays within three minutes of boot; a later launcher restart goes straight to the home screen.
- **Faster first frame.** The install script compiles the launcher ahead of time, because the home screen's first
  frame is the last step Android waits for before ending the boot animation.

### Wi-Fi

A built-in Wi-Fi picker that works entirely with the remote.

- **On/off switch** for the Wi-Fi radio.
- **Connection card** for the current network: name, signal quality in words and dBm, IP address, link speed and
  band with channel number.
- **Live network list**, rescanned every 20 seconds. Each network appears once (its strongest access point), with
  a drawn signal icon (0 to 4 bars, padlock for secured networks), the security type (Open, WEP, WPA/WPA2, WPA3,
  Enterprise) and the band (2.4, 5 or 6 GHz). The connected network is listed first, then saved networks, then the
  rest by signal strength. Focus stays on the same network when the list refreshes.
- **Password entry** in an overlay placed above the on-screen keyboard, with **Show password**, a minimum-length
  check (8 characters for WPA, any length for WEP keys) and hexadecimal key support. A wrong password is detected
  from the authentication error, the failed network is removed again and the password screen reopens with an error
  message.
- **Open and saved networks** connect with one press. Long-press a saved network to forget it.
- Networks saved earlier through Android TV settings, and Enterprise (802.1X) networks, are handed to Android TV
  settings, because Android only lets an app change networks that app created.

### Network & internet

- **Summary card** for the active connection (Ethernet takes priority over Wi-Fi): online, no internet or offline,
  with IP address, router (default gateway) and DNS servers.
- **Wi-Fi** row with the connected network name, opening the Wi-Fi picker.
- **Ethernet** row with connection state, internet check and link speed (10 or 100 Mbps), or "Cable unplugged".
- **Device name**, opening the Android TV rename screen.
- **Wi-Fi and Ethernet MAC addresses**, read from the kernel because Android 11 hides them from apps.
- Link to Android TV network settings for proxy and static IP.
- The page updates live as connections change.

### Display

Display controls go through the Amlogic system service, so they act on the actual HDMI output.

- **Resolution.** **Automatic (best for this TV)** or any mode the connected TV reports over HDMI, sorted from
  highest to lowest (4K at 60, 50, 30, 25 and 24 Hz, 1080p, 1080i, 720p and lower). Computer-only modes such as
  1024×768 are left out. After switching, a **Keep this resolution?** dialog counts down from 15 seconds. If nothing
  is pressed (for example because the TV shows no signal), or Back or **Revert** is pressed, the previous mode is
  restored. The countdown keeps running even if the page is closed.
- **Screen size.** 80 to 100 percent, for TVs that crop the edges of the picture (overscan). Applied live while the
  slider moves.
- **Brightness, Contrast and Colour.** Sliders from 0 to 100, with 50 as the standard picture. They adjust the
  video processor after video and menus are combined, so they change the whole picture, home screen included. The
  values are saved and re-applied at every boot. **Reset picture** returns all three to 50.
- **Summary card** with the current HDMI mode, whether it was chosen automatically, the raw signal name and the
  interface resolution (the UI renders at 1920×1080 even on 4K output).
- Link to Android TV display settings for HDR and colour format.

### Sound

- **Volume** slider for the main audio stream, shown as a percentage. Moving it also unmutes.
- **Mute** switch.
- **Remote click sounds** switch for the system sound effects.
- **Audio output** shown in the summary card: TV speakers over HDMI, a sound system over HDMI ARC, USB audio or
  Bluetooth.
- Link to Android TV sound settings for surround sound and audio formats.

### Date & time

- **Large live clock** with the full date.
- **Time zone card**: city, zone name, current offset from GMT and whether the clock is set automatically.
- **Automatic date & time** and **Automatic time zone** switches.
- **24-hour clock** switch, with a preview of the format.
- Links to Android TV settings to pick a time zone or set the time by hand.

### Apps

- **Storage card**: free space, total space and the number of installed apps. The free-space figure turns amber
  below 0.5 GB.
- **Google Play Store** shortcut.
- **Every installed app** with its version, marked **Built in** for system apps. Selecting an app opens
  **Open**, **App info** and, for apps you installed, **Uninstall**.
- Link to Android TV app settings for permissions and special access.

### All apps

A grid of every launchable app (both TV and phone-style launch entries), six per row in alphabetical order, with
the same long-press and Menu options as the home screen.

### About

The Yeti TV logo, Yeti TV OS version and the founder credit, with live device information: device name, software
version, processor, memory (total and free), storage (free and total), IP address with the connection type, and
uptime.

### Remote control and focus

- Everything is reachable with the D-pad, OK, Back, Home and Menu.
- Focused cards get a white ring drawn above the card image (so it also shows on app banners), zoom by 10% and
  lift with a shadow. Focused buttons and pills turn solid white with dark text and icons, Google TV style.
- Sliders move one step per left or right press; Back closes any dialog first.
- Pressing Home while already on the home screen returns focus to the hero's main button.
- Dialogs block focus from reaching the page behind them.

### Design system and screen scaling

- The whole interface is designed on a 1920×1080 canvas inside 5% TV-safe margins and scaled to the real window
  size. The same layout looks identical at 720p, 1080p and on 4K output, whatever pixel density the box reports.
- Near-black neutral surfaces, a single Yeti-blue accent, rounded cards and pill buttons, Android's system sans-serif
  font in regular and medium weights.
- Icons that must stay sharp at any size (the Wi-Fi signal icon) are drawn in code rather than shipped as images.

## Screenshots

| | |
|---|---|
| ![Boot animation](docs/screenshots/boot-animation.png) | ![Boot hand-off](docs/screenshots/boot-handoff.png) |
| ![Display](docs/screenshots/display.png) | ![Resolution](docs/screenshots/resolution.png) |
| ![Sound](docs/screenshots/sound.png) | ![Apps](docs/screenshots/apps.png) |

## Supported hardware

Yeti TV OS is built and tested on an **Amlogic S905W2** box running an Android 11 TV base with 4K HDMI output and a
1080p interface.

- The home screen, Wi-Fi, Network, Sound, Date & time, Apps and About work on any Android TV device running
  Android 5.0 or later.
- Resolution, screen size and the picture sliders need the Amlogic vendor library (`droidlogic.software.core`).
  Where it is missing, those controls hide themselves.
- The picture sliders, resolution and screen size also rely on the base firmware allowing the app to reach the
  Amlogic system service (true when SELinux is permissive, as on the reference box).

## Install

1. Download `YetiTV-OS-1.0.apk` from the [latest release](../../releases/latest).
2. Install it and make it the home app:

   ```bash
   adb install -r YetiTV-OS-1.0.apk
   adb shell cmd package set-home-activity tv.yeti.launcher/.MainActivity
   ```

3. Grant the permissions listed under [Permissions](#permissions).
4. Optionally install the boot animation (see [Boot animation](#boot-animation)).

Updates must be signed with the same key as the installed copy, otherwise Android refuses `adb install -r` and the
old version has to be uninstalled first.

## Build from source

Requirements: Android SDK with platform 34 and JDK 17 (Android Studio's bundled JDK works).

```bash
./gradlew assembleRelease
```

Release builds are signed with the key named in `keystore.properties` (copy `keystore.properties.example` and fill
it in). Without that file the build uses the debug key.

On Windows, `tools/dev.ps1` builds, installs over USB or network adb, compiles the app ahead of time, grants the
permissions, sets Yeti TV OS as the home app and saves a screenshot of every screen to `shots/`:

```powershell
powershell -ExecutionPolicy Bypass -File tools\dev.ps1
```

See the [developer guide](docs/DEVELOPER_GUIDE.md) for architecture, hardware notes and device setup.

## Permissions

Most permissions are granted at install time. These four have to be granted over adb (the install script does it):

| Permission | Needed for | Without it |
|---|---|---|
| `ACCESS_FINE_LOCATION` | Wi-Fi scan results and the connected network name | The Wi-Fi picker asks for it |
| `WRITE_SETTINGS` (app-op) | Remote click sounds, 24-hour clock | The switch opens Android TV settings |
| `WRITE_SECURE_SETTINGS` | Automatic date & time and time zone | The switch opens Android TV settings |
| `INTERACT_ACROSS_USERS` | Resolution changes | Resolution cannot be changed |

```bash
adb shell appops set tv.yeti.launcher WRITE_SETTINGS allow
adb shell pm grant tv.yeti.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant tv.yeti.launcher android.permission.INTERACT_ACROSS_USERS
adb shell pm grant tv.yeti.launcher android.permission.ACCESS_FINE_LOCATION
```

The app targets API 28 on purpose: Android only lets apps targeting API 28 or lower add, enable and switch Wi-Fi
networks themselves, which the Wi-Fi picker needs.

## Boot animation

```bash
python branding/make_bootanimation.py
```

Needs Python with Pillow and NumPy. It builds `branding/bootanimation.zip` from the power-on logo
(`branding/yeti_bootup.bmp`) and also writes the launcher's matching splash image. On the reference box the
animation lives on the `oem` partition:

```bash
adb push branding/bootanimation.zip /data/local/tmp/
adb shell "mount -o remount,rw /oem && cp /data/local/tmp/bootanimation.zip /oem/media/ && chmod 644 /oem/media/bootanimation.zip && sync && mount -o remount,ro /oem"
```

On devices without an `oem` partition it goes in `/system/media/bootanimation.zip`.

## Project layout

| Path | Contents |
|---|---|
| `app/src/main/java/tv/yeti/launcher/` | The launcher: `MainActivity` (home), `BootIntro` (boot hand-off), `SettingsPage` and the settings pages, `WifiActivity` and `Wifi`, `Amlogic` (display controls), `Ui` (design system and scaling) |
| `app/src/main/res/` | Icons, logos, splash and theme |
| `branding/` | Logos, power-on logo, boot animation and its generator |
| `tools/dev.ps1` | Build, install and screenshot script |
| `docs/DEVELOPER_GUIDE.md` | Architecture, hardware notes, display internals, boot branding, Play Store fixes, safety |
| `docs/screenshots/` | Screenshots used in this README |

## Known limitations

- The Wi-Fi picker can only switch to or forget networks it saved itself.
- The remote's dedicated app buttons still follow the base firmware's mapping.
- The all-apps grid still uses square tiles rather than the 16:9 cards of the home screen.
- Apps that require a Play Protect certified device (such as Netflix) depend on the base firmware's certification,
  not on Yeti TV OS.
