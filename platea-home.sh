#!/usr/bin/env bash
# Platea Home: a minimal, tvOS-style launcher. Debloating is CleanYourTV's, always at its latest version.
#
#   ./platea-home.sh build                          build (CONFIG=<json> picks the TV's configuration)
#   TV_HOST=<ip> ./platea-home.sh install           install and make it Home, safely (SIPARIO=1: also the screen saver;
#                                                       APK=<file>: install a ready-made APK instead of building)
#   TV_HOST=<ip> ./platea-home.sh debloat <list>    debloat with tools/disable.sh from the latest CleanYourTV
#   TV_HOST=<ip> ./platea-home.sh restore           give Home and the screen saver back, then remove Platea Home
#   TV_HOST=<ip> ./platea-home.sh status            Home, installed version, memory
#
# Safe install: Platea Home must really open before it becomes Home; a stock launcher is disabled only if the TV
# keeps preferring it, and if anything goes wrong Home goes back to what it was.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
PKG=io.github.gionnio.plateahome
ACT=$PKG/.MainActivity
CACHE="$HERE/.cache/cleanyourtv"
STOCK="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher"
KEYSVC="$PKG/$PKG.KeyService"

need_tv() {
  : "${TV_HOST:?set TV_HOST=<IP address of the TV>}"
  SERIAL="$TV_HOST:5555"
  adb connect "$SERIAL" >/dev/null 2>&1 || true
  if ! adb -s "$SERIAL" shell echo ok 2>/dev/null | grep -q ok; then
    adb kill-server >/dev/null 2>&1; adb start-server >/dev/null 2>&1; adb connect "$SERIAL" >/dev/null 2>&1 || true
  fi
  adb -s "$SERIAL" shell echo ok 2>/dev/null | grep -q ok || { echo "[!!] Cannot reach the TV at $SERIAL (is it on? network debugging on? press Allow on the TV)"; exit 1; }
  STATE="$HERE/state/$(printf '%s' "$TV_HOST" | tr -c 'A-Za-z0-9._-' '_')"
  mkdir -p "$STATE"
}
t() { adb -s "$SERIAL" shell "$@" | tr -d '\r'; }
home_now() { t cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1; }
# Fire OS keeps Amazon's Home whatever set-home-activity says: there dumbtv's Home guard opens Platea Home (firetv/README.md).
is_fireos() { [ "$(t getprop ro.product.manufacturer)" = Amazon ] || t pm list packages com.amazon.tv.launcher | grep -qx package:com.amazon.tv.launcher; }
guard_on() { t settings get secure enabled_accessibility_services | grep -q "tv.dumbtv.home/"; }
# The remote-button service (KeyService) is on only when the config has "keys". It is added to the accessibility
# services already enabled (dumbtv's guard, for one), never in their place, and restore takes only it away.
config_has_keys() { [ -n "${CONFIG:-}" ] && [ -f "$CONFIG" ] && python3 -c 'import json,sys; sys.exit(0 if json.load(open(sys.argv[1])).get("keys") else 1)' "$CONFIG"; }
keys_on() {
  local now; now="$(t settings get secure enabled_accessibility_services)"
  case "$now" in *"$PKG/"*) return 0 ;; esac
  [ -s "$STATE/a11y-enabled-before" ] || t settings get secure accessibility_enabled > "$STATE/a11y-enabled-before"
  if [ -z "$now" ] || [ "$now" = null ]; then now="$KEYSVC"; else now="$now:$KEYSVC"; fi
  t settings put secure enabled_accessibility_services "$now"
  t settings put secure accessibility_enabled 1
}
keys_off() {
  local now new; now="$(t settings get secure enabled_accessibility_services)"
  case "$now" in *"$PKG/"*) ;; *) return 0 ;; esac
  new="$(printf '%s' "$now" | tr ':' '\n' | { grep -v "^$PKG/" || true; } | paste -sd: -)"
  if [ -n "$new" ]; then t settings put secure enabled_accessibility_services "$new"
  else
    t settings delete secure enabled_accessibility_services >/dev/null
    local before=""; [ -s "$STATE/a11y-enabled-before" ] && before="$(cat "$STATE/a11y-enabled-before")"
    if [ "$before" = 1 ]; then t settings put secure accessibility_enabled 1; else t settings put secure accessibility_enabled 0; fi
  fi
  rm -f "$STATE/a11y-enabled-before"
  echo "[OK] Remote-button service off"
}
apply_keys() {
  if config_has_keys; then keys_on; echo "[OK] Remote-button service on (keys from $CONFIG). Test it with the real remote."
  else keys_off; fi
}
restore_saver() {
  local saver=""; [ -s "$STATE/screensaver-before" ] && saver="$(cat "$STATE/screensaver-before")"
  if [ -n "$saver" ] && [ "$saver" != null ] && t pm list packages "${saver%%/*}" | grep -qx "package:${saver%%/*}"; then
    t settings put secure screensaver_components "$saver"
  elif [ "$(t settings get secure screensaver_components)" = "$PKG/.SiparioDream" ]; then
    t settings delete secure screensaver_components >/dev/null
  fi
}

install() {
  need_tv
  # APK=<file> installs a ready-made APK (from the GitHub release): no JDK or Android SDK needed.
  local apk="${APK:-}"
  if [ -z "$apk" ]; then "$HERE/build.sh"; apk="$HERE/build/platea-home.apk"; fi
  [ -f "$apk" ] || { echo "[!!] APK not found: $apk"; exit 1; }
  local before; before="$(home_now)"
  if [ ! -s "$STATE/home-before" ] && [ "${before%%/*}" != "$PKG" ]; then echo "$before" > "$STATE/home-before"; fi
  if [ ! -s "$STATE/screensaver-before" ]; then t settings get secure screensaver_components > "$STATE/screensaver-before"; fi
  local out; out="$(adb -s "$SERIAL" install --no-incremental -r "$apk" 2>&1 | tail -1)"
  echo "$out"
  case "$out" in
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*|*signatures\ do\ not\ match*)
      echo "[!!] Platea Home is already installed with another signing key (another build or the release APK)."
      echo "     Run ./platea-home.sh restore first, then install again."; exit 1 ;;
    *Success*) ;;
    *) echo "[!!] The install failed: Home stays $before"; exit 1 ;;
  esac
  # Android hides the Wi-Fi network name without the location permission: the Network panel needs it. No GPS, no internet.
  t pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1 || true
  t pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION >/dev/null 2>&1 || true
  # It must really open first; only then does it become Home. On Fire OS this also matters after an update: the update
  # closes Platea Home, and dumbtv's guard only reacts when Amazon's Home appears, so the next Home press would do nothing.
  local start; start="$(t am start -W -n "$ACT")"
  echo "$start" | grep -q "Status: ok" || { echo "$start"; echo "[!!] Platea Home does not open: Home stays $before"; exit 1; }
  if is_fireos; then
    if [ "${SIPARIO:-0}" = 1 ]; then t settings put secure screensaver_components "$PKG/.SiparioDream"; echo "[OK] Screen saver: Sipario"; fi
    apply_keys
    if guard_on; then
      echo "[OK] Fire OS: Platea Home is installed and dumbtv's Home guard is on. Press Home on the real remote to check."
    else
      echo "[!!] Fire OS: Platea Home is installed, but Fire OS keeps Amazon's Home."
      echo "     To open Platea Home with the Home button, set up dumbtv's Home guard: see firetv/README.md."
    fi
    return 0
  fi
  t cmd package set-home-activity --user 0 "$ACT" >/dev/null
  local now; now="$(home_now)"
  local disabled_now=""
  if [ "$now" != "$ACT" ]; then
    local owner="${now%%/*}"
    case " $STOCK " in
      *" $owner "*)
        echo "[..] The TV still prefers $owner: disabling it (restore enables it again)"
        if t pm disable-user --user 0 "$owner" >/dev/null; then echo "$owner" >> "$STATE/disabled-by-platea-home"; disabled_now="$owner"; fi
        t cmd package set-home-activity --user 0 "$ACT" >/dev/null ;;
    esac
    now="$(home_now)"
  fi
  if [ "$now" != "$ACT" ]; then
    echo "[!!] Home resolves to '$now', not Platea Home: putting the previous Home back"
    # First enable what this run disabled: a disabled launcher cannot be Home again.
    if [ -n "$disabled_now" ]; then
      t pm enable --user 0 "$disabled_now" >/dev/null && echo "[OK] Enabled $disabled_now again"
      grep -vx "$disabled_now" "$STATE/disabled-by-platea-home" > "$STATE/disabled.tmp" || true
      mv "$STATE/disabled.tmp" "$STATE/disabled-by-platea-home"
    fi
    if [ -s "$STATE/home-before" ]; then
      local back; back="$(cat "$STATE/home-before")"
      t cmd package set-home-activity --user 0 "$back" >/dev/null
      now="$(home_now)"
      if [ "$now" = "$back" ]; then echo "[OK] Home is $back again"
      else echo "[!!] Home resolves to '$now', not $back: check it by hand (on Google TV, setupwraith's RecoveryActivity wins while it is enabled)"; fi
    fi
    exit 1
  fi
  if [ "${SIPARIO:-0}" = 1 ]; then
    t settings put secure screensaver_components "$PKG/.SiparioDream"
    echo "[OK] Screen saver: Sipario"
  fi
  apply_keys
  t input keyevent KEYCODE_HOME
  echo "[OK] Platea Home is Home ($(home_now))"
}

restore() {
  need_tv
  if is_fireos; then
    restore_saver
    keys_off
    t pm uninstall "$PKG" >/dev/null && echo "[OK] Platea Home removed"
    rm -f "$STATE/home-before" "$STATE/screensaver-before"
    if guard_on; then
      echo "Fire OS: dumbtv's Home guard is still on. To give Home back to Amazon, run dumbtv's launcher/install.sh --undo."
    fi
    return 0
  fi
  if [ -s "$STATE/disabled-by-platea-home" ]; then
    while read -r p; do [ -n "$p" ] && t pm enable --user 0 "$p" >/dev/null && echo "[OK] Enabled $p again"; done < "$STATE/disabled-by-platea-home"
    : > "$STATE/disabled-by-platea-home"
  fi
  # The Home to go back to: the recorded one if its app is still installed, else the first stock launcher installed.
  # A launcher that a debloat disabled is enabled again: without it the TV would have no Home.
  local target=""; [ -s "$STATE/home-before" ] && target="$(cat "$STATE/home-before")"
  if [ -n "$target" ] && ! t pm list packages "${target%%/*}" | grep -qx "package:${target%%/*}"; then target=""; fi
  if [ -z "$target" ]; then
    for p in $STOCK; do
      if t pm list packages "$p" | grep -qx "package:$p"; then
        target="$(t cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME "$p" | tail -1)"
        case "$target" in "$p"/*) break ;; *) target="$p/.MainActivity"; break ;; esac
      fi
    done
  fi
  [ -n "$target" ] || { echo "[!!] No previous Home and no stock launcher installed: not removing Platea Home"; exit 1; }
  if t pm list packages -d "${target%%/*}" | grep -qx "package:${target%%/*}"; then
    t pm enable --user 0 "${target%%/*}" >/dev/null && echo "[OK] Enabled ${target%%/*} again (it was disabled)"
  fi
  t cmd package set-home-activity --user 0 "$target" >/dev/null
  restore_saver
  keys_off
  local now; now="$(home_now)"
  if [ "${now%%/*}" != "$PKG" ] && [ "${now%%/*}" != android ]; then
    t pm uninstall "$PKG" >/dev/null && echo "[OK] Platea Home removed"
    rm -f "$STATE/home-before" "$STATE/screensaver-before"
  else
    echo "[!!] Home resolves to '$now': not removing Platea Home"
  fi
  echo "Home: $(home_now)"
}

debloat() {
  need_tv
  local list="${1:?give the list: examples/<model>/debloat.txt from CleanYourTV, or your own file}"
  if is_fireos; then
    echo "[!!] Fire OS: CleanYourTV is not meant for Fire TV. Debloat with dumbtv instead (see firetv/README.md)."; exit 1
  fi
  if [ -d "$CACHE/.git" ]; then git -C "$CACHE" pull -q; else mkdir -p "$(dirname "$CACHE")"; git clone -q https://github.com/Gathin23/cleanyourtv "$CACHE"; fi
  [ -x "$CACHE/tools/venv/bin/python" ] || { python3 -m venv "$CACHE/tools/venv"; "$CACHE/tools/venv/bin/pip" install -q -r "$CACHE/tools/requirements.txt"; }
  [ -f "$list" ] || list="$CACHE/$list"
  echo "--- CleanYourTV $(git -C "$CACHE" log -1 --format='%h of %ad' --date=short); latest changes to the tools and the list:"
  git -C "$CACHE" log -5 --format='  %h %ad %s' --date=short -- tools/disable.sh tools/restore.sh "$list" 2>/dev/null || true
  ( cd "$CACHE" && TV_HOST="$TV_HOST" tools/disable.sh "$list" )
  echo "To undo the debloat: (cd $CACHE && TV_HOST=$TV_HOST tools/restore.sh $list)"
}

status() {
  need_tv
  echo "Home:         $(home_now)"
  echo "Version:      $(t dumpsys package $PKG | sed -n 's/^ *versionName=//p' | head -1)"
  echo "Memory:       $(t dumpsys meminfo $PKG | awk '/TOTAL PSS:/{print $3 " KB"; exit}')"
  echo "Screen saver: $(t settings get secure screensaver_components)"
  echo "Remote keys:  $(t settings get secure enabled_accessibility_services | grep -q "$PKG/" && echo on || echo off)"
  if is_fireos; then echo "Fire OS:      yes, dumbtv Home guard $(guard_on && echo on || echo off)"; fi
}

case "${1:-}" in
  build) "$HERE/build.sh" ;;
  install) install ;;
  restore) restore ;;
  debloat) shift; debloat "$@" ;;
  status) status ;;
  *) sed -n 2,12p "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
