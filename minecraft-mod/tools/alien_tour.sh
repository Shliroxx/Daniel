#!/usr/bin/env bash
# Testlauf: je Alien alle 6 Faehigkeiten (R V B, Shift+R V B) gegen zwei Ziel-Dummies, Bildschirmfoto je Faehigkeit.
# Vorher: tools/client_smoke.sh start. Aufruf: tools/alien_tour.sh <ausgabeordner> heatblast xlr8 ...
set -u
cd "$(dirname "$0")/.."
export DISPLAY=:99
OUT=$1; shift
C=tools/client_smoke.sh
i=0
for alien in "$@"; do
	i=$((i+1))
	$C cmd "/spreadplayers ~$((i*40)) ~$((i*25)) 0 1 false @s"
	$C cmd "/hero transform kingdomomnitrix:$alien"
	sleep 4
	$C cmd "/tp @s ~ ~ ~ 0 12"
	$C cmd "/summon husk ^-1 ^ ^6 {NoAI:1b,Health:200f,Attributes:[{Name:\"generic.max_health\",Base:200}]}"
	$C cmd "/summon husk ^1 ^ ^7 {NoAI:1b,Health:200f,Attributes:[{Name:\"generic.max_health\",Base:200}]}"
	sleep 0.5
	n=0
	for k in r v b R V B; do
		n=$((n+1))
		if [[ $k =~ [A-Z] ]]; then
			xdotool keydown shift; sleep 0.1; xdotool key "${k,,}"; sleep 0.1; xdotool keyup shift
		else
			xdotool key "$k"
		fi
		sleep 0.45
		$C shot "$OUT/${alien}_$n.png" >/dev/null 2>&1
		sleep 1.4
	done
	$C cmd "/kill @e[type=husk]"
	$C cmd "/kill @e[type=item]"
done
