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
let anfragen = [];
let antwortText = '';
let antwortReihe = [];   // wird der Reihe nach abgearbeitet, sonst antwortText

const standardFetch = async (url, optionen = {}) => {
  if (String(url).endsWith('spec.json')) {
    return { ok: true, json: async () => JSON.parse(readFileSync(join(webDir, 'spec.json'), 'utf8')) };
  }
  letzteAnfrage = { url: String(url), optionen };
  anfragen.push(letzteAnfrage);

  const text = antwortReihe.length ? antwortReihe.shift() : antwortText;
  // Jeder Anbieter verpackt seine Antwort anders — hier beide Formen.
  const umschlag = String(url).includes('openrouter')
    ? { choices: [{ message: { content: text } }] }
    : { candidates: [{ content: { parts: [{ text }] } }] };
  return { ok: true, status: 200, text: async () => JSON.stringify(umschlag) };
};

globalThis.fetch = standardFetch;

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

// --- Plausibilitaet: Widersprueche werden gekappt ---------------------------
// Das Modell meldet ein kritisches Problem in "fuellhoehe" UND randkontakt,
// bewertet die Kategorie aber mit 70. Beides zieht die Zahl herunter: der
// strengere Wert (Randkontakt, 40) gewinnt.
const a = Engine.normalisiere(ANTWORT, true);
assert.equal(a.scores.fuellhoehe, 40, `Fuellhoehe nicht gekappt: ${a.scores.fuellhoehe}`);
assert.ok(a.kappungen.some((k) => k.grund.includes('Rand')), 'Kappung nicht begruendet');
assert.ok(a.kappungen.every((k) => k.auf < k.von), 'Kappung muss nach unten gehen');

// 60*.20 + 40*.15 + 65*.15 + 55*.20 + 85*.10 + 75*.10 + 60*.10 = 60.75 -> 61
assert.equal(a.gesamtscore, 61, `Note falsch: ${a.gesamtscore}`);
assert.equal(a.stufe, 'acceptable');
assert.equal(a.stufe_text, 'brauchbar');

// dieselbe Eingabe muss dieselbe Note ergeben
assert.equal(Engine.normalisiere(ANTWORT, true).gesamtscore, 61);

// Ohne Widerspruch bleibt die Bewertung des Modells stehen
const sauberesUrteil = Engine.normalisiere({
  ...ANTWORT, probleme: [],
  tabak: { ...ANTWORT.tabak, randkontakt: false },
  airflow: { ...ANTWORT.airflow, blockade_risiko: 'low' },
}, true);
assert.equal(sauberesUrteil.scores.fuellhoehe, 70);
assert.deepEqual(sauberesUrteil.kappungen, []);
assert.equal(sauberesUrteil.gesamtscore, 65);

// HMD beruehrt den Tabak -> Hitzemanagement kann nicht gut sein
const heiss = Engine.normalisiere({
  ...ANTWORT, probleme: [],
  tabak: { ...ANTWORT.tabak, randkontakt: false },
  hmd: { ...ANTWORT.hmd, erkannt: true, kontakt_tabak: true },
  scores: { ...ANTWORT.scores, hitzemanagement: 90 },
}, true);
assert.equal(heiss.scores.hitzemanagement, 30, 'HMD-Kontakt nicht gekappt');

// Verdeckte Oeffnung -> Airflow kann nicht gut sein
const dicht = Engine.normalisiere({
  ...ANTWORT, probleme: [],
  tabak: { ...ANTWORT.tabak, randkontakt: false },
  airflow: { zentrale_oeffnung_frei: false, blockade_risiko: 'high', confidence: 70 },
  scores: { ...ANTWORT.scores, airflow: 95 },
}, true);
assert.equal(dicht.scores.airflow, 30, 'verdeckte Oeffnung nicht gekappt');

// --- Befund wird uebernommen -------------------------------------------------
assert.equal(Engine.normalisiere({ ...ANTWORT, befund: 'Phunnel, halbvoll, links Klumpen.' }, true).befund,
             'Phunnel, halbvoll, links Klumpen.');

// --- Geradeziehen -----------------------------------------------------------
assert.equal(a.probleme.length, 2, 'live: hoechstens zwei Probleme');
assert.equal(a.probleme[0].severity, 'critical', 'kritisches zuerst');
assert.equal(a.probleme[0].symbol, '🔴');
assert.deepEqual(a.optimierungen.map((o) => o.schritt), [1, 2, 3]);
assert.equal(a.optimierungen[0].aktion, 'remove_tobacco');
assert.equal(a.optimierungen[2].aktion, 'redistribute_tobacco', 'erfundene Aktion ersetzt');
assert.equal(a.optimierungen[2].aktion_text, 'neu verteilen');
assert.equal(a.prognose.score_nach_optimierung, 61, 'Prognose nie unter Ist-Stand');
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

// --- Duenne Bilder gelten als vorlaeufig -------------------------------------
assert.equal(a.vorlaeufig, false, 'gutes Bild ist nicht vorlaeufig');

const duenn = Engine.normalisiere({
  ...ANTWORT,
  bildqualitaet: { schaerfe: 20, licht: 30, perspektive: 'unklar', kopf_vollstaendig: true },
}, true);
assert.equal(duenn.vorlaeufig, true, 'unscharfes Bild muss vorlaeufig sein');
assert.equal(duenn.gesamtscore, 61, 'vorlaeufig heisst nicht: keine Note');

const unsicher = Engine.normalisiere({
  ...ANTWORT,
  confidence: { gesamt: 25, kopf_erkennung: 25, tabak_analyse: 25, fuellhoehe: 25,
                airflow: 25, hitzemanagement: 25, optimierung: 25 },
}, true);
assert.equal(unsicher.vorlaeufig, true, 'niedrige Sicherheit muss vorlaeufig sein');

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
assert.equal(eins.gesamtscore, 61);
assert.equal(eins.sprechen, true);
assert.ok(eins.konsens, 'jede Analyse traegt den Konsens mit sich');
assert.equal(eins.konsens.score, 61, 'ein Bild ist sein eigener Konsens');
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

globalThis.fetch = standardFetch;   // nach den Fehlertests wieder normal antworten

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

// --- Konsens ueber mehrere Bilder --------------------------------------------
const reihe = new Engine.Sitzung({ ziel: 'balanced' });
[60, 64, 62].forEach((wert) => {
  reihe.aufnehmen(Engine.normalisiere({
    ...ANTWORT, probleme: [], tabak: { ...ANTWORT.tabak, randkontakt: false },
    scores: { tabak_verteilung: wert, fuellhoehe: wert, airflow: wert, hitzemanagement: wert,
              kopfgeometrie: wert, tabak_kompatibilitaet: wert, zielerreichung: wert },
  }, true));
});
const konsens = reihe.konsens();
assert.equal(konsens.score, 62, `Median falsch: ${konsens.score}`);
assert.equal(konsens.bilder, 3);
assert.equal(konsens.stabil, true, 'enge Werte muessen stabil heissen');
assert.equal(konsens.trend, 2, 'Trend von 60 auf 62');

// Ein Ausreisser darf den Median nicht kippen
reihe.aufnehmen(Engine.normalisiere({
  ...ANTWORT, probleme: [], tabak: { ...ANTWORT.tabak, randkontakt: false },
  scores: { tabak_verteilung: 5, fuellhoehe: 5, airflow: 5, hitzemanagement: 5,
            kopfgeometrie: 5, tabak_kompatibilitaet: 5, zielerreichung: 5 },
}, true));
assert.ok(reihe.konsens().score >= 33, 'Median darf nicht auf den Ausreisser springen');
assert.equal(reihe.konsens().stabil, false, 'grosse Spanne ist nicht stabil');

// Vorlaeufige Bilder zaehlen nicht mit
const nurDuenn = new Engine.Sitzung({ ziel: 'balanced' });
nurDuenn.aufnehmen(Engine.normalisiere({
  ...ANTWORT,
  bildqualitaet: { schaerfe: 10, licht: 10, perspektive: 'unklar', kopf_vollstaendig: true },
}, true));
assert.equal(nurDuenn.konsens(), null, 'aus vorlaeufigen Bildern kein Konsens');

// --- Sprachbefehle -----------------------------------------------------------
assert.equal(Engine.befehlErkennen('weiter'), 'weiter');
assert.equal(Engine.befehlErkennen('okay dann mal weiter bitte'), 'weiter');
assert.equal(Engine.befehlErkennen('bewerten'), 'analyse');
assert.equal(Engine.befehlErkennen('zurück zur vorherigen phase'), 'zurueck');
assert.equal(Engine.befehlErkennen('mach mal pause'), 'pause');
assert.equal(Engine.befehlErkennen('kamera an'), 'start');
assert.equal(Engine.befehlErkennen('bring mich ins hauptmenü'), 'menue');
assert.equal(Engine.befehlErkennen('wie viele punkte habe ich'), 'status');
assert.equal(Engine.befehlErkennen(''), null);
assert.equal(Engine.befehlErkennen('das wetter ist schön'), null,
             'Alltagssatz darf keinen Befehl ausloesen');

// --- (1) Maßstab -------------------------------------------------------------
assert.equal(Engine.kopfSuchen('Oblako Phunnel M').durchmesser_mm, 78);
assert.equal(Engine.kopfSuchen('oblako phunnel m').durchmesser_mm, 78, 'Gross- und Kleinschreibung egal');
assert.equal(Engine.kopfSuchen('mein Oblako Phunnel M von 2023').durchmesser_mm, 78, 'Zusaetze stoeren nicht');
assert.equal(Engine.kopfSuchen('Fantasiekopf 9000'), null);
assert.equal(Engine.kopfSuchen(''), null);

assert.equal(Engine.durchmesserBestimmen({ durchmesser_mm: 82 }).mm, 82);
assert.equal(Engine.durchmesserBestimmen({ durchmesser_mm: 82 }).quelle, 'angegeben');
assert.equal(Engine.durchmesserBestimmen({ kopf_modell: 'Kaya Phunnel' }).mm, 75, 'faellt auf die Liste zurueck');
assert.equal(Engine.durchmesserBestimmen({ kopf_modell: 'unbekannt' }), null);
assert.equal(Engine.durchmesserBestimmen({ durchmesser_mm: 5 }), null, 'unsinnige Werte werden verworfen');
assert.equal(Engine.durchmesserBestimmen({ durchmesser_mm: 500 }), null);

const mitMassstab = Engine.promptBauen({
  modus: 'voll', kontext: { ziel: 'balanced', kopf_modell: 'Oblako Phunnel M' }, verlauf: '', lernen: '',
});
assert.ok(mitMassstab.includes('78 mm'), 'Durchmesser steht im Prompt');
assert.ok(mitMassstab.includes('Groessenbezug'), 'Massstab-Anleitung steht im Prompt');

const ohneMassstab = Engine.promptBauen({
  modus: 'voll', kontext: { ziel: 'balanced' }, verlauf: '', lernen: '',
});
assert.ok(!ohneMassstab.includes('Groessenbezug'), 'ohne Angabe kein Massstab-Block');

// --- (3) Mehrere Bilder -------------------------------------------------------
const mehrere = Engine.promptBauen({
  modus: 'voll', kontext: { ziel: 'balanced' }, verlauf: '', lernen: '', bilder: 3,
});
assert.ok(mehrere.includes('mehrere Bilder desselben Kopfes'));
assert.ok(mehrere.includes('ERSTE Bild'), 'Marker beziehen sich auf das erste Bild');

antwortText = JSON.stringify(ANTWORT);
anfragen = [];
const dreiBilder = new Engine.Sitzung({ ziel: 'balanced' });
await dreiBilder.analysieren([new Blob(['a']), new Blob(['b']), new Blob(['c'])], 'voll');
const geschickt = JSON.parse(anfragen[0].optionen.body).contents[0].parts;
assert.equal(geschickt.filter((t) => t.inline_data).length, 3, 'alle drei Bilder gehen mit');
assert.equal(dreiBilder.analyse.bilder, 3);

// --- (4) Lernregeln -----------------------------------------------------------
speicher.delete('shisha.profil');
assert.deepEqual(Engine.profil.lernregeln(), [], 'ohne Sessions keine Regeln');

Engine.profil.merken({ hitze: 5, geschmack: 3, score: 70 });
Engine.profil.merken({ hitze: 4, geschmack: 3, score: 70 });
assert.deepEqual(Engine.profil.lernregeln(), [], 'zwei Rueckmeldungen reichen nicht');

Engine.profil.merken({ hitze: 5, geschmack: 3, score: 70 });
const regeln = Engine.profil.lernregeln();
assert.equal(regeln.length, 1, `genau eine Regel erwartet, waren: ${regeln.map((r) => r.id)}`);
assert.equal(regeln[0].id, 'zu_heiss');
assert.equal(regeln[0].treffer, 3);
assert.ok(Engine.profil.lernkontext().includes('Kohle weniger'), 'die Regel steht im Prompt');
assert.ok(Engine.profil.lernkontext().includes('halte dich daran'), 'und zwar als Vorgabe');

// Gegenprobe: ein anderes Feld loest die Regel nicht aus
speicher.delete('shisha.profil');
[1, 2, 3].forEach(() => Engine.profil.merken({ geschmack: 5, rauch: 5, score: 90 }));
assert.deepEqual(Engine.profil.lernregeln().map((r) => r.id), [], 'gute Sessions erzeugen keine Korrekturregel');

// --- (5) Vorher/Nachher --------------------------------------------------------
const vorher = Engine.normalisiere({
  ...ANTWORT, probleme: [], tabak: { ...ANTWORT.tabak, randkontakt: false },
  scores: { tabak_verteilung: 60, fuellhoehe: 60, airflow: 60, hitzemanagement: 60,
            kopfgeometrie: 60, tabak_kompatibilitaet: 60, zielerreichung: 60 },
  prognose: { ...ANTWORT.prognose, score_nach_optimierung: 85 },
}, false);
const nachher = Engine.normalisiere({
  ...ANTWORT, probleme: [], tabak: { ...ANTWORT.tabak, randkontakt: false },
  scores: { tabak_verteilung: 90, fuellhoehe: 60, airflow: 55, hitzemanagement: 80,
            kopfgeometrie: 60, tabak_kompatibilitaet: 60, zielerreichung: 60 },
}, false);

const v = Engine.vergleiche(vorher, nachher);
assert.equal(v.von, 60);
// 90*.20 + 60*.15 + 55*.15 + 80*.20 + 60*.10 + 60*.10 + 60*.10 = 69.25 -> 69
assert.equal(v.auf, 69);
assert.equal(v.delta, 9);
assert.deepEqual(v.besser.map((k) => k.key), ['tabak_verteilung', 'hitzemanagement']);
assert.deepEqual(v.schlechter.map((k) => k.key), ['airflow'], 'auch Verschlechterungen werden benannt');
assert.equal(v.versprochen, 85);
assert.equal(v.prognose_abweichung, 16, 'die Prognose war 16 Punkte zu optimistisch');
assert.equal(Engine.vergleiche(null, nachher), null);

// Zweite Vollanalyse in derselben Sitzung ist ein Nachmessen
antwortText = JSON.stringify(ANTWORT);
const nachmessen = new Engine.Sitzung({ ziel: 'balanced' });
await nachmessen.analysieren(new Blob(['x']), 'voll');
const zweiteRunde = await nachmessen.analysieren(new Blob(['x']), 'voll');
assert.ok(zweiteRunde.vergleich, 'die zweite Vollanalyse vergleicht mit der ersten');
assert.equal(zweiteRunde.vergleich.delta, 0, 'gleiche Antwort, gleiche Note');

// --- (7) Kontingent ------------------------------------------------------------
speicher.delete('shisha.verbrauch');
assert.equal(Engine.verbrauch('gemini').anzahl, 0);
assert.equal(Engine.verbrauch('gemini').limit, 200);

const vorZaehler = Engine.verbrauch('gemini').anzahl;
await new Engine.Sitzung({ ziel: 'balanced' }).analysieren(new Blob(['x']), 'live');
assert.equal(Engine.verbrauch('gemini').anzahl, vorZaehler + 1, 'jede Anfrage wird gezaehlt');

speicher.set('shisha.verbrauch', JSON.stringify({
  tag: new Date().toISOString().slice(0, 10), anbieter: { gemini: 199 },
}));
assert.equal(Engine.verbrauch('gemini').warnung, true, 'kurz vor dem Limit wird gewarnt');
assert.equal(Engine.verbrauch('gemini').erschoepft, false);
speicher.set('shisha.verbrauch', JSON.stringify({
  tag: new Date().toISOString().slice(0, 10), anbieter: { gemini: 200 },
}));
assert.equal(Engine.verbrauch('gemini').erschoepft, true);

// Ohne bekanntes Limit gibt es nichts zu warnen
assert.equal(Engine.verbrauch('server').limit, 0);
assert.equal(Engine.verbrauch('server').warnung, false);

// Ein alter Zaehlerstand von gestern gilt nicht mehr
speicher.set('shisha.verbrauch', JSON.stringify({ tag: '2000-01-01', anbieter: { gemini: 999 } }));
assert.equal(Engine.verbrauch('gemini').anzahl, 0, 'der Zaehler faengt jeden Tag neu an');

// --- (9) Gegenprobe -------------------------------------------------------------
const einigeAntwort = JSON.stringify({
  ...ANTWORT, probleme: [], tabak: { ...ANTWORT.tabak, randkontakt: false },
});
const abweichendeAntwort = JSON.stringify({
  ...ANTWORT, probleme: [], tabak: { ...ANTWORT.tabak, randkontakt: false },
  scores: { tabak_verteilung: 20, fuellhoehe: 20, airflow: 20, hitzemanagement: 20,
            kopfgeometrie: 20, tabak_kompatibilitaet: 20, zielerreichung: 20 },
});

Engine.einstellungenSpeichern({ anbieter: 'gemini', gegenprobe: 'openrouter', openrouter_key: 'zweit-key' });

// Einig: beide sagen dasselbe
antwortReihe = [einigeAntwort, einigeAntwort];
anfragen = [];
const einig = await new Engine.Sitzung({ ziel: 'balanced' }).analysieren(new Blob(['x']), 'voll');
assert.equal(anfragen.length, 2, 'zwei Anbieter werden gefragt');
assert.ok(anfragen[1].url.includes('openrouter'), 'der zweite ist ein anderer Anbieter');
assert.equal(einig.gegenprobe.einig, true);
assert.equal(einig.gegenprobe.abweichung, 0);

// Uneinig: die Sicherheit wird heruntergesetzt
antwortReihe = [einigeAntwort, abweichendeAntwort];
const uneinig = await new Engine.Sitzung({ ziel: 'balanced' }).analysieren(new Blob(['x']), 'voll');
assert.equal(uneinig.gegenprobe.einig, false);
assert.ok(uneinig.gegenprobe.abweichung > 15);
assert.ok(uneinig.confidence.gesamt <= 45, 'uneinige Modelle heissen niedrige Sicherheit');
assert.ok(uneinig.gegenprobe.strittig.length > 0, 'die strittigen Kategorien werden benannt');

// Ohne zweiten Anbieter passiert nichts
Engine.einstellungenSpeichern({ gegenprobe: 'aus' });
antwortReihe = [];
antwortText = einigeAntwort;
anfragen = [];
const ohne = await new Engine.Sitzung({ ziel: 'balanced' }).analysieren(new Blob(['x']), 'voll');
assert.equal(anfragen.length, 1);
assert.equal(ohne.gegenprobe, undefined);

// Derselbe Anbieter waere keine Gegenprobe
Engine.einstellungenSpeichern({ anbieter: 'gemini', gegenprobe: 'gemini' });
anfragen = [];
await new Engine.Sitzung({ ziel: 'balanced' }).analysieren(new Blob(['x']), 'voll');
assert.equal(anfragen.length, 1, 'gegen sich selbst pruefen bringt nichts');

console.log('ALLE TESTS BESTANDEN');
