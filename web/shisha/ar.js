/* Hookah Analyzer — Kamera, AR-Overlay, Live-Analyse und Report.
 *
 * Ablauf: Livebild zeigen, warten bis das Handy ruhig und scharf ist, dann ein
 * Einzelbild an den Server schicken. Waehrend die Analyse laeuft, zeichnet das
 * Overlay das letzte Ergebnis weiter — so ruckelt nichts.
 *
 * Die Marker kommen als normalisierte Bildkoordinaten (0 bis 1) zurueck und
 * werden hier auf den sichtbaren Bildausschnitt umgerechnet. Das Video liegt mit
 * object-fit: cover im Bild, also wird links/rechts oder oben/unten beschnitten —
 * genau das rechnet bildAufBildschirm() heraus.
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
  remove: '−',
  loosen: '≋',
  distribute: '↔',
  ok: '✓',
  fill_height: '⎯',
  hmd: '◎',
  coal: '●',
};

const KATEGORIE_NAMEN = {
  tabak_verteilung: 'Tabakverteilung',
  fuellhoehe: 'Fuellhoehe',
  airflow: 'Airflow',
  hitzemanagement: 'Hitzemanagement',
  kopfgeometrie: 'Kopfgeometrie',
  tabak_kompatibilitaet: 'Kompatibilitaet',
  zielerreichung: 'Zielerreichung',
};

const KONF_NAMEN = {
  gesamt: 'Gesamt',
  kopf_erkennung: 'Kopf',
  tabak_analyse: 'Tabak',
  fuellhoehe: 'Fuellhoehe',
  airflow: 'Airflow',
  hitzemanagement: 'Hitze',
  optimierung: 'Optimierung',
};

const PFEIL = { hoch: '↑', gleich: '→', runter: '↓' };

// Ab diesen Werten gilt das Bild als brauchbar.
const RUHE_SCHWELLE = 7.0;     // mittlere Pixelaenderung zwischen zwei Miniaturen
const SCHAERFE_SCHWELLE = 6.0; // Kantenstaerke in der Miniatur
const HELL_MIN = 34;           // darunter ist es zu dunkel
const PAUSE_MS = 900;          // Verschnaufpause zwischen zwei Analysen

const zustand = {
  sitzung: null,
  laeuft: false,
  busy: false,
  ton: true,
  analyse: null,
  phasen: [],
  phase: null,
  puls: 0,
  wakeLock: null,
  feedback: {},
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

// Vollbild fuer den Upload.
const shot = document.createElement('canvas');
const shotCtx = shot.getContext('2d');

async function kameraStarten() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    throw new Error(
      'Die Kamera ist nicht freigegeben. Das passiert, wenn die Seite ohne HTTPS ' +
      'geladen wird — ruf sie ueber https://… auf.'
    );
  }
  const stream = await navigator.mediaDevices.getUserMedia({
    video: {
      facingMode: { ideal: 'environment' },
      width: { ideal: 1920 },
      height: { ideal: 1080 },
    },
    audio: false,
  });
  video.srcObject = stream;
  await video.play();
}

async function bildschirmWachhalten() {
  try {
    if ('wakeLock' in navigator) zustand.wakeLock = await navigator.wakeLock.request('screen');
  } catch (_) {
    /* nicht schlimm — dann geht das Display eben irgendwann aus */
  }
}

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && zustand.laeuft && !zustand.wakeLock) {
    bildschirmWachhalten();
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

  let bewegung = 999;
  if (letzteMini) {
    let diff = 0;
    for (let p = 0; p < grau.length; p++) diff += Math.abs(grau[p] - letzteMini[p]);
    bewegung = diff / grau.length;
  }
  letzteMini = grau;

  let kanten = 0;
  for (let y = 1; y < mini.height - 1; y++) {
    for (let x = 1; x < mini.width - 1; x++) {
      const p = y * mini.width + x;
      kanten += Math.abs(grau[p] * 2 - grau[p - 1] - grau[p + 1]);
      kanten += Math.abs(grau[p] * 2 - grau[p - mini.width] - grau[p + mini.width]);
    }
  }

  return {
    bewegung,
    schaerfe: kanten / (mini.width * mini.height),
    helligkeit: summe / grau.length,
  };
}

function guetePruefen() {
  const guete = bildGuete();
  if (!guete) return false;
  let problem = '';
  if (guete.helligkeit < HELL_MIN) problem = 'zu dunkel';
  else if (guete.bewegung > RUHE_SCHWELLE) problem = 'halt still';
  else if (guete.schaerfe < SCHAERFE_SCHWELLE) problem = 'unscharf';
  if (problem) {
    setzeLage(problem, 'warn');
    return false;
  }
  return true;
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

async function senden(pfad, blob, extra = {}) {
  const daten = new FormData();
  daten.append('bild', blob, 'kopf.jpg');
  const frage = new URLSearchParams({ id: zustand.sitzung || '', ...extra });
  const antwort = await fetch(`${pfad}?${frage}`, { method: 'POST', body: daten });
  const inhalt = await antwort.json().catch(() => ({ ok: false, fehler: 'Antwort unlesbar' }));
  if (!antwort.ok || !inhalt.ok) throw new Error(inhalt.fehler || `Server: ${antwort.status}`);
  return inhalt;
}

async function schleife() {
  while (zustand.laeuft) {
    if (zustand.busy || !guetePruefen()) {
      await schlafen(220);
      continue;
    }

    zustand.busy = true;
    setzeLage('analysiere', 'denkt');
    try {
      const blob = await bildAufnehmen(1024);
      const inhalt = await senden('/api/shisha/live', blob, { phase: zustand.phase || '' });
      zustand.sitzung = inhalt.sitzung_id;
      liveUebernehmen(inhalt.analyse);
    } catch (fehler) {
      setzeLage(kurz(fehler.message), 'fehler');
      await schlafen(2500);
    } finally {
      zustand.busy = false;
    }
    await schlafen(PAUSE_MS);
  }
}

const schlafen = (ms) => new Promise((fertig) => setTimeout(fertig, ms));
const kurz = (text) => (text || 'Fehler').slice(0, 44);

// --------------------------------------------------------------------------
// Live-Ergebnis in die Oberflaeche
// --------------------------------------------------------------------------

function liveUebernehmen(analyse) {
  zustand.analyse = analyse;
  zustand.phase = analyse.phase || zustand.phase;

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
    ? [kopf.modell || (kopf.art !== 'unbekannt' ? kopf.art : 'Kopf erkannt'),
       quellenKuerzel(kopf.quelle)].filter(Boolean).join(' · ')
    : (analyse.analysis_status === 'insufficient_image' ? 'Bild zu schlecht' : 'kein Kopf im Bild');

  const scoreFeld = $('liveScore');
  scoreFeld.hidden = analyse.gesamtscore === null;
  if (analyse.gesamtscore !== null) scoreFeld.textContent = `${analyse.gesamtscore}/100`;

  $('fortschritt').style.width = `${Math.round((analyse.fortschritt || 0) * 100)}%`;
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
    const hoehe = tabak.fuellhoehe_mm;
    werte.push([
      'Hoehe',
      hoehe < 0 ? `${Math.abs(hoehe)} mm ueber Rand` : `${hoehe} mm unter Rand`,
      tabak.fuellhoehe_quelle,
    ]);
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
    feld.innerHTML = `${name} <b>${wert}</b>${quelle ? ` ${quellenKuerzel(quelle)}` : ''}`;
    box.appendChild(feld);
  });
}

function quellenKuerzel(quelle) {
  if (quelle === 'observed') return 'gesehen';
  if (quelle === 'estimated') return 'geschaetzt';
  if (quelle === 'unknown') return 'unsicher';
  return '';
}

function setzeLage(text, art) {
  const feld = $('lage');
  feld.textContent = text;
  feld.className = `marke ${art || ''}`;
}

function phasenZeichnen() {
  const box = $('phasen');
  box.innerHTML = '';
  const aktuell = zustand.phasen.findIndex((p) => p.key === zustand.phase);
  zustand.phasen.forEach((phase, index) => {
    const knopf = document.createElement('button');
    knopf.className = 'phase';
    if (index === aktuell) knopf.classList.add('aktiv');
    else if (aktuell >= 0 && index < aktuell) knopf.classList.add('erledigt');
    knopf.textContent = phase.name;
    knopf.title = phase.ziel;
    knopf.onclick = () => phaseSetzen(phase.key);
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
  const versatzX = (cw - vw * skala) / 2;
  const versatzY = (ch - vh * skala) / 2;
  return { x: versatzX + nx * vw * skala, y: versatzY + ny * vh * skala, skala, vw, vh };
}

function zeichnen() {
  requestAnimationFrame(zeichnen);
  if (!zustand.laeuft) return;

  const breite = overlay.clientWidth;
  const hoehe = overlay.clientHeight;
  ctx.clearRect(0, 0, breite, hoehe);
  zustand.puls += 0.05;

  const analyse = zustand.analyse;
  const ok = analyse && analyse.analysis_status === 'ok';

  zeichneSucher(breite / 2, hoehe * 0.44, Math.min(breite, hoehe) * 0.32, ok);

  if (!ok) {
    zeichneMitteltext(
      breite / 2,
      hoehe * 0.44 + Math.min(breite, hoehe) * 0.32 + 34,
      analyse && analyse.analysis_status === 'insufficient_image'
        ? 'Naeher ran, ruhiger halten, mehr Licht'
        : 'Kopf von oben ins Bild holen'
    );
    return;
  }

  (analyse.ar_marker || []).forEach((marker) => zeichneMarker(marker));
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

function zeichneMarker(marker) {
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
    ctx.setLineDash([]);
    ctx.restore();
    zeichneEtikett(oben.x + breite / 2, y - 14, marker, farbe);
    return;
  }

  const auffaellig = marker.typ !== 'ok';
  const radius = Math.min(14, breite / 3, hoehe / 3);

  ctx.strokeStyle = farbe;
  ctx.lineWidth = auffaellig ? 2.5 : 2;
  ctx.globalAlpha = auffaellig ? 0.95 : 0.7;
  if (auffaellig) {
    ctx.shadowColor = farbe;
    ctx.shadowBlur = 14;
    ctx.setLineDash([9, 6]);
    ctx.lineDashOffset = -zustand.puls * 10;
  }
  rundesRechteck(oben.x, oben.y, breite, hoehe, radius);
  ctx.stroke();

  ctx.setLineDash([]);
  ctx.globalAlpha = auffaellig ? 0.14 + Math.sin(zustand.puls * 2) * 0.04 : 0.08;
  ctx.fillStyle = farbe;
  ctx.fill();
  ctx.restore();

  zeichneEtikett(oben.x + breite / 2, oben.y - 12, marker, farbe);
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

function zeichneEtikett(x, y, marker, farbe) {
  const text = `${MARKER_SYMBOL[marker.typ] || ''} ${marker.label || ''}`.trim();
  if (!text) return;

  ctx.save();
  ctx.font = '600 13px -apple-system, system-ui, sans-serif';
  const breite = ctx.measureText(text).width + 16;
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
  ctx.fillText(text, links + breite / 2, oben + hoehe / 2 + 0.5);
  ctx.restore();
}

function zeichneScore(x, y, score) {
  const radius = 26;
  const farbe = score >= 85 ? '#57e39a' : score >= 70 ? '#5fe3ff' : score >= 50 ? '#ffb454' : '#ff6b7d';

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

function zeichneMitteltext(x, y, text) {
  ctx.save();
  ctx.fillStyle = 'rgba(220, 238, 246, 0.85)';
  ctx.font = '500 14px -apple-system, system-ui, sans-serif';
  ctx.textAlign = 'center';
  ctx.shadowColor = 'rgba(0, 0, 0, 0.85)';
  ctx.shadowBlur = 6;
  ctx.fillText(text, x, y);
  ctx.restore();
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
// Sitzung und Kontext
// --------------------------------------------------------------------------

function kontextLesen() {
  const gewaehlt = document.querySelector('#zielwahl .aktiv');
  return {
    ziel: gewaehlt ? gewaehlt.dataset.ziel : 'balanced',
    kopf_modell: $('fKopf').value,
    tabak_marke: $('fMarke').value,
    tabak_sorte: $('fSorte').value,
    hmd: $('fHmd').value,
    kohlen: $('fKohlen').value,
    notiz: $('fNotiz').value,
  };
}

async function sitzungStarten() {
  const antwort = await fetch('/api/shisha/sitzung', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id: zustand.sitzung, kontext: kontextLesen() }),
  });
  const inhalt = await antwort.json();
  zustand.sitzung = inhalt.sitzung.id;
  zustand.phasen = inhalt.sitzung.phasen;
  zustand.phase = inhalt.sitzung.phase;
  zustand.analyse = null;
  $('fortschritt').style.width = '0%';
  $('coach').textContent = 'Halt die Kamera von oben ueber den Kopf.';
  $('schritte').innerHTML = '';
  $('probleme').innerHTML = '';
  $('messwerte').innerHTML = '';
  $('rueckfrage').hidden = true;
  $('liveScore').hidden = true;
  phasenZeichnen();
}

async function phaseSetzen(key, weiter = false) {
  const antwort = await fetch('/api/shisha/phase', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id: zustand.sitzung, phase: key, weiter }),
  });
  const inhalt = await antwort.json();
  zustand.phase = inhalt.sitzung.phase;
  $('fortschritt').style.width = `${Math.round(inhalt.sitzung.fortschritt * 100)}%`;
  phasenZeichnen();
}

// --------------------------------------------------------------------------
// Vollanalyse
// --------------------------------------------------------------------------

async function vollanalyse() {
  if (zustand.busy) return;
  zustand.busy = true;
  $('analyseButton').disabled = true;
  setzeLage('Vollanalyse', 'denkt');
  try {
    const blob = await bildAufnehmen(1280);
    const inhalt = await senden('/api/shisha/analyse', blob);
    reportZeigen(inhalt.analyse);
  } catch (fehler) {
    setzeLage(kurz(fehler.message), 'fehler');
    $('coach').textContent = `Analyse fehlgeschlagen: ${fehler.message}`;
  } finally {
    zustand.busy = false;
    $('analyseButton').disabled = false;
  }
}

function reportZeigen(analyse) {
  const score = analyse.gesamtscore;
  const farbe = score === null ? '#7d95a3'
    : score >= 85 ? '#57e39a' : score >= 70 ? '#5fe3ff' : score >= 50 ? '#ffb454' : '#ff6b7d';

  $('noteZahl').textContent = score === null ? '–' : score;
  $('noteZahl').style.color = farbe;
  const kreis = $('noteKreis');
  kreis.style.strokeDashoffset = String(327 * (1 - (score || 0) / 100));
  kreis.style.stroke = farbe;

  $('stufeText').textContent = analyse.stufe_text || 'nicht bewertbar';

  const kopf = analyse.kopf || {};
  const tabak = analyse.tabak || {};
  const teile = [];
  if (kopf.modell || kopf.art !== 'unbekannt') teile.push(kopf.modell || kopf.art);
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

  // Kategorien
  const kategorien = $('kategorien');
  kategorien.innerHTML = '';
  Object.entries(analyse.scores || {}).forEach(([key, wert]) => {
    const zeile = document.createElement('div');
    zeile.className = 'kat';
    const balkenFarbe = wert >= 80 ? '#57e39a' : wert >= 60 ? '#5fe3ff' : wert >= 40 ? '#ffb454' : '#ff6b7d';
    zeile.innerHTML =
      `<span class="kat-name">${KATEGORIE_NAMEN[key] || key}</span>` +
      `<span class="kat-leiste"><span class="kat-fuell" style="width:${wert}%;background:${balkenFarbe}"></span></span>` +
      `<span class="kat-zahl">${wert}</span>`;
    kategorien.appendChild(zeile);
  });

  // Probleme
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

  // Optimierungsplan
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
  if (!(analyse.optimierungen || []).length) {
    plan.innerHTML = '<li>Nichts mehr zu tun — Kohle drauf.</li>';
  }

  // Erwartetes Ergebnis
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

  // Sicherheiten
  const konfidenz = $('konfidenz');
  konfidenz.innerHTML = '';
  Object.entries(analyse.confidence || {}).forEach(([key, wert]) => {
    const feld = document.createElement('span');
    feld.className = `konf${wert < 50 ? ' niedrig' : ''}`;
    feld.innerHTML = `${KONF_NAMEN[key] || key} <b>${wert}%</b>`;
    konfidenz.appendChild(feld);
  });

  $('report').hidden = false;
  $('report').scrollTop = 0;
  if (score !== null) sprich(`${score} von 100, ${analyse.stufe_text}. ${analyse.coach_satz || ''}`);
  else sprich(analyse.coach_satz || 'Das Bild reicht fuer eine Bewertung nicht aus.');
  vibriere([40, 80, 40]);
}

function escape(text) {
  const feld = document.createElement('div');
  feld.textContent = text || '';
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

async function feedbackSenden() {
  const daten = {
    id: zustand.sitzung,
    ...zustand.feedback,
    dauer_min: $('fDauer').value,
    notiz: $('fFeedbackNotiz').value,
  };
  try {
    const antwort = await fetch('/api/shisha/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(daten),
    });
    const inhalt = await antwort.json();
    if (!inhalt.ok) throw new Error(inhalt.fehler || 'Speichern fehlgeschlagen');
    treffsicherheitZeigen(inhalt.treffsicherheit);
    $('feedback').hidden = true;
    zustand.feedback = {};
    document.querySelectorAll('.skala-knoepfe button').forEach((k) => k.classList.remove('aktiv'));
  } catch (fehler) {
    $('treffsicherheit').textContent = fehler.message;
  }
}

function treffsicherheitZeigen(werte) {
  if (!werte || !werte.sessions) {
    $('treffsicherheit').textContent = 'Noch keine gespeicherten Sessions.';
    return;
  }
  const teile = [`${werte.sessions} Session(s) gespeichert`];
  if (werte.genauigkeit !== undefined && werte.genauigkeit !== null) {
    teile.push(`Vorhersagegenauigkeit ${werte.genauigkeit}%`);
  }
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

$('losButton').addEventListener('click', async () => {
  const knopf = $('losButton');
  knopf.disabled = true;
  knopf.textContent = 'starte …';
  $('startFehler').textContent = '';
  try {
    tonFreischalten();
    await kameraStarten();
    await sitzungStarten();
    overlayAnpassen();
    bildschirmWachhalten();

    $('start').hidden = true;
    $('oben').hidden = false;
    $('unten').hidden = false;
    zustand.laeuft = true;
    setzeLage('live', '');
    schleife();
  } catch (fehler) {
    $('startFehler').textContent = fehler.message;
    knopf.disabled = false;
    knopf.textContent = 'Kamera starten';
  }
});

$('tonButton').addEventListener('click', () => {
  zustand.ton = !zustand.ton;
  $('tonButton').textContent = zustand.ton ? '🔊' : '🔇';
  $('tonButton').classList.toggle('aus', !zustand.ton);
  if (!zustand.ton && window.speechSynthesis) speechSynthesis.cancel();
});

$('weiterButton').addEventListener('click', () => phaseSetzen('', true));
$('analyseButton').addEventListener('click', vollanalyse);

$('neuButton').addEventListener('click', async () => {
  if (!confirm('Neuen Kopf anfangen?')) return;
  await sitzungStarten();
});

$('zurueckButton').addEventListener('click', () => { $('report').hidden = true; });

$('nochmalButton').addEventListener('click', async () => {
  $('report').hidden = true;
  await sitzungStarten();
});

$('feedbackButton').addEventListener('click', () => { $('feedback').hidden = false; });
$('feedbackAbbruch').addEventListener('click', () => { $('feedback').hidden = true; });
$('feedbackSenden').addEventListener('click', feedbackSenden);

// Startbildschirm mit dem Serverzustand fuellen.
fetch('/api/shisha/status')
  .then((antwort) => antwort.json())
  .then((status) => {
    zustand.phasen = status.phasen || [];
    $('startInfo').textContent = status.bereit
      ? `Analyse: ${status.denkapparat}`
      : `Analyse nicht bereit: ${status.fehler || 'unbekannt'}`;
    treffsicherheitZeigen(status.treffsicherheit);
  })
  .catch(() => { $('startInfo').textContent = 'Server nicht erreichbar.'; });

skalenBauen();
overlayAnpassen();
requestAnimationFrame(zeichnen);
