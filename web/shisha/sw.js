/* Service Worker — haelt die App auch ohne Netz startbereit.
 *
 * Der Rahmen (HTML, CSS, Code, Regelwerk) liegt im Cache. Die Analyse selbst
 * braucht natuerlich Netz — die geht immer direkt ans Modell und wird nie
 * zwischengespeichert.
 */

const CACHE = 'hookah-analyzer-v10';

const DATEIEN = [
  './',
  './index.html',
  './ar.css',
  './ar.js',
  './engine.js',
  './steuerung.js',
  './spec.json',
  './icon.svg',
  './icon-192.png',
  './icon-512.png',
  './manifest.webmanifest',
];

// So lange darf das Netz brauchen, bevor aus dem Cache geliefert wird. Im
// schlechten Mobilfunk ist "verbunden, aber nichts kommt durch" der Normalfall —
// ohne Frist haengt der Start dann am weissen Bild, obwohl alles da waere.
const NETZ_FRIST_MS = 2500;

self.addEventListener('install', (ereignis) => {
  ereignis.waitUntil(
    caches.open(CACHE)
      // Einzeln statt addAll: sonst ist eine fehlende Datei genug, damit die
      // Installation scheitert und es gar keinen Offline-Betrieb gibt — still,
      // ohne jedes Anzeichen.
      .then((cache) => Promise.all(DATEIEN.map((datei) => cache.add(datei).catch(() => {}))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (ereignis) => {
  ereignis.waitUntil(
    caches.keys()
      .then((namen) => Promise.all(namen.filter((n) => n !== CACHE).map((n) => caches.delete(n))))
      .then(() => self.clients.claim())
  );
});

/** Netz mit Frist — laeuft sie ab, gilt die Anfrage als gescheitert. */
function mitFrist(anfrage) {
  return new Promise((fertig, scheitern) => {
    const uhr = setTimeout(() => scheitern(new Error('zu langsam')), NETZ_FRIST_MS);
    fetch(anfrage).then(
      (antwort) => { clearTimeout(uhr); fertig(antwort); },
      (fehler) => { clearTimeout(uhr); scheitern(fehler); }
    );
  });
}

self.addEventListener('fetch', (ereignis) => {
  const anfrage = ereignis.request;

  // Nur eigene Dateien und nur Abrufe — Analysen laufen immer durchs Netz.
  if (anfrage.method !== 'GET' || new URL(anfrage.url).origin !== location.origin) return;

  ereignis.respondWith(
    // Erst das Netz versuchen, damit Aenderungen sofort ankommen; sonst Cache.
    mitFrist(anfrage)
      .then((antwort) => {
        if (antwort.ok) {
          const kopie = antwort.clone();
          caches.open(CACHE).then((cache) => cache.put(anfrage, kopie));
        }
        return antwort;
      })
      .catch(() => caches.match(anfrage).then((treffer) => {
        if (treffer) return treffer;
        // Nur beim Seitenaufruf ist die Startseite eine sinnvolle Antwort. Fuer
        // alles andere waere sie eine Luege: spec.json bekaeme HTML geliefert
        // und meldete einen JSON-Fehler statt "kein Netz".
        if (anfrage.mode === 'navigate') return caches.match('./index.html');
        return new Response('', { status: 504, statusText: 'kein Netz' });
      }))
  );
});
