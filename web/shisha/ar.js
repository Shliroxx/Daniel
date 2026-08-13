/* Hookah Analyzer — Kamera, AR-Overlay und Bedienung.
 *
 * Diese Datei kuemmert sich nur um das, was man sieht und anfasst. Das Denken
 * steckt in engine.js: Prompt bauen, Modell fragen, Antwort geradeziehen, Note
 * rechnen. Ein eigener Server ist dafuer nicht noetig.
 *
 * Ablauf: Livebild zeigen, warten bis das Handy ruhig und scharf ist, dann ein
 * Einzelbild analysieren lassen. Waehrend das laeuft, zeichnet das Overlay das
 * letzte Ergebnis weiter — so ruckelt nichts.
 *
 * Die Marker kommen als normalisierte Bildkoordinaten (0 bis 1). Das Video liegt
 * mit object-fit: cover im Rahmen, wird also beschnitten — bildAufBildschirm()
 * rechnet das heraus.
 */

'use strict';

const $ = (id) => document.getElementById(id);

const MARKER_FARBE = {
  remove: '#ff6b7d',
  loosen: '#ffb454',
  distribute: '#5fe3ff',
  ok: '#57e39a',
  fill_height: '#6fa8ff',
  hmd: '#b98cff',
  coal: '#ff9a5c',
};

const MARKER_SYMBOL = {
  remove: '−', loosen: '≋', distribute: '↔', ok: '✓',
  fill_height: '⎯', hmd: '◎', coal: '●',
};

const KONF_NAMEN = {
  gesamt: 'Gesamt', kopf_erkennung: 'Kopf', tabak_analyse: 'Tabak', fuellhoehe: 'Fuellhoehe',
  airflow: 'Airflow', hitzemanagement: 'Hitze', optimierung: 'Optimierung',
};

const PFEIL = { hoch: '↑', gleich: '→', runter: '↓' };

// Die Schwellen fuer Ruhe, Schaerfe und Helligkeit stehen in steuerung.js.
const PAUSE_MS = 900;          // Verschnaufpause zwischen zwei Analysen

const zustand = {
  sitzung: null,
  laeuft: false,        // Analyseschleife arbeitet
  pausiert: false,      // Kamera steht, Pausenblende ist offen
  busy: false,
  ton: true,
  hoeren: false,        // Sprachsteuerung an
  analyse: null,
  puls: 0,
  wakeLock: null,
  feedback: {},
  blindSeit: 0,         // seit wann liefert die Kamera kein Bild mehr
  lauf: 0,              // Nummer des aktuellen Schleifendurchgangs
  wartetSeit: 0,        // seit wann wartet die Schleife auf ein brauchbares Bild
  // Bildverschiebung seit der letzten Analyse, in normalisierten Koordinaten.
  versatz: { x: 0, y: 0 },
  markerZeit: 0,        // wann die aktuellen Marker entstanden sind
  aufnahmen: [],        // gesammelte Bilder der Mehrfachaufnahme
};

// --------------------------------------------------------------------------
// Kamera
// --------------------------------------------------------------------------

const video = $('kamera');
const overlay = $('overlay');
const ctx = overlay.getContext('2d');

// Miniatur fuer Ruhe- und Schaerfepruefung — bewusst winzig, das kostet nichts.
const mini = document.createElement('canvas');
mini.width = 64;
mini.height = 48;
const miniCtx = mini.getContext('2d', { willReadFrequently: true });
let letzteMini = null;
let analyseMini = null;   // die Miniatur des zuletzt analysierten Bildes
let letzteAbtastung = 0;  // wann zuletzt eine Miniatur genommen wurde

/* Schaetzt, wie weit sich das Bild seit dem letzten Frame verschoben hat.
 *
 * Simple Suche ueber ein kleines Fenster: die Verschiebung mit der geringsten
 * Abweichung gewinnt. Auf 64x48 Pixeln kostet das nichts und reicht voellig,
 * um die AR-Marker mitwandern zu lassen, solange das Handy nur geschwenkt wird.
 * Bei Drehung oder Abstandsaenderung stimmt es nicht mehr — deshalb verblassen
 * die Marker zusaetzlich mit der Zeit.
 */
const SUCHWEITE = 5;

function versatzSchaetzen(jetzt, vorher) {
  if (!vorher) return { x: 0, y: 0 };

  let bestesX = 0;
  let bestesY = 0;
  let bestes = Infinity;

  for (let dy = -SUCHWEITE; dy <= SUCHWEITE; dy++) {
    for (let dx = -SUCHWEITE; dx <= SUCHWEITE; dx++) {
      let summe = 0;
      let anzahl = 0;
      // Nur den Bereich vergleichen, den beide Bilder abdecken.
      for (let y = SUCHWEITE; y < mini.height - SUCHWEITE; y += 2) {
        const zeileJetzt = y * mini.width;
        const zeileVorher = (y - dy) * mini.width;
        for (let x = SUCHWEITE; x < mini.width - SUCHWEITE; x += 2) {
          summe += Math.abs(jetzt[zeileJetzt + x] - vorher[zeileVorher + x - dx]);
          anzahl++;
        }
      }
      const mittel = summe / anzahl;
      if (mittel < bestes) {
        bestes = mittel;
        bestesX = dx;
        bestesY = dy;
      }
    }
  }

  return { x: bestesX / mini.width, y: bestesY / mini.height };
}

/** Mittlere Abweichung zweier Miniaturen — fuer den Sparmodus. */
function unterschied(a, b) {
  if (!a || !b) return null;
  let summe = 0;
  for (let i = 0; i < a.length; i++) summe += Math.abs(a[i] - b[i]);
  return summe / a.length;
}

// Vollbild fuer die Analyse.
const shot = document.createElement('canvas');
const shotCtx = shot.getContext('2d');

let strom = null;

async function kameraStarten() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    throw new Error(
      'Die Kamera ist nicht freigegeben. Das passiert, wenn die Seite ohne HTTPS ' +
      'geladen wird — ruf sie ueber https://… auf.'
    );
  }

  kameraStoppen();
  strom = await navigator.mediaDevices.getUserMedia({
    video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
    audio: false,
  });

  // iOS gibt die Kamera frei, sobald die App laenger im Hintergrund war. Dann
  // endet die Spur und das Livebild friert ein — davon wollen wir erfahren.
  strom.getVideoTracks().forEach((spur) => {
    spur.addEventListener('ended', () => pausieren('Die Kamera wurde vom System freigegeben.'));
  });

  video.srcObject = strom;
  letzteMini = null;
  await video.play();
}

function kameraStoppen() {
  if (!strom) return;
  strom.getTracks().forEach((spur) => spur.stop());
  strom = null;
  // Sonst haengt der letzte Frame im Video und blitzt beim naechsten Start auf,
  // und die Miniaturen der alten Sitzung verfaelschen den ersten Vergleich.
  video.srcObject = null;
  letzteMini = null;
  analyseMini = null;
}

/** Liefert die Kamera gerade ein brauchbares Livebild? */
function kameraLaeuft() {
  if (!strom || !video.videoWidth) return false;
  return strom.getVideoTracks().some((spur) => spur.readyState === 'live');
}

// -- Pause und Wiederaufnahme ----------------------------------------------

function pausieren(grund) {
  if (zustand.pausiert) return;
  zustand.laeuft = false;
  zustand.pausiert = true;
  zustand.wartetSeit = 0;
  if (window.speechSynthesis) speechSynthesis.cancel();
  // Angehalten heisst angehalten: sonst laeuft die Kamera weiter, das
  // Aufnahmelicht bleibt an und der Akku zieht, waehrend die App behauptet,
  // sie stehe. fortsetzen() startet sie ohnehin komplett neu.
  kameraStoppen();
  bildschirmFreigeben();
  $('pauseGrund').textContent = grund || 'Die Kamera steht.';
  $('pauseFehler').textContent = '';
  $('pause').hidden = false;
  // Der Ton bleibt an: so kann man die Pause auch per Sprache beenden.
}

async function fortsetzen() {
  const knopf = $('weiterKamera');
  knopf.disabled = true;
  knopf.textContent = 'Kamera startet …';
  try {
    await kameraStarten();
    await bildschirmWachhalten();
    zustand.blindSeit = 0;
    zustand.pausiert = false;
    zustand.laeuft = true;
    $('pause').hidden = true;
    setzeLage('live', '');
    schleife();
  } catch (fehler) {
    $('pauseFehler').textContent = `Kamera laesst sich nicht starten: ${fehler.message}`;
  } finally {
    knopf.disabled = false;
    knopf.textContent = 'Kamera fortsetzen';
  }
}

function zumHauptmenue() {
  zustand.laeuft = false;
  zustand.pausiert = false;
  zustand.analyse = null;
  zustand.lauf++;          // laufende Antworten gehoeren nicht mehr hierher
  kameraStoppen();
  bildschirmFreigeben();
  hoerenAus();
  if (window.speechSynthesis) speechSynthesis.cancel();
  $('pause').hidden = true;
  $('report').hidden = true;
  $('feedback').hidden = true;
  $('oben').hidden = true;
  $('unten').hidden = true;
  $('start').hidden = false;
  $('losButton').textContent = 'Kamera starten';
  startBereitschaft();
}

/* Bildschirm wachhalten — und merken, wenn das System die Sperre zurueckholt.
 *
 * iOS gibt die Sperre frei, sobald die Seite in den Hintergrund geht. Das
 * Sentinel-Objekt bleibt dabei bestehen, ist also weiter wahr — wer nur darauf
 * prueft, fordert nie wieder an. Folge: einmal kurz aus der App heraus, und ab
 * da geht der Bildschirm mitten im Bauen aus.
 */
async function bildschirmWachhalten() {
  try {
    if (!('wakeLock' in navigator)) return;
    const sperre = await navigator.wakeLock.request('screen');
    zustand.wakeLock = sperre;
    sperre.addEventListener('release', () => {
      if (zustand.wakeLock === sperre) zustand.wakeLock = null;
    });
  } catch (_) {
    /* nicht schlimm — dann geht das Display eben irgendwann aus */
  }
}

/** Sperre zurueckgeben — im Menue und in der Pause braucht sie niemand. */
async function bildschirmFreigeben() {
  const sperre = zustand.wakeLock;
  zustand.wakeLock = null;
  try {
    if (sperre) await sperre.release();
  } catch (_) { /* schon weg */ }
}

document.addEventListener('visibilitychange', async () => {
  if (document.visibilityState !== 'visible') {
    // Im Hintergrund analysieren wir nicht weiter — das spart Kontingent und
    // Akku, und iOS friert die Kamera ohnehin ein.
    if (zustand.laeuft) {
      zustand.laeuft = false;
      zustand.pausiert = false;   // noch keine Blende: vielleicht kommt er gleich zurueck
    }
    return;
  }

  if (!zustand.wakeLock) bildschirmWachhalten();

  // Zurueck aus dem Hintergrund: laeuft die Kamera noch, geht es einfach weiter.
  if (kameraLaeuft()) {
    try {
      await video.play();
    } catch (_) { /* dann entscheidet der Plan unten auf pausieren */ }
  }

  const plan = Steuerung.rueckkehrPlan({
    spurLebt: kameraLaeuft(),
    videoLaeuft: !video.paused,
    pausiert: zustand.pausiert,
    imHauptmenue: !$('start').hidden,
    sitzungDa: Boolean(zustand.sitzung),
  });

  if (plan.aktion === 'weiter' && !zustand.laeuft) {
    zustand.laeuft = true;
    zustand.blindSeit = 0;
    setzeLage('live', '');
    schleife();
  } else if (plan.aktion === 'pausieren') {
    pausieren(plan.grund);
  }
});

// --------------------------------------------------------------------------
// Bildguete: Bewegung, Schaerfe, Helligkeit
// --------------------------------------------------------------------------

function bildGuete() {
  if (!video.videoWidth) return null;
  miniCtx.drawImage(video, 0, 0, mini.width, mini.height);
  const daten = miniCtx.getImageData(0, 0, mini.width, mini.height).data;

  const grau = new Float32Array(mini.width * mini.height);
  let summe = 0;
  for (let i = 0, p = 0; i < daten.length; i += 4, p++) {
    const wert = 0.299 * daten[i] + 0.587 * daten[i + 1] + 0.114 * daten[i + 2];
    grau[p] = wert;
    summe += wert;
  }

  const bewegung = letzteMini === null ? 999 : unterschied(grau, letzteMini);

  // Verschiebung nur schaetzen, wenn sich ueberhaupt etwas bewegt hat.
  if (letzteMini && bewegung > 0.6) {
    const schub = versatzSchaetzen(grau, letzteMini);
    zustand.versatz.x += schub.x;
    zustand.versatz.y += schub.y;
  }

  letzteMini = grau;
  zustand.letzteGrau = grau;

  let kanten = 0;
  for (let y = 1; y < mini.height - 1; y++) {
    for (let x = 1; x < mini.width - 1; x++) {
      const p = y * mini.width + x;
      kanten += Math.abs(grau[p] * 2 - grau[p - 1] - grau[p + 1]);
      kanten += Math.abs(grau[p] * 2 - grau[p - mini.width] - grau[p + mini.width]);
    }
  }

  return { bewegung, schaerfe: kanten / (mini.width * mini.height), helligkeit: summe / grau.length };
}

/** Wie stark hat sich das Bild seit der letzten Analyse veraendert? */
function veraenderungSeitAnalyse() {
  return unterschied(zustand.letzteGrau, analyseMini);
}

/* Taugt das aktuelle Bild? `wartetMs` ist die Zeit ohne Analyse.
 *
 * Die Sekundenzahl steht mit in der Anzeige, damit man sieht, dass die App
 * nicht haengt, sondern wartet — und ab wann sie es trotzdem versucht.
 */
function guetePruefen(wartetMs = 0) {
  const urteil = Steuerung.bildBewerten(bildGuete(), Steuerung.GRENZEN, wartetMs);
  if (!urteil.ok) {
    if (urteil.problem !== 'kein Bild') {
      const sekunden = Math.floor(wartetMs / 1000);
      setzeLage(sekunden >= 3 ? `${urteil.problem} · ${sekunden}s` : urteil.problem, 'warn');
    }
    return false;
  }
  return urteil;
}

// --------------------------------------------------------------------------
// Analyseschleife
// --------------------------------------------------------------------------

function bildAufnehmen(maxKante) {
  const faktor = Math.min(1, maxKante / Math.max(video.videoWidth, video.videoHeight));
  shot.width = Math.round(video.videoWidth * faktor);
  shot.height = Math.round(video.videoHeight * faktor);
  shotCtx.drawImage(video, 0, 0, shot.width, shot.height);
  return new Promise((fertig) => shot.toBlob(fertig, 'image/jpeg', 0.78));
}

/* Die Analyseschleife — und zwar genau eine davon.
 *
 * Wechselt die App in den Hintergrund, wird `laeuft` auf false gesetzt; die
 * Schleife haengt aber womoeglich gerade in einer laufenden Anfrage fest und
 * merkt das erst danach. Kommt der Nutzer in der Zwischenzeit zurueck, startet
 * eine zweite Schleife — und die alte laeuft weiter, weil `laeuft` wieder true
 * ist. Ergebnis: doppelt so viele Anfragen und doppelter Kontingentverbrauch.
 * Deshalb bekommt jeder Durchgang eine Nummer; nur die juengste arbeitet.
 */
async function schleife() {
  const meiner = ++zustand.lauf;
  const meineRunde = () => zustand.laeuft && zustand.lauf === meiner;

  while (meineRunde()) {
    const lage = Steuerung.kameraLage({
      spurLebt: kameraLaeuft(),
      videoLaeuft: !video.paused,
      blindSeit: zustand.blindSeit,
    });
    zustand.blindSeit = lage.blindSeit;

    if (lage.aktion === 'pausieren') {
      pausieren(lage.grund);
      return;
    }
    if (lage.aktion === 'warten') {
      await schlafen(300);
      continue;
    }

    if (!zustand.wartetSeit) zustand.wartetSeit = Date.now();
    const wartetMs = Date.now() - zustand.wartetSeit;

    const urteil = zustand.busy ? false : guetePruefen(wartetMs);
    if (!urteil) {
      await schlafen(220);
      continue;
    }

    // Unveraendertes Bild kostet sonst Kontingent fuer dieselbe Antwort.
    const sparen = Steuerung.lohntAnalyse(veraenderungSeitAnalyse(), Engine.einstellungen().sparmodus);
    if (!sparen.lohnt) {
      setzeLage(sparen.grund, '');
      // Hier wird nicht auf ein brauchbares Bild gewartet — das Bild ist in
      // Ordnung, es hat sich nur nichts getan. Die Geduldsuhr gehoert also
      // zurueckgesetzt, sonst gilt der naechste Wackler nach einer ruhigen
      // Minute sofort als Notfall und geht ungeprueft raus.
      zustand.wartetSeit = 0;
      await schlafen(600);
      continue;
    }

    zustand.busy = true;
    zustand.wartetSeit = 0;
    let ruhe = PAUSE_MS;
    let stolperstein = '';
    setzeLage(urteil.nachsichtig ? 'analysiere (unruhig)' : 'analysiere', 'denkt');
    try {
      // 896 Pixel Kante reichen dem Modell und halten die Uebertragung klein.
      const blob = await bildAufnehmen(896);
      analyseMini = zustand.letzteGrau;
      zustand.versatz = { x: 0, y: 0 };
      const ergebnis = await zustand.sitzung.analysieren(blob, 'live', false);
      // Waehrend der Anfrage kann eine neue Runde begonnen haben — dann ist
      // dieses Ergebnis veraltet. Es wird dann weder angezeigt noch einsortiert:
      // sonst schoebe es sich in Verlauf und Konsens und koennte sogar die
      // Bauphase weiterschalten, ohne dass etwas davon zu sehen waere.
      if (meineRunde()) liveUebernehmen(zustand.sitzung.aufnehmen(ergebnis));
    } catch (fehler) {
      setzeLage(kurz(fehler.message), 'fehler');
      $('coach').textContent = fehler.message;
      ruhe = Steuerung.fehlerRuhe(fehler.status || 0);
      stolperstein = fehler.message;
    } finally {
      // Vor der Wartezeit freigeben, nicht danach: sonst blockiert eine lange
      // Kontingentpause den Knopf fuer die Vollanalyse gleich mit.
      zustand.busy = false;
      kontingentZeigen();
    }

    if (ruhe === null) {
      pausieren(stolperstein);
      return;
    }
    await schlafen(ruhe);
  }
}

/** Wartet, bis keine Anfrage mehr laeuft — hoechstens aber so lange. */
async function warteAufFrei(hoechstensMs) {
  const bis = Date.now() + hoechstensMs;
  while (zustand.busy && Date.now() < bis) await schlafen(120);
  return !zustand.busy;
}

/** Zeigt an, wie viele Anfragen das heutige Freikontingent schon gekostet hat. */
function kontingentZeigen() {
  const werte = Engine.verbrauch();
  const feld = $('kontingent');
  if (!werte.limit) {
    feld.hidden = true;
    return;
  }
  feld.hidden = false;
  feld.textContent = `${werte.anzahl}/${werte.limit}`;
  feld.className = `marke${werte.erschoepft ? ' fehler' : werte.warnung ? ' warn' : ''}`;
  feld.title = werte.erschoepft
    ? 'Tageskontingent aufgebraucht — morgen wieder, oder Anbieter wechseln.'
    : `heute verbrauchte Anfragen bei ${werte.anbieter}`;
}

const schlafen = (ms) => new Promise((fertig) => setTimeout(fertig, ms));
const kurz = (text) => (text || 'Fehler').slice(0, 44);

// --------------------------------------------------------------------------
// Live-Ergebnis in die Oberflaeche
// --------------------------------------------------------------------------

function liveUebernehmen(analyse) {
  zustand.analyse = analyse;
  zustand.markerZeit = Date.now();

  const ok = analyse.analysis_status === 'ok';
  $('coach').textContent = analyse.coach_satz || (ok ? 'Weiter so.' : 'Kopf ins Bild holen.');
  $('coach').classList.toggle('gut', ok && !analyse.probleme.length);

  const schritte = $('schritte');
  schritte.innerHTML = '';
  analyse.optimierungen.forEach((eintrag) => {
    const zeile = document.createElement('li');
    zeile.textContent = eintrag.text;
    schritte.appendChild(zeile);
  });

  const probleme = $('probleme');
  probleme.innerHTML = '';
  analyse.probleme.forEach((problem) => {
    const zeile = document.createElement('div');
    zeile.className = `problem ${problem.severity}`;
    zeile.dataset.symbol = problem.symbol;
    zeile.textContent = problem.titel;
    probleme.appendChild(zeile);
  });

  const frage = $('rueckfrage');
  frage.hidden = !analyse.rueckfrage;
  frage.textContent = analyse.rueckfrage || '';

  messwerteZeigen(analyse);

  const kopf = analyse.kopf || {};
  $('kopfInfo').textContent = ok
    ? kopfBeschriftung(kopf)
    : (analyse.analysis_status === 'insufficient_image' ? 'Bild zu schlecht' : 'kein Kopf im Bild');

  // Angezeigt wird der Konsens ueber mehrere Bilder, nicht der Einzelwert —
  // sonst springt die Zahl bei jedem Frame.
  const scoreFeld = $('liveScore');
  const konsens = analyse.konsens;
  scoreFeld.hidden = !konsens && analyse.gesamtscore === null;
  if (konsens) {
    const pfeil = konsens.trend > 4 ? ' ↑' : konsens.trend < -4 ? ' ↓' : '';
    scoreFeld.textContent = `${konsens.score}/100${pfeil}`;
    scoreFeld.classList.toggle('unsicher', !konsens.stabil);
    scoreFeld.title = konsens.stabil
      ? `stabil ueber ${konsens.bilder} Bilder`
      : `schwankt noch (${konsens.bilder} Bilder, Spanne ${konsens.spanne})`;
  } else if (analyse.gesamtscore !== null) {
    scoreFeld.textContent = `${analyse.gesamtscore}/100`;
    scoreFeld.classList.add('unsicher');
    scoreFeld.title = 'Einzelbild, noch kein Konsens';
  }

  $('fortschritt').style.width = `${Math.round(analyse.fortschritt * 100)}%`;
  phasenZeichnen();
  setzeLage(ok ? 'live' : 'suche Kopf', ok ? '' : 'warn');

  if (analyse.sprechen && analyse.coach_satz) sprich(analyse.coach_satz);
  if (analyse.phase_gewechselt) vibriere(30);
  if (analyse.probleme.some((p) => p.severity === 'critical')) vibriere([20, 60, 20]);
}

function messwerteZeigen(analyse) {
  const box = $('messwerte');
  box.innerHTML = '';
  if (analyse.analysis_status !== 'ok') return;

  const tabak = analyse.tabak || {};
  const werte = [];
  if (tabak.fuellhoehe_mm !== null) {
    werte.push(['Hoehe', tabak.fuellhoehe_mm < 0
      ? `${Math.abs(tabak.fuellhoehe_mm)} mm ueber Rand`
      : `${tabak.fuellhoehe_mm} mm unter Rand`, tabak.fuellhoehe_quelle]);
  }
  if (tabak.dichte) werte.push(['Dichte', `${tabak.dichte}/100`, tabak.quelle]);
  if (tabak.gleichmaessigkeit) werte.push(['Gleichmass', `${tabak.gleichmaessigkeit}/100`, null]);
  if (analyse.airflow) werte.push(['Airflow-Risiko', analyse.airflow.blockade_risiko, null]);
  if (analyse.hmd && analyse.hmd.erkannt && analyse.hmd.abstand_mm !== null) {
    werte.push(['HMD-Abstand', `${analyse.hmd.abstand_mm} mm`, null]);
  }

  werte.forEach(([name, wert, quelle]) => {
    const feld = document.createElement('span');
    feld.className = 'messwert';
    feld.innerHTML = `${escape(name)} <b>${escape(String(wert))}</b>${quelle ? ` ${quellenKuerzel(quelle)}` : ''}`;
    box.appendChild(feld);
  });

  if (analyse.vorlaeufig) {
    const feld = document.createElement('span');
    feld.className = 'messwert vorlaeufig';
    feld.textContent = 'vorläufig — Bild zu dünn für ein Urteil';
    box.appendChild(feld);
  }
}

function quellenKuerzel(quelle) {
  if (quelle === 'observed') return 'gesehen';
  if (quelle === 'estimated') return 'geschätzt';
  if (quelle === 'unknown') return 'unsicher';
  if (quelle === 'angegeben') return 'angenommen';
  return '';
}

/* Wie der Kopf benannt wird — und ob er erkannt oder nur angenommen ist.
 * Das muss auseinanderzuhalten sein, sonst haelt man eine Annahme fuer ein
 * Messergebnis.
 */
function kopfBeschriftung(kopf) {
  if (!kopf) return '';
  const name = kopf.modell || (kopf.art !== 'unbekannt' ? kopf.art : 'Kopf erkannt');
  return kopf.angenommen ? `${name} · angenommen` : [name, quellenKuerzel(kopf.quelle)].filter(Boolean).join(' · ');
}

function setzeLage(text, art) {
  const feld = $('lage');
  feld.textContent = text;
  feld.className = `marke ${art || ''}`;
}

function phasenZeichnen() {
  const box = $('phasen');
  const phasen = Engine.spec.phasen;
  box.innerHTML = '';
  const aktuell = phasen.findIndex((p) => p.key === zustand.sitzung.phase);
  phasen.forEach((phase, index) => {
    const knopf = document.createElement('button');
    knopf.className = 'phase';
    if (index === aktuell) knopf.classList.add('aktiv');
    else if (aktuell >= 0 && index < aktuell) knopf.classList.add('erledigt');
    knopf.textContent = phase.name;
    knopf.title = phase.ziel;
    knopf.onclick = () => {
      zustand.sitzung.phaseSetzen(phase.key);
      fortschrittZeichnen();
      neuBeurteilen();
    };
    box.appendChild(knopf);
  });
  const aktives = box.querySelector('.aktiv');
  if (aktives) aktives.scrollIntoView({ inline: 'center', block: 'nearest', behavior: 'smooth' });
}

// --------------------------------------------------------------------------
// AR-Overlay
// --------------------------------------------------------------------------

function overlayAnpassen() {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  overlay.width = Math.round(overlay.clientWidth * dpr);
  overlay.height = Math.round(overlay.clientHeight * dpr);
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
}

window.addEventListener('resize', overlayAnpassen);
window.addEventListener('orientationchange', () => setTimeout(overlayAnpassen, 300));

/* Rechnet normalisierte Bildkoordinaten auf den sichtbaren Ausschnitt um.
 * Das Video liegt mit object-fit: cover im Rahmen, wird also beschnitten. */
function bildAufBildschirm(nx, ny) {
  const cw = overlay.clientWidth;
  const ch = overlay.clientHeight;
  const vw = video.videoWidth || cw;
  const vh = video.videoHeight || ch;
  const skala = Math.max(cw / vw, ch / vh);
  return { x: (cw - vw * skala) / 2 + nx * vw * skala, y: (ch - vh * skala) / 2 + ny * vh * skala };
}

function zeichnen() {
  requestAnimationFrame(zeichnen);
  if (!zustand.laeuft) return;

  // Bildbewegung mitschreiben, unabhaengig von der Analyseschleife.
  //
  // Frueher passierte das nur in der Guetepruefung — und die kommt waehrend
  // einer laufenden Anfrage gar nicht dran. Genau in diesen zwei bis vier
  // Sekunden bewegt sich das Handy aber. Die Marker klebten dann an den
  // Koordinaten des analysierten Bildes, und der naechste Vergleich lief ueber
  // eine Luecke von Sekunden ins Leere. Zehnmal pro Sekunde reicht dafuer.
  if (zustand.busy && Date.now() - letzteAbtastung > 100) {
    letzteAbtastung = Date.now();
    bildGuete();
  }

  const breite = overlay.clientWidth;
  const hoehe = overlay.clientHeight;
  ctx.clearRect(0, 0, breite, hoehe);
  zustand.puls += 0.05;

  const analyse = zustand.analyse;
  const ok = analyse && analyse.analysis_status === 'ok';

  zeichneSucher(breite / 2, hoehe * 0.44, Math.min(breite, hoehe) * 0.32, ok);

  if (!ok) {
    zeichneMitteltext(breite / 2, hoehe * 0.44 + Math.min(breite, hoehe) * 0.32 + 34,
      analyse && analyse.analysis_status === 'insufficient_image'
        ? 'Naeher ran, ruhiger halten, mehr Licht'
        : 'Kopf von oben ins Bild holen');
    return;
  }

  // Marker wandern mit der Bildbewegung mit und verblassen mit dem Alter.
  const staerke = Steuerung.alterFaktor(Date.now() - zustand.markerZeit);
  (analyse.ar_marker || []).forEach((marker) => {
    zeichneMarker(Steuerung.markerVerschieben(marker, zustand.versatz), staerke);
  });
  if (analyse.gesamtscore !== null) zeichneScore(breite - 18, hoehe * 0.44, analyse.gesamtscore);
}

function zeichneSucher(mx, my, radius, aktiv) {
  ctx.save();
  ctx.strokeStyle = aktiv ? 'rgba(95, 227, 255, 0.28)' : 'rgba(125, 149, 163, 0.45)';
  ctx.lineWidth = 1.5;
  ctx.setLineDash([10, 8]);
  ctx.lineDashOffset = -zustand.puls * 12;
  ctx.beginPath();
  ctx.arc(mx, my, radius, 0, Math.PI * 2);
  ctx.stroke();

  ctx.setLineDash([]);
  ctx.lineWidth = 2.5;
  ctx.strokeStyle = aktiv ? 'rgba(95, 227, 255, 0.7)' : 'rgba(125, 149, 163, 0.6)';
  [-0.75, -0.25, 0.25, 0.75].forEach((teil) => {
    const mitte = teil * Math.PI;
    ctx.beginPath();
    ctx.arc(mx, my, radius * 1.16, mitte - 0.08 * Math.PI, mitte + 0.08 * Math.PI);
    ctx.stroke();
  });
  ctx.restore();
}

function zeichneMarker(marker, staerke = 1) {
  const farbe = MARKER_FARBE[marker.typ] || MARKER_FARBE.distribute;
  const oben = bildAufBildschirm(marker.x, marker.y);
  const unten = bildAufBildschirm(marker.x + marker.w, marker.y + marker.h);
  const breite = unten.x - oben.x;
  const hoehe = unten.y - oben.y;

  ctx.save();

  if (marker.typ === 'fill_height') {
    // Fuellhoehe ist eine Linie, kein Kasten.
    const y = oben.y + hoehe / 2;
    ctx.strokeStyle = farbe;
    ctx.lineWidth = 2.5;
    ctx.setLineDash([12, 7]);
    ctx.lineDashOffset = -zustand.puls * 10;
    ctx.beginPath();
    ctx.moveTo(oben.x, y);
    ctx.lineTo(unten.x, y);
    ctx.stroke();
    ctx.restore();
    zeichneEtikett(oben.x + breite / 2, y - 14, marker, farbe, staerke);
    return;
  }

  const auffaellig = marker.typ !== 'ok';
  ctx.strokeStyle = farbe;
  ctx.lineWidth = auffaellig ? 2.5 : 2;
  ctx.globalAlpha = (auffaellig ? 0.95 : 0.7) * staerke;
  if (auffaellig) {
    ctx.shadowColor = farbe;
    ctx.shadowBlur = 14;
    ctx.setLineDash([9, 6]);
    ctx.lineDashOffset = -zustand.puls * 10;
  }
  rundesRechteck(oben.x, oben.y, breite, hoehe, Math.min(14, breite / 3, hoehe / 3));
  ctx.stroke();

  ctx.setLineDash([]);
  ctx.globalAlpha = auffaellig ? 0.14 + Math.sin(zustand.puls * 2) * 0.04 : 0.08;
  ctx.fillStyle = farbe;
  ctx.fill();
  ctx.restore();

  zeichneEtikett(oben.x + breite / 2, oben.y - 12, marker, farbe, staerke);
}

function rundesRechteck(x, y, breite, hoehe, radius) {
  const r = Math.max(0, Math.min(radius, breite / 2, hoehe / 2));
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + breite, y, x + breite, y + hoehe, r);
  ctx.arcTo(x + breite, y + hoehe, x, y + hoehe, r);
  ctx.arcTo(x, y + hoehe, x, y, r);
  ctx.arcTo(x, y, x + breite, y, r);
  ctx.closePath();
}

function zeichneEtikett(x, y, marker, farbe, staerke = 1) {
  const beschriftung = `${MARKER_SYMBOL[marker.typ] || ''} ${marker.label || ''}`.trim();
  if (!beschriftung) return;

  ctx.save();
  ctx.globalAlpha = staerke;
  ctx.font = '600 13px -apple-system, system-ui, sans-serif';
  const breite = ctx.measureText(beschriftung).width + 16;
  const hoehe = 22;
  const links = Math.max(6, Math.min(x - breite / 2, overlay.clientWidth - breite - 6));
  const oben = Math.max(6, y - hoehe);

  ctx.fillStyle = 'rgba(4, 8, 13, 0.82)';
  rundesRechteck(links, oben, breite, hoehe, 11);
  ctx.fill();
  ctx.strokeStyle = farbe;
  ctx.lineWidth = 1;
  ctx.stroke();

  ctx.fillStyle = farbe;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(beschriftung, links + breite / 2, oben + hoehe / 2 + 0.5);
  ctx.restore();
}

function zeichneScore(x, y, score) {
  const radius = 26;
  const farbe = noteFarbe(score);

  ctx.save();
  ctx.translate(x - radius, y);
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.14)';
  ctx.lineWidth = 5;
  ctx.beginPath();
  ctx.arc(0, 0, radius, 0, Math.PI * 2);
  ctx.stroke();

  ctx.strokeStyle = farbe;
  ctx.lineCap = 'round';
  ctx.beginPath();
  ctx.arc(0, 0, radius, -Math.PI / 2, -Math.PI / 2 + (score / 100) * Math.PI * 2);
  ctx.stroke();

  ctx.fillStyle = farbe;
  ctx.font = '700 17px -apple-system, system-ui, sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(String(score), 0, 0);
  ctx.restore();
}

function zeichneMitteltext(x, y, beschriftung) {
  ctx.save();
  ctx.fillStyle = 'rgba(220, 238, 246, 0.85)';
  ctx.font = '500 14px -apple-system, system-ui, sans-serif';
  ctx.textAlign = 'center';
  ctx.shadowColor = 'rgba(0, 0, 0, 0.85)';
  ctx.shadowBlur = 6;
  ctx.fillText(beschriftung, x, y);
  ctx.restore();
}

const noteFarbe = (score) =>
  score >= 85 ? '#57e39a' : score >= 70 ? '#5fe3ff' : score >= 50 ? '#ffb454' : '#ff6b7d';


// --------------------------------------------------------------------------
// Sprachsteuerung
// --------------------------------------------------------------------------

/* Freihaendig bedienen — beim Kopfbauen sind beide Haende voll.
 *
 * Die Erkennung laeuft in Safari ueber webkitSpeechRecognition. Sie hoert
 * bewusst nur auf kurze Befehle aus spec.json, nicht auf Fliesstext, und wird
 * waehrend der eigenen Sprachausgabe angehalten — sonst hoert sie sich selbst
 * zu und loest ihre eigenen Hinweise als Befehle aus.
 */

const Erkennung = window.SpeechRecognition || window.webkitSpeechRecognition;
let hoerer = null;
let hoererPause = false;   // waehrend die App selbst spricht

function spracheMoeglich() {
  return Boolean(Erkennung);
}

function hoererBauen() {
  const h = new Erkennung();
  h.lang = 'de-DE';
  h.continuous = true;
  h.interimResults = false;
  h.maxAlternatives = 2;

  h.onresult = (ereignis) => {
    if (hoererPause) return;
    for (let i = ereignis.resultIndex; i < ereignis.results.length; i++) {
      const ergebnis = ereignis.results[i];
      if (!ergebnis.isFinal) continue;
      // Alle Alternativen durchprobieren — die erste ist nicht immer die beste.
      for (let a = 0; a < ergebnis.length; a++) {
        const befehl = Engine.befehlErkennen(ergebnis[a].transcript);
        if (befehl) {
          befehlAusfuehren(befehl, ergebnis[a].transcript);
          return;
        }
      }
    }
  };

  h.onerror = (ereignis) => {
    // "no-speech" und "aborted" sind Alltag, kein Grund zur Meldung.
    if (ereignis.error === 'not-allowed' || ereignis.error === 'service-not-allowed') {
      hoerenAus();
      setzeLage('Mikrofon verweigert', 'fehler');
    }
  };

  // Safari beendet die Erkennung nach kurzer Stille von selbst — neu starten.
  h.onend = () => {
    if (!zustand.hoeren) return;
    try {
      h.start();
    } catch (_) { /* laeuft schon */ }
  };

  return h;
}

function hoerenAn() {
  if (!spracheMoeglich() || zustand.hoeren) return;
  hoerer = hoerer || hoererBauen();
  try {
    hoerer.start();
    zustand.hoeren = true;
    hoerenAnzeigen();
    sprich('Ich höre.');
  } catch (fehler) {
    setzeLage('Sprache nicht verfügbar', 'warn');
  }
}

function hoerenAus() {
  zustand.hoeren = false;
  if (hoerer) {
    try {
      hoerer.stop();
    } catch (_) { /* war schon aus */ }
  }
  hoerenAnzeigen();
}

function hoerenAnzeigen() {
  const knopf = $('hoerButton');
  if (!knopf) return;
  knopf.textContent = zustand.hoeren ? '🎙️' : '🎤';
  knopf.classList.toggle('aus', !zustand.hoeren);
  knopf.classList.toggle('an', zustand.hoeren);
  $('hoerMarke').hidden = !zustand.hoeren;
}

function befehlAusfuehren(befehl, gesagt) {
  const plan = Steuerung.befehlPlan(befehl, {
    laeuft: zustand.laeuft,
    pausiert: zustand.pausiert,
    imHauptmenue: !$('start').hidden,
  });

  if (plan.aktion === 'nichts') return;
  vibriere(20);
  $('hoerMarke').textContent = `„${(gesagt || '').trim().slice(0, 24)}"`;

  switch (plan.aktion) {
    case 'phase_vor':
      zustand.sitzung.weiter();
      fortschrittZeichnen();
      neuBeurteilen();
      sprich(`Phase ${zustand.sitzung.phaseInfo.name}.`);
      break;
    case 'phase_zurueck': {
      const phasen = Engine.spec.phasen;
      const index = phasen.findIndex((p) => p.key === zustand.sitzung.phase);
      zustand.sitzung.phaseSetzen(phasen[Math.max(0, index - 1)].key);
      fortschrittZeichnen();
      neuBeurteilen();
      sprich(`Zurück zu ${zustand.sitzung.phaseInfo.name}.`);
      break;
    }
    case 'vollanalyse':
      sprich('Ich schaue mir den Kopf genau an.');
      vollanalyse();
      break;
    case 'neue_sitzung':
      sitzungStarten();
      sprich('Neuer Kopf.');
      break;
    case 'pausieren':
      pausieren('Auf Zuruf angehalten.');
      break;
    case 'fortsetzen':
    case 'kamera_starten':
      fortsetzen();
      break;
    case 'hauptmenue':
      zumHauptmenue();
      break;
    case 'ton_aus':
      zustand.ton = false;
      tonAnzeigen();
      if (window.speechSynthesis) speechSynthesis.cancel();
      break;
    case 'ton_an':
      zustand.ton = true;
      tonAnzeigen();
      sprich('Ton ist an.');
      break;
    case 'wiederholen':
      if (zustand.analyse && zustand.analyse.coach_satz) {
        // Sperre umgehen: hier ist die Wiederholung ausdruecklich gewollt.
        const satz = zustand.analyse.coach_satz;
        if (zustand.sitzung) zustand.sitzung.gesagt.delete(satz.trim().toLowerCase());
        sprich(satz);
      } else {
        sprich('Ich habe noch nichts gesagt.');
      }
      break;
    case 'status_sagen': {
      const konsens = zustand.analyse && zustand.analyse.konsens;
      if (konsens) sprich(`${konsens.score} von 100, ${konsens.stufe_text}.`);
      else sprich('Noch keine Bewertung.');
      break;
    }
    default:
      break;
  }
}

function fortschrittZeichnen() {
  $('fortschritt').style.width = `${Math.round(zustand.sitzung.fortschritt() * 100)}%`;
  phasenZeichnen();
}

/* Naechstes Bild auf jeden Fall wieder analysieren.
 *
 * Der Sparmodus vergleicht nur Pixel. Wechselt der Nutzer die Bauphase, aendert
 * sich aber der Prompt, nicht das Bild — der Kopf liegt ja unveraendert da.
 * Ohne diesen Anstoss steht dann "unveraendert" auf dem Schirm, bis jemand das
 * Handy bewegt, und der Coach redet weiter ueber die alte Phase.
 */
function neuBeurteilen() {
  analyseMini = null;
}

function tonAnzeigen() {
  $('tonButton').textContent = zustand.ton ? '🔊' : '🔇';
  $('tonButton').classList.toggle('aus', !zustand.ton);
}

// --------------------------------------------------------------------------
// Sprachausgabe
// --------------------------------------------------------------------------

let stimme = null;

function stimmeSuchen() {
  const alle = window.speechSynthesis ? speechSynthesis.getVoices() : [];
  stimme = alle.find((s) => s.lang && s.lang.toLowerCase().startsWith('de')) || null;
}

if (window.speechSynthesis) {
  stimmeSuchen();
  speechSynthesis.onvoiceschanged = stimmeSuchen;
}

function sprich(text) {
  if (!zustand.ton || !window.speechSynthesis || !text) return;
  speechSynthesis.cancel();
  const spruch = new SpeechSynthesisUtterance(text);
  spruch.lang = 'de-DE';
  spruch.rate = 1.08;
  if (stimme) spruch.voice = stimme;

  // Waehrend die App spricht, hoert sie nicht zu — sonst nimmt sie ihre eigenen
  // Hinweise als Befehle entgegen.
  spruch.onstart = () => { hoererPause = true; };
  spruch.onend = () => { hoererPause = false; };
  spruch.onerror = () => { hoererPause = false; };

  speechSynthesis.speak(spruch);
}

function tonFreischalten() {
  // Safari gibt die Sprachausgabe erst nach einer echten Nutzeraktion frei.
  if (!window.speechSynthesis) return;
  const stumm = new SpeechSynthesisUtterance(' ');
  stumm.volume = 0;
  speechSynthesis.speak(stumm);
}

function vibriere(muster) {
  if (navigator.vibrate) navigator.vibrate(muster);
}

// --------------------------------------------------------------------------
// Einstellungen
// --------------------------------------------------------------------------

function einstellungenFuellen() {
  const e = Engine.einstellungen();
  [...$('anbieterwahl').children].forEach((k) => k.classList.toggle('aktiv', k.dataset.anbieter === e.anbieter));
  $('fGeminiKey').value = e.gemini_key;
  $('fGeminiModell').value = e.gemini_modell;
  $('fOrKey').value = e.openrouter_key;
  $('fOrModell').value = e.openrouter_modell;
  $('fServerUrl').value = e.server_url;
  $('fServerToken').value = e.server_token;
  $('fGegenprobe').value = e.gegenprobe;
  $('fSparmodus').checked = e.sparmodus;
  $('fStandardkopf').value = e.standardkopf;
  $('fStandardkopf').placeholder = (Engine.spec.koepfe || {}).standard || '';
  anbieterUmschalten(e.anbieter);
  lernregelnZeigen();
}

function anbieterUmschalten(anbieter) {
  ['gemini', 'openrouter', 'server'].forEach((name) => {
    $(`block-${name}`).hidden = name !== anbieter;
  });
}

function einstellungenSpeichern() {
  const gewaehlt = document.querySelector('#anbieterwahl .aktiv');
  Engine.einstellungenSpeichern({
    anbieter: gewaehlt ? gewaehlt.dataset.anbieter : 'gemini',
    gemini_key: $('fGeminiKey').value.trim(),
    gemini_modell: $('fGeminiModell').value.trim() || 'gemini-2.5-flash',
    openrouter_key: $('fOrKey').value.trim(),
    openrouter_modell: $('fOrModell').value.trim(),
    server_url: $('fServerUrl').value.trim(),
    server_token: $('fServerToken').value.trim(),
    gegenprobe: $('fGegenprobe').value,
    sparmodus: $('fSparmodus').checked,
    standardkopf: $('fStandardkopf').value.trim(),
  });
  $('einstellungen').hidden = true;
  startBereitschaft();
}

/** Zeigt, was die App aus deinen Rueckmeldungen abgeleitet hat. */
function lernregelnZeigen() {
  const box = $('lernregeln');
  const regeln = Engine.profil.lernregeln();
  box.innerHTML = '';

  if (!regeln.length) {
    box.innerHTML = '<p class="hinweis-text">Noch nichts abgeleitet — dafür braucht es mindestens '
      + `${Engine.spec.lernregeln.ab_sessions} gleichlautende Rückmeldungen nach dem Rauchen.</p>`;
    return;
  }

  regeln.forEach((regel) => {
    const zeile = document.createElement('div');
    zeile.className = 'lernregel';
    zeile.innerHTML = `<b>${escape(regel.titel)}</b> <small>${regel.treffer} Sessions</small>`
      + `<span>${escape(regel.anweisung)}</span>`;
    box.appendChild(zeile);
  });
}

function startBereitschaft() {
  const ok = Engine.bereit();
  $('losButton').disabled = !ok;
  $('startInfo').textContent = ok
    ? `Analyse über ${Engine.anbieterName()}`
    : 'Noch kein Zugang eingerichtet — tipp auf „Modell und Zugang".';
  const werte = Engine.profil.treffsicherheit();
  if (werte.sessions) {
    $('startInfo').textContent += ` · ${werte.sessions} Session(s) gelernt`;
  }
}

// --------------------------------------------------------------------------
// Sitzung
// --------------------------------------------------------------------------

function kontextLesen() {
  const gewaehlt = document.querySelector('#zielwahl .aktiv');
  return {
    ziel: gewaehlt ? gewaehlt.dataset.ziel : 'balanced',
    kopf_modell: $('fKopf').value.trim(),
    tabak_marke: $('fMarke').value.trim(),
    tabak_sorte: $('fSorte').value.trim(),
    hmd: $('fHmd').value.trim(),
    kohlen: $('fKohlen').value.trim(),
    notiz: $('fNotiz').value.trim(),
    aussendurchmesser_mm: $('fDurchmesser').value.trim(),
  };
}

/* Traegt den Durchmesser nach, sobald ein bekannter Kopf eingetippt wird.
 *
 * Der Maßstab ist die wichtigste Einzelangabe fuer verlaessliche Millimeter —
 * aber niemand misst freiwillig nach. Steht der Kopf in der Liste, geht es ohne.
 */
function durchmesserVorschlagen() {
  const treffer = Engine.kopfSuchen($('fKopf').value);
  if (treffer && !$('fDurchmesser').value.trim()) {
    $('fDurchmesser').value = String(treffer.aussendurchmesser_mm);
    $('fDurchmesser').classList.add('vorgeschlagen');
  }
}

function sitzungStarten() {
  zustand.sitzung = new Engine.Sitzung(kontextLesen());
  zustand.analyse = null;
  $('fortschritt').style.width = '0%';
  $('coach').textContent = 'Halt die Kamera von oben ueber den Kopf.';
  $('schritte').innerHTML = '';
  $('probleme').innerHTML = '';
  $('messwerte').innerHTML = '';
  $('rueckfrage').hidden = true;
  $('liveScore').hidden = true;
  // Angaben merken, damit man sie beim naechsten Mal nicht neu tippt. Klappt das
  // nicht (privater Modus, volles Geraet), ist das kein Grund, den Start
  // abzubrechen — deshalb hier kein ungeschuetztes setItem mehr.
  try {
    localStorage.setItem('shisha.kontext', JSON.stringify(zustand.sitzung.kontext));
  } catch (_) { /* dann eben nicht gemerkt */ }
  phasenZeichnen();
}

function kontextWiederherstellen() {
  let gespeichert = {};
  try {
    gespeichert = JSON.parse(localStorage.getItem('shisha.kontext') || '{}');
  } catch (_) { /* dann eben leer */ }

  $('fKopf').value = gespeichert.kopf_modell || '';
  $('fMarke').value = gespeichert.tabak_marke || '';
  $('fSorte').value = gespeichert.tabak_sorte || '';
  $('fHmd').value = gespeichert.hmd || '';
  $('fKohlen').value = gespeichert.kohlen || '';
  $('fDurchmesser').value = gespeichert.aussendurchmesser_mm || '';
  if (gespeichert.ziel) {
    [...$('zielwahl').children].forEach((k) => k.classList.toggle('aktiv', k.dataset.ziel === gespeichert.ziel));
  }
}

// --------------------------------------------------------------------------
// Vollanalyse
// --------------------------------------------------------------------------

/* Sammelt Bilder fuer das Endurteil.
 *
 * Aus einem Winkel bleibt die hintere Randkante verdeckt — genau dort entsteht
 * Randanbrand. Deshalb auf Wunsch drei Aufnahmen, zwischen denen der Nutzer den
 * Kopf dreht. Gewartet wird jeweils, bis das Bild ruhig und scharf ist.
 */
const WINKEL_ANSAGE = [
  'Halt drauf.',
  'Jetzt den Kopf um ein Drittel drehen.',
  'Und noch einmal drehen.',
];

/* Wartet auf ein brauchbares Bild.
 *
 * Liefert true, sobald es ruhig und scharf ist. Wird es das nicht, nehmen wir
 * das Bild nach Ablauf trotzdem — ein leicht wackliges Bild ist besser als gar
 * keins. Steht die Kamera dagegen ganz still, hat Warten keinen Zweck: dann
 * bricht die Aufnahme sofort ab, statt drei Mal acht Sekunden zu mahlen und am
 * Ende drei eingefrorene Bilder zu verschicken.
 */
async function warteAufRuhigesBild(hoechstensMs = 8000) {
  const start = Date.now();
  const bis = start + hoechstensMs;
  while (Date.now() < bis) {
    if (!kameraLaeuft()) throw new Error('Die Kamera liefert gerade kein Bild.');
    if (!video.paused && guetePruefen(Date.now() - start)) return true;
    await schlafen(180);
  }
  return false;
}

async function bilderSammeln(anzahl) {
  const bilder = [];
  for (let nummer = 1; nummer <= anzahl; nummer++) {
    if (anzahl > 1) {
      $('aufnahme').hidden = false;
      $('aufnahmeText').textContent = `Bild ${nummer} von ${anzahl} — ${WINKEL_ANSAGE[nummer - 1] || 'Halt drauf.'}`;
      sprich(WINKEL_ANSAGE[nummer - 1] || 'Halt drauf.');
      // Kurz Zeit zum Drehen, bevor wir auf Ruhe warten.
      if (nummer > 1) await schlafen(1800);
    }
    await warteAufRuhigesBild();
    bilder.push(await bildAufnehmen(1152));
    vibriere(25);
  }
  $('aufnahme').hidden = true;
  return bilder;
}

async function vollanalyse() {
  if (zustand.busy) {
    // Frueher kam hier ein stilles return: der Knopf wirkte kaputt, wenn gerade
    // eine Liverunde lief — nach einem Netzfehler bis zu drei Sekunden lang.
    setzeLage('gleich', 'denkt');
    zustand.lauf++;          // die laufende Liverunde ist damit entwertet
    await warteAufFrei(4000);
    if (zustand.busy) return;
  }
  zustand.busy = true;
  const meineSitzung = zustand.sitzung;
  const meiner = ++zustand.lauf;
  $('analyseButton').disabled = true;
  setzeLage('Vollanalyse', 'denkt');

  try {
    const anzahl = $('fWinkel').checked ? 3 : 1;
    const bilder = await bilderSammeln(anzahl);
    setzeLage('bewerte', 'denkt');

    const analyse = await meineSitzung.analysieren(bilder, 'voll');
    // Waehrend der Anfrage kann der Nutzer laengst im Hauptmenue sein oder eine
    // neue Sitzung begonnen haben. Dann draengt sich der Report nicht mehr ueber
    // den Bildschirm und landet auch nicht in der Historie einer Sitzung, die es
    // nicht mehr gibt.
    if (zustand.lauf !== meiner || zustand.sitzung !== meineSitzung) return;
    reportZeigen(analyse);
    historieMerken(analyse, bilder[0]);
    setzeLage('live', '');
  } catch (fehler) {
    $('aufnahme').hidden = true;
    setzeLage(kurz(fehler.message), 'fehler');
    $('coach').textContent = `Analyse fehlgeschlagen: ${fehler.message}`;
  } finally {
    zustand.busy = false;
    $('analyseButton').disabled = false;
    kontingentZeigen();
    // Das eben aufgenommene Bild ist der neue Bezugspunkt fuer den Sparmodus.
    // Ohne das haelt die Liveschleife den Kopf fuer veraendert und schickt
    // sofort nach dem Report noch eine Anfrage hinterher.
    if (zustand.letzteGrau) analyseMini = zustand.letzteGrau;
    // Die Rundennummer oben hat die Liveschleife beendet — hier laeuft sie
    // wieder an, sonst steht die Vorschau nach dem Report still.
    if (zustand.laeuft && !zustand.pausiert && zustand.sitzung === meineSitzung) schleife();
  }
}

// --------------------------------------------------------------------------
// Historie
// --------------------------------------------------------------------------

/** Legt das Ergebnis samt kleinem Vorschaubild auf diesem Geraet ab. */
async function historieMerken(analyse, bild) {
  try {
    const eintrag = {
      zeit: Date.now(),
      score: analyse.gesamtscore,
      stufe_text: analyse.stufe_text,
      scores: analyse.scores,
      kontext: analyse.kontext,
      kopf: (analyse.kopf || {}).modell || (analyse.kopf || {}).art || '',
      vorschau: bild ? await vorschauBauen(bild) : null,
    };
    await Engine.historie.speichern(eintrag);
  } catch (_) {
    /* ohne Historie laeuft die App genauso — kein Grund zu stoeren */
  }
}

/** Schrumpft das Bild auf Daumennagelgroesse, damit die Ablage klein bleibt. */
function vorschauBauen(blob) {
  return new Promise((fertig) => {
    const bild = new Image();
    const url = URL.createObjectURL(blob);
    bild.onload = () => {
      const flaeche = document.createElement('canvas');
      const kante = 160;
      const faktor = kante / Math.max(bild.width, bild.height);
      flaeche.width = Math.round(bild.width * faktor);
      flaeche.height = Math.round(bild.height * faktor);
      flaeche.getContext('2d').drawImage(bild, 0, 0, flaeche.width, flaeche.height);
      URL.revokeObjectURL(url);
      fertig(flaeche.toDataURL('image/jpeg', 0.6));
    };
    bild.onerror = () => { URL.revokeObjectURL(url); fertig(null); };
    bild.src = url;
  });
}

async function historieZeigen() {
  const liste = $('historieListe');
  liste.innerHTML = '<li class="leer">wird geladen …</li>';
  $('historie').hidden = false;

  let eintraege = [];
  try {
    eintraege = await Engine.historie.laden(30);
  } catch (fehler) {
    liste.innerHTML = `<li class="leer">${escape(fehler.message)}</li>`;
    return;
  }

  kurveZeichnen(eintraege);

  if (!eintraege.length) {
    liste.innerHTML = '<li class="leer">Noch keine bewerteten Köpfe.</li>';
    return;
  }

  liste.innerHTML = '';
  eintraege.forEach((eintrag) => {
    const zeile = document.createElement('li');
    const datum = new Date(eintrag.zeit).toLocaleString('de-DE',
      { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
    zeile.innerHTML =
      (eintrag.vorschau ? `<img src="${eintrag.vorschau}" alt="" />` : '<span class="kein-bild">–</span>')
      + `<span class="historie-text"><b>${eintrag.score === null ? '–' : eintrag.score}/100</b>`
      + ` ${escape(eintrag.stufe_text || '')}<small>${datum}`
      + `${eintrag.kopf ? ' · ' + escape(eintrag.kopf) : ''}</small></span>`;
    liste.appendChild(zeile);
  });
}

/* Zeichnet den Notenverlauf als schlichte Linie.
 *
 * Alt links, neu rechts — so liest sich Fortschritt in der gewohnten Richtung.
 */
function kurveZeichnen(eintraege) {
  const flaeche = $('kurve');
  const zeichner = flaeche.getContext('2d');
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  flaeche.width = flaeche.clientWidth * dpr;
  flaeche.height = flaeche.clientHeight * dpr;
  zeichner.setTransform(dpr, 0, 0, dpr, 0, 0);

  const breite = flaeche.clientWidth;
  const hoehe = flaeche.clientHeight;
  zeichner.clearRect(0, 0, breite, hoehe);

  const werte = eintraege.filter((e) => typeof e.score === 'number').map((e) => e.score).reverse();
  if (werte.length < 2) {
    zeichner.fillStyle = 'rgba(125, 149, 163, 0.8)';
    zeichner.font = '13px -apple-system, system-ui, sans-serif';
    zeichner.textAlign = 'center';
    zeichner.fillText('Ab zwei Köpfen zeigt sich hier dein Verlauf.', breite / 2, hoehe / 2);
    return;
  }

  const rand = 14;
  const x = (i) => rand + (i / (werte.length - 1)) * (breite - rand * 2);
  const y = (wert) => hoehe - rand - (wert / 100) * (hoehe - rand * 2);

  // Zielmarke bei 85 — ab da ist ein Kopf richtig gut.
  zeichner.strokeStyle = 'rgba(87, 227, 154, 0.28)';
  zeichner.setLineDash([5, 5]);
  zeichner.beginPath();
  zeichner.moveTo(rand, y(85));
  zeichner.lineTo(breite - rand, y(85));
  zeichner.stroke();
  zeichner.setLineDash([]);

  zeichner.strokeStyle = '#5fe3ff';
  zeichner.lineWidth = 2;
  zeichner.lineJoin = 'round';
  zeichner.beginPath();
  werte.forEach((wert, i) => (i ? zeichner.lineTo(x(i), y(wert)) : zeichner.moveTo(x(i), y(wert))));
  zeichner.stroke();

  werte.forEach((wert, i) => {
    zeichner.fillStyle = noteFarbe(wert);
    zeichner.beginPath();
    zeichner.arc(x(i), y(wert), 3.5, 0, Math.PI * 2);
    zeichner.fill();
  });
}

// --------------------------------------------------------------------------
// Report als Bild teilen
// --------------------------------------------------------------------------

/* Malt den Report auf eine Leinwand und schiebt ihn ins Teilen-Menue.
 *
 * Bewusst von Hand gezeichnet statt aus dem HTML geschnitten: so passt das Bild
 * ins Hochformat und enthaelt nur, was auch ohne die App verstaendlich ist.
 */
async function reportTeilen() {
  const analyse = zustand.sitzung && zustand.sitzung.analyse;
  if (!analyse) return;

  const flaeche = document.createElement('canvas');
  flaeche.width = 1080;
  flaeche.height = 1350;
  const z = flaeche.getContext('2d');

  z.fillStyle = '#04080d';
  z.fillRect(0, 0, flaeche.width, flaeche.height);

  const score = analyse.gesamtscore;
  const farbe = score === null ? '#7d95a3' : noteFarbe(score);

  // Notenring
  const mx = flaeche.width / 2;
  const my = 320;
  z.lineWidth = 26;
  z.strokeStyle = 'rgba(255,255,255,0.09)';
  z.beginPath();
  z.arc(mx, my, 150, 0, Math.PI * 2);
  z.stroke();
  z.strokeStyle = farbe;
  z.lineCap = 'round';
  z.beginPath();
  z.arc(mx, my, 150, -Math.PI / 2, -Math.PI / 2 + ((score || 0) / 100) * Math.PI * 2);
  z.stroke();

  z.fillStyle = farbe;
  z.textAlign = 'center';
  z.font = '700 96px -apple-system, system-ui, sans-serif';
  z.fillText(score === null ? '–' : String(score), mx, my + 26);
  z.font = '500 26px -apple-system, system-ui, sans-serif';
  z.fillStyle = '#7d95a3';
  z.fillText('von 100', mx, my + 70);

  z.fillStyle = '#dceef6';
  z.font = '600 44px -apple-system, system-ui, sans-serif';
  z.fillText(analyse.stufe_text || 'nicht bewertbar', mx, my + 150);

  // Kategorien als Balken
  let y = my + 240;
  z.textAlign = 'left';
  Object.entries(analyse.scores || {}).forEach(([key, wert]) => {
    z.fillStyle = '#7d95a3';
    z.font = '500 28px -apple-system, system-ui, sans-serif';
    z.fillText(Engine.spec.kategorien[key] || key, 90, y);

    z.fillStyle = 'rgba(255,255,255,0.09)';
    z.fillRect(480, y - 20, 440, 14);
    z.fillStyle = noteFarbe(wert);
    z.fillRect(480, y - 20, 440 * (wert / 100), 14);

    z.fillStyle = '#dceef6';
    z.textAlign = 'right';
    z.fillText(String(wert), 990, y);
    z.textAlign = 'left';
    y += 56;
  });

  // Wichtigste Probleme
  y += 24;
  z.fillStyle = '#7d95a3';
  z.font = '500 24px -apple-system, system-ui, sans-serif';
  z.fillText('ERKANNTE PROBLEME', 90, y);
  y += 46;
  (analyse.probleme || []).slice(0, 4).forEach((problem) => {
    z.fillStyle = problem.severity === 'critical' || problem.severity === 'high' ? '#ff6b7d'
      : problem.severity === 'medium' ? '#ffb454' : '#57e39a';
    z.font = '600 30px -apple-system, system-ui, sans-serif';
    z.fillText(`• ${problem.titel}`.slice(0, 44), 90, y);
    y += 46;
  });
  if (!(analyse.probleme || []).length) {
    z.fillStyle = '#57e39a';
    z.font = '600 30px -apple-system, system-ui, sans-serif';
    z.fillText('• keine', 90, y);
  }

  z.fillStyle = '#2b8fae';
  z.font = '500 24px -apple-system, system-ui, sans-serif';
  z.textAlign = 'center';
  z.fillText('Hookah Analyzer', mx, flaeche.height - 60);

  const blob = await new Promise((fertig) => flaeche.toBlob(fertig, 'image/png'));
  const datei = new File([blob], 'kopf-bewertung.png', { type: 'image/png' });

  // Teilen geht nicht ueberall — dann eben herunterladen.
  if (navigator.canShare && navigator.canShare({ files: [datei] })) {
    try {
      await navigator.share({ files: [datei], title: 'Meine Kopf-Bewertung' });
      return;
    } catch (_) {
      return;   // abgebrochen ist kein Fehler
    }
  }

  const url = URL.createObjectURL(blob);
  const verweis = document.createElement('a');
  verweis.href = url;
  verweis.download = 'kopf-bewertung.png';
  verweis.click();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}

function reportZeigen(analyse) {
  const score = analyse.gesamtscore;
  const farbe = score === null ? '#7d95a3' : noteFarbe(score);

  $('noteZahl').textContent = score === null ? '–' : score;
  $('noteZahl').style.color = farbe;
  const kreis = $('noteKreis');
  kreis.style.strokeDashoffset = String(327 * (1 - (score || 0) / 100));
  kreis.style.stroke = farbe;

  $('stufeText').textContent = analyse.stufe_text || 'nicht bewertbar';

  const kopf = analyse.kopf || {};
  const tabak = analyse.tabak || {};
  const teile = [];
  if (kopf.modell || kopf.art !== 'unbekannt') teile.push(kopfBeschriftung(kopf));
  if (tabak.fuellhoehe_mm !== null) {
    teile.push(tabak.fuellhoehe_mm < 0
      ? `${Math.abs(tabak.fuellhoehe_mm)} mm ueber Rand`
      : `${tabak.fuellhoehe_mm} mm unter Rand`);
  }
  if (tabak.dichte) teile.push(`Dichte ${tabak.dichte}/100`);
  if (analyse.hmd && analyse.hmd.erkannt) teile.push(`HMD ${analyse.hmd.modell || 'erkannt'}`);
  if (analyse.kohle && analyse.kohle.status === 'visible' && analyse.kohle.anzahl) {
    teile.push(`${analyse.kohle.anzahl} Kohlen`);
  }
  $('erkanntes').textContent = teile.join(' · ') || 'nichts sicher erkennbar';

  const prognose = analyse.prognose || {};
  $('prognoseZeile').textContent =
    score !== null && prognose.score_nach_optimierung > score
      ? `nach Optimierung ${prognose.score_nach_optimierung}/100`
      : score !== null ? 'kaum noch Luft nach oben' : '';

  const frage = $('reportFrage');
  frage.hidden = !analyse.rueckfrage;
  frage.textContent = analyse.rueckfrage || '';

  // Was das Modell gesehen hat, bevor es geurteilt hat.
  $('befund').textContent = analyse.befund || 'kein Befund geliefert';

  const kategorien = $('kategorien');
  kategorien.innerHTML = '';
  const ausgenommen = analyse.nicht_bewertbar || [];
  Object.entries(analyse.scores || {}).forEach(([key, wert]) => {
    const zeile = document.createElement('div');
    zeile.className = 'kat';
    // In fruehen Bauphasen gibt es manche Kategorien noch gar nicht. Sie stehen
    // trotzdem da, aber ohne Zahl — eine 0 waere hier eine Behauptung.
    if (ausgenommen.includes(key)) {
      zeile.classList.add('kat-offen');
      zeile.innerHTML =
        `<span class="kat-name">${escape(Engine.spec.kategorien[key] || key)}</span>`
        + '<span class="kat-leiste"></span>'
        + '<span class="kat-zahl">–</span>';
      zeile.title = 'in dieser Phase noch nicht beurteilbar — zaehlt nicht zur Note';
      kategorien.appendChild(zeile);
      return;
    }
    zeile.innerHTML =
      `<span class="kat-name">${escape(Engine.spec.kategorien[key] || key)}</span>` +
      `<span class="kat-leiste"><span class="kat-fuell" style="width:${wert}%;background:${noteFarbe(wert)}"></span></span>` +
      `<span class="kat-zahl">${wert}</span>`;
    kategorien.appendChild(zeile);
  });

  // Wo die App die Bewertung des Modells heruntergesetzt hat, und warum.
  const kappungen = $('kappungen');
  kappungen.innerHTML = '';
  (analyse.kappungen || []).forEach((kappung) => {
    const zeile = document.createElement('div');
    zeile.className = 'kappung';
    zeile.textContent =
      `${Engine.spec.kategorien[kappung.kategorie] || kappung.kategorie}: `
      + `${kappung.von} → ${kappung.auf}, weil ${kappung.grund}`;
    kappungen.appendChild(zeile);
  });

  const probleme = $('problemliste');
  probleme.innerHTML = '';
  (analyse.probleme || []).forEach((problem) => {
    const zeile = document.createElement('li');
    zeile.className = problem.severity;
    zeile.innerHTML =
      `<div class="problem-kopf"><span class="problem-titel">${problem.symbol} ${escape(problem.titel)}</span>` +
      `<span class="problem-conf">${problem.confidence}%</span></div>` +
      `<div class="problem-text">${escape(problem.beschreibung)}</div>`;
    probleme.appendChild(zeile);
  });
  if (!(analyse.probleme || []).length) {
    probleme.innerHTML = '<li class="low"><div class="problem-titel">🟢 Keine Probleme erkannt</div></li>';
  }

  const plan = $('planliste');
  plan.innerHTML = '';
  (analyse.optimierungen || []).forEach((schritt) => {
    const zeile = document.createElement('li');
    zeile.innerHTML =
      `<span class="plan-meta">${escape(schritt.aktion_text)}${schritt.bereich ? ' · ' + escape(schritt.bereich) : ''}</span>` +
      escape(schritt.text) +
      (schritt.wirkung ? `<span class="plan-wirkung">→ ${escape(schritt.wirkung)}</span>` : '');
    plan.appendChild(zeile);
  });
  if (!(analyse.optimierungen || []).length) plan.innerHTML = '<li>Nichts mehr zu tun — Kohle drauf.</li>';

  const erwartung = $('erwartung');
  erwartung.innerHTML = '';
  [['Geschmack', prognose.geschmack], ['Rauch', prognose.rauch],
   ['Sessiondauer', prognose.dauer], ['Hitzerisiko', prognose.hitzerisiko]]
    .forEach(([name, richtung]) => {
      const feld = document.createElement('div');
      const klasse = richtung === 'hoch' ? 'pfeil-hoch' : richtung === 'runter' ? 'pfeil-runter' : 'pfeil-gleich';
      // Beim Hitzerisiko ist runter das Gute — Farbe entsprechend drehen.
      const gedreht = name === 'Hitzerisiko'
        ? (richtung === 'hoch' ? 'pfeil-runter' : richtung === 'runter' ? 'pfeil-hoch' : klasse)
        : klasse;
      feld.innerHTML = `<span>${name}</span><b class="${gedreht}">${PFEIL[richtung] || '→'}</b>`;
      erwartung.appendChild(feld);
    });

  const konfidenz = $('konfidenz');
  konfidenz.innerHTML = '';
  Object.entries(analyse.confidence || {}).forEach(([key, wert]) => {
    const feld = document.createElement('span');
    feld.className = `konf${wert < 50 ? ' niedrig' : ''}`;
    feld.innerHTML = `${KONF_NAMEN[key] || key} <b>${wert}%</b>`;
    konfidenz.appendChild(feld);
  });

  gegenprobeZeigen(analyse.gegenprobe);
  vergleichZeigen(analyse.vergleich);

  $('report').hidden = false;
  $('report').scrollTop = 0;
  sprich(score !== null
    ? `${score} von 100, ${analyse.stufe_text}. ${analyse.coach_satz || ''}`
    : analyse.coach_satz || 'Das Bild reicht fuer eine Bewertung nicht aus.');
  vibriere([40, 80, 40]);
}

/** Was das zweite Modell gesagt hat — und ob die beiden sich einig sind. */
function gegenprobeZeigen(gegenprobe) {
  const block = $('gegenprobe');
  block.hidden = !gegenprobe;
  if (!gegenprobe) return;

  if (gegenprobe.fehler) {
    block.className = 'gegenprobe warn';
    block.textContent = `Gegenprobe nicht möglich: ${gegenprobe.fehler}`;
    return;
  }

  block.className = `gegenprobe ${gegenprobe.einig ? 'einig' : 'uneinig'}`;
  const strittig = (gegenprobe.strittig || []).map((k) => k.name).join(', ');
  block.innerHTML = gegenprobe.einig
    ? `<b>Zweites Modell: ${gegenprobe.score}/100</b> — die beiden sind sich einig `
      + `(${gegenprobe.abweichung} Punkte auseinander).`
    : `<b>Zweites Modell: ${gegenprobe.score}/100</b> — ${gegenprobe.abweichung} Punkte Unterschied. `
      + `Die Bewertung ist unsicher${strittig ? `, strittig ist vor allem: ${escape(strittig)}` : ''}.`;
}

/** Gegenüberstellung von vorher und nachher, nach einer Korrektur. */
function vergleichZeigen(vergleich) {
  const block = $('vergleich');
  block.hidden = !vergleich;
  if (!vergleich) return;

  const pfeil = (delta) => (delta > 0 ? `+${delta}` : String(delta));
  const zeilen = vergleich.kategorien
    .filter((k) => k.delta !== 0)
    .sort((a, b) => b.delta - a.delta)
    .map((k) => `<div class="vergleich-zeile ${k.delta > 0 ? 'besser' : 'schlechter'}">`
      + `<span>${escape(k.name)}</span><b>${k.von} → ${k.auf} (${pfeil(k.delta)})</b></div>`)
    .join('');

  const kopf = vergleich.delta === null
    ? 'Der neue Kopf ließ sich nicht bewerten.'
    : `<b>${vergleich.von} → ${vergleich.auf}</b> (${pfeil(vergleich.delta)} Punkte)`;

  const prognose = vergleich.prognose_abweichung === null ? ''
    : `<div class="vergleich-fuss">Vorhergesagt waren ${vergleich.versprochen} — `
      + `${vergleich.prognose_abweichung <= 5 ? 'gut getroffen' : `${vergleich.prognose_abweichung} Punkte daneben`}.</div>`;

  block.innerHTML = `<div class="vergleich-kopf">${kopf}</div>${zeilen || '<div class="vergleich-zeile">nichts hat sich verändert</div>'}${prognose}`;
}

function escape(text) {
  const feld = document.createElement('div');
  feld.textContent = text === null || text === undefined ? '' : text;
  return feld.innerHTML;
}

// --------------------------------------------------------------------------
// Session-Feedback
// --------------------------------------------------------------------------

function skalenBauen() {
  document.querySelectorAll('.skala').forEach((zeile) => {
    const knoepfe = zeile.querySelector('.skala-knoepfe');
    for (let wert = 1; wert <= 5; wert++) {
      const knopf = document.createElement('button');
      knopf.textContent = String(wert);
      knopf.onclick = () => {
        zustand.feedback[zeile.dataset.feld] = wert;
        [...knoepfe.children].forEach((k) => k.classList.toggle('aktiv', k === knopf));
      };
      knoepfe.appendChild(knopf);
    }
  });
}

function feedbackSpeichern() {
  const eintrag = { ...zustand.feedback };
  const dauer = parseInt($('fDauer').value, 10);
  if (Number.isFinite(dauer)) eintrag.dauer_min = Math.max(0, Math.min(600, dauer));
  const notiz = $('fFeedbackNotiz').value.trim();
  if (notiz) eintrag.notiz = notiz.slice(0, 300);

  if (!Object.keys(eintrag).length) {
    $('treffsicherheit').textContent = 'Noch nichts ausgewaehlt.';
    return;
  }

  const sitzung = zustand.sitzung;
  if (sitzung) {
    eintrag.ziel = sitzung.kontext.ziel;
    eintrag.kopf_modell = sitzung.kontext.kopf_modell;
    eintrag.tabak = `${sitzung.kontext.tabak_marke} ${sitzung.kontext.tabak_sorte}`.trim();
    if (sitzung.analyse && sitzung.analyse.gesamtscore !== null) {
      eintrag.score = sitzung.analyse.gesamtscore;
    }
  }

  Engine.profil.merken(eintrag);
  treffsicherheitZeigen();
  $('feedback').hidden = true;
  zustand.feedback = {};
  $('fDauer').value = '';
  $('fFeedbackNotiz').value = '';
  document.querySelectorAll('.skala-knoepfe button').forEach((k) => k.classList.remove('aktiv'));
}

function treffsicherheitZeigen() {
  const werte = Engine.profil.treffsicherheit();
  if (!werte.sessions) {
    $('treffsicherheit').textContent = 'Noch keine gespeicherten Sessions.';
    return;
  }
  const teile = [`${werte.sessions} Session(s) gespeichert`];
  if (werte.genauigkeit !== null) teile.push(`Vorhersagegenauigkeit ${werte.genauigkeit}%`);
  $('treffsicherheit').textContent = teile.join(' · ');
}

// --------------------------------------------------------------------------
// Verdrahtung
// --------------------------------------------------------------------------

$('zielwahl').addEventListener('click', (ereignis) => {
  const knopf = ereignis.target.closest('[data-ziel]');
  if (!knopf) return;
  [...$('zielwahl').children].forEach((k) => k.classList.toggle('aktiv', k === knopf));
});

$('anbieterwahl').addEventListener('click', (ereignis) => {
  const knopf = ereignis.target.closest('[data-anbieter]');
  if (!knopf) return;
  [...$('anbieterwahl').children].forEach((k) => k.classList.toggle('aktiv', k === knopf));
  anbieterUmschalten(knopf.dataset.anbieter);
});

$('zugangButton').addEventListener('click', () => {
  einstellungenFuellen();
  $('einstellungen').hidden = false;
});

$('zugangSpeichern').addEventListener('click', einstellungenSpeichern);
$('zugangAbbruch').addEventListener('click', () => { $('einstellungen').hidden = true; });

$('losButton').addEventListener('click', async () => {
  const knopf = $('losButton');
  knopf.disabled = true;
  knopf.textContent = 'starte …';
  $('startFehler').textContent = '';
  try {
    tonFreischalten();
    await kameraStarten();
    sitzungStarten();
    overlayAnpassen();
    bildschirmWachhalten();

    $('start').hidden = true;
    $('oben').hidden = false;
    $('unten').hidden = false;
    zustand.laeuft = true;
    zustand.blindSeit = 0;
    setzeLage('live', '');
    schleife();

    // Sprachsteuerung nur anbieten, wenn der Browser sie kann.
    $('hoerButton').hidden = !spracheMoeglich();
    if (spracheMoeglich() && $('fSprache').checked) hoerenAn();
    kontingentZeigen();
  } catch (fehler) {
    $('startFehler').textContent = fehler.message;
    knopf.disabled = false;
  } finally {
    knopf.textContent = 'Kamera starten';
  }
});

$('tonButton').addEventListener('click', () => {
  zustand.ton = !zustand.ton;
  tonAnzeigen();
  if (!zustand.ton && window.speechSynthesis) speechSynthesis.cancel();
});

$('hoerButton').addEventListener('click', () => {
  if (zustand.hoeren) hoerenAus();
  else hoerenAn();
});

$('weiterKamera').addEventListener('click', fortsetzen);
$('pauseMenue').addEventListener('click', zumHauptmenue);
$('menueButton').addEventListener('click', () => {
  if (confirm('Zurück ins Hauptmenü? Die laufende Sitzung wird beendet.')) zumHauptmenue();
});

$('weiterButton').addEventListener('click', () => {
  zustand.sitzung.weiter();
  fortschrittZeichnen();
  neuBeurteilen();
});

$('analyseButton').addEventListener('click', vollanalyse);

$('neuButton').addEventListener('click', () => {
  if (confirm('Neuen Kopf anfangen?')) sitzungStarten();
});

$('zurueckButton').addEventListener('click', () => { $('report').hidden = true; });

$('nochmalButton').addEventListener('click', () => {
  $('report').hidden = true;
  sitzungStarten();
});

$('feedbackButton').addEventListener('click', () => {
  treffsicherheitZeigen();
  $('feedback').hidden = false;
});

$('nachmessenButton').addEventListener('click', () => {
  $('report').hidden = true;
  sprich('Zeig mir den Kopf noch einmal.');
  vollanalyse();
});

$('teilenButton').addEventListener('click', reportTeilen);
$('historieButton').addEventListener('click', historieZeigen);
$('historieZu').addEventListener('click', () => { $('historie').hidden = true; });

$('historieLeeren').addEventListener('click', async () => {
  if (!confirm('Die gesamte Historie löschen?')) return;
  try {
    await Engine.historie.leeren();
    historieZeigen();
  } catch (fehler) {
    $('historieListe').innerHTML = `<li class="leer">${escape(fehler.message)}</li>`;
  }
});

$('fKopf').addEventListener('change', durchmesserVorschlagen);
$('fKopf').addEventListener('blur', durchmesserVorschlagen);

$('feedbackAbbruch').addEventListener('click', () => { $('feedback').hidden = true; });
$('feedbackSenden').addEventListener('click', feedbackSpeichern);

// --------------------------------------------------------------------------
// Start
// --------------------------------------------------------------------------

skalenBauen();
overlayAnpassen();
requestAnimationFrame(zeichnen);

Engine.specLaden()
  .then(() => {
    zielwahlFuellen();
    kopflisteFuellen();
    kontextWiederherstellen();
    startBereitschaft();
  })
  .catch((fehler) => {
    $('startFehler').textContent = `Regelwerk konnte nicht geladen werden: ${fehler.message}`;
  });

/** Fuellt die Vorschlagsliste fuer das Kopfmodell aus spec.json. */
function kopflisteFuellen() {
  const liste = $('kopfliste');
  liste.innerHTML = '';
  ((Engine.spec.koepfe || {}).liste || []).forEach((kopf) => {
    const eintrag = document.createElement('option');
    eintrag.value = kopf.name;
    eintrag.label = `${kopf.aussendurchmesser_mm} mm`;
    liste.appendChild(eintrag);
  });
}

function zielwahlFuellen() {
  const box = $('zielwahl');
  box.innerHTML = '';
  Object.entries(Engine.spec.ziele).forEach(([key, ziel], index) => {
    const knopf = document.createElement('button');
    knopf.className = `chip${index === 0 ? ' aktiv' : ''}`;
    knopf.dataset.ziel = key;
    knopf.textContent = ziel.name.replace('Maximaler ', '').replace('Ausgewogen', 'ausgewogen');
    knopf.title = ziel.prioritaet;
    box.appendChild(knopf);
  });
}

// Die App als Ganzes offline verfuegbar halten.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').catch(() => { /* geht auch ohne */ });
  });
}
