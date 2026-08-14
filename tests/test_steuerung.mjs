/* Test der Steuerlogik.
 *
 * Genau die Entscheidungen, die in der Praxis Aerger gemacht haben: eingefrorene
 * Kamera, Pause zur falschen Zeit, Sprachbefehle im falschen Zustand.
 *
 * Ausfuehren:  node tests/test_steuerung.mjs
 */

import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import assert from 'node:assert/strict';

const require = createRequire(import.meta.url);
const Steuerung = require(join(dirname(fileURLToPath(import.meta.url)), '..', 'web', 'shisha', 'steuerung.js'));

// --- Bildguete ---------------------------------------------------------------
const gut = { helligkeit: 120, bewegung: 2, schaerfe: 20 };
assert.equal(Steuerung.bildBewerten(gut).ok, true);

// Dunkelheit wird zuerst gemeldet: sie ist die Ursache, Unschaerfe oft die Folge.
assert.equal(Steuerung.bildBewerten({ helligkeit: 10, bewegung: 99, schaerfe: 1 }).problem, 'zu dunkel');
assert.equal(Steuerung.bildBewerten({ ...gut, bewegung: 30 }).problem, 'halt still');
assert.equal(Steuerung.bildBewerten({ ...gut, schaerfe: 2 }).problem, 'unscharf');
assert.equal(Steuerung.bildBewerten(null).ok, false);

// Geduld: ein wackliges Bild wird nach fuenf Sekunden trotzdem genommen —
// sonst wartet die App ewig und schickt nie etwas los.
const wacklig = { helligkeit: 60, bewegung: 12, schaerfe: 4.5 };
assert.equal(Steuerung.bildBewerten(wacklig).ok, false, 'am Anfang bleibt es streng');
assert.equal(Steuerung.bildBewerten(wacklig, Steuerung.GRENZEN, 6000).ok, true, 'nach 6 s reicht es');
assert.equal(Steuerung.bildBewerten(wacklig, Steuerung.GRENZEN, 6000).nachsichtig, true);
assert.equal(Steuerung.bildBewerten(gut).nachsichtig, false, 'ein gutes Bild braucht keine Nachsicht');

// Und nach zwoelf Sekunden geht auch ein wildes Bild raus — Schweigen hilft nicht.
const wild = { helligkeit: 60, bewegung: 120, schaerfe: 1 };
assert.equal(Steuerung.bildBewerten(wild, Steuerung.GRENZEN, 6000).ok, false, 'nach 6 s noch nicht');
assert.equal(Steuerung.bildBewerten(wild, Steuerung.GRENZEN, 13000).ok, true, 'nach 13 s trotzdem');

// Ganz aus geht die Pruefung aber nie: stockdunkel bleibt stockdunkel.
assert.equal(
  Steuerung.bildBewerten({ helligkeit: 8, bewegung: 1, schaerfe: 9 }, Steuerung.GRENZEN, 60000).problem,
  'zu dunkel'
);

// --- Pause nach einem Fehler ---------------------------------------------------
// Ein abgelehnter Schluessel wird beim zwanzigsten Versuch nicht besser.
assert.equal(Steuerung.fehlerRuhe(401), null);
assert.equal(Steuerung.fehlerRuhe(403), null);
// Erschoepftes Kontingent braucht Zeit, keine Wiederholungen.
assert.ok(Steuerung.fehlerRuhe(429) >= 20000);
// Eine Stoerung beim Anbieter darf es bald wieder versuchen.
assert.ok(Steuerung.fehlerRuhe(500) <= 5000 && Steuerung.fehlerRuhe(500) > 0);
assert.equal(Steuerung.fehlerRuhe(0), Steuerung.fehlerRuhe(500), 'Netzabbruch zaehlt als Stoerung');

// --- Kameralage --------------------------------------------------------------
const laeuft = { spurLebt: true, videoLaeuft: true };
assert.equal(Steuerung.kameraLage(laeuft).aktion, 'analysieren');
assert.equal(Steuerung.kameraLage(laeuft).blindSeit, 0, 'laufende Kamera setzt den Zaehler zurueck');

// Kurzer Aussetzer: erst warten, nicht gleich die Blende werfen.
const ersteStoerung = Steuerung.kameraLage({ spurLebt: true, videoLaeuft: false, blindSeit: 0, jetzt: 1000 });
assert.equal(ersteStoerung.aktion, 'warten');
assert.equal(ersteStoerung.blindSeit, 1000, 'merkt sich den Beginn der Stoerung');

const kurzDanach = Steuerung.kameraLage({ spurLebt: true, videoLaeuft: false, blindSeit: 1000, jetzt: 3000 });
assert.equal(kurzDanach.aktion, 'warten', 'zwei Sekunden sind noch kein Einfrieren');

const langeStill = Steuerung.kameraLage({ spurLebt: true, videoLaeuft: false, blindSeit: 1000, jetzt: 9000 });
assert.equal(langeStill.aktion, 'pausieren');
assert.match(langeStill.grund, /eingefroren/);

// Tote Spur bekommt eine andere Begruendung als ein stehendes Bild.
const spurTot = Steuerung.kameraLage({ spurLebt: false, videoLaeuft: false, blindSeit: 1000, jetzt: 9000 });
assert.match(spurTot.grund, /freigegeben/);

// --- Rueckkehr aus dem Hintergrund -------------------------------------------
const sitzung = { sitzungDa: true, pausiert: false, imHauptmenue: false };
assert.equal(Steuerung.rueckkehrPlan({ ...sitzung, spurLebt: true, videoLaeuft: true }).aktion, 'weiter',
             'lebende Kamera laeuft ohne Nachfrage weiter');
assert.equal(Steuerung.rueckkehrPlan({ ...sitzung, spurLebt: false, videoLaeuft: false }).aktion, 'pausieren');
assert.equal(Steuerung.rueckkehrPlan({ ...sitzung, pausiert: true, spurLebt: true, videoLaeuft: true }).aktion, 'nichts',
             'eine offene Pausenblende wird nicht hintenrum weggeraeumt');
assert.equal(Steuerung.rueckkehrPlan({ ...sitzung, imHauptmenue: true, spurLebt: true, videoLaeuft: true }).aktion, 'nichts');
assert.equal(Steuerung.rueckkehrPlan({ ...sitzung, sitzungDa: false, spurLebt: true, videoLaeuft: true }).aktion, 'nichts');

// --- Sparmodus ---------------------------------------------------------------
assert.equal(Steuerung.lohntAnalyse(0.3, true).lohnt, false, 'unveraendertes Bild kostet kein Kontingent');
assert.equal(Steuerung.lohntAnalyse(0.3, false).lohnt, true, 'ohne Sparmodus wird immer gefragt');
assert.equal(Steuerung.lohntAnalyse(9, true).lohnt, true);
assert.equal(Steuerung.lohntAnalyse(null, true).lohnt, true, 'ohne Vergleichswert lieber fragen');

// --- Marker mitfuehren -------------------------------------------------------
const marker = { x: 0.5, y: 0.5, w: 0.2, h: 0.2, typ: 'remove' };
assert.deepEqual(Steuerung.markerVerschieben(marker, { x: 0, y: 0 }), marker, 'ohne Bewegung keine Aenderung');

const verschoben = Steuerung.markerVerschieben(marker, { x: 0.1, y: -0.2 });
assert.equal(Math.round(verschoben.x * 100), 60);
assert.equal(Math.round(verschoben.y * 100), 30);
assert.equal(verschoben.typ, 'remove', 'die uebrigen Felder bleiben');

// Am Rand wird geklemmt, damit nichts aus dem Bild rutscht.
const anschlag = Steuerung.markerVerschieben(marker, { x: 0.9, y: 0.9 });
assert.equal(Math.round(anschlag.x * 100), 80);
assert.equal(Math.round(anschlag.y * 100), 80);

const gegenAnschlag = Steuerung.markerVerschieben(marker, { x: -0.9, y: -0.9 });
assert.equal(gegenAnschlag.x, 0);
assert.equal(gegenAnschlag.y, 0);

// --- Verblassen --------------------------------------------------------------
assert.equal(Steuerung.alterFaktor(0), 1, 'frischer Marker ist voll da');
assert.ok(Steuerung.alterFaktor(3000) < 1 && Steuerung.alterFaktor(3000) > 0.4);
assert.equal(Steuerung.alterFaktor(60000), 0.25, 'alte Marker verschwinden nicht ganz');

// --- Sprachbefehle im richtigen Zustand --------------------------------------
const live = { laeuft: true, pausiert: false, imHauptmenue: false };
assert.equal(Steuerung.befehlPlan('weiter', live).aktion, 'phase_vor');
assert.equal(Steuerung.befehlPlan('analyse', live).aktion, 'vollanalyse');
assert.equal(Steuerung.befehlPlan('pause', live).aktion, 'pausieren');
assert.equal(Steuerung.befehlPlan('start', live).aktion, 'nichts', 'was laeuft, muss nicht gestartet werden');

const pause = { laeuft: false, pausiert: true, imHauptmenue: false };
assert.equal(Steuerung.befehlPlan('start', pause).aktion, 'fortsetzen');
assert.equal(Steuerung.befehlPlan('menue', pause).aktion, 'hauptmenue');
assert.equal(Steuerung.befehlPlan('weiter', pause).aktion, 'nichts',
             'in der Pause wird die Phase nicht heimlich weitergestellt');
assert.equal(Steuerung.befehlPlan('analyse', pause).aktion, 'nichts');

const menue = { laeuft: false, pausiert: false, imHauptmenue: true };
assert.equal(Steuerung.befehlPlan('start', menue).aktion, 'kamera_starten');
assert.equal(Steuerung.befehlPlan('analyse', menue).aktion, 'nichts');
assert.equal(Steuerung.befehlPlan('neu', menue).aktion, 'nichts');

// Unbekanntes tut nichts, statt zu raten.
assert.equal(Steuerung.befehlPlan('kaffeekochen', live).aktion, 'nichts');
assert.equal(Steuerung.befehlPlan(null, live).aktion, 'nichts');

console.log('ALLE TESTS BESTANDEN');
