#!/usr/bin/env bash
# Platea Home: il launcher di CleanYourTV con lo stile Platea Home, compilato sopra l'ultima versione di CleanYourTV.
#
#   ./platea-home.sh build                 scarica CleanYourTV, applica overlay/ e patches/, compila
#   TV_HOST=<ip> ./platea-home.sh install  compila e installa con lo script di CleanYourTV (Home sicura)
#   TV_HOST=<ip> ./platea-home.sh theme [cartella]   invia il tema (di serie: theme/)
#   ./platea-home.sh status                versione di CleanYourTV usata e ultima buona
#
# Variabili: CYT_REF (commit, tag o ramo; di serie origin/main), STOCK_LAUNCHER (passata a build-and-install.sh),
# APP_ID (nome del pacchetto; di serie quello di CleanYourTV, com.gathin.tvhome), JAVA_HOME (JDK 17).
# Se la versione richiesta non si applica o non compila, si torna all'ultima buona (state/last-good) e lo si dice.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
CACHE="$HERE/.cache/cleanyourtv"
BUILD="$HERE/build/cyt"
STATE="$HERE/state"
export JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/corretto-17.0.18/Contents/Home}"
mkdir -p "$STATE" "$HERE/build"

fetch() {
  if [ -d "$CACHE/.git" ]; then git -C "$CACHE" fetch -q origin
  else mkdir -p "$(dirname "$CACHE")"; git clone -q https://github.com/Gathin23/cleanyourtv "$CACHE"; fi
  if [ ! -x "$CACHE/tools/venv/bin/python" ]; then
    python3 -m venv "$CACHE/tools/venv" && "$CACHE/tools/venv/bin/pip" install -q -r "$CACHE/tools/requirements.txt"
  fi
}

# Estrae CleanYourTV a quel commit in build/cyt, applica overlay e patch, compila. 0 = APK pronto.
build_at() {
  local sha="$1"
  rm -rf "$BUILD"; mkdir -p "$BUILD"
  git -C "$CACHE" archive "$sha" | tar -x -C "$BUILD"
  cp -R "$HERE/overlay/." "$BUILD/"
  python3 -I "$HERE/patches/apply.py" "$BUILD" || return 1
  ln -s "$CACHE/tools/venv" "$BUILD/tools/venv"
  local sdk="${ANDROID_HOME:-}"
  [ -n "$sdk" ] || for d in ~/Library/Android/sdk ~/Android/Sdk /opt/homebrew/share/android-commandlinetools; do [ -d "$d" ] && sdk="$d" && break; done
  echo "sdk.dir=$sdk" > "$BUILD/launcher/local.properties"
  local appid=()
  [ -n "${APP_ID:-}" ] && appid=(-PlauncherApplicationId="$APP_ID")
  ( cd "$BUILD/launcher" && ./gradlew assembleRelease -q --console=plain ${appid[@]+"${appid[@]}"} ) || return 1
  [ -f "$BUILD/launcher/app/build/outputs/apk/release/app-release.apk" ]
}

build() {
  fetch
  local want sha last
  want="${CYT_REF:-origin/main}"
  sha="$(git -C "$CACHE" rev-parse --verify "$want^{commit}")"
  last="$(cat "$STATE/last-good" 2>/dev/null || true)"
  if [ -n "$last" ] && [ "$last" != "$sha" ]; then
    echo "--- CleanYourTV: novità nel launcher dall'ultima versione buona ($(git -C "$CACHE" rev-list --count "$last..$sha" -- launcher) commit):"
    git -C "$CACHE" log --format='  %h %ad %s' --date=short "$last..$sha" -- launcher | head -15
  fi
  echo "[..] Compilo Platea Home sopra CleanYourTV $(git -C "$CACHE" log -1 --format='%h del %ad' --date=short "$sha")"
  if build_at "$sha"; then
    echo "$sha" > "$STATE/last-good"
    echo "[OK] APK pronto: $BUILD/launcher/app/build/outputs/apk/release/app-release.apk"
  elif [ -n "$last" ] && [ "$last" != "$sha" ]; then
    echo "[!!] Questa versione di CleanYourTV non si applica o non compila: torno all'ultima buona ($last)."
    build_at "$last" || { echo "[!!] Non compila nemmeno l'ultima buona: controlla JDK e SDK."; exit 1; }
    echo "[OK] APK pronto (versione buona precedente)."
  else
    echo "[!!] Build fallita e nessuna versione buona salvata."; exit 1
  fi
}

case "${1:-}" in
  build) build ;;
  install)
    : "${TV_HOST:?imposta TV_HOST=<ip della TV>}"
    build
    ( cd "$BUILD" && SKIP_BUILD=1 launcher/build-and-install.sh )
    ;;
  theme)
    : "${TV_HOST:?imposta TV_HOST=<ip della TV>}"
    [ -d "$BUILD/tools" ] || { echo "Prima ./platea-home.sh build"; exit 1; }
    ( cd "$BUILD" && tools/theme.sh "$(cd "${2:-$HERE/theme}" && pwd)" )
    ;;
  status)
    echo "Ultima versione buona di CleanYourTV: $(cat "$STATE/last-good" 2>/dev/null || echo nessuna)"
    [ -d "$CACHE/.git" ] && echo "Ultima disponibile: $(git -C "$CACHE" log -1 --format='%h %ad %s' --date=short origin/main)"
    ;;
  *) sed -n 2,11p "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
