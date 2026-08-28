/* Steuerlogik — die Entscheidungen, ohne Kamera und ohne Oberflaeche.
 *
 * Hier steht, WANN etwas passieren soll: wann ein Bild taugt, wann die Kamera
 * als eingefroren gilt, was ein Sprachbefehl ausloest, wie weit Marker
 * mitwandern. Das WIE — Video anfassen, zeichnen, Knoepfe schalten — bleibt in
 * ar.js.
 *
 * Der Grund fuer die Trennung: genau diese Entscheidungen haben in der Praxis
 * Aerger gemacht (eingefrorene Kamera, Pause zur falschen Zeit), und genau sie
 * liessen sich vorher nicht testen, weil sie mitten im Kamera-Code steckten.
 * Als reine Funktionen sind sie in tests/test_steuerung.mjs abgedeckt.
 */

'use strict';

const Steuerung = (() => {

  // Standardgrenzen. ar.js reicht bei Bedarf eigene herein.
  const GRENZEN = {
    ruhe: 7.0,          // mittlere Pixelaenderung zwischen zwei Miniaturen
    schaerfe: 6.0,      // Kantenstaerke in der Miniatur
    helligkeit: 34,     // darunter ist es zu dunkel
    blindMs: 4000,      // so lange darf das Bild stehen, bevor wir pausieren
    sparUnterschied: 1.2, // darunter gilt das Bild als unveraendert
    verblassenMs: 6000, // nach dieser Zeit ist ein Marker ganz verblasst
    fehlerRuheMs: 3000,     // Pause nach einer Stoerung
    kontingentRuheMs: 30000, // Pause, wenn das Freikontingent erschoepft ist
    geduldMs: 5000,     // so lange wird auf ein sauberes Bild gewartet
    notfallMs: 12000,   // danach wird geschickt, was da ist
    nachsicht: { ruhe: 16, schaerfe: 3.4, helligkeit: 22 }, // dazwischen reicht weniger
  };

  /* Die Grenzen, die gerade gelten.
   *
   * Aus der Hand gehalten ist ein Bild selten ganz ruhig und selten ganz scharf.
   * Wer streng bleibt, wartet ewig und schickt nie etwas los — genau das war am
   * Geraet zu sehen: langes Scannen ohne Ergebnis. Nach `geduldMs` ohne Analyse
   * wird deshalb nachsichtiger gemessen. Lieber ein etwas weicheres Bild
   * beurteilen und die Note als vorlaeufig kennzeichnen als gar nichts sagen.
   */
  function geltendeGrenzen(wartetMs, grenzen = GRENZEN) {
    if (!(wartetMs > grenzen.geduldMs)) return { grenzen, nachsichtig: false };
    if (!(wartetMs > grenzen.notfallMs)) {
      return { grenzen: { ...grenzen, ...grenzen.nachsicht }, nachsichtig: true };
    }
    // Nach zwoelf Sekunden ohne jedes Ergebnis ist Schweigen die schlechteste
    // Antwort. Dann geht das Bild raus, wie es ist — nur stockdunkel bringt
    // wirklich nichts, das bleibt die einzige Huerde.
    return {
      grenzen: { ...grenzen, ruhe: Infinity, schaerfe: 0, helligkeit: grenzen.nachsicht.helligkeit },
      nachsichtig: true,
    };
  }

  /* Taugt dieses Bild fuer eine Analyse?
   *
   * Reihenfolge mit Absicht: zu dunkel ist die Ursache, unscharf oft nur die
   * Folge. Wer zuerst "unscharf" liest, macht mehr Licht nicht an.
   */
  function bildBewerten(guete, grenzen = GRENZEN, wartetMs = 0) {
    if (!guete) return { ok: false, problem: 'kein Bild', nachsichtig: false };
    const g = geltendeGrenzen(wartetMs, grenzen);
    const schlecht = (problem) => ({ ok: false, problem, nachsichtig: g.nachsichtig });

    if (guete.helligkeit < g.grenzen.helligkeit) return schlecht('zu dunkel');
    if (guete.bewegung > g.grenzen.ruhe) return schlecht('halt still');
    if (guete.schaerfe < g.grenzen.schaerfe) return schlecht('unscharf');
    return { ok: true, problem: '', nachsichtig: g.nachsichtig };
  }

  /* Was soll die Analyseschleife als naechstes tun?
   *
   * `blindSeit` ist der Zeitpunkt, seit dem kein Bild mehr kommt (0 = laeuft).
   * Kurze Aussetzer sind normal, deshalb wird erst nach `blindMs` pausiert.
   */
  function kameraLage(lage, grenzen = GRENZEN) {
    const { spurLebt, videoLaeuft, blindSeit = 0, jetzt = Date.now() } = lage;

    if (spurLebt && videoLaeuft) {
      return { aktion: 'analysieren', blindSeit: 0, grund: '' };
    }

    const seit = blindSeit || jetzt;
    if (jetzt - seit > grenzen.blindMs) {
      return {
        aktion: 'pausieren',
        blindSeit: seit,
        grund: spurLebt ? 'Das Livebild ist eingefroren.' : 'Die Kamera wurde vom System freigegeben.',
      };
    }
    return { aktion: 'warten', blindSeit: seit, grund: '' };
  }

  /* Was passiert, wenn die App aus dem Hintergrund zurueckkommt?
   *
   * Laeuft die Kamera noch, geht es ohne Nachfrage weiter — alles andere waere
   * eine unnoetige Huerde. Nur wenn sie wirklich weg ist, kommt die Blende.
   */
  function rueckkehrPlan(lage) {
    const { spurLebt, videoLaeuft, pausiert, imHauptmenue, sitzungDa, anleitungOffen } = lage;
    // Steht eine Anleitung offen, wird sie gelesen — dahinter faengt nichts
    // wieder an zu analysieren, zu reden und Kontingent zu verbrauchen.
    if (pausiert || imHauptmenue || !sitzungDa || anleitungOffen) return { aktion: 'nichts', grund: '' };
    if (spurLebt && videoLaeuft) return { aktion: 'weiter', grund: '' };
    return { aktion: 'pausieren', grund: 'Die App war im Hintergrund — die Kamera wurde angehalten.' };
  }

  /* Lohnt sich fuer dieses Bild ueberhaupt eine Anfrage?
   *
   * Im Sparmodus wird nichts verschickt, solange sich seit der letzten Analyse
   * praktisch nichts geaendert hat. Ein Kopf, der unveraendert vor der Kamera
   * liegt, bekommt sonst zehnmal dieselbe Bewertung — auf Kosten des
   * Freikontingents.
   */
  function lohntAnalyse(unterschied, sparmodus, grenzen = GRENZEN) {
    if (!sparmodus) return { lohnt: true, grund: '' };
    if (unterschied === null || unterschied === undefined) return { lohnt: true, grund: '' };
    if (unterschied < grenzen.sparUnterschied) {
      return { lohnt: false, grund: 'unverändert' };
    }
    return { lohnt: true, grund: '' };
  }

  /* Wie lange wird nach einem Fehler gewartet? `null` heisst: gar nicht mehr.
   *
   * Nicht jeder Fehler heisst "gleich nochmal". Ein abgelehnter Schluessel wird
   * beim zwanzigsten Versuch auch nicht besser — da hilft nur anhalten und es
   * sagen. Ein erschoepftes Kontingent braucht Zeit, keine Wiederholungen;
   * vorher lief die Schleife alle vier Sekunden weiter ins Leere. Alles andere
   * ist eine Stoerung und darf es bald wieder versuchen.
   */
  function fehlerRuhe(status, grenzen = GRENZEN) {
    if (status === 401 || status === 403) return null;
    if (status === 429) return grenzen.kontingentRuheMs;
    return grenzen.fehlerRuheMs;
  }

  /* Verschiebt einen Marker um die Bildbewegung seit seiner Analyse.
   *
   * Die Marker beziehen sich auf das Bild, das analysiert wurde. Bewegt sich
   * das Handy danach, zeigen sie sonst neben den Kopf. `versatz` ist die
   * Verschiebung in normalisierten Bildkoordinaten.
   */
  function markerVerschieben(marker, versatz) {
    if (!versatz || (!versatz.x && !versatz.y)) return marker;
    const klemmen = (wert, breite) => Math.max(0, Math.min(wert, 1 - breite));
    return {
      ...marker,
      x: klemmen(marker.x + versatz.x, marker.w),
      y: klemmen(marker.y + versatz.y, marker.h),
    };
  }

  /* Wie kraeftig wird ein Marker noch gezeichnet?
   *
   * Frische Marker sind voll da, aeltere verblassen — so ist auf einen Blick
   * klar, ob die Markierung noch zum aktuellen Bild passt oder schon steht.
   */
  function alterFaktor(alterMs, grenzen = GRENZEN) {
    if (!(alterMs > 0)) return 1;
    const rest = 1 - alterMs / grenzen.verblassenMs;
    return Math.max(0.25, Math.min(1, rest));
  }

  /* Was loest ein erkannter Sprachbefehl aus?
   *
   * Gibt nur die Absicht zurueck; ausgefuehrt wird sie in ar.js. So laesst sich
   * pruefen, dass "pause" nicht mitten im Hauptmenue etwas anhaelt und
   * "weitermachen" nur greift, wenn wirklich pausiert ist.
   */
  function befehlPlan(befehl, lage = {}) {
    const { pausiert = false, imHauptmenue = false, laeuft = false,
            anleitungOffen = false } = lage;

    /* Bei offener Anleitung liest der Nutzer. Dann darf ein Zuruf nicht die
     * Kamera anwerfen oder eine Vollanalyse starten, waehrend das Blatt noch
     * ueber dem Bild liegt — nur der Weg heraus bleibt offen.
     */
    if (anleitungOffen) {
      if (befehl === 'menue') return { aktion: 'hauptmenue', grund: '' };
      return { aktion: 'nichts', grund: 'Anleitung offen' };
    }

    if (imHauptmenue) {
      // Im Hauptmenue steuert man nichts, was eine laufende Sitzung braucht.
      return befehl === 'start'
        ? { aktion: 'kamera_starten' }
        : { aktion: 'nichts', grund: 'im Hauptmenü' };
    }

    if (pausiert) {
      if (befehl === 'start') return { aktion: 'fortsetzen' };
      if (befehl === 'menue') return { aktion: 'hauptmenue' };
      return { aktion: 'nichts', grund: 'pausiert' };
    }

    switch (befehl) {
      case 'weiter': return { aktion: 'phase_vor', sprich: true };
      case 'zurueck': return { aktion: 'phase_zurueck', sprich: true };
      case 'analyse': return laeuft
        ? { aktion: 'vollanalyse', sprich: true }
        : { aktion: 'nichts', grund: 'Kamera steht' };
      case 'neu': return { aktion: 'neue_sitzung', sprich: true };
      case 'pause': return { aktion: 'pausieren' };
      case 'menue': return { aktion: 'hauptmenue' };
      case 'ruhe': return { aktion: 'ton_aus' };
      case 'sprich': return { aktion: 'ton_an', sprich: true };
      case 'wiederhole': return { aktion: 'wiederholen' };
      case 'status': return { aktion: 'status_sagen' };
      case 'start': return { aktion: 'nichts', grund: 'läuft bereits' };
      default: return { aktion: 'nichts', grund: 'unbekannt' };
    }
  }

  return {
    GRENZEN,
    geltendeGrenzen,
    bildBewerten,
    kameraLage,
    rueckkehrPlan,
    lohntAnalyse,
    fehlerRuhe,
    markerVerschieben,
    alterFaktor,
    befehlPlan,
  };
})();

// Fuer den Test unter Node — im Browser gibt es kein module.
if (typeof module !== 'undefined' && module.exports) module.exports = Steuerung;
