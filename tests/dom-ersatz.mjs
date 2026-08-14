/* Minimaler Browser-Ersatz, gerade so viel wie ar.js anfasst. */
import { readFileSync } from 'node:fs';

export function baueUmgebung(webDir, { antwort }) {
  const protokoll = { fehler: [], gesprochen: [], vibriert: [], geteilt: [], anfragen: [] };
  const elemente = new Map();

  function klassenListe() {
    const menge = new Set();
    return {
      add: (...k) => k.forEach((x) => menge.add(x)),
      remove: (...k) => k.forEach((x) => menge.delete(x)),
      toggle: (k, an) => (an === undefined ? (menge.has(k) ? menge.delete(k) : menge.add(k))
                                           : (an ? menge.add(k) : menge.delete(k))),
      contains: (k) => menge.has(k),
      get liste() { return [...menge]; },
    };
  }

  function neuesElement(id = '', tag = 'div') {
    const el = {
      id, tag, value: '', checked: false, hidden: false,
      disabled: false, title: '', placeholder: '', className: '', dataset: {},
      style: {}, children: [], classList: klassenListe(), horcher: {},
      addEventListener(art, fn) { (this.horcher[art] ||= []).push(fn); },
      appendChild(kind) { this.children.push(kind); return kind; },
      scrollIntoView() {},
      closest(wahl) {
        const attribut = wahl.replace(/[[\]]/g, '');
        return this.dataset[attribut.replace('data-', '')] !== undefined ? this : null;
      },
      querySelector(wahl) {
        if (wahl === '.aktiv') return this.children.find((k) => k.classList.contains('aktiv')) || null;
        if (wahl === '.skala-knoepfe') return this.children[0] || null;
        return null;
      },
      querySelectorAll() { return []; },
      /** Klick von aussen ausloesen. */
      klick() {
        (this.horcher.click || []).forEach((fn) => fn({ target: this }));
        if (typeof this.onclick === 'function') this.onclick({ target: this });
      },
    };
    let roh = '';
    let text = '';
    Object.defineProperty(el, 'textContent', {
      get: () => text,
      set: (wert) => {
        text = wert === null || wert === undefined ? '' : String(wert);
        roh = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        el.children.length = 0;
      },
    });
    Object.defineProperty(el, 'innerHTML', {
      get: () => roh,
      // Wie im Browser: Zuweisung ersetzt den Inhalt, leert also die Kinder.
      set: (wert) => { roh = String(wert); text = roh.replace(/<[^>]*>/g, ''); el.children.length = 0; },
    });
    Object.defineProperty(el, 'clientWidth', { get: () => 390 });
    Object.defineProperty(el, 'clientHeight', { get: () => 780 });
    return el;
  }

  // --- Leinwand ---------------------------------------------------------
  let bildMuster = 0;
  let wackelt = false;   // simuliert eine zittrige Hand: jedes Bild sieht anders aus
  function neuerKontext(flaeche) {
    const nichts = () => {};
    return {
      setTransform: nichts, clearRect: nichts, save: nichts, restore: nichts,
      beginPath: nichts, closePath: nichts, moveTo: nichts, lineTo: nichts,
      arc: nichts, arcTo: nichts, stroke: nichts, fill: nichts, fillRect: nichts,
      fillText: nichts, setLineDash: nichts, translate: nichts, rotate: nichts,
      drawImage: nichts,
      measureText: (t) => ({ width: t.length * 7 }),
      getImageData: (x, y, b, h) => {
        if (wackelt) bildMuster++;
        // Schachbrett mit genug Kanten und Helligkeit, damit das Bild taugt.
        //
        // Beim Wackeln wird das Muster verschoben, nicht invertiert: eine reine
        // Invertierung hat die Periode zwei, und zwei Abtastungen dazwischen
        // ergaben wieder dasselbe Bild — dann sah ein wild wackelndes Bild fuer
        // die Guetepruefung voellig ruhig aus. Der Schritt 7 gegen die Periode 4
        // wiederholt sich so schnell nicht.
        const schub = wackelt ? (bildMuster * 7) % 13 : bildMuster;
        const daten = new Uint8ClampedArray(b * h * 4);
        for (let i = 0, p = 0; p < b * h; p++, i += 4) {
          const zx = (p % b) + schub;
          const zy = Math.floor(p / b);
          const hell = ((zx >> 1) + (zy >> 1)) % 2 ? 210 : 60;
          daten[i] = daten[i + 1] = daten[i + 2] = hell;
          daten[i + 3] = 255;
        }
        return { data: daten };
      },
    };
  }

  function neueFlaeche() {
    const f = neuesElement('', 'canvas');
    f.width = 64; f.height = 48;
    f.getContext = () => (f._ctx ||= neuerKontext(f));
    f.toBlob = (fertig) => fertig(new Blob(['bilddaten'], { type: 'image/jpeg' }));
    f.toDataURL = () => 'data:image/jpeg;base64,AAAA';
    return f;
  }

  // --- Dokument ---------------------------------------------------------
  const html = readFileSync(`${webDir}/index.html`, 'utf8');
  [...html.matchAll(/id="([a-zA-Z-]+)"/g)].forEach((t) => elemente.set(t[1], neuesElement(t[1])));

  const video = elemente.get('kamera');
  video.videoWidth = 1920;
  video.videoHeight = 1080;
  video.paused = false;
  video.play = async () => { video.paused = false; };

  const overlay = elemente.get('overlay');
  overlay.getContext = () => (overlay._ctx ||= neuerKontext(overlay));

  const kurve = elemente.get('kurve');
  kurve.getContext = () => (kurve._ctx ||= neuerKontext(kurve));

  const dokument = {
    getElementById: (id) => elemente.get(id) || null,
    createElement: (tag) => (tag === 'canvas' ? neueFlaeche() : neuesElement('', tag)),
    querySelector: (wahl) => {
      // Nur die Formen, die ar.js benutzt: "#box .aktiv"
      const treffer = wahl.match(/^#([a-zA-Z]+) \.aktiv$/);
      if (!treffer) return null;
      const box = elemente.get(treffer[1]);
      return box ? box.children.find((k) => k.classList.contains('aktiv')) || null : null;
    },
    querySelectorAll: (wahl) => {
      if (wahl === '.skala-knoepfe button') return [];
      if (wahl === '.skala') {
        return ['geschmack', 'rauch', 'kratzen', 'hitze'].map((feld) => {
          const z = neuesElement('', 'div');
          z.dataset.feld = feld;
          const knoepfe = neuesElement('', 'div');
          z.appendChild(knoepfe);
          z.querySelector = () => knoepfe;
          return z;
        });
      }
      return [];
    },
    addEventListener: (art, fn) => { (dokument._horcher[art] ||= []).push(fn); },
    _horcher: {},
    visibilityState: 'visible',
  };
  function nichtsTun() {}

  // --- Fenster ----------------------------------------------------------
  const spur = { readyState: 'live', addEventListener: nichtsTun, stop: nichtsTun };
  const speicher = new Map();
  const global = {
    document: dokument,
    localStorage: {
      getItem: (k) => (speicher.has(k) ? speicher.get(k) : null),
      setItem: (k, v) => speicher.set(k, String(v)),
      removeItem: (k) => speicher.delete(k),
    },
    location: { origin: 'https://beispiel.test' },
    performance: { now: () => Date.now() },
    requestAnimationFrame: () => 0,
    devicePixelRatio: 2,
    setTimeout, clearTimeout, setInterval, clearInterval,
    Blob, File, URL, Image: class { set src(_) { setTimeout(() => this.onerror && this.onerror(), 0); } },
    console,
    confirm: () => true,
    speechSynthesis: {
      cancel: nichtsTun,
      getVoices: () => [{ lang: 'de-DE', name: 'Anna' }],
      speak: (s) => {
        protokoll.gesprochen.push(s.text);
        if (s.onstart) s.onstart();
        if (s.onend) s.onend();
      },
    },
    SpeechSynthesisUtterance: class { constructor(t) { this.text = t; } },
    navigator: {
      vibrate: (m) => protokoll.vibriert.push(m),
      onLine: true,
      mediaDevices: {
        getUserMedia: async () => {
          // Eine Spur, deren Zustand der Test umschalten kann.
          spur.readyState = 'live';
          return { getVideoTracks: () => [spur], getTracks: () => [spur] };
        },
      },
      canShare: () => true,
      share: async (d) => protokoll.geteilt.push(d.title),
    },
    fetch: async (url, optionen = {}) => {
      if (String(url).endsWith('spec.json')) {
        return { ok: true, json: async () => JSON.parse(readFileSync(`${webDir}/spec.json`, 'utf8')) };
      }
      protokoll.anfragen.push({ url: String(url), optionen, zeit: Date.now() });
      const ergebnis = antwort(String(url));
      if (ergebnis.verzoegerung) await new Promise((f) => setTimeout(f, ergebnis.verzoegerung));
      if (ergebnis.status && ergebnis.status !== 200) {
        return { ok: false, status: ergebnis.status, text: async () => ergebnis.text || '' };
      }
      const umschlag = String(url).includes('openrouter')
        ? { choices: [{ message: { content: ergebnis.text } }] }
        : { candidates: [{ content: { parts: [{ text: ergebnis.text }] } }] };
      return { ok: true, status: 200, text: async () => JSON.stringify(umschlag) };
    },
  };
  global.addEventListener = (art, fn) => { (global._horcher[art] ||= []).push(fn); };
  global._horcher = {};
  global.window = global;
  global.globalThis = global;
  global.atob = atob;
  global.btoa = btoa;

  return {
    global, elemente, protokoll, dokument, spur,
    bildAendern: () => { bildMuster++; },
    wackeln: (an) => { wackelt = Boolean(an); },
  };
}
