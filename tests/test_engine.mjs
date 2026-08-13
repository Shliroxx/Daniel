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
  // gesamt fehlt absichtlich — die App mittelt dann die uebrigen
  confidence: { kopf_erkennung: 88, tabak_analyse: 71, fuellhoehe: 60, airflow: 65,
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

// 60*.20 + 40*.15 + 65*.15 + 55*.20 + 85*.10 + 75*.10 + 60*.10 = 60.75 -> 61.
// Gemeldet ist aber ein KRITISCHES Problem (Tabak am Rand) — dann ist auch die
// Gesamtnote gedeckelt. Eine Kategorie herunterzustufen reicht nicht, wenn die
// uebrigen sechs die Zahl wieder hochziehen: sonst stuende neben "Tabak
// beruehrt das HMD" eine glatte Zwei.
assert.equal(a.gesamtscore, 55, `Note falsch: ${a.gesamtscore}`);
assert.equal(a.stufe, 'needs_improvement');
assert.ok(a.kappungen.some((k) => k.kategorie === 'gesamt'), 'die Deckelung steht im Report');

// dieselbe Eingabe muss dieselbe Note ergeben
assert.equal(Engine.normalisiere(ANTWORT, true).gesamtscore, 55);

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
assert.equal(a.prognose.score_nach_optimierung, 55, 'Prognose nie unter Ist-Stand');
assert.equal(a.confidence.gesamt, 66, 'Gesamtsicherheit aus den anderen gemittelt');

// Eine ehrliche Null bleibt aber stehen: wer sagt "ich bin mir gar nicht sicher",
// darf sich das nicht wegrechnen lassen — sonst gilt das Ergebnis nicht als vorlaeufig.
const ehrlich = Engine.normalisiere({ ...ANTWORT, confidence: { ...ANTWORT.confidence, gesamt: 0 } }, true);
assert.equal(ehrlich.confidence.gesamt, 0);
assert.equal(ehrlich.vorlaeufig, true);

// Und eine Null bei einem Teilwert zaehlt im Mittel mit
const mitNull = Engine.normalisiere({
  ...ANTWORT,
  confidence: { kopf_erkennung: 100, tabak_analyse: 0, fuellhoehe: 0, airflow: 0,
                hitzemanagement: 0, optimierung: 0 },
}, true);
assert.ok(mitNull.confidence.gesamt < 30, `Nullen duerfen nicht herausfallen: ${mitNull.confidence.gesamt}`);
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
assert.equal(duenn.gesamtscore, 55, 'vorlaeufig heisst nicht: keine Note');

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
assert.equal(eins.gesamtscore, 55);
assert.equal(eins.sprechen, true);
assert.ok(eins.konsens, 'jede Analyse traegt den Konsens mit sich');
assert.equal(eins.konsens.score, 55, 'ein Bild ist sein eigener Konsens');
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
  // wirklich sauber: kein Randkontakt, freier Airflow — sonst stuft die App ab,
  // und abgestuft ist kein erledigter Bauschritt
  tabak: { ...ANTWORT.tabak, randkontakt: false, klumpen: false },
  airflow: { ...ANTWORT.airflow, blockade_risiko: 'low' },
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
assert.equal(Engine.kopfSuchen('Oblako Phunnel M').aussendurchmesser_mm, 78);
assert.equal(Engine.kopfSuchen('oblako phunnel m').aussendurchmesser_mm, 78, 'Gross- und Kleinschreibung egal');
assert.equal(Engine.kopfSuchen('mein Oblako Phunnel M von 2023').aussendurchmesser_mm, 78, 'Zusaetze stoeren nicht');
assert.equal(Engine.kopfSuchen('Fantasiekopf 9000'), null);
assert.equal(Engine.kopfSuchen(''), null);

assert.equal(Engine.durchmesserBestimmen({ aussendurchmesser_mm: 82 }).mm, 82);
assert.equal(Engine.durchmesserBestimmen({ aussendurchmesser_mm: 82 }).quelle, 'angegeben');
assert.equal(Engine.durchmesserBestimmen({ kopf_modell: 'Kaya Phunnel' }).mm, 75, 'faellt auf die Liste zurueck');

// Eigene Angabe schlaegt den Standardkopf.
assert.equal(Engine.durchmesserBestimmen({ kopf_modell: 'Kaya Phunnel', aussendurchmesser_mm: 90 }).mm, 90);

// Unsinnige Werte werden verworfen — dann greift der Standardkopf.
assert.equal(Engine.durchmesserBestimmen({ aussendurchmesser_mm: 5 }).mm, 78);
assert.match(Engine.durchmesserBestimmen({ aussendurchmesser_mm: 5 }).quelle, /angenommen/);
assert.equal(Engine.durchmesserBestimmen({ aussendurchmesser_mm: 500 }).mm, 78);

// Ohne jede Angabe steht immer noch der Standardkopf zur Verfuegung.
assert.equal(Engine.durchmesserBestimmen({}).mm, 78);
assert.match(Engine.durchmesserBestimmen({}).quelle, /angenommen \(Oblako Phunnel M\)/);

const mitMassstab = Engine.promptBauen({
  modus: 'voll', kontext: { ziel: 'balanced', kopf_modell: 'Oblako Phunnel M' }, verlauf: '', lernen: '',
});
assert.ok(mitMassstab.includes('78 mm'), 'Durchmesser steht im Prompt');
assert.ok(mitMassstab.includes('Groessenbezug'), 'Massstab-Anleitung steht im Prompt');

// --- Kopf erkennen, auch leer und von der Seite -------------------------------
const erkennung = Engine.promptBauen({ modus: 'live', phase: 'kopf', kontext: {}, verlauf: '', lernen: '' });
assert.ok(erkennung.includes('von der Seite'), 'Seitenansicht ist beschrieben');
assert.ok(erkennung.includes('LEERER Kopf ist ein gueltiger Kopf'), 'leerer Kopf ist gueltig');
assert.ok(erkennung.includes('no_head_detected'), 'wann abgebrochen wird, steht drin');
assert.ok(!erkennung.includes('In der Regel schaust du von schraeg oben'),
          'der alte Ein-Winkel-Satz ist raus');

// --- Standardkopf --------------------------------------------------------------
assert.equal(Engine.standardKopf().name, 'Oblako Phunnel M');
assert.equal(Engine.angenommenerKopf({}).herkunft, 'standard');
assert.equal(Engine.angenommenerKopf({ kopf_modell: 'Kaya Phunnel' }).herkunft, 'angegeben');
assert.equal(Engine.angenommenerKopf({ kopf_modell: 'Kaya Phunnel' }).aussendurchmesser_mm, 75);

// Ein unbekannter, frei eingetippter Kopf gilt trotzdem als Angabe des Nutzers.
const frei = Engine.angenommenerKopf({ kopf_modell: 'Opa seine Selbstbau-Schale' });
assert.equal(frei.herkunft, 'angegeben');
assert.equal(frei.name, 'Opa seine Selbstbau-Schale');
assert.equal(frei.aussendurchmesser_mm, null, 'ohne Massangabe kein erfundener Durchmesser');

// Eigener Standardkopf schlaegt den aus spec.json
Engine.einstellungenSpeichern({ standardkopf: 'Vyro Rocket' });
assert.equal(Engine.standardKopf().name, 'Vyro Rocket');
assert.equal(Engine.durchmesserBestimmen({}).mm, 76);
Engine.einstellungenSpeichern({ standardkopf: '' });

// --- Nicht erkannter Kopf wird angenommen, nicht erfunden ----------------------
const unerkannt = Engine.normalisiere(
  { ...ANTWORT, kopf: { art: 'unbekannt', modell: null, quelle: 'unknown', confidence: 10 } },
  true,
  { kopf_modell: '' }
);
assert.equal(unerkannt.kopf.art, 'phunnel', 'faellt auf den Standardkopf zurueck');
assert.equal(unerkannt.kopf.modell, 'Oblako Phunnel M');
assert.equal(unerkannt.kopf.angenommen, true, 'und ist als Annahme gekennzeichnet');
assert.equal(unerkannt.kopf.quelle, 'angegeben', 'nie als "observed" ausgeben');
assert.equal(unerkannt.kopf.confidence, 10, 'eine Annahme erhoeht die Sicherheit nicht');

// Was der Nutzer gesagt hat, schlaegt den Standardkopf
const gesagt = Engine.normalisiere(
  { ...ANTWORT, kopf: { art: 'unbekannt', modell: null, quelle: 'unknown', confidence: 10 } },
  true,
  { kopf_modell: 'Killerkopf gross' }
);
assert.equal(gesagt.kopf.art, 'killer');
assert.equal(gesagt.kopf.modell, 'Killerkopf gross');

// Erkennt das Modell den Kopf selbst, bleibt es dabei
const erkannt = Engine.normalisiere(ANTWORT, true, { kopf_modell: 'Killerkopf gross' });
assert.equal(erkannt.kopf.art, 'phunnel', 'eine echte Erkennung wird nicht ueberschrieben');
assert.equal(erkannt.kopf.modell, 'Oblako M');
assert.equal(erkannt.kopf.angenommen, false);
assert.equal(erkannt.kopf.quelle, 'observed');

// --- Ratlosigkeit wird nicht vorgelesen ---------------------------------------
// Am Geraet kam "Oblako M nicht erkannt" aus dem Lautsprecher. Das ist keine
// Hilfe: welcher Kopf es ist, hat der Nutzer gesagt.
const klage = Engine.normalisiere(
  { ...ANTWORT, coach_satz: 'Oblako M nicht erkannt.' },
  true,
  { kopf_modell: 'Oblako Phunnel M' }
);
assert.equal(klage.coach_satz, klage.optimierungen[0].text, 'stattdessen der naechste Handgriff');

// Ohne Handgriff und ohne Problem bleibt ein Satz uebrig, der weiterhilft
const klageLeer = Engine.normalisiere(
  { ...ANTWORT, probleme: [], optimierungen: [], coach_satz: 'Kopfmodell kann ich nicht bestimmen.' },
  true, {}
);
assert.ok(!/nicht bestimmen/.test(klageLeer.coach_satz));
assert.ok(klageLeer.coach_satz.length > 10);

// Ist wirklich kein Kopf im Bild, wird gesagt, was zu tun ist
const klageOhneKopf = Engine.normalisiere(
  { ...ANTWORT, analysis_status: 'no_head_detected', coach_satz: 'Ich kann keinen Kopf erkennen.' },
  true, {}
);
assert.ok(/ins Bild/.test(klageOhneKopf.coach_satz));

// Ein normaler Satz bleibt unangetastet
assert.equal(Engine.normalisiere(ANTWORT, true, {}).coach_satz, ANTWORT.coach_satz);

// --- Mehrdeutige Kopfnamen werden nicht stillschweigend geraten -----------------
// "Killerkopf" passt auf klein (68 mm) und gross (80 mm) — 15 Prozent
// Massstabsfehler, wenn einfach der erste gewinnt. Dann lieber der Standardkopf.
assert.equal(Engine.kopfSuchen('Killerkopf'), null, 'mehrdeutig heisst kein Treffer');
assert.equal(Engine.kopfSuchen('Killerkopf klein').aussendurchmesser_mm, 68, 'eindeutig geht weiter');
assert.equal(Engine.kopfSuchen('mein Oblako Phunnel M von 2023').aussendurchmesser_mm, 78,
             'Zusaetze stoeren nicht — der laengste Treffer gewinnt');

// --- Leerer Kopf: keine erfundenen Tabaknoten -----------------------------------
// In der Phase "Kopf pruefen" ist der Kopf absichtlich leer. Frueher musste das
// Modell trotzdem Tabakverteilung und Fuellhoehe benoten — zusammen 35 Prozent
// Gewicht — und lieferte entweder erfundene Zahlen oder strafte den fehlenden
// Tabak ab.
const leererKopf = {
  ...ANTWORT, probleme: [], optimierungen: [],
  tabak: { ...ANTWORT.tabak, randkontakt: false, klumpen: false },
  airflow: { ...ANTWORT.airflow, blockade_risiko: 'low' },
  scores: { tabak_verteilung: 0, fuellhoehe: 0, airflow: 0, hitzemanagement: 80,
            kopfgeometrie: 90, tabak_kompatibilitaet: 0, zielerreichung: 0 },
};
const inPhaseKopf = Engine.normalisiere(leererKopf, false, {}, 'kopf');
// 80*.20 + 90*.10 = 25, Gewichtssumme .30 -> 83
assert.equal(inPhaseKopf.gesamtscore, 83, `leerer Kopf falsch benotet: ${inPhaseKopf.gesamtscore}`);
assert.deepEqual(inPhaseKopf.nicht_bewertbar.sort(),
  ['airflow', 'fuellhoehe', 'tabak_kompatibilitaet', 'tabak_verteilung', 'zielerreichung']);

// Dieselbe Antwort spaeter im Bau ist ein schlechter Kopf — da zaehlt alles.
const spaeter = Engine.normalisiere(leererKopf, false, {}, 'glattziehen');
assert.equal(spaeter.gesamtscore, 25);
assert.deepEqual(spaeter.nicht_bewertbar, []);

// Ohne Phasenangabe bleibt es beim alten Verhalten: alle Kategorien zaehlen.
assert.equal(Engine.normalisiere(leererKopf, false, {}).gesamtscore, 25);

// Der Prompt sagt dem Modell auch, welche Kategorien gerade null bleiben duerfen
const phasenPrompt = Engine.promptBauen({
  modus: 'live', kontext: { ziel: 'balanced' }, phase: 'kopf', verlauf: '', lernen: '',
});
assert.ok(phasenPrompt.includes('gibt es in dieser Phase noch gar nicht'));
assert.ok(phasenPrompt.includes('tabak_verteilung'));

// --- Angabe und Annahme sind zweierlei -----------------------------------------
// Der Standardkopf greift immer. Frueher bekam das Modell deshalb auch ohne
// jede Angabe zu lesen, der Nutzer habe den Kopf genannt und es solle ihn nicht
// anzweifeln — die Annahme frass die Erkennung, die sie absichern sollte.
const ohneAngabe = Engine.promptBauen({
  modus: 'live', kontext: { ziel: 'balanced' }, verlauf: '', lernen: '',
});
assert.ok(ohneAngabe.includes('KEINEN Kopf angegeben'), 'Vermutung wird als Vermutung benannt');
assert.ok(!ohneAngabe.includes('Der Nutzer hat gesagt, welchen Kopf'), 'und nicht als Angabe');
assert.ok(ohneAngabe.includes('Erkenne die Kopfart trotzdem'), 'erkannt wird weiter selbst');

const mitAngabe = Engine.promptBauen({
  modus: 'live', kontext: { ziel: 'balanced', kopf_modell: 'Oblako Phunnel M' }, verlauf: '', lernen: '',
});
assert.ok(mitAngabe.includes('Der Nutzer hat gesagt, welchen Kopf'));
assert.ok(!mitAngabe.includes('KEINEN Kopf angegeben'));

// --- Kohle wird nicht mehr uebersehen -------------------------------------------
// Die ganze Phase "Kohle auflegen" war ungeschuetzt: ein gemeldeter Hotspot
// konnte neben einer glatten 90 im Hitzemanagement stehen.
const hotspot = Engine.normalisiere({
  ...ANTWORT, probleme: [], tabak: { ...ANTWORT.tabak, randkontakt: false },
  kohle: { status: 'visible', anzahl: 3, hotspot_risiko: 'high', confidence: 70 },
  scores: { ...ANTWORT.scores, hitzemanagement: 90 },
}, false);
assert.equal(hotspot.scores.hitzemanagement, 40, 'Hotspot nicht gekappt');

const kalt = Engine.normalisiere({
  ...ANTWORT, probleme: [], tabak: { ...ANTWORT.tabak, randkontakt: false },
  kohle: { status: 'visible', anzahl: 3, durchgegluht: false, confidence: 70 },
  scores: { ...ANTWORT.scores, hitzemanagement: 90 },
}, false);
assert.equal(kalt.scores.hitzemanagement, 35, 'nicht durchgegluehte Kohle nicht gekappt');
assert.ok(kalt.gesamtscore <= 55, 'und die Gesamtnote wird mitgezogen');

// --- Eine geratene Kategorie kappt nichts ----------------------------------------
// "kohle" ist kein gueltiger Kategorieschluessel. Der Standard taugt zum
// Anzeigen, nicht zum Herunterstufen: sonst wird die Tabakverteilung fuer ein
// Kohleproblem halbiert und das Hitzemanagement bleibt bei 90.
const falscheKategorie = Engine.normalisiere({
  ...ANTWORT, tabak: { ...ANTWORT.tabak, randkontakt: false },
  probleme: [{ id: 'x', severity: 'critical', kategorie: 'kohle', titel: 'Kohle mittig',
               beschreibung: 'ueber dem Kamin', confidence: 80, aktion: 'reposition_coals' }],
}, false);
assert.equal(falscheKategorie.scores.tabak_verteilung, 60, 'nicht fuer ein Kohleproblem gekappt');
assert.ok(falscheKategorie.probleme[0].titel === 'Kohle mittig', 'angezeigt wird es trotzdem');

// --- Live wird gekappt, bevor gekuerzt wird ---------------------------------------
// Die Anzeige zeigt live nur zwei Probleme. Wurde vorher gekuerzt, kappte das
// dritte kritische Problem gar nichts mehr.
const dreiKritische = Engine.normalisiere({
  ...ANTWORT, tabak: { ...ANTWORT.tabak, randkontakt: false },
  probleme: [
    { id: 'a', severity: 'critical', kategorie: 'fuellhoehe', titel: 'A', confidence: 90, aktion: 'remove_tobacco' },
    { id: 'b', severity: 'critical', kategorie: 'airflow', titel: 'B', confidence: 80, aktion: 'loosen_tobacco' },
    { id: 'c', severity: 'critical', kategorie: 'kopfgeometrie', titel: 'C', confidence: 70, aktion: 'remove_tobacco' },
  ],
}, true);
assert.equal(dreiKritische.probleme.length, 2, 'live bleiben zwei stehen');
assert.equal(dreiKritische.scores.kopfgeometrie, 45, 'das dritte kappt trotzdem');

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

// --- Veraltete Antwort veraendert die Sitzung nicht ----------------------------
// Waehrend einer laufenden Anfrage kann die App im Hintergrund gewesen sein.
// Sortiert sich so eine Antwort trotzdem ein, springt im Stillen die Bauphase
// weiter — sichtbar wird das erst beim naechsten Ergebnis, ohne erkennbaren Grund.
const stillstand = new Engine.Sitzung({ ziel: 'balanced' });
const vorPhase = stillstand.phase;
await stillstand.analysieren(new Blob(['x']), 'live', false);
await stillstand.analysieren(new Blob(['x']), 'live', false);
await stillstand.analysieren(new Blob(['x']), 'live', false);
assert.equal(stillstand.verlauf.length, 0, 'nichts einsortiert');
assert.equal(stillstand.phase, vorPhase, 'und die Phase steht noch');
const eingereiht = stillstand.aufnehmen(await stillstand.analysieren(new Blob(['x']), 'live', false));
assert.equal(stillstand.verlauf.length, 1, 'per Hand geht es weiter wie bisher');
assert.ok(eingereiht.konsens !== undefined || eingereiht.gesamtscore !== null);

// --- (7) Kontingent ------------------------------------------------------------
speicher.delete('shisha.verbrauch');
assert.equal(Engine.verbrauch('gemini').anzahl, 0);
assert.equal(Engine.verbrauch('gemini').limit, 200);

const vorZaehler = Engine.verbrauch('gemini').anzahl;
await new Engine.Sitzung({ ziel: 'balanced' }).analysieren(new Blob(['x']), 'live');
assert.equal(Engine.verbrauch('gemini').anzahl, vorZaehler + 1, 'jede Anfrage wird gezaehlt');

// Was nie beim Anbieter ankam, kostet auch kein Kontingent. Vorher zaehlte
// jede 429, jede 401 und jeder Netzabbruch mit — bei erschoepftem Kontingent
// lief der Zaehler im Sekundentakt hoch, obwohl nichts durchging.
speicher.delete('shisha.verbrauch');
globalThis.fetch = async () => ({ ok: false, status: 429, text: async () => 'quota' });
await new Engine.Sitzung({ ziel: 'balanced' }).analysieren(new Blob(['x']), 'live').catch(() => {});
assert.equal(Engine.verbrauch('gemini').anzahl, 0, '429 kostet kein Kontingent');

globalThis.fetch = async () => { throw new TypeError('kein netz'); };
await new Engine.Sitzung({ ziel: 'balanced' }).analysieren(new Blob(['x']), 'live').catch(() => {});
assert.equal(Engine.verbrauch('gemini').anzahl, 0, 'Netzabbruch kostet kein Kontingent');

// Eine Antwort, die durchkam und nur leer war, kostet dagegen sehr wohl.
globalThis.fetch = async () => ({
  ok: true, status: 200,
  text: async () => JSON.stringify({ candidates: [{ finishReason: 'MAX_TOKENS', content: { parts: [] } }] }),
});
const abgeschnitten = await new Engine.Sitzung({ ziel: 'balanced' })
  .analysieren(new Blob(['x']), 'live').catch((f) => f);
assert.match(abgeschnitten.message, /abgeschnitten/);
assert.equal(Engine.verbrauch('gemini').anzahl, 1, 'angekommen ist angekommen');
globalThis.fetch = standardFetch;

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
