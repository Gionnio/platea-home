#!/usr/bin/env bash
# Platea Home: launcher tvOS minimale. Il debloat è quello di CleanYourTV, sempre all'ultima versione.
#
#   ./platea-home.sh build                          compila (CONFIG=<json> per la configurazione della TV)
#   TV_HOST=<ip> ./platea-home.sh install           installa e lo rende la Home, in modo sicuro (SIPARIO=1: anche salvaschermo)
#   TV_HOST=<ip> ./platea-home.sh debloat <elenco>  debloat con tools/disable.sh dell'ultima CleanYourTV
#   TV_HOST=<ip> ./platea-home.sh restore           ridà la Home e il salvaschermo di prima, poi rimuove Platea Home
#   TV_HOST=<ip> ./platea-home.sh status            Home, versione installata, memoria
#
# Installazione sicura: Platea Home deve aprirsi davvero prima di diventare la Home; un launcher di serie viene
# disattivato solo se la TV continua a preferirlo, e se qualcosa non va la Home torna a quella di prima.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
PKG=io.github.gionnio.plateahome
ACT=$PKG/.MainActivity
CACHE="$HERE/.cache/cleanyourtv"
STOCK="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher"

need_tv() {
  : "${TV_HOST:?imposta TV_HOST=<ip della TV>}"
  SERIAL="$TV_HOST:5555"
  adb connect "$SERIAL" >/dev/null 2>&1 || true
  if ! adb -s "$SERIAL" shell echo ok 2>/dev/null | grep -q ok; then
    adb kill-server >/dev/null 2>&1; adb start-server >/dev/null 2>&1; adb connect "$SERIAL" >/dev/null 2>&1 || true
  fi
  adb -s "$SERIAL" shell echo ok 2>/dev/null | grep -q ok || { echo "[!!] TV non raggiungibile su $SERIAL (accesa? debug attivo? premi Consenti)"; exit 1; }
  STATE="$HERE/state/$(echo "$TV_HOST" | tr -c 'A-Za-z0-9._-' '_')"
  mkdir -p "$STATE"
}
t() { adb -s "$SERIAL" shell "$@" | tr -d '\r'; }
home_now() { t cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1; }

install() {
  need_tv
  "$HERE/build.sh"
  local before; before="$(home_now)"
  if [ ! -s "$STATE/home-before" ] && [ "${before%%/*}" != "$PKG" ]; then echo "$before" > "$STATE/home-before"; fi
  if [ ! -s "$STATE/screensaver-before" ]; then t settings get secure screensaver_components > "$STATE/screensaver-before"; fi
  adb -s "$SERIAL" install --no-incremental -r "$HERE/build/platea-home.apk" | tail -1
  # Prima deve aprirsi davvero; solo dopo diventa la Home.
  local start; start="$(t am start -W -n "$ACT")"
  echo "$start" | grep -q "Status: ok" || { echo "$start"; echo "[!!] Platea Home non si apre: la Home resta $before"; exit 1; }
  t cmd package set-home-activity --user 0 "$ACT" >/dev/null
  local now; now="$(home_now)"
  if [ "$now" != "$ACT" ]; then
    local owner="${now%%/*}"
    case " $STOCK " in
      *" $owner "*)
        echo "[..] La TV preferisce ancora $owner: lo disattivo (si riattiva con restore)"
        t pm disable-user --user 0 "$owner" >/dev/null && echo "$owner" >> "$STATE/disabled-by-platea-home"
        t cmd package set-home-activity --user 0 "$ACT" >/dev/null ;;
    esac
    now="$(home_now)"
  fi
  if [ "$now" != "$ACT" ]; then
    echo "[!!] La Home risolve a '$now', non a Platea Home: rimetto quella di prima"
    [ -s "$STATE/home-before" ] && t cmd package set-home-activity --user 0 "$(cat "$STATE/home-before")" >/dev/null
    exit 1
  fi
  if [ "${SIPARIO:-0}" = 1 ]; then
    t settings put secure screensaver_components "$PKG/.SiparioDream"
    echo "[OK] Salvaschermo: Sipario"
  fi
  t input keyevent KEYCODE_HOME
  echo "[OK] Platea Home è la Home ($(home_now))"
}

restore() {
  need_tv
  if [ -s "$STATE/disabled-by-platea-home" ]; then
    while read -r p; do [ -n "$p" ] && t pm enable --user 0 "$p" >/dev/null && echo "[OK] Riattivato $p"; done < "$STATE/disabled-by-platea-home"
    : > "$STATE/disabled-by-platea-home"
  fi
  if [ -s "$STATE/home-before" ]; then t cmd package set-home-activity --user 0 "$(cat "$STATE/home-before")" >/dev/null; fi
  if [ -s "$STATE/screensaver-before" ] && [ "$(cat "$STATE/screensaver-before")" != null ]; then
    t settings put secure screensaver_components "$(cat "$STATE/screensaver-before")"
  fi
  local now; now="$(home_now)"
  if [ "${now%%/*}" != "$PKG" ]; then
    t pm uninstall "$PKG" >/dev/null && echo "[OK] Platea Home rimosso"
    rm -f "$STATE/home-before" "$STATE/screensaver-before"
  else
    echo "[!!] La Home è ancora Platea Home: non la rimuovo (manca la Home di prima?)"
  fi
  echo "Home: $(home_now)"
}

debloat() {
  need_tv
  local list="${1:?indica l'elenco: examples/<modello>/debloat.txt di CleanYourTV o un file tuo}"
  if [ -d "$CACHE/.git" ]; then git -C "$CACHE" pull -q; else mkdir -p "$(dirname "$CACHE")"; git clone -q https://github.com/Gathin23/cleanyourtv "$CACHE"; fi
  [ -x "$CACHE/tools/venv/bin/python" ] || { python3 -m venv "$CACHE/tools/venv"; "$CACHE/tools/venv/bin/pip" install -q -r "$CACHE/tools/requirements.txt"; }
  [ -f "$list" ] || list="$CACHE/$list"
  echo "--- CleanYourTV $(git -C "$CACHE" log -1 --format='%h del %ad' --date=short); ultimi cambi a strumenti ed elenco:"
  git -C "$CACHE" log -5 --format='  %h %ad %s' --date=short -- tools/disable.sh tools/restore.sh "$list" 2>/dev/null || true
  ( cd "$CACHE" && TV_HOST="$TV_HOST" tools/disable.sh "$list" )
  echo "Ripristino del debloat: (cd $CACHE && TV_HOST=$TV_HOST tools/restore.sh $list)"
}

status() {
  need_tv
  echo "Home:       $(home_now)"
  echo "Versione:   $(t dumpsys package $PKG | sed -n 's/^ *versionName=//p' | head -1)"
  echo "Memoria:    $(t dumpsys meminfo $PKG | awk '/TOTAL PSS:/{print $3 " KB"; exit}')"
  echo "Salvaschermo: $(t settings get secure screensaver_components)"
}

case "${1:-}" in
  build) "$HERE/build.sh" ;;
  install) install ;;
  restore) restore ;;
  debloat) shift; debloat "$@" ;;
  status) status ;;
  *) sed -n 2,11p "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
