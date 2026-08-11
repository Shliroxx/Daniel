/* Test der Analyse-Engine.
 *
 * Gefuettert wird eine absichtlich fehlerhafte Modellantwort: geschoente Note,
 * Marker ausserhalb des Bildes, erfundene Aktion, Prognose unter dem Ist-Stand.
 * Der Test prueft, dass die Engine das geradezieht.
 *
 * Ausfuehren:  node tests/test_engine.mjs
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import assert from 'node:assert/strict';

const hier = dirname(fileURLToPath(import.meta.url));
const webDir = join(hier, '..', 'web', 'shisha');

// --- Browser-Umgebung nachbauen, so weit die Engine sie braucht -------------
const speicher = new Map();
globalThis.localStorage = {
  getItem: (k) => (speicher.has(k) ? speicher.get(k) : null),
  setItem: (k, v) => speicher.set(k, String(v)),
  removeItem: (k) => speicher.delete(k),
};
globalThis.performance = globalThis.performance || { now: () => Date.now() };
globalThis.location = { origin: 'https://beispiel.test' };

let letzteAnfrage = null;
let antwortText = '';

globalThis.fetch = async (url, optionen = {}) => {
  if (String(url).endsWith('spec.json')) {
    return { ok: true, json: async () => JSON.parse(readFileSync(join(webDir, 'spec.json'), 'utf8')) };
  }
  letzteAnfrage = { url: String(url), optionen };
  // Antwort so verpacken, wie Gemini sie liefert.
  const umschlag = { candidates: [{ content: { parts: [{ text: antwortText }] } }] };
  return { ok: true, status: 200, text: async () => JSON.stringify(umschlag) };
};

const Engine = (await import(join(webDir, 'engine.js'))).default
  || (await import('node:module')).createRequire(import.meta.url)(join(webDir, 'engine.js'));

await Engine.specLaden();

// --- Die absichtlich fehlerhafte Modellantwort ------------------------------
const ANTWORT = {
  analysis_status: 'ok',
  bildqualitaet: { schaerfe: 82, licht: 70, perspektive: 'schraeg', kopf_vollstaendig: true, hinweis: '' },
  kopf: { art: 'phunnel', modell: 'Oblako M', geometrie: 'mittel, tief',
          zentrale_oeffnung_sichtbar: true, quelle: 'observed', confidence: 88 },
  tabak: { fuellhoehe_mm: 1.5, fuellhoehe_quelle: 'estimated', dichte: 72, gleichmaessigkeit: 55,
           klumpen: true, luecken: false, randkontakt: true, ueber_rand: false,
           menge_gramm: 'ca. 14-17 g', quelle: 'estimated', confidence: 71 },
  airflow: { zentrale_oeffnung_frei: true, blockade_risiko: 'medium', notiz: 'leicht verdichtet', confidence: 65 },
  hmd: { erkannt: false, modell: null, zentriert: null, abstand_mm: null, kontakt_tabak: null, confidence: 20 },
  kohle: { status: 'not_visible', anzahl: 3, position: '', hotspot_risiko: 'unknown', confidence: 10 },
  scores: { tabak_verteilung: 60, fuellhoehe: 70, airflow: 65, hitzemanagement: 55,
            kopfgeometrie: 85, tabak_kompatibilitaet: 75, zielerreichung: 60 },
  gesamtscore: 99,                       // geschoent — muss ueberschrieben werden
  probleme: [
    { id: 'randkontakt', severity: 'critical', kategorie: 'fuellhoehe', titel: 'Tabak am Rand',
      beschreibung: 'Bei 3 Uhr liegt Tabak an der Wand.', confidence: 80, aktion: 'remove_tobacco' },
    { id: 'klumpen', severity: 'medium', kategorie: 'tabak_verteilung', titel: 'Klumpen',
      beschreibung: 'Verdichteter Batzen links.', confidence: 62, aktion: 'loosen_tobacco' },
    { id: 'egal', severity: 'low', kategorie: 'airflow', titel: 'Leicht dicht',
      beschreibung: 'x', confidence: 40, aktion: 'loosen_tobacco' },
  ],
  optimierungen: [
    { schritt: 2, aktion: 'loosen_tobacco', bereich: 'links', text: 'Lockere den Batzen auf.', wirkung: 'gleichmaessige Hitze' },
    { schritt: 1, aktion: 'remove_tobacco', bereich: '3 Uhr', text: 'Nimm bei 3 Uhr eine Prise weg.', wirkung: 'kein Randanbrand' },
    { schritt: 3, aktion: 'quatsch', bereich: '', text: 'Oberflaeche glattziehen.', wirkung: '' },
  ],
  ar_marker: [
    { typ: 'remove', x: 0.62, y: 0.30, w: 0.18, h: 0.15, label: 'hier weg', aktion: 'remove_tobacco' },
    { typ: 'loosen', x: 0.2, y: 0.4, w: 0.2, h: 0.2, label: 'auflockern', aktion: 'loosen_tobacco' },
    { typ: 'kaputt', x: 1.4, y: 0.4, w: 0.2, h: 0.2, label: 'geraten', aktion: 'x' },
    { typ: 'remove', x: 1.05, y: 0.5, w: 0.2, h: 0.2, label: 'knapp daneben', aktion: 'remove_tobacco' },
    { typ: 'fill_height', x: 0.3, y: 0.55, w: 0.4, h: 0.05, label: 'Zielhoehe', aktion: 'add_tobacco' },
  ],
  prognose: { score_nach_optimierung: 40, geschmack: 'hoch', rauch: 'gleich', dauer: 'hoch',
              hitzerisiko: 'runter', verbesserung: { tabak_verteilung: 88, hitzemanagement: 80, airflow: 85 } },
  confidence: { gesamt: 0, kopf_erkennung: 88, tabak_analyse: 71, fuellhoehe: 60, airflow: 65,
                hitzemanagement: 40, optimierung: 70 },
  rueckfrage: null,
  coach_satz: 'Nimm bei drei Uhr etwas Tabak weg.',
};

// --- JSON aus verrauschtem Text --------------------------------------------
assert.equal(Engine.jsonAusText('```json\n{"a": 1}\n```').a, 1);
assert.equal(Engine.jsonAusText('Hier bitte: {"a": {"b": 2}} — fertig.').a.b, 2);
assert.throws(() => Engine.jsonAusText('gar kein json'), /kein JSON/);

// --- Deterministische Note --------------------------------------------------
// 60*.20 + 70*.15 + 65*.15 + 55*.20 + 85*.10 + 75*.10 + 60*.10 = 64.75 -> 65
const a = Engine.normalisiere(ANTWORT, true);
assert.equal(a.gesamtscore, 65, `Note falsch: ${a.gesamtscore}`);
assert.equal(a.stufe, 'acceptable');
assert.equal(a.stufe_text, 'brauchbar');

// dieselbe Eingabe muss dieselbe Note ergeben
assert.equal(Engine.normalisiere(ANTWORT, true).gesamtscore, 65);

// --- Geradeziehen -----------------------------------------------------------
assert.equal(a.probleme.length, 2, 'live: hoechstens zwei Probleme');
assert.equal(a.probleme[0].severity, 'critical', 'kritisches zuerst');
assert.equal(a.probleme[0].symbol, '🔴');
assert.deepEqual(a.optimierungen.map((o) => o.schritt), [1, 2, 3]);
assert.equal(a.optimierungen[0].aktion, 'remove_tobacco');
assert.equal(a.optimierungen[2].aktion, 'redistribute_tobacco', 'erfundene Aktion ersetzt');
assert.equal(a.optimierungen[2].aktion_text, 'neu verteilen');
assert.equal(a.prognose.score_nach_optimierung, 65, 'Prognose nie unter Ist-Stand');
assert.equal(a.confidence.gesamt, 66, 'Gesamtsicherheit aus den anderen gemittelt');
assert.equal(a.tabak.menge_gramm, 'ca. 14-17 g');
assert.equal(a.kohle.anzahl, null, 'nicht sichtbare Kohle hat keine Anzahl');

const marker = a.ar_marker;
assert.ok(!marker.some((m) => m.label === 'geraten'), 'x=1.4 ist geraten und fliegt raus');
assert.ok(marker.some((m) => m.label === 'knapp daneben'), 'x=1.05 wird zurechtgerueckt');
assert.ok(marker.every((m) => m.x >= 0 && m.x + m.w <= 1.0001 && m.y >= 0 && m.y + m.h <= 1.0001));
assert.ok(marker.every((m) => Engine.spec.marker_typen.includes(m.typ)));

// Vollanalyse kuerzt weniger stark
assert.equal(Engine.normalisiere(ANTWORT, false).probleme.length, 3);

// Ohne Kopf im Bild gibt es keine Note statt einer erfundenen
const leer = Engine.normalisiere({ analysis_status: 'no_head_detected', scores: { tabak_verteilung: 90 } }, true);
assert.equal(leer.gesamtscore, null);
assert.equal(leer.stufe_text, null);

// Muell darf nicht durchschlagen
const muell = Engine.normalisiere({ scores: 'kaputt', probleme: 'nein', ar_marker: 42, tabak: null }, false);
assert.equal(muell.gesamtscore, 0);
assert.deepEqual(muell.probleme, []);
assert.deepEqual(muell.ar_marker, []);
assert.equal(muell.tabak.fuellhoehe_mm, null);

// --- Prompt -----------------------------------------------------------------
const prompt = Engine.promptBauen({
  modus: 'live',
  phase: 'einstreuen',
  kontext: { ziel: 'geschmack', kopf_modell: 'Oblako Phunnel M', tabak_marke: 'Adalya',
             tabak_sorte: 'Love 66', hmd: 'Kaloud Lotus', kohlen: '3 x 26er', notiz: '' },
  verlauf: '',
  lernen: '',
});
['Oblako Phunnel M', 'Adalya', 'Kaloud Lotus', 'Maximaler Geschmack', '3 x 26er', 'Einstreuen', 'analysis_status']
  .forEach((teil) => assert.ok(prompt.includes(teil), `fehlt im Prompt: ${teil}`));

const offen = Engine.promptBauen({
  modus: 'voll', kontext: { ziel: 'balanced' }, verlauf: '', lernen: '',
});
assert.ok(offen.includes('Nicht angegeben: Kopfmodell, Tabak, HMD, Kohlenanzahl'));

// --- Sitzung ----------------------------------------------------------------
antwortText = JSON.stringify(ANTWORT);
Engine.einstellungenSpeichern({ anbieter: 'gemini', gemini_key: 'test-key', gemini_modell: 'gemini-2.5-flash' });
assert.ok(Engine.bereit());

const sitzung = new Engine.Sitzung({ ziel: 'geschmack', kopf_modell: 'Oblako Phunnel M' });
const eins = await sitzung.analysieren(new Blob(['x'], { type: 'image/jpeg' }), 'live');
assert.equal(eins.gesamtscore, 65);
assert.equal(eins.sprechen, true);
assert.ok(letzteAnfrage.url.includes('generativelanguage.googleapis.com'));
assert.equal(JSON.parse(letzteAnfrage.optionen.body).generationConfig.temperature, 0);
assert.equal(letzteAnfrage.optionen.headers['x-goog-api-key'], 'test-key');

const zwei = await sitzung.analysieren(new Blob(['x'], { type: 'image/jpeg' }), 'live');
assert.equal(zwei.sprechen, false, 'derselbe Satz wird nicht zweimal gesagt');
assert.equal(sitzung.phase, 'kopf', 'bei kritischem Problem keine Phase weiter');
assert.ok(sitzung.verlaufstext().includes('Tabak am Rand'));

sitzung.weiter();
assert.equal(sitzung.phase, 'tabak');

// Phasenwechsel erst bei sauberen Bildern
const sauber = {
  ...ANTWORT, probleme: [], coach_satz: 'Passt so.',
  scores: { tabak_verteilung: 90, fuellhoehe: 90, airflow: 90, hitzemanagement: 90,
            kopfgeometrie: 90, tabak_kompatibilitaet: 90, zielerreichung: 90 },
  confidence: { gesamt: 80, kopf_erkennung: 80, tabak_analyse: 80, fuellhoehe: 80,
                airflow: 80, hitzemanagement: 80, optimierung: 80 },
};
antwortText = JSON.stringify(sauber);
await sitzung.analysieren(new Blob(['x']), 'live');
assert.equal(sitzung.phase, 'tabak', 'ein sauberes Bild reicht nicht');
await sitzung.analysieren(new Blob(['x']), 'live');
assert.equal(sitzung.phase, 'einstreuen', 'zwei saubere Bilder gehen weiter');

// --- Fehler des Anbieters lesbar machen -------------------------------------
globalThis.fetch = async () => ({ ok: false, status: 429, text: async () => 'quota' });
await assert.rejects(sitzung.analysieren(new Blob(['x']), 'live'), /Freikontingent/);
globalThis.fetch = async () => ({ ok: false, status: 401, text: async () => 'bad key' });
await assert.rejects(sitzung.analysieren(new Blob(['x']), 'live'), /Schluessel/);

// --- Lernspeicher -----------------------------------------------------------
Engine.profil.merken({ ziel: 'geschmack', score: 80, geschmack: 2, rauch: 4, kratzen: 4, hitze: 5, dauer_min: 45 });
const treffer = Engine.profil.treffsicherheit();
assert.equal(treffer.sessions, 1);
assert.equal(treffer.vergleichbar, 1);
assert.ok(treffer.genauigkeit > 0 && treffer.genauigkeit < 100);
assert.ok(Engine.profil.lernkontext().includes('Kratzen: 4'));
assert.ok(Engine.profil.lernkontext().includes('zu heiss'), 'Konsequenz-Hinweis fehlt');

// gute Session -> hohe erlebte Note
assert.ok(Engine.profil.erlebteNote({ geschmack: 5, rauch: 5, kratzen: 1, hitze: 3 }) === 100);

console.log('ALLE TESTS BESTANDEN');
