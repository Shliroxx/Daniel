#!/usr/bin/env bash
# Startet den Minecraft-Client der Mod auf einem virtuellen Bildschirm (Xvfb) und steuert ihn per xdotool.
# Nur fuer Linux-Testumgebungen (Cloud/CI). Benoetigt: Xvfb, xdotool, ImageMagick (import).
#
#   tools/client_smoke.sh start [welt]      Client starten und in die Welt einsteigen (Standard: smoke)
#   tools/client_smoke.sh cmd "/befehl"      Chatbefehl ausfuehren
#   tools/client_smoke.sh key <taste>        Taste druecken (xdotool-Name, z. B. g, F5, Return)
#   tools/client_smoke.sh click [anzahl]     Linksklicks (Abstand 0,45 s)
#   tools/client_smoke.sh shot <datei.png>   Bildschirmfoto
#   tools/client_smoke.sh errors             unerwartete Fehler aus dem Log
#   tools/client_smoke.sh stop               Client beenden
set -euo pipefail
cd "$(dirname "$0")/.."
export DISPLAY="${DISPLAY:-:99}"
LOG=run/logs/latest.log
OUT=${SMOKE_OUT:-/tmp/kingdomomnitrix-client.log}

window() { xdotool search --name "Minecraft" | head -1; }

java_pids() {
	ps -eo pid=,comm=,args= | awk '$2 == "java" && /net\.fabricmc\.devlaunchinjector|KnotClient|GradleDaemon|GradleWrapperMain/ {print $1}'
}

case "${1:-}" in
	start)
		world="${2:-smoke}"
		if ! pgrep -x Xvfb > /dev/null; then
			Xvfb "$DISPLAY" -screen 0 1280x720x24 > /dev/null 2>&1 &
			sleep 2
		fi
		rm -rf run/logs
		nohup ./gradlew runClient --no-daemon --args="--quickPlaySingleplayer $world" > "$OUT" 2>&1 &
		for _ in $(seq 1 120); do
			sleep 4
			if grep -qE "joined the game" "$LOG" 2>/dev/null; then echo "Client in der Welt."; exit 0; fi
			if grep -qE "BUILD FAILED|Crash report|Exception in thread \"main\"" "$OUT" "$LOG" 2>/dev/null; then
				echo "Start fehlgeschlagen:"; grep -E "error:|BUILD FAILED|Exception" "$OUT" "$LOG" | head -20; exit 1
			fi
		done
		echo "Zeitueberschreitung beim Start"; exit 1 ;;
	cmd)
		w=$(window); xdotool key --window "$w" t; sleep 0.6
		xdotool type --window "$w" --delay 25 "$2"; sleep 0.3; xdotool key --window "$w" Return; sleep 1 ;;
	key)
		xdotool key --window "$(window)" "$2"; sleep 0.5 ;;
	click)
		for _ in $(seq 1 "${2:-1}"); do xdotool click 1; sleep 0.45; done ;;
	shot)
		import -window root "$2" ;;
	errors)
		grep -E "ERROR|Exception" "$LOG" | grep -viE "data fixer|yggdrasil|allowlist|SoundSystem|OpenAL|JsonSyntax|BEGIN_OBJECT|chat session|No key layers" || echo "keine unerwarteten Fehler" ;;
	stop)
		for pid in $(java_pids); do kill "$pid" 2> /dev/null || true; done
		echo "Client beendet." ;;
	*)
		sed -n 2,13p "$0"; exit 2 ;;
esac
