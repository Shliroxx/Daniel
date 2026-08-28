/* Ein kompletter Durchgang durch die App — als wuerde jemand sie bedienen.
 *
 * Die uebrigen Tests pruefen Engine und Steuerlogik. ar.js dagegen — Kamera,
 * Overlay, Knoepfe, Bildschirme — wurde nie ausgefuehrt, und genau dort sassen
 * die Fehler, die im Alltag auffallen. Dieser Test laedt die drei Skripte in
 * einem nachgebauten Browser (tests/dom-ersatz.mjs) und spielt eine Sitzung
 * durch: Zugang einrichten, Kamera starten, Livebetrieb, Vollanalyse aus drei
 * Winkeln, Report, Nachmessen, Teilen, Feedback, Pause, Sprache, Hauptmenue —
 * und danach die Stoerfaelle.
 *
 * Gefunden hat er damit unter anderem: zwei parallel laufende Analyseschleifen
 * nach einem Wechsel in den Hintergrund (doppelter Kontingentverbrauch) und
 * eine Vollanalyse, die bei toter Kamera 30 Sekunden lang ins Leere lief.
 *
 * Ausfuehren:  node tests/test_durchlauf.mjs      (dauert etwa eine Minute)
 */
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { baueUmgebung } from './dom-ersatz.mjs';

const webDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'web', 'shisha');

const ANTWORT = {
  analysis_status: 'ok',
  befund: 'Phunnel von schraeg oben, etwa zu zwei Dritteln gefuellt.',
  bildqualitaet: { schaerfe: 80, licht: 70, perspektive: 'schraeg', kopf_vollstaendig: true, hinweis: '' },
  kopf: { art: 'phunnel', modell: 'Oblako Phunnel M', geometrie: 'mittel', zentrale_oeffnung_sichtbar: true, quelle: 'observed', confidence: 85 },
  tabak: { fuellhoehe_mm: 2, fuellhoehe_quelle: 'estimated', dichte: 55, gleichmaessigkeit: 70,
           klumpen: false, luecken: false, randkontakt: false, ueber_rand: false,
           menge_gramm: 'ca. 15 g', quelle: 'estimated', confidence: 72 },
  airflow: { zentrale_oeffnung_frei: true, blockade_risiko: 'low', notiz: '', confidence: 70 },
  hmd: { erkannt: false, modell: null, zentriert: null, abstand_mm: null, kontakt_tabak: null, confidence: 20 },
  kohle: { status: 'not_visible', anzahl: null, position: '', hotspot_risiko: 'unknown', confidence: 10 },
  scores: { tabak_verteilung: 72, fuellhoehe: 75, airflow: 78, hitzemanagement: 65,
            kopfgeometrie: 80, tabak_kompatibilitaet: 75, zielerreichung: 70 },
  probleme: [{ id: 'leichte_unruhe', severity: 'medium', kategorie: 'tabak_verteilung',
               titel: 'Bei 9 Uhr etwas hoeher', beschreibung: 'Links liegt mehr Tabak.',
               confidence: 65, aktion: 'redistribute_tobacco' }],
  optimierungen: [{ schritt: 1, aktion: 'redistribute_tobacco', bereich: '9 Uhr',
                    text: 'Zieh bei 9 Uhr etwas Tabak zur Mitte.', wirkung: 'gleichmaessige Hitze' }],
  ar_marker: [{ typ: 'distribute', x: 0.3, y: 0.35, w: 0.2, h: 0.18, label: 'verteilen', aktion: 'redistribute_tobacco' },
              { typ: 'fill_height', x: 0.25, y: 0.6, w: 0.5, h: 0.04, label: 'Zielhoehe', aktion: 'add_tobacco' }],
  prognose: { score_nach_optimierung: 84, geschmack: 'hoch', rauch: 'gleich', dauer: 'gleich',
              hitzerisiko: 'runter', verbesserung: { tabak_verteilung: 88, hitzemanagement: 75, airflow: 80 } },
  confidence: { gesamt: 72, kopf_erkennung: 85, tabak_analyse: 72, fuellhoehe: 65, airflow: 70, hitzemanagement: 60, optimierung: 70 },
  rueckfrage: null,
  coach_satz: 'Zieh bei neun Uhr etwas Tabak zur Mitte.',
};

// Steuerbare Antwort: Text, Fehlerstatus, Verzoegerung.
const antwortPlan = { text: (_url) => JSON.stringify(ANTWORT), status: 200, verzoegerung: 0 };
const { global, elemente, protokoll, dokument, spur, bildAendern, wackeln } = baueUmgebung(webDir, {
  antwort: (url) => ({ text: antwortPlan.text(url), status: antwortPlan.status, verzoegerung: antwortPlan.verzoegerung }),
});

const kontext = vm.createContext(global);
const schritte = [];
const probleme = [];

function pruefe(name, bedingung, zusatz = '') {
  schritte.push(`${bedingung ? '  ok  ' : ' FEHL '} ${name}${zusatz ? ` — ${zusatz}` : ''}`);
  if (!bedingung) probleme.push(name + (zusatz ? ` — ${zusatz}` : ''));
}

const warte = (ms) => new Promise((f) => setTimeout(f, ms));

/* Warten, bis etwas eingetreten ist — statt auf gut Glueck eine feste Zeit.
 *
 * Feste Wartezeiten sind der haeufigste Grund fuer Tests, die auf einem
 * langsamen Rechner ohne Grund umfallen: die Mehrfachaufnahme braucht allein
 * zweimal 1,8 s zwischen den Winkeln, und jede Aufnahme darf bis zu 8 s auf ein
 * ruhiges Bild warten. So ist der Test bei schnellen Laeufen kuerzer und bei
 * langsamen trotzdem gruen.
 */
async function warteBis(bedingung, hoechstensMs = 20000) {
  const bis = Date.now() + hoechstensMs;
  while (Date.now() < bis) {
    if (bedingung()) return true;
    await warte(60);
  }
  return false;
}
const $ = (id) => elemente.get(id);

// --- Laden ------------------------------------------------------------------
// Wie im Browser: klassische Skripte teilen sich einen Scope, deshalb alle drei
// in einem Rutsch. Der Anhang reicht heraus, was der Test anfassen muss.
const quelle = ['steuerung.js', 'engine.js', 'ar.js']
  .map((datei) => readFileSync(`${webDir}/${datei}`, 'utf8'))
  .join('\n;\n')
  + '\n; globalThis.__Engine = Engine; globalThis.__Steuerung = Steuerung; globalThis.__zustand = zustand;';

try {
  vm.runInContext(quelle, kontext, { filename: 'app.js' });
  pruefe('App laedt ohne Absturz', true);
} catch (fehler) {
  pruefe('App laedt ohne Absturz', false, `${fehler.message}\n${(fehler.stack || '').split('\n')[1] || ''}`);
  console.log(schritte.join('\n'));
  process.exit(1);
}

await warte(60);   // specLaden().then(...) durchlassen
pruefe('Regelwerk geladen', Boolean(kontext.__Engine.spec), $('startInfo').textContent);
pruefe('Zielwahl gefuellt', $('zielwahl').children.length === 4, `${$('zielwahl').children.length} Ziele`);
pruefe('Kopfliste gefuellt', $('kopfliste').children.length > 5, `${$('kopfliste').children.length} Koepfe`);
pruefe('Start ohne Schluessel gesperrt', $('losButton').disabled === true);

// --- Zugang einrichten -------------------------------------------------------
$('zugangButton').klick();
$('fGeminiKey').value = 'test-key';
$('zugangSpeichern').klick();
pruefe('Start nach Schluessel frei', $('losButton').disabled === false, $('startInfo').textContent);
pruefe('Standardkopf im Feld', $('fStandardkopf').placeholder === 'Oblako Phunnel M');

// --- Angaben eintragen -------------------------------------------------------
$('fPack').value = 'locker';
$('fKopf').value = 'Oblako Phunnel M';
(($('fKopf').horcher.change) || []).forEach((fn) => fn({ target: $('fKopf') }));
pruefe('Durchmesser vorgeschlagen', $('fDurchmesser').value === '78', `war "${$('fDurchmesser').value}"`);
pruefe('Packmethoden zur Auswahl', $('fPack').children.length === 3, `${$('fPack').children.length} Eintraege`);

// --- Kamera starten ----------------------------------------------------------
$('losButton').klick();
await warte(80);
pruefe('Startbildschirm weg', $('start').hidden === true);
pruefe('Bedienleisten sichtbar', $('oben').hidden === false && $('unten').hidden === false);

// --- Livebetrieb -------------------------------------------------------------
await warte(1400);
pruefe('Livebild wird analysiert', protokoll.anfragen.length >= 1, `${protokoll.anfragen.length} Anfragen`);
pruefe('Coach spricht', $('coach').textContent.length > 5, `"${$('coach').textContent}"`);
pruefe('Kopf benannt', $('kopfInfo').textContent.includes('Oblako'), `"${$('kopfInfo').textContent}"`);
pruefe('Note angezeigt', $('liveScore').textContent.includes('/100'), `"${$('liveScore').textContent}"`);
pruefe('Problem gelistet', $('probleme').children.length === 1);
// Die Livekarte traegt nur noch, was beim Bauen zaehlt: Ampel, Coach, Probleme.
// Messwerte und die nummerierte Liste stehen im Report — jede Zeile in der Karte
// kostet Sicht auf den Kopf, auf den man zielt.
pruefe('Livekarte bleibt schlank',
       $('messwerte') === undefined && $('schritte') === undefined,
       `messwerte=${$('messwerte') === undefined ? 'weg' : 'da'}, schritte=${$('schritte') === undefined ? 'weg' : 'da'}`);
// Und der Nachbau kennt jetzt das hidden-Attribut — sonst war jede Pruefung auf
// "ist verborgen" wertlos.
pruefe('Nachbau kennt hidden', $('aufnahme').hidden === true, `aufnahme.hidden=${$('aufnahme').hidden}`);
pruefe('Kontingent gezaehlt', $('kontingent').textContent.startsWith('1/') || $('kontingent').textContent.startsWith('2/'),
       `"${$('kontingent').textContent}"`);
pruefe('Gesprochen', protokoll.gesprochen.some((s) => s.includes('neun Uhr')), protokoll.gesprochen.join(' | ').slice(0, 60));

// Live darf das Modell nicht erst gruebeln — sonst dauert jede Runde ewig und
// die Antwort wird vom Denken aufgefressen (finishReason MAX_TOKENS).
const ersteAnfrage = JSON.parse(protokoll.anfragen[0].optionen.body);
pruefe('Live ohne Denkzeit gefragt',
       ersteAnfrage.generationConfig.thinkingConfig.thinkingBudget === 0,
       JSON.stringify(ersteAnfrage.generationConfig.thinkingConfig));

// --- Die Schleife: Problem -> Anleitung -> korrigieren -> Bestaetigung ----------
// Das ist der Kern der App. Vorher stand ein Problem nur als Text da: keine
// Ursache, kein Handgriff, kein Weg zurueck vor die Kamera, keine Bestaetigung.
pruefe('Ampel zeigt einen Zustand', $('ampel').hidden === false, $('ampelText').textContent);
pruefe('Ampel ist gelb bei einem mittleren Problem',
       $('ampel').className.includes('gelb'), $('ampel').className);
pruefe('Problem ist anklickbar',
       $('probleme').children.length === 1 && $('probleme').children[0].children.length === 2,
       `${$('probleme').children.length} Probleme`);

// Die ganze Zeile ist das Ziel — ein 26 Pixel hoher Knopf am Zeilenende trifft
// man einhaendig im Stehen nicht.
const problemZeile = $('probleme').children[0];
pruefe('Zeile fuehrt zur Anleitung', typeof problemZeile.onclick === 'function');
pruefe('Und sagt es auch', problemZeile.children[1].textContent.startsWith('Zeig mir wie'));

problemZeile.klick();
pruefe('Anleitung geht auf', $('anleitung').hidden === false);
pruefe('Anleitung nennt das Problem', $('anleitungTitel').textContent === 'Bei 9 Uhr etwas hoeher',
       `"${$('anleitungTitel').textContent}"`);
pruefe('Anleitung nennt die Ursache', $('anleitungGrund').textContent.includes('Links liegt mehr'),
       `"${$('anleitungGrund').textContent}"`);
pruefe('Anleitung hat Handgriffe', $('anleitungSchritte').children.length >= 2,
       `${$('anleitungSchritte').children.length} Schritte`);
pruefe('Anleitung hat ein Zielbild', $('anleitungZiel').textContent.length > 15,
       `"${$('anleitungZiel').textContent}"`);

// "Erledigt" schliesst das Blatt und prueft sofort neu, statt zu warten
$('anleitungPruefen').klick();
pruefe('Anleitung wieder zu', $('anleitung').hidden === true);
pruefe('Es wird sofort nachgeprueft',
       ['pruefe nach', 'analysiere'].includes($('lage').textContent),
       `"${$('lage').textContent}"`);
// Rueckmeldung dort, wo der Daumen ist — die Marke ganz oben liest in dem
// Moment niemand.
pruefe('Rueckmeldung in der Karte', $('coach').textContent.includes('9 Uhr'),
       `"${$('coach').textContent}"`);

// Hinter dem offenen Blatt darf nichts weiterlaufen — auch nicht ueber den
// Umweg "App in den Hintergrund und zurueck".
problemZeile.klick();
pruefe('Anleitung wieder offen', $('anleitung').hidden === false);
// Eine im selben Moment schon abgeschickte Anfrage laesst sich nicht mehr
// zurueckholen — die zaehlt nicht. Gemessen wird, ob DANACH noch etwas anfaengt.
while (kontext.__zustand.busy) await warte(100);
const vorLesen = protokoll.anfragen.length;
dokument.visibilityState = 'hidden';
(dokument._horcher.visibilitychange || []).forEach((fn) => fn());
await warte(150);
dokument.visibilityState = 'visible';
for (const fn of dokument._horcher.visibilitychange || []) await fn();
await warte(2500);
pruefe('Hinter der Anleitung bleibt es still',
       protokoll.anfragen.length === vorLesen && kontext.__zustand.laeuft === false,
       `${protokoll.anfragen.length - vorLesen} Anfragen, laeuft=${kontext.__zustand.laeuft}`);

// Und das Hauptmenue raeumt das Blatt mit weg, statt es liegen zu lassen
$('menueButton').klick();
await warte(200);
pruefe('Hauptmenue raeumt die Anleitung weg', $('anleitung').hidden === true);
pruefe('Kein Blatt ueber dem Startbildschirm', $('start').hidden === false);

// Zurueck in die Sitzung fuer den Rest des Durchgangs
$('losButton').klick();
await warte(1500);

// Jetzt ist das Problem behoben — die App muss das bestaetigen
const ohneProblem = { ...ANTWORT, probleme: [], optimierungen: [],
                      coach_satz: 'Sieht jetzt gleichmaessig aus.' };
antwortPlan.text = () => JSON.stringify(ohneProblem);
bildAendern();
await warteBis(() => $('behoben').hidden === false, 6000);
pruefe('Verbesserung wird bestaetigt', $('behoben').hidden === false, $('behoben').textContent);
pruefe('Und zwar mit dem Namen des Problems',
       $('behoben').textContent.includes('Bei 9 Uhr'), `"${$('behoben').textContent}"`);
pruefe('Bestaetigung wird auch gesagt',
       protokoll.gesprochen.some((t) => t.startsWith('Besser')),
       protokoll.gesprochen.slice(-3).join(' | ').slice(0, 60));
pruefe('Ampel jetzt gruen', $('ampel').className.includes('gruen'), $('ampel').className);
pruefe('Keine Probleme mehr gelistet', $('probleme').children.length === 0);

antwortPlan.text = () => JSON.stringify(ANTWORT);
bildAendern();
await warteBis(() => $('probleme').children.length > 0, 6000);

// --- Sparmodus ---------------------------------------------------------------
const vorSparen = protokoll.anfragen.length;
await warte(1500);
pruefe('Sparmodus greift bei gleichem Bild', protokoll.anfragen.length === vorSparen,
       `${protokoll.anfragen.length - vorSparen} zusaetzliche Anfragen`);

bildAendern();
await warte(1500);
pruefe('Nach Bildaenderung wieder Analyse', protokoll.anfragen.length > vorSparen,
       `${protokoll.anfragen.length - vorSparen} neue Anfragen`);

// --- Phase weiter ------------------------------------------------------------
const phaseVorher = kontext.__zustand.sitzung.phase;
$('weiterButton').klick();
pruefe('Phase gewechselt', kontext.__zustand.sitzung.phase !== phaseVorher,
       `${phaseVorher} -> ${kontext.__zustand.sitzung.phase}`);
pruefe('Phasenleiste neu gezeichnet', $('phasen').children.length === 6);

console.log(schritte.join('\n'));
console.log(probleme.length ? `\n${probleme.length} PROBLEM(E)` : '\nTeil 1 sauber');
kontext.__zustand.laeuft = false;

// ===========================================================================
// Teil 2 — Vollanalyse, Report, Nachmessen, Teilen, Historie, Pause, Sprache
// ===========================================================================
kontext.__zustand.laeuft = true;
const teil2 = [];
function pruefe2(name, bedingung, zusatz = '') {
  teil2.push(`${bedingung ? '  ok  ' : ' FEHL '} ${name}${zusatz ? ` — ${zusatz}` : ''}`);
  if (!bedingung) probleme.push(name + (zusatz ? ` — ${zusatz}` : ''));
}

// --- Vollanalyse aus drei Winkeln -------------------------------------------
const vorVoll = protokoll.anfragen.length;
$('fWinkel').checked = true;
$('analyseButton').klick();
// Auf ein echtes Zeichen warten: die Note steht erst da, wenn der Report
// gefuellt ist. (Im Nachbau ist `hidden` anfangs false — das taugt nicht.)
await warteBis(() => $('aufnahme').hidden === true && String($('noteZahl').textContent).length > 0);
await warte(120);

pruefe2('Aufnahme-Ansage wieder zu', $('aufnahme').hidden === true);
pruefe2('Drei Winkel angesagt',
        protokoll.gesprochen.filter((s) => s.includes('drehen')).length === 2,
        protokoll.gesprochen.slice(-4).join(' | ').slice(0, 70));
const vollAnfrage = protokoll.anfragen[protokoll.anfragen.length - 1];
const teile = JSON.parse(vollAnfrage.optionen.body).contents[0].parts;
pruefe2('Drei Bilder verschickt', teile.filter((t) => t.inline_data).length === 3,
        `${teile.filter((t) => t.inline_data).length} Bilder`);
pruefe2('Genau eine Anfrage fuer die Vollanalyse', protokoll.anfragen.length === vorVoll + 1);

// --- Report -------------------------------------------------------------------
pruefe2('Report offen', $('report').hidden === false);
// Ohne HMD und ohne Kohle im Bild zaehlt das Hitzemanagement nicht mit — es
// gibt dazu nichts zu sehen. Die uebrigen sechs Kategorien ergeben 75.
pruefe2('Note im Report', String($('noteZahl').textContent) === '75', `"${$('noteZahl').textContent}"`);
pruefe2('Stufe benannt', $('stufeText').textContent === 'gut', `"${$('stufeText').textContent}"`);
pruefe2('Befund gezeigt', $('befund').textContent.includes('Phunnel'));
pruefe2('Sieben Kategorien', $('kategorien').children.length === 7, `${$('kategorien').children.length}`);
pruefe2('Messwerte im Report', $('messwerteReport').children.length >= 2,
        `${$('messwerteReport').children.length} Werte`);
pruefe2('Optimierungsplan', $('planliste').children.length === 1);
pruefe2('Erwartung gefuellt', $('erwartung').children.length === 4);
pruefe2('Sicherheiten gefuellt', $('konfidenz').children.length === 7);
pruefe2('Erkanntes benannt', $('erkanntes').textContent.includes('Oblako'), `"${$('erkanntes').textContent}"`);
pruefe2('Prognose-Zeile', $('prognoseZeile').textContent.includes('84'), `"${$('prognoseZeile').textContent}"`);
pruefe2('Keine Gegenprobe wenn aus', $('gegenprobe').hidden === true);
pruefe2('Kein Vergleich beim ersten Mal', $('vergleich').hidden === true);

// --- Teilen ---------------------------------------------------------------------
$('teilenButton').klick();
await warte(200);
pruefe2('Report geteilt', protokoll.geteilt.length === 1, protokoll.geteilt.join());

// --- Nachmessen -------------------------------------------------------------------
ANTWORT.scores = { tabak_verteilung: 88, fuellhoehe: 80, airflow: 80, hitzemanagement: 78,
                   kopfgeometrie: 80, tabak_kompatibilitaet: 80, zielerreichung: 82 };
ANTWORT.probleme = [];
$('nachmessenButton').klick();
await warteBis(() => $('vergleich').innerHTML.includes('→'));
await warte(120);

pruefe2('Nachmessen liefert bessere Note', String($('noteZahl').textContent) === '82', `"${$('noteZahl').textContent}"`);
pruefe2('Vergleich sichtbar', $('vergleich').hidden === false);
pruefe2('Vergleich zeigt Delta', $('vergleich').innerHTML.includes('75 → 82'), $('vergleich').innerHTML.slice(0, 60));
pruefe2('Prognose bewertet', $('vergleich').innerHTML.includes('Vorhergesagt'),
        $('vergleich').innerHTML.slice(-90));

// --- Session-Feedback ----------------------------------------------------------
$('feedbackButton').klick();
pruefe2('Feedback offen', $('feedback').hidden === false);
kontext.__zustand.feedback = { geschmack: 2, rauch: 3, kratzen: 5, hitze: 5 };
$('fDauer').value = '50';
$('feedbackSenden').klick();
pruefe2('Feedback gespeichert', kontext.__Engine.profil.laden().sessions.length === 1);
pruefe2('Feedback traegt die Note', kontext.__Engine.profil.laden().sessions[0].score === 82,
        String(kontext.__Engine.profil.laden().sessions[0].score));
pruefe2('Feedback-Blatt zu', $('feedback').hidden === true);

// Drei gleichlautende Rueckmeldungen -> Regel
kontext.__Engine.profil.merken({ hitze: 5, kratzen: 5, score: 80 });
kontext.__Engine.profil.merken({ hitze: 4, kratzen: 4, score: 80 });
const regeln = kontext.__Engine.profil.lernregeln();
pruefe2('Lernregeln abgeleitet', regeln.length === 2, regeln.map((r) => r.id).join(', '));
$('zugangButton').klick();
pruefe2('Regeln in den Einstellungen', $('lernregeln').children.length === 2);
$('zugangAbbruch').klick();

// --- Pause und Wiederaufnahme ----------------------------------------------------
$('report').hidden = true;
kontext.__zustand.laeuft = true;
$("kamera").paused = true;
await warte(5200);
pruefe2('Eingefrorenes Bild fuehrt zur Pause', $('pause').hidden === false, $('pauseGrund').textContent);
pruefe2('Schleife gestoppt', kontext.__zustand.laeuft === false);

$("kamera").paused = false;
$('weiterKamera').klick();
await warte(400);
pruefe2('Kamera fortgesetzt', $('pause').hidden === true && kontext.__zustand.laeuft === true);

// --- Sprachbefehle ----------------------------------------------------------------
const vorPhase = kontext.__zustand.sitzung.phase;
kontext.befehlAusfuehren('weiter', 'weiter bitte');
pruefe2('Sprachbefehl weiter', kontext.__zustand.sitzung.phase !== vorPhase,
        `${vorPhase} -> ${kontext.__zustand.sitzung.phase}`);
kontext.befehlAusfuehren('pause', 'pause');
pruefe2('Sprachbefehl pause', $('pause').hidden === false);
kontext.befehlAusfuehren('weiter', 'weiter');
pruefe2('In der Pause keine Phasenaenderung', $('pause').hidden === false);
kontext.befehlAusfuehren('start', 'weitermachen');
await warte(300);
pruefe2('Sprachbefehl weitermachen', $('pause').hidden === true);

// --- Historie -----------------------------------------------------------------------
$('historieButton').klick();
await warte(300);
pruefe2('Historie oeffnet', $('historie').hidden === false);
pruefe2('Historie meldet fehlende Ablage im Klartext',
        $('historieListe').innerHTML.includes('speichert keine Historie'),
        $('historieListe').innerHTML.slice(0, 80));
$('historieZu').klick();

// --- Hauptmenue ----------------------------------------------------------------------
$('menueButton').klick();
pruefe2('Zurueck im Hauptmenue', $('start').hidden === false && $('oben').hidden === true);
pruefe2('Schleife beendet', kontext.__zustand.laeuft === false);
pruefe2('Start bleibt bedienbar', $('losButton').disabled === false);

console.log(teil2.join('\n'));



// ===========================================================================
// Teil 3 — Stoerfaelle, Abbrueche, Nebenlaeufigkeit
// ===========================================================================
const zeilen = [];
function p3(name, ok, zusatz = '') {
  zeilen.push(`${ok ? '  ok  ' : ' FEHL '} ${name}${zusatz ? ` — ${zusatz}` : ''}`);
  if (!ok) probleme.push(`[Stoerfall] ${name}${zusatz ? ` — ${zusatz}` : ''}`);
}
const z = kontext.__zustand;

// Sitzung wieder starten
$('losButton').klick();
await warte(200);
kontext.__Engine.einstellungenSpeichern({ sparmodus: false });
// Die echte Kontingentpause dauert 30 s — fuer den Test verkuerzt, sonst
// stuende der Durchlauf eine halbe Minute still. Die echten Werte bleiben zum
// Vergleich erhalten.
const RUHE_ECHT = { ...kontext.__Steuerung.GRENZEN };
kontext.__Steuerung.GRENZEN.kontingentRuheMs = 1200;

// --- 1. Modell antwortet Muell ------------------------------------------------
antwortPlan.text = () => 'Tut mir leid, ich kann das Bild nicht sehen.';
bildAendern();
await warte(1800);
p3('Muell-Antwort wird abgefangen', z.laeuft === true, 'Schleife laeuft weiter');
p3('Muell-Antwort wird erklaert', $('coach').textContent.includes('JSON'), `"${$('coach').textContent}"`);

// --- 2. Kontingent erschoepft (429) -------------------------------------------
antwortPlan.text = () => '';
antwortPlan.status = 429;
bildAendern();
await warte(4200);
p3('429 im Klartext', $('coach').textContent.includes('Freikontingent'), `"${$('coach').textContent}"`);
p3('Schleife ueberlebt 429', z.laeuft === true);
// Nach 429 wird laenger gewartet als nach einer gewoehnlichen Stoerung — sonst
// laeuft der Zaehler ins Leere weiter, waehrend beim Anbieter nichts durchgeht.
p3('Kontingentpause ist laenger als die Stoerungspause',
   kontext.__Steuerung.fehlerRuhe(429, RUHE_ECHT) > kontext.__Steuerung.fehlerRuhe(500, RUHE_ECHT));
p3('Kein Hammern bei abgelehntem Schluessel', kontext.__Steuerung.fehlerRuhe(401) === null);

// --- 3. Kein Kopf im Bild -------------------------------------------------------
antwortPlan.status = 200;
antwortPlan.text = () => JSON.stringify({ analysis_status: 'no_head_detected', coach_satz: 'Kopf ins Bild halten.' });
bildAendern();
// Nach einem Fehler schlaeft die Schleife 3 s — so lange muss der Test warten.
await warte(5000);
p3('Ohne Kopf keine Note', $('liveScore').hidden === true || !String($('liveScore').textContent).includes('/'),
   `"${$('liveScore').textContent}" hidden=${$('liveScore').hidden}`);
p3('Ohne Kopf klare Ansage', $('kopfInfo').textContent === 'kein Kopf im Bild', `"${$('kopfInfo').textContent}"`);
p3('Ohne Kopf keine Probleme in der Karte', $('probleme').children.length === 0);
p3('Ampel meldet unsicher statt zu raten', $('ampel').className.includes('unsicher'),
   `"${$('ampelText').textContent}"`);

// --- 4. Bild zu schlecht ----------------------------------------------------------
antwortPlan.text = () => JSON.stringify({ analysis_status: 'insufficient_image', coach_satz: 'Mehr Licht.' });
bildAendern();
await warte(2500);
p3('Schlechtes Bild wird benannt', $('kopfInfo').textContent === 'Bild zu schlecht', `"${$('kopfInfo').textContent}"`);

// --- 5. Gegenprobe ------------------------------------------------------------------
antwortPlan.text = (url) => JSON.stringify(String(url).includes('openrouter')
  ? { ...ANTWORT, scores: Object.fromEntries(Object.keys(ANTWORT.scores).map((k) => [k, 25])) }
  : ANTWORT);
kontext.__Engine.einstellungenSpeichern({ gegenprobe: 'openrouter', openrouter_key: 'zweit' });
// Livebetrieb anhalten, damit nur die Vollanalyse zaehlt.
z.laeuft = false;
await warte(1600);
const vorGegen = protokoll.anfragen.length;
$('fWinkel').checked = false;
$('analyseButton').klick();
await warte(3000);
// Zwei Anfragen gehoeren zur Gegenprobe; eine dritte waere eine Liverunde, die
// sich dazwischengeschoben hat — deshalb eine Spanne statt Gleichheit.
p3('Gegenprobe fragt zweimal', protokoll.anfragen.length - vorGegen >= 2
   && protokoll.anfragen.length - vorGegen <= 3,
   `${protokoll.anfragen.length - vorGegen} Anfragen`);
p3('Uneinigkeit wird gezeigt', $('gegenprobe').hidden === false && $('gegenprobe').innerHTML.includes('unsicher'),
   $('gegenprobe').innerHTML.slice(0, 70));
const konfText = $('konfidenz').children.map((k) => k.innerHTML).join(' ');
p3('Sicherheit heruntergesetzt', konfText.includes('Gesamt <b>45%</b>'), konfText.slice(0, 60));
kontext.__Engine.einstellungenSpeichern({ gegenprobe: 'aus' });
$('zurueckButton').klick();
z.laeuft = true;
kontext.__zustand.blindSeit = 0;

// --- 6. Vollanalyse, wenn das Bild nie ruhig wird -------------------------------------
antwortPlan.text = () => JSON.stringify(ANTWORT);
// Kamera tot: die Spur ist beendet, nicht nur das Video angehalten
spur.readyState = 'ended';
$('kamera').paused = true;
$('fWinkel').checked = true;
z.laeuft = false;
await warte(600);
const begonnen = Date.now();
$('analyseButton').klick();
await warte(3000);
const gedauert = Math.round((Date.now() - begonnen) / 1000);
p3('Tote Kamera bricht schnell ab statt zu mahlen', gedauert < 10,
   `${gedauert} s bis zur Meldung`);
p3('Abbruch wird erklaert', $('coach').textContent.includes('Kamera') || $('coach').textContent.includes('fehlgeschlagen'),
   `"${$('coach').textContent}"`);
p3('Analyse-Knopf wieder frei', $('analyseButton').disabled === false);
p3('Ansage wieder zu', $('aufnahme').hidden === true);
spur.readyState = 'live';
$('kamera').paused = false;
z.laeuft = true;
kontext.__zustand.blindSeit = 0;

// --- 7. Hauptmenue waehrend laufender Analyse -------------------------------------------
antwortPlan.verzoegerung = 1500;
bildAendern();
await warte(400);
$('menueButton').klick();
await warte(2500);
p3('Kein Report ueber dem Hauptmenue', $('report').hidden === true && $('start').hidden === false,
   `report=${$('report').hidden} start=${$('start').hidden}`);
p3('Keine Schleife im Hauptmenue', z.laeuft === false);

// --- 8. App im Hintergrund und zurueck ----------------------------------------------------
antwortPlan.verzoegerung = 0;
$('losButton').klick();
await warte(300);

const messe = async (ms) => {
  const vorher = protokoll.anfragen.length;
  await warte(ms);
  return protokoll.anfragen.length - vorher;
};
const takt = async () => {
  // Das Bild aendert sich regelmaessig, aber nicht dauernd: zwischen zwei
  // Aenderungen muss es kurz ruhig sein, sonst kommt die Guetepruefung nie
  // durch und gemessen wuerde nur die Notbremse nach zwoelf Sekunden.
  const uhr = setInterval(bildAendern, 700);
  const anzahl = await messe(5000);
  clearInterval(uhr);
  return anzahl;
};

const taktVorher = await takt();

// Wechsel in den Hintergrund und zurueck — waehrend eine Anfrage laeuft
antwortPlan.verzoegerung = 900;
bildAendern();
await warte(300);
dokument.visibilityState = 'hidden';
(dokument._horcher.visibilitychange || []).forEach((fn) => fn());
await warte(200);
dokument.visibilityState = 'visible';
for (const fn of dokument._horcher.visibilitychange || []) await fn();
await warte(1500);
antwortPlan.verzoegerung = 0;

const taktNachher = await takt();

// Abstaende zwischen den Anfragen: bei nur einer Schleife liegen mindestens
// PAUSE_MS (900 ms) plus Pruefzyklus dazwischen.
// Ohne Anfragen misst der Vergleich nichts — dann ist der Test kaputt, nicht die App.
p3('Nach dem Hintergrund laeuft die Analyse wieder', taktNachher >= 2, `${taktNachher} Anfragen in 5 s`);

const zeiten = protokoll.anfragen.slice(-Math.max(taktNachher, 1)).map((a) => a.zeit);
const abstaende = zeiten.slice(1).map((t, i) => t - zeiten[i]);
const kleinster = abstaende.length ? Math.min(...abstaende) : 9999;

p3('Kein doppelter Analysetakt nach Hintergrund',
   taktNachher <= taktVorher + 1,
   `vorher ${taktVorher}, nachher ${taktNachher} Anfragen in 5 s`);
p3('Abstand zwischen Anfragen bleibt eingehalten', kleinster >= 800,
   `kleinster Abstand ${kleinster} ms, alle: ${abstaende.join(', ')}`
   );

// --- 9. Zittrige Hand: es darf nicht ewig gescannt werden ---------------------------------
// Am Geraet gemeldet: "braucht erstmal lange zum Scannen". Aus der Hand ist ein
// Bild nie ganz ruhig — bleibt die Pruefung streng, geht nie etwas raus.
wackeln(true);
// Eine schon laufende Anfrage erst auslaufen lassen — sonst zaehlt der Test
// sie mit und misst nicht, was er messen will.
while (z.busy) await warte(100);
await warte(300);
// Die Geduldsuhr laeuft ab der letzten Analyse. Fuer die Messung wird sie hier
// auf jetzt gestellt, sonst haengt das Ergebnis daran, wie lange die Abschnitte
// davor gedauert haben.
z.wartetSeit = Date.now();
const vorWackeln = protokoll.anfragen.length;
await warte(2500);
p3('Wackelbild wird zuerst abgewartet', protokoll.anfragen.length === vorWackeln,
   `${protokoll.anfragen.length - vorWackeln} Anfragen in den ersten 2,5 s`);
p3('Wartegrund steht in der Anzeige', /halt still|unscharf/.test($('lage').textContent),
   `"${$('lage').textContent}"`);

// Der Sekundenzaehler erscheint erst ab drei Sekunden Wartezeit, und die zaehlt
// ab der letzten Analyse — nicht ab hier. Deshalb mit Abstand pruefen.
await warte(2000);
p3('Wartezeit wird mitgezaehlt', /·\s*\d+s/.test($('lage').textContent), `"${$('lage').textContent}"`);

await warte(11000);
p3('Nach Geduldsfrist wird trotzdem analysiert', protokoll.anfragen.length > vorWackeln,
   `${protokoll.anfragen.length - vorWackeln} Anfragen nach 14 s`);
wackeln(false);

z.laeuft = false;
console.log(zeilen.join('\n'));

if (probleme.length) {
  console.error(`\n${probleme.length} BEFUND(E):\n- ${probleme.join('\n- ')}`);
  process.exit(1);
}
console.log('\nALLE TESTS BESTANDEN');
process.exit(0);
