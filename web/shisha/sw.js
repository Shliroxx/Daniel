/* Service Worker — haelt die App auch ohne Netz startbereit.
 *
 * Der Rahmen (HTML, CSS, Code, Regelwerk) liegt im Cache. Die Analyse selbst
 * braucht natuerlich Netz — die geht immer direkt ans Modell und wird nie
 * zwischengespeichert.
 */

const CACHE = 'hookah-analyzer-v16';

/* Ohne diese Dateien laeuft nichts.
 *
 * Sie werden gemeinsam abgelegt: fehlt eine, scheitert die ganze Installation,
 * und die alte, vollstaendige Fassung bleibt in Betrieb. Frueher durfte jede
 * einzeln scheitern — brach das Netz waehrend der Installation weg, lag
 * offline eine halbe App im Cache: die Seite kam, ein Skript fehlte, und alle
 * Knoepfe waren tot.
 */
const KERN = [
  './',
  './index.html',
  './ar.css',
  './ar.js',
  './fehler.js',
  './engine.js',
  './steuerung.js',
  './spec.json',
];

// Beiwerk darf fehlen — ohne Symbol startet die App trotzdem.
const BEIWERK = [
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
      .then((cache) => cache.addAll(KERN)
        .then(() => Promise.all(BEIWERK.map((datei) => cache.add(datei).catch(() => {})))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (ereignis) => {
  ereignis.waitUntil(
    caches.keys()
      .then((namen) => {
        const alte = namen.filter((n) => n !== CACHE);
        return Promise.all(alte.map((n) => caches.delete(n))).then(() => alte.length > 0);
      })
      .then((warUpdate) => self.clients.claim().then(() => warUpdate))
      .then((warUpdate) => {
        // Nur bei einem echten Update melden — beim allerersten Besuch gibt es
        // nichts Altes, und ein Neuladen waere dort nur ein Flackern.
        if (!warUpdate) return;
        return self.clients.matchAll({ type: 'window' }).then((fenster) => {
          fenster.forEach((f) => f.postMessage({ art: 'neue-fassung', cache: CACHE }));
        });
      })
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
