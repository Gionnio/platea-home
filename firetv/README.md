# Platea Home on Fire TV

Fire OS does not let another app become the default Home: `cmd package set-home-activity` answers `Success` and changes
nothing, and the Home button never reaches an accessibility key filter. Platea Home works on Fire TV anyway, through the
**Home guard of [dumbtv](https://github.com/humblemuzzu/dumbtv)** (MIT), with a small patch.

Tested on a **Fire TV Stick 4K Max (2nd gen, AFTKRT), Fire OS 8.1.8.5**: the stick boots into Platea Home, and Home from
inside an app opens Platea Home. Details and measurements: [issue #2](https://github.com/Gionnio/platea-home/issues/2).

## How it works
1. dumbtv's `Guard` (an accessibility service) waits for Amazon's home window
   (`com.amazon.tv.launcher.ui.HomeActivity_vNext`).
2. It covers the screen with a black overlay. Fire OS 8 / Android 11 only allows an app with a visible window to start
   an activity, and the cover is that window.
3. It launches Platea Home through an `AlarmManager` `PendingIntent`. `system_server` sends it, so the 5-second
   app-switch delay after Home doesn't apply. dumbtv's `install.sh` sets `min_futurity=0` for that.
4. With [`dumbtv-guard.patch`](dumbtv-guard.patch) the Guard opens the first installed target, which is Platea Home,
   without the `HOME` category, and drops the cover as soon as Platea Home's window appears.

You see about 0.3 seconds of black, then Platea Home.

## Steps
1. **Debloat with dumbtv**, not with CleanYourTV, whose tools are not meant for Fire TV. Clone
   `https://github.com/humblemuzzu/dumbtv` and follow its README or `AGENTS.md` (`configure.sh`, `debloat.sh`,
   `lockdown.sh`). Say yes to **dumbtv home**: its guard is what opens Platea Home. Keep Prime Video
   (`com.amazon.firebat`) if you use it.
2. **Build Platea Home** with a Fire TV config. [`config/firetv.json`](../config/firetv.json) already hides Amazon's
   system screens; Platea Home also hides them on its own when it runs on Fire OS. Then install it:
   ```bash
   CONFIG=config/firetv.json SIPARIO=1 TV_HOST=<stick ip> ./platea-home.sh install
   ```
   On Fire OS the script installs the app and sets the screen saver, but does not try to change Home.
   With the release APK use `APK=platea-home.apk` instead of `CONFIG=…`.
3. **Patch and install dumbtv's Home guard.** It needs a JDK and the Android SDK with `platforms;android-25`. Clone
   dumbtv next to this repo (step 1 may already have done it):
   ```bash
   git clone https://github.com/humblemuzzu/dumbtv
   cd dumbtv && git apply ../platea-home/firetv/dumbtv-guard.patch
   TV_HOST=<stick ip> ./launcher/install.sh --build
   ```
   The patch was made against dumbtv commit `755991b`. If it no longer applies, the change is small enough to redo by
   hand: see the diff.
4. **Test with the physical remote.** Press Home from inside an app, and turn the stick off and on. An injected
   `input keyevent HOME` passing is not proof.

## Undo
- `TV_HOST=<stick ip> ./platea-home.sh restore` removes Platea Home and puts the screen saver back.
- dumbtv's `launcher/install.sh --undo` gives Home back to Amazon.
- dumbtv's `scripts/restore.sh` undoes its debloat.

## Notes
- The settings gear opens Fire TV Settings. That is fine: the Guard only reacts to Amazon's home screen.
- Platea Home's `MainActivity` stays exported with no permission, because the Guard starts it by component.
- Credits: the Home guard technique and code are dumbtv's (MIT). The patch only changes which home it opens.
