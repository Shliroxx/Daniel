#!/usr/bin/env bash
# Visual-Audit: fotografiert alle Aliens (Front, Seite, Ego-Sicht), Herzlose und den Boss in einer flachen Testwelt.
# Voraussetzung: tools/client_smoke.sh server && tools/client_smoke.sh join (Spieler "Tester" ist OP).
#
#   tools/visual_audit.sh [ausgabeordner] [teil]    teil: aliens | enemies | all (Standard: all)
#
# Ergebnis: <ordner>/<name>_front.png, _side.png, _first.png, …, plus <ordner>/contact_sheet.png (Uebersicht).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:-build/visual_audit}"
PART="${2:-all}"
C=tools/client_smoke.sh
mkdir -p "$OUT"
log() { printf '[audit] %s\n' "$*"; }

# Bildausschnitt um die Spielfigur (Fenster 1280x720; Spielbereich liegt in der Mitte)
crop() { convert "$1" -crop 640x480+320+120 +repage "$1"; }

stage() {
	$C cmd "/gamerule doMobSpawning false"
	$C cmd "/gamerule doDaylightCycle false"
	$C cmd "/time set noon"
	$C cmd "/weather clear"
	$C cmd "/kill @e[type=!player]"
	$C cmd "/effect give Tester resistance infinite 255 true"
	$C cmd "/hero revert Tester"
}

# Kamera: der Spieler steht, ein unsichtbarer Rahmen ist die Kulisse. F5 zweimal = Ansicht von vorn.
view_front() { $C key F5; $C key F5; sleep 0.8; }
view_back_to_first() { $C key F5; sleep 0.5; }

shoot_alien() {
	local alien="$1"
	log "Alien $alien"
	$C cmd "/hero revert Tester"
	sleep 1
	$C cmd "/tp Tester 0.5 -60 0.5 0 0"
	$C cmd "/hero transform kingdomomnitrix:$alien Tester"
	sleep 0.4
	$C shot "$OUT/${alien}_transform.png"
	sleep 2.5
	view_front
	$C shot "$OUT/${alien}_front.png"; crop "$OUT/${alien}_front.png"
	$C cmd "/tp Tester 0.5 -60 0.5 90 0"
	sleep 0.8
	$C shot "$OUT/${alien}_side.png"; crop "$OUT/${alien}_side.png"
	view_back_to_first
	$C cmd "/tp Tester 0.5 -60 0.5 0 0"
	sleep 0.6
	$C shot "$OUT/${alien}_first.png"
}

shoot_enemy() {
	local entity="$1" name="${1##*:}"
	log "Gegner $entity"
	$C cmd "/kill @e[type=!player]"
	$C cmd "/tp Tester 0.5 -60 0.5 180 10"
	$C cmd "/summon $entity 0.5 -60 -3.5 {NoAI:1b,Rotation:[0f,0f],PersistenceRequired:1b}"
	sleep 1.5
	$C shot "$OUT/enemy_${name}.png"; crop "$OUT/enemy_${name}.png"
}

stage
if [ "$PART" = "all" ] || [ "$PART" = "aliens" ]; then
	for alien in heatblast xlr8 four_arms diamondhead grey_matter; do shoot_alien "$alien"; done
	$C cmd "/hero revert Tester"
fi
if [ "$PART" = "all" ] || [ "$PART" = "enemies" ]; then
	for entity in kingdomomnitrix:shadow kingdomomnitrix:soldier kingdomomnitrix:air_soldier kingdomomnitrix:large_body \
			kingdomomnitrix:darkball; do
		shoot_enemy "$entity"
	done
	$C cmd "/kill @e[type=!player]"
fi
montage "$OUT"/*_front.png "$OUT"/*_side.png "$OUT"/enemy_*.png -tile 5x -geometry 320x240+2+2 -background '#222' \
	"$OUT/contact_sheet.png" 2> /dev/null || true
log "fertig: $OUT"
