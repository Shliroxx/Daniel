#!/usr/bin/env bash
# Testlauf Kraefte 2.0: je Alien jede Kraft (Tasten und Kombos) per /hero power <n> gegen ein Ziel-Dummy,
# Bildschirmfoto je Kraft. Vorher: tools/client_smoke.sh start. Aufruf: tools/power_tour.sh <ausgabeordner> four_arms ...
set -u
cd "$(dirname "$0")/.."
export DISPLAY=:99
OUT=$1; shift
mkdir -p "$OUT"
C=tools/client_smoke.sh
DATA=src/main/resources/data/kingdomomnitrix/kingdomomnitrix/alien
DUMMY='{NoAI:1b,Health:300f,attributes:[{id:"minecraft:generic.max_health",base:300}]}'
i=0
for alien in "$@"; do
	i=$((i+1))
	count=$(python3 -c "import json,sys; print(len(json.load(open(sys.argv[1]))['abilities']))" "$DATA/$alien.json")
	# zufaelliger Versatz: nicht jeder Lauf an derselben (evtl. engen) Stelle
	$C cmd "/spreadplayers ~$((i*40 + RANDOM % 600)) ~$((i*25 + RANDOM % 600)) 0 1 false @s"
	# immer an die Oberflaeche (nicht in Hoehlen), damit das Ziel frei vor dem Alien steht
	$C cmd "/execute positioned over motion_blocking_no_leaves run tp @s ~ ~ ~"
	# flache, freie Testflaeche: Hang oder Schlucht duerfen das Ziel nicht in eine Wand setzen
	$C cmd "/fill ~-8 ~ ~-8 ~8 ~8 ~8 minecraft:air"
	$C cmd "/fill ~-8 ~-1 ~-8 ~8 ~-1 ~8 minecraft:stone"
	# Arenamitte merken: Bewegungs-Kraefte tragen den Spieler sonst aus der Testflaeche
	$C cmd "/kill @e[type=minecraft:marker,tag=power_tour]"
	$C cmd "/summon minecraft:marker ~ ~ ~ {Tags:[\"power_tour\"]}"
	$C cmd "/hero transform kingdomomnitrix:$alien"
	sleep 4
	for ((n=0; n<count; n++)); do
		$C cmd "/kill @e[type=husk]"
		# Krater frueherer Kraefte einebnen
		$C cmd "/execute at @e[type=minecraft:marker,tag=power_tour,limit=1] run fill ~-8 ~ ~-8 ~8 ~8 ~8 minecraft:air"
		$C cmd "/execute at @e[type=minecraft:marker,tag=power_tour,limit=1] run fill ~-8 ~-3 ~-8 ~8 ~-1 ~8 minecraft:stone"
		$C cmd "/execute at @e[type=minecraft:marker,tag=power_tour,limit=1] run tp @s ~ ~ ~ 0 8"
		$C cmd "/summon husk ^ ^1 ^4 $DUMMY"
		sleep 0.6
		$C cmd "/hero power $n"
		sleep 0.5
		$C shot "$OUT/${alien}_$(printf %02d $n).png" >/dev/null 2>&1
		sleep 1.6
	done
	$C cmd "/kill @e[type=husk]"
	$C cmd "/kill @e[type=item]"
done
