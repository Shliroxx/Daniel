/* Frueher Fehlerfaenger — laeuft vor allen anderen Skripten.
 *
 * ar.js hat einen eigenen Fehlerfaenger, der aber erst greift, wenn ar.js
 * selbst geladen ist. Faellt vorher etwas aus — steuerung.js kommt nicht durch,
 * engine.js hat einen Syntaxfehler, der Cache liefert eine halbe Datei —, dann
 * verpufft die Meldung ungesehen und die Seite steht nur still da.
 *
 * Diese Datei sammelt deshalb ab der ersten Zeile mit. ar.js liest die Liste
 * spaeter aus und zeigt sie auf dem Startbildschirm an.
 */

'use strict';

window.FRUEHE_FEHLER = [];

window.addEventListener('error', (ereignis) => {
  const ort = ereignis.filename
    ? `${String(ereignis.filename).split('/').pop()}:${ereignis.lineno}`
    : '';
  const text = ereignis.message || 'unbekannter Fehler';
  window.FRUEHE_FEHLER.push(ort ? `${text} (${ort})` : text);
  fruehAnzeigen();
});

window.addEventListener('unhandledrejection', (ereignis) => {
  const grund = ereignis.reason;
  window.FRUEHE_FEHLER.push(`${(grund && grund.message) || String(grund)} (unbehandelt)`);
  fruehAnzeigen();
});

/* Notanzeige, falls ar.js gar nicht erst laeuft.
 *
 * Laeuft ar.js spaeter doch an, schreibt es dasselbe Feld ohnehin neu. */
function fruehAnzeigen() {
  const feld = document.getElementById('startFehler');
  if (!feld) return;
  feld.textContent = `Fehler: ${window.FRUEHE_FEHLER[window.FRUEHE_FEHLER.length - 1]}`;
  feld.title = window.FRUEHE_FEHLER.join('\n');
}
