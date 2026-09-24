/* Vertragstests: Regeln, die kein Browser hier durchsetzt.
 *
 * Die uebrigen Tests laufen in einem nachgebauten DOM. Der kennt keine
 * Content-Security-Policy, parst kein HTML und laedt keine fremden Adressen —
 * genau die Fehler also, die erst am Geraet auffallen und dort als "tut
 * nichts" ankommen: ein Inline-Style, den der Browser stillschweigend
 * verwirft, oder eine Ressource, die gar nicht erst geladen wird.
 *
 * Diese Datei prueft solche Regeln am Quelltext statt am Verhalten. Das ist
 * grob, kostet aber Sekunden und haelt genau die Zusagen fest, auf denen der
 * Schutz des Schluessels tatsaechlich ruht.
 *
 * Ausfuehren:  node tests/test_vertrag.mjs
 */
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const webDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'web', 'shisha');
const html = readFileSync(join(webDir, 'index.html'), 'utf8');

// --- Die Richtlinie selbst -----------------------------------------------------
const richtlinie = (html.match(/http-equiv="Content-Security-Policy"\s+content="([^"]+)"/) || [])[1];
assert.ok(richtlinie, 'index.html muss eine Content-Security-Policy tragen');
assert.ok(richtlinie.includes("default-src 'none'"), "default-src 'none' ist die Grundlage");
assert.ok(richtlinie.includes("script-src 'self'"), "script-src 'self' muss gesetzt sein");

/* Diese beiden Schlupfloecher machen die Richtlinie wertlos.
 *
 * Solange kein Inline-Skript und kein eval erlaubt ist, kann eine
 * eingeschleuste Zeichenkette keinen Code ausfuehren — und nur deshalb ist es
 * vertretbar, den API-Schluessel im localStorage des Geraets zu halten.
 */
assert.ok(!richtlinie.includes("'unsafe-inline'"), "'unsafe-inline' hebelt script-src aus");
assert.ok(!richtlinie.includes("'unsafe-eval'"), "'unsafe-eval' hebelt script-src aus");

/* frame-ancestors wirkt im meta-Tag nicht — jeder Browser ignoriert es dort.
 * Stand es trotzdem da, taeuschte es einen Schutz vor, den es nicht gab. Der
 * echte Schutz gegen Einbettung sitzt in fehler.js, als erstes Skript. */
assert.ok(!richtlinie.includes('frame-ancestors'),
  'frame-ancestors im meta-Tag ist wirkungslos und taeuscht Schutz vor');
const frueh = readFileSync(join(webDir, 'fehler.js'), 'utf8');
assert.ok(/window\.top\s*!==\s*window\.self/.test(frueh), 'fehler.js muss die Einbettung pruefen');
const erstesSkript = (html.match(/<script\s+src="([^"]+)"/) || [])[1];
assert.equal(erstesSkript, 'fehler.js', 'der Einbettungsschutz muss vor allem anderen laufen');

// --- Was die Richtlinie verbietet, darf auch nicht dastehen --------------------
// Ohne 'unsafe-inline' verwirft der Browser beides stumm: der Knopf bleibt
// unverdrahtet, der Balken unsichtbar. Im Test faellt das sonst nie auf, weil
// der Nachbau innerHTML nur als Zeichenkette ablegt.
assert.ok(!/\sstyle="/.test(html), 'Inline-Styles sind durch die CSP gesperrt');
assert.ok(!/\son[a-z]+="/.test(html), 'Inline-Ereignisse sind durch die CSP gesperrt');
assert.ok(!/<script(?![^>]*\ssrc=)/.test(html), 'Inline-Skripte sind durch die CSP gesperrt');

// Auch nicht ueber den Umweg innerHTML in den Skripten.
const skripte = readdirSync(webDir).filter((d) => d.endsWith('.js'));
skripte.forEach((datei) => {
  const quelle = readFileSync(join(webDir, datei), 'utf8');
  assert.ok(!/style="\$\{|style='\$\{/.test(quelle),
    `${datei}: Inline-Style ueber eine Vorlage — die CSP verwirft ihn stumm`);
});

/* --- Nur Syntax, die auch aeltere iPhones verstehen ----------------------------
 *
 * Ein einziges Konstrukt, das Safari nicht kennt, ist beim Laden ein
 * Syntaxfehler — dann laeuft die ganze Datei nicht, und alle Knoepfe sind tot.
 * Genau das Bild, das der Nutzer als "die Buttons funktionieren nicht"
 * beschrieben hat. Lookbehind im Regex kann Safari erst ab iOS 16.4; die
 * logischen Zuweisungen ab 14. Node versteht beides, deshalb faellt es in
 * keinem anderen Test auf.
 */
const ZU_NEU = [
  [/\(\?<[=!]/, 'Lookbehind im Regex (Safari erst ab iOS 16.4)'],
  [/\|\|=|&&=|\?\?=/, 'logische Zuweisung (Safari erst ab iOS 14)'],
  [/\.at\(\s*-?\d/, 'Array.prototype.at (Safari erst ab iOS 15.4)'],
  [/structuredClone\(/, 'structuredClone (Safari erst ab iOS 15.4)'],
  [/Object\.hasOwn\(/, 'Object.hasOwn (Safari erst ab iOS 15.4)'],
  [/\.findLast(Index)?\(/, 'findLast (Safari erst ab iOS 15.4)'],
];
skripte.forEach((datei) => {
  const quelle = readFileSync(join(webDir, datei), 'utf8')
    .split('\n').filter((zeile) => !/^\s*(\/\/|\*)/.test(zeile)).join('\n');
  ZU_NEU.forEach(([muster, was]) => {
    assert.ok(!muster.test(quelle), `${datei}: ${was} — auf aelteren iPhones laeuft die Datei dann gar nicht`);
  });
});

// --- Alles Fremde bleibt draussen ----------------------------------------------
// default-src 'none' laesst nichts von aussen zu. Ein Font, ein Symbolsatz oder
// eine Bibliothek von einem CDN wuerde am Geraet einfach fehlen.
const fremde = [...html.matchAll(/(?:src|href)="(https?:\/\/[^"]+)"/g)].map((t) => t[1]);
assert.deepEqual(fremde, [], `Fremde Ressourcen in index.html: ${fremde.join(', ')}`);

// --- Der Schluessel gehoert nicht in die Adresse -------------------------------
// In einer URL landet er in Protokollen, im Verlauf und in Fehlerberichten.
// In einem Kopf nicht. Alle drei Anbieter muessen es so halten.
const engine = readFileSync(join(webDir, 'engine.js'), 'utf8');
assert.ok(!/[?&](key|api_key|apikey|token)=\$\{/.test(engine),
  'Kein Schluessel in der Adresse — er gehoert in einen Kopf');

// --- Der Service Worker darf den Schluesselverkehr nicht sehen -----------------
// Die Analyse ist ein POST an eine fremde Herkunft. Wuerde der Worker das
// abfangen, koennte eine Antwort mitsamt Anfrage im Cache landen.
const sw = readFileSync(join(webDir, 'sw.js'), 'utf8');
assert.ok(/method\s*!==\s*'GET'/.test(sw), 'Der Worker darf nur GET abfangen');
assert.ok(/origin\s*!==\s*(location|self\.location)\.origin/.test(sw),
  'Der Worker darf nur die eigene Herkunft abfangen');

// --- Jede Datei, die der Worker cacht, muss es auch geben ----------------------
// Ein Tippfehler in der Liste faellt sonst erst offline auf, und dann fehlt
// ausgerechnet die Datei, die den Fehler haette melden koennen.
const vorhanden = new Set(readdirSync(webDir));
[...sw.matchAll(/'\.\/([^']+)'/g)].map((t) => t[1]).forEach((datei) => {
  if (!datei) return;
  assert.ok(vorhanden.has(datei), `sw.js cacht "${datei}", die Datei gibt es aber nicht`);
});

// --- Jedes Skript aus dem HTML wird auch gecacht --------------------------------
// Sonst laedt die App offline nur zur Haelfte: die Seite kommt, ein Skript
// fehlt, und alle Knoepfe sind tot.
[...html.matchAll(/<script\s+src="([^"]+)"/g)].map((t) => t[1]).forEach((datei) => {
  assert.ok(sw.includes(`./${datei}`), `${datei} wird geladen, aber nicht gecacht`);
});

console.log('ALLE TESTS BESTANDEN');
