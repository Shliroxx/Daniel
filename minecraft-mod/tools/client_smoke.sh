#!/usr/bin/env bash
# Startet den Minecraft-Client der Mod auf einem virtuellen Bildschirm (Xvfb) und steuert ihn per xdotool.
# Nur fuer Linux-Testumgebungen (Cloud/CI). Benoetigt: Xvfb, xdotool, ImageMagick (import).
#
#   tools/client_smoke.sh start [welt]      Client starten und in die Welt einsteigen (Standard: smoke)
#   tools/client_smoke.sh server            Dedicated Server (run-server/, offline, ohne allow-flight) starten
#   tools/client_smoke.sh join              Client starten und mit dem lokalen Server verbinden
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
	ps -eo pid=,comm=,args= | awk '$2 == "java" && /net\.fabricmc\.devlaunchinjector|KnotClient|KnotServer|GradleDaemon|GradleWrapperMain/ {print $1}'
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
	server)
		mkdir -p run-server
		echo "eula=true" > run-server/eula.txt
		[ -f run-server/server.properties ] || printf 'online-mode=false\nallow-flight=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nenforce-secure-profile=false\n' > run-server/server.properties
		# Testspieler Tester, Tester2 … Tester4 als OP (Offline-UUID = MD5-UUID von "OfflinePlayer:<Name>")
		python3 -c 'import hashlib,uuid,json
def op(n):
    b=bytearray(hashlib.md5(("OfflinePlayer:"+n).encode()).digest());b[6]=b[6]&0x0f|0x30;b[8]=b[8]&0x3f|0x80
    return {"uuid":str(uuid.UUID(bytes=bytes(b))),"name":n,"level":4,"bypassesPlayerLimit":False}
print(json.dumps([op(n) for n in ["Tester","Tester2","Tester3","Tester4"]]))' > run-server/ops.json
		rm -rf run-server/logs
		nohup ./gradlew runServer --no-daemon --args="nogui" > "${OUT%.log}-server.log" 2>&1 &
		for _ in $(seq 1 90); do
			sleep 4
			if grep -q "Done (" run-server/logs/latest.log 2>/dev/null; then echo "Server bereit."; exit 0; fi
			if grep -qE "BUILD FAILED|Crash report" "${OUT%.log}-server.log" 2>/dev/null; then echo "Serverstart fehlgeschlagen"; exit 1; fi
		done
		echo "Zeitueberschreitung beim Serverstart"; exit 1 ;;
	join)
		if ! pgrep -x Xvfb > /dev/null; then
			Xvfb "$DISPLAY" -screen 0 1280x720x24 > /dev/null 2>&1 &
			sleep 2
		fi
		rm -rf run/logs
		nohup ./gradlew runClient --no-daemon --args="--username Tester --quickPlayMultiplayer localhost:25565" > "$OUT" 2>&1 &
		for _ in $(seq 1 120); do
			sleep 4
			if grep -q "joined the game" run-server/logs/latest.log 2>/dev/null; then echo "Client verbunden."; exit 0; fi
			if grep -qE "BUILD FAILED|Crash report|Exception in thread \"main\"" "$OUT" 2>/dev/null; then echo "Start fehlgeschlagen"; exit 1; fi
		done
		echo "Zeitueberschreitung beim Verbinden"; exit 1 ;;
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
