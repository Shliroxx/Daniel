/* Service Worker — haelt die App auch ohne Netz startbereit.
 *
 * Der Rahmen (HTML, CSS, Code, Regelwerk) liegt im Cache. Die Analyse selbst
 * braucht natuerlich Netz — die geht immer direkt ans Modell und wird nie
 * zwischengespeichert.
 */

const CACHE = 'hookah-analyzer-v4';

const DATEIEN = [
  './',
  './index.html',
  './ar.css',
  './ar.js',
  './engine.js',
  './steuerung.js',
  './spec.json',
  './icon.svg',
  './manifest.webmanifest',
];

self.addEventListener('install', (ereignis) => {
  ereignis.waitUntil(
    caches.open(CACHE)
      .then((cache) => cache.addAll(DATEIEN))
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

self.addEventListener('fetch', (ereignis) => {
  const anfrage = ereignis.request;

  // Nur eigene Dateien und nur Abrufe — Analysen laufen immer durchs Netz.
  if (anfrage.method !== 'GET' || new URL(anfrage.url).origin !== location.origin) return;

  ereignis.respondWith(
    // Erst das Netz versuchen, damit Aenderungen sofort ankommen; sonst Cache.
    fetch(anfrage)
      .then((antwort) => {
        if (antwort.ok) {
          const kopie = antwort.clone();
          caches.open(CACHE).then((cache) => cache.put(anfrage, kopie));
        }
        return antwort;
      })
      .catch(() => caches.match(anfrage).then((treffer) => treffer || caches.match('./index.html')))
  );
});
