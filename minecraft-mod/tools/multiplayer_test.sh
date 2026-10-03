#!/usr/bin/env bash
# Mehrspieler-Test mit bis zu 4 Clients auf einem virtuellen Bildschirm (Xvfb). Voraussetzung:
#   tools/client_smoke.sh server   (Dedicated Server, OP fuer Tester … Tester4)
#   tools/client_smoke.sh join     (Client 1 = "Tester", gestartet ueber Gradle)
# Danach:
#   tools/multiplayer_test.sh add <n>             Client n (2–4) als "Tester<n>" in run-client<n>/ starten und verbinden
#   tools/multiplayer_test.sh cmd <n> "/befehl"   Chatbefehl in Client n (1 = Tester)
#   tools/multiplayer_test.sh key <n> <taste>     Taste in Client n
#   tools/multiplayer_test.sh click <n> <x> <y>   Linksklick in Fenster n (Fensterkoordinaten)
#   tools/multiplayer_test.sh shot <n> <datei>    Bildschirmfoto von Client n
#   tools/multiplayer_test.sh list                laufende Clients
#   tools/multiplayer_test.sh stop                Zusatz-Clients beenden (Client 1 und Server: client_smoke.sh stop)
set -euo pipefail
cd "$(dirname "$0")/.."
export DISPLAY="${DISPLAY:-:99}"
ROOT=$(pwd)

name_of() { [ "$1" = "1" ] && echo "Tester" || echo "Tester$1"; }

# Jeder Zusatz-Client bekommt einen eigenen virtuellen Bildschirm (:100, :101, :102): ohne Fenstermanager folgt der
# Tastaturfokus sonst nicht zuverlaessig, und das Spiel ignoriert kuenstlich zugestellte Tastendruecke.
display_of() { [ "$1" = "1" ] && echo "${DISPLAY}" || echo ":$((98 + $1))"; }

pid_of() {
	local name; name=$(name_of "$1")
	ps -eo pid=,args= | awk -v n="$name" '/fabric.dli.env=client/ && !/awk/ { for (i = 1; i <= NF; i++) if ($i == "--username" && $(i+1) == n) print $1 }' | head -1
}

window_of() {
	local pid; pid=$(pid_of "$1")
	[ -n "$pid" ] || { echo "Client $1 laeuft nicht" >&2; exit 1; }
	DISPLAY=$(display_of "$1") xdotool search --onlyvisible --pid "$pid" --name "Minecraft" | head -1
}

case "${1:-}" in
	add)
		n="${2:?Nummer 2–4}"
		template=$(pid_of 1)
		[ -n "$template" ] || { echo "Erst Client 1 starten: tools/client_smoke.sh join"; exit 1; }
		dir="$ROOT/run-client$n"
		screen=$(display_of "$n")
		if ! pgrep -f "Xvfb $screen " > /dev/null; then
			Xvfb "$screen" -screen 0 854x480x24 > /dev/null 2>&1 &
			sleep 2
		fi
		mkdir -p "$dir"
		# sparsame Einstellungen: vier Clients teilen sich 4 Kerne
		if [ -f "$ROOT/run/options.txt" ]; then
			sed -e 's/^renderDistance:.*/renderDistance:4/' -e 's/^simulationDistance:.*/simulationDistance:5/' \
				-e 's/^maxFps:.*/maxFps:20/' -e 's/^graphicsMode:.*/graphicsMode:0/' "$ROOT/run/options.txt" > "$dir/options.txt"
		fi
		mapfile -t args < <(tr '\0' '\n' < "/proc/$template/cmdline")
		out=("${args[0]}" "-Xmx1536m")
		for ((i = 1; i < ${#args[@]}; i++)); do
			if [ "${args[$i]}" = "--username" ]; then out+=("--username" "$(name_of "$n")"); i=$((i + 1)); else out+=("${args[$i]}"); fi
		done
		# nur neue Beitritts-Meldungen zaehlen (aeltere Zeilen stammen von frueheren Verbindungen)
		joined_before=$(grep -c "$(name_of "$n") joined the game" run-server/logs/latest.log 2> /dev/null || true)
		(cd "$dir" && DISPLAY="$screen" nohup "${out[@]}" > "$dir/client.log" 2>&1 &)
		for _ in $(seq 1 90); do
			sleep 4
			joined=$(grep -c "$(name_of "$n") joined the game" run-server/logs/latest.log 2> /dev/null || true)
			if [ "${joined:-0}" -gt "${joined_before:-0}" ]; then echo "Client $n verbunden."; exit 0; fi
			if grep -qE "Crash report|Exception in thread \"main\"" "$dir/client.log" 2> /dev/null; then echo "Client $n abgestuerzt"; tail -20 "$dir/client.log"; exit 1; fi
		done
		echo "Zeitueberschreitung (Client $n)"; exit 1 ;;
	cmd)
		w=$(window_of "$2"); export DISPLAY; DISPLAY=$(display_of "$2"); xdotool windowfocus "$w" 2> /dev/null || true
		xdotool key t; sleep 0.6
		xdotool type --delay 25 "$3"; sleep 0.3; xdotool key Return; sleep 1 ;;
	key)
		w=$(window_of "$2"); export DISPLAY; DISPLAY=$(display_of "$2"); xdotool windowfocus "$w" 2> /dev/null || true
		xdotool key "$3"; sleep 0.5 ;;
	click)
		w=$(window_of "$2"); export DISPLAY; DISPLAY=$(display_of "$2")
		xdotool mousemove --window "$w" "$3" "$4" click 1; sleep 0.5 ;;
	shot)
		w=$(window_of "$2"); export DISPLAY; DISPLAY=$(display_of "$2"); sleep 0.3
		import -window "$w" "$3" ;;
	list)
		for n in 1 2 3 4; do p=$(pid_of "$n"); [ -n "$p" ] && echo "Client $n ($(name_of "$n")): PID $p"; done; true ;;
	stop)
		for n in 2 3 4; do
			p=$(pid_of "$n")
			if [ -n "$p" ]; then kill "$p" 2> /dev/null || true; fi
			pkill -f "Xvfb $(display_of "$n") " 2> /dev/null || true
		done
		echo "Zusatz-Clients beendet." ;;
	*)
		sed -n 2,14p "$0"; exit 2 ;;
esac
