# AGENTS.md

Instructions for coding agents (Claude Code, Codex, Gemini CLI, Cursor, Copilot and others) working in this
repository. People should start with [README.md](README.md); someone setting up a TV pastes [PROMPT.md](PROMPT.md).

Platea Home is a home screen only. **Debloating belongs to [CleanYourTV](https://github.com/Gathin23/cleanyourtv)**:
use its lists and tools, never write your own debloat lists or scripts here, and follow its own `PROMPT.md` and
`AGENTS.md` safety rules while you debloat.

## Which job are you doing?

| You were asked to | Do this |
|---|---|
| Set up a TV: debloat it and make Platea Home its home screen | Job A below, on the real TV |
| Change this repo: the launcher, a translation, the scripts, docs | Job B below |

## Job A: set up a TV

### 0. Ground rules (not negotiable)
- **Look first, ask, then act.** Do not disable, install or change anything before you have shown the plan and
  the person has answered your questions.
- **Use the scripts.** Use `platea-home.sh` for build, install, debloat, restore and status. Never set the Home
  activity, disable a launcher or edit secure settings by hand.
- **Disable, never uninstall.** Debloating uses `pm disable-user --user 0` through CleanYourTV's `tools/disable.sh`,
  which keeps a restore record. Never `pm uninstall` a system app.
- **Never touch:**
  - core Android (permission controller, package installer, ext services, module metadata, `com.android.providers.*`,
    systemui, shell, settings);
  - the TV middleware and the input services behind HDMI and the tuner;
  - the live TV app, HDMI-CEC, the remote and Bluetooth pairing;
  - the keyboard, the Play Store and Play Services;
  - the firmware updater.
  CleanYourTV's `disable.sh` refuses many of these on its own: do not override its guards
  (`FORCE_HOME`, `FORCE_SETUPWRAITH`, `TVINPUT_ALLOW`, `HANDLERS_ALLOW`) unless the person agrees to the exact case
  the script's message describes.
- **Wi-Fi owned by setupwraith:** on Google TV the network saved during first setup can belong to
  `com.google.android.tungsten.setupwraith`. Check `adb shell dumpsys wifi | grep -o 'cname=[^ ]*'` before anything
  disables it, or the TV drops off the network and ADB with it.
- **Ask about what people use**: streaming apps, the voice assistant (switching it off also kills voice search in
  apps), Chromecast, AirPlay, live TV, HbbTV, recording to USB, the universal-remote / IR blaster. Disable only what
  they do not use.

### 1. Connect and look (read only)
- `adb connect <ip>:5555`, then always `adb -s <ip>:5555`, because other devices may be connected.
  - "unauthorized": ask the person to press **Allow** on the TV.
  - "No route to host" while the TV answers ping: `adb kill-server && adb start-server`.
- Collect:
  - model, `ro.product.device`, Android version and SDK, fingerprint;
  - `MemAvailable`, free space on `/data`;
  - the current Home (`cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`);
  - the installed packages;
  - the apps really used (`dumpsys usagestats`);
  - Wi-Fi or Ethernet, and the Wi-Fi owner.
- Platea Home needs **Android 8.0 / API 26** or later. On anything older, stop and say so.
- **Fire TV** (`ro.product.manufacturer` is `Amazon`): stop here and follow **"Fire TV path"** below instead of
  steps 2 to 5.
- Samsung, LG and Roku are not Android TV.

### 2. Plan and questions
- Find the TV in CleanYourTV's `examples/`: `./platea-home.sh debloat` clones it into `.cache/cleanyourtv`, or clone
  it yourself. Compare model, device code, region and firmware with the list's header.
  - **Exact model:** use that list. Comment out the lines for apps the person keeps; keep any extras in a separate file.
  - **Only a sibling model:** use it as a guide, check every package on the TV, and say so.
  - **Nothing:** build a list from what you saw, in small batches, with a reboot between them.
- Ask the questions above, plus:
  - the **6 dock apps**, checking each package with `pm list packages`, because names differ by brand
    (e.g. `com.apple.atve.sony.appletv`);
  - the palette (Grafite, Notte blu, Giorno);
  - whether to use the Sipario screen saver.

### 3. Back up and debloat
- Save `pm list packages -f`, `pm list packages -d`, `settings list global|secure|system` and `getprop` to a folder
  outside the repo.
- `TV_HOST=<ip> ./platea-home.sh debloat <list>`. If any package stays enabled or the script refuses one, stop and
  report; do not loop.
- Keep the restore command it prints: `(cd .cache/cleanyourtv && TV_HOST=<ip> tools/restore.sh <list>)`.

### 4. Configure and install Platea Home
- Copy `config/default.json` to `config/<tv-name>.json` and fill in:
  - `dock`: at most 6 installed packages;
  - `hidden`: apps to keep off Home;
  - `live_tv`: the live TV package, e.g. `com.sony.dtv.tvx` on Sony or `com.mediatek.wwtv.tvcenter` on many MediaTek sets;
  - `palette`, `sipario_palette`;
  - optional `lines` for apps without a built-in description.
- `CONFIG=config/<tv-name>.json SIPARIO=1 TV_HOST=<ip> ./platea-home.sh install`. The script:
  1. checks that Platea Home really starts;
  2. then makes it Home;
  3. disables a stock Google launcher only if the TV keeps preferring it;
  4. rolls back if Home does not move.
  If it reports a failure, stop and report. Do not force it.
- The first build creates `keys/platea-home.keystore`. Tell the person to back it up, because without it the app
  cannot be updated in place. Never commit it.

### 5. Verify and hand over
- Screenshots: `adb -s <ip>:5555 exec-out screencap -p > shot.png`.
  - Check the Home, the quick panel (gear), Inputs, the Apps page and launcher settings.
  - A black screenshot of live TV or DRM content is normal.
  - A black screenshot everywhere usually means the TV is in standby (`dumpsys power | grep mWakefulness`): ask
    before waking it.
- Reboot once and confirm that Home is still Platea Home and the disabled packages stayed disabled.
- **An injected key is not proof** for the remote. Ask the person to press the real Home, Back, Input and gear
  buttons, and to hold OK on an app.
- Measure memory: `dumpsys meminfo io.github.gionnio.plateahome` (TOTAL PSS), about a minute after the last action.
- Tell the person how to undo everything:
  - `TV_HOST=<ip> ./platea-home.sh restore`;
  - CleanYourTV's `tools/restore.sh <list>`.
- Ask whether to turn network ADB debugging off. Port 5555 is not encrypted, but the scripts above need it.

### Fire TV path
Fire OS keeps Amazon's Home whatever you set: `set-home-activity` answers `Success` and changes nothing, and
`com.amazon.tv.launcher` is protected. Never try to disable it. Platea Home is opened instead by the Home guard of
[dumbtv](https://github.com/humblemuzzu/dumbtv) (MIT), with this repo's patch. Background:
[`firetv/README.md`](firetv/README.md).

1. **Look first:** model, Fire OS version, installed packages, the apps really used. Show the plan and ask the same
   questions as step 2, minus HbbTV and inputs (a stick has none).
2. **Debloat with dumbtv, never CleanYourTV** (`platea-home.sh debloat` refuses on Fire OS):
   1. `git clone https://github.com/humblemuzzu/dumbtv` next to this repo;
   2. read dumbtv's own `AGENTS.md` and follow it for the debloat (`configure.sh`, `debloat.sh`, `lockdown.sh` and
      its questions);
   3. when it asks about **dumbtv home**, answer yes: its Home guard is what opens Platea Home. Its DNS filter is
      the person's choice.
   Keep Prime Video (`com.amazon.firebat`) and the Appstore (`com.amazon.venezia`) unless told otherwise.
3. **Configure and install Platea Home:**
   1. copy `config/firetv.json` to `config/<tv-name>.json`;
   2. set the 6 dock apps, checked with `pm list packages`. Fire TV names differ: YouTube is
      `com.amazon.firetv.youtube`, Prime Video `com.amazon.firebat`, Apple TV `com.apple.atve.amazon.appletv`;
   3. run `CONFIG=config/<tv-name>.json SIPARIO=1 TV_HOST=<ip> ./platea-home.sh install`. On Fire OS it installs and
      sets the screen saver, but does not touch Home.
4. **Patch and install dumbtv's Home guard.** It needs `platforms;android-25` in the Android SDK:
   ```bash
   cd ../dumbtv && git apply ../platea-home/firetv/dumbtv-guard.patch
   TV_HOST=<ip> ./launcher/install.sh --build
   ```
   If the patch does not apply, stop and report: do not hand-edit dumbtv's Guard.
5. **Verify.** On Fire OS these results are expected:
   - `cmd package resolve-activity … HOME` still answers `com.amazon.tv.launcher/...`;
   - `settings get secure enabled_accessibility_services` contains `tv.dumbtv.home/tv.dumbtv.home.Guard`;
   - `./platea-home.sh status` shows `dumbtv Home guard on`.
   Then ask the person to press **Home on the physical remote** from inside an app, and to turn the stick off and on.
   It must open Platea Home after about 0.3 s of black. An injected `input keyevent KEYCODE_HOME` passing proves nothing.
6. **Undo:**
   - `./platea-home.sh restore` removes Platea Home and puts the screen saver back;
   - dumbtv's `launcher/install.sh --undo` gives Home back to Amazon;
   - dumbtv's `scripts/restore.sh` undoes its debloat.

### Known pitfalls
- **Fire TV after an update:** installing a new APK closes Platea Home. dumbtv's guard only reacts when Amazon's Home
  appears, so if Amazon's Home is already in front the next Home press does nothing. `platea-home.sh install` reopens
  Platea Home after the install. If you install with plain `adb install`, run
  `am start -n io.github.gionnio.plateahome/.MainActivity` afterwards.
- **Installing any other launcher resets the default Home** and Android shows the chooser. Run `platea-home.sh install`
  again (or `cmd package set-home-activity`) afterwards.
- **A launcher's saved choices win over its config.** In Platea Home, `pm clear io.github.gionnio.plateahome` brings
  back the config's dock, hidden apps and palette.
- **Some packages refuse `disable-user` in a batch** but take it by hand: check again after the reboot.
- **Firmware and app-store updates can turn packages back on.** Running the same `debloat` again is safe.

## Job B: change the code

### Rules
- **No network:** no `INTERNET` permission, no analytics, no ads, no background services or polling. The only
  permissions are:
  - `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE`, to show the connection;
  - `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`, only because Android hides the Wi-Fi name without them. The
    install script grants them over ADB, and the app never reads the location.
- **No libraries and no Gradle:** plain Java and Android views, built by `build.sh`, with `minSdk` 26.
- **Light:**
  - the APK stays under 256 KB (about 100 KB today);
  - Home stays under 40 MB of RAM about a minute after start on a real TV (about 22 MB on a Sony BRAVIA VH1);
  - load app banners lazily, only for the tiles on screen.
- **Layout follows the mockup:** sizes are in pixels of a 1920×1080 TV, scaled with `Ui.px()`. The design is neutral:
  graphite ground, white focus, colour only from the apps' own banners, the Figtree font.
- **Every user-facing text** goes in `app/res/values/strings.xml` (English, the default) **and**
  `app/res/values-it/strings.xml` (Italian), never inline in Java. Text that starts with a space needs quotes in the
  XML (`" · on"`), or Android drops the space.
- **Android traps already met here:**
  - in a vertical `LinearLayout`, children added without layout params get `MATCH_PARENT` width, so add text views
    with `WRAP_CONTENT`;
  - in bash, never put an apostrophe inside `${var:?message}`.
- **The safety logic** in `platea-home.sh install` and `restore` (start check, Home check, rollback, the state files)
  must not be weakened.

### Before you commit
- `./build.sh` builds with no errors.
- Install on a real TV and check every screen you touched with a screenshot, D-pad from every edge.
- Measure memory as above.
- Messages: `area: what changed`, plus one line on the TV you tested on.
- Never commit `keys/`, `state/`, `build/` or `.cache/`, nor IP addresses, Wi-Fi names, serials or account emails in
  docs and screenshots.
