/* Analyse-Engine — laeuft komplett im Browser.
 *
 * Das Handy schickt das Kamerabild direkt an ein Bildmodell und wertet die
 * Antwort selbst aus. Kein eigener Server noetig: die App liegt auf einer
 * statischen Adresse, der Schluessel liegt nur auf diesem Geraet.
 *
 * Die Regeln stehen in spec.json — das ist die einzige Quelle. Hier steht nur,
 * wie daraus ein Prompt wird und wie die Antwort geradegezogen wird.
 *
 * Wichtig: die Gesamtnote rechnet diese Datei, nicht das Modell. Damit ergibt
 * dasselbe Bild dieselbe Note, egal welches Modell antwortet.
 */

'use strict';

const Engine = (() => {

  // ------------------------------------------------------------------------
  // Spezifikation laden
  // ------------------------------------------------------------------------

  let spec = null;

  async function specLaden() {
    if (spec) return spec;
    const antwort = await fetch('spec.json', { cache: 'no-cache' });
    if (!antwort.ok) throw new Error('spec.json konnte nicht geladen werden');
    spec = await antwort.json();
    return spec;
  }

  const zeilen = (block) => (Array.isArray(block) ? block.join('\n') : String(block || ''));

  // ------------------------------------------------------------------------
  // Einstellungen (bleiben auf diesem Geraet)
  // ------------------------------------------------------------------------

  const STANDARD_EINSTELLUNGEN = {
    anbieter: 'gemini',
    gemini_key: '',
    gemini_modell: 'gemini-2.5-flash',
    openrouter_key: '',
    openrouter_modell: 'meta-llama/llama-4-maverick:free',
    server_url: '',
  };

  function einstellungen() {
    try {
      return { ...STANDARD_EINSTELLUNGEN, ...JSON.parse(localStorage.getItem('shisha.einstellungen') || '{}') };
    } catch (_) {
      return { ...STANDARD_EINSTELLUNGEN };
    }
  }

  function einstellungenSpeichern(werte) {
    const neu = { ...einstellungen(), ...werte };
    localStorage.setItem('shisha.einstellungen', JSON.stringify(neu));
    return neu;
  }

  function bereit() {
    const e = einstellungen();
    if (e.anbieter === 'gemini') return Boolean(e.gemini_key);
    if (e.anbieter === 'openrouter') return Boolean(e.openrouter_key);
    if (e.anbieter === 'server') return Boolean(e.server_url || location.origin);
    return false;
  }

  function anbieterName() {
    const e = einstellungen();
    if (e.anbieter === 'gemini') return `Google Gemini · ${e.gemini_modell}`;
    if (e.anbieter === 'openrouter') return `OpenRouter · ${e.openrouter_modell}`;
    return `eigener Rechner · ${e.server_url || location.origin}`;
  }

  // ------------------------------------------------------------------------
  // Anbieter
  // ------------------------------------------------------------------------

  class AnalyseFehler extends Error {}

  const AUFTRAG = 'Analysiere dieses Bild nach den Vorgaben und antworte nur mit dem JSON.';

  async function base64(blob) {
    const puffer = await blob.arrayBuffer();
    const bytes = new Uint8Array(puffer);
    let roh = '';
    // In Haeppchen, sonst sprengt ein grosses Bild den Aufrufstapel.
    for (let i = 0; i < bytes.length; i += 8192) {
      roh += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192));
    }
    return btoa(roh);
  }

  function fehlerText(status, rohtext) {
    if (status === 429) return 'Freikontingent gerade erschoepft — kurz warten.';
    if (status === 401 || status === 403) return 'Schluessel wird abgelehnt. Stimmt er noch?';
    if (status === 404) return 'Modell nicht gefunden. Anderen Modellnamen eintragen.';
    if (status >= 500) return 'Der Anbieter hat gerade eine Stoerung.';
    const kurz = (rohtext || '').replace(/\s+/g, ' ').slice(0, 140);
    return kurz || `Fehler ${status}`;
  }

  async function ueberGemini(blob, prompt, e) {
    const antwort = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(e.gemini_modell)}:generateContent`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-goog-api-key': e.gemini_key },
        body: JSON.stringify({
          system_instruction: { parts: [{ text: prompt }] },
          contents: [{
            role: 'user',
            parts: [
              { inline_data: { mime_type: 'image/jpeg', data: await base64(blob) } },
              { text: AUFTRAG },
            ],
          }],
          // temperature 0, damit dasselbe Bild moeglichst dasselbe Ergebnis gibt.
          generationConfig: { temperature: 0, responseMimeType: 'application/json', maxOutputTokens: 2600 },
        }),
      }
    );

    const rohtext = await antwort.text();
    if (!antwort.ok) throw new AnalyseFehler(fehlerText(antwort.status, rohtext));

    const daten = JSON.parse(rohtext);
    const teile = ((daten.candidates || [])[0] || {}).content || {};
    const text = (teile.parts || []).map((p) => p.text || '').join('');
    if (!text) {
      const grund = ((daten.candidates || [])[0] || {}).finishReason || 'leere Antwort';
      throw new AnalyseFehler(`Modell hat nichts geliefert (${grund}).`);
    }
    return text;
  }

  async function ueberOpenRouter(blob, prompt, e) {
    const antwort = await fetch('https://openrouter.ai/api/v1/chat/completions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${e.openrouter_key}` },
      body: JSON.stringify({
        model: e.openrouter_modell,
        temperature: 0,
        max_tokens: 2600,
        response_format: { type: 'json_object' },
        messages: [
          { role: 'system', content: prompt },
          {
            role: 'user',
            content: [
              { type: 'text', text: AUFTRAG },
              { type: 'image_url', image_url: { url: `data:image/jpeg;base64,${await base64(blob)}` } },
            ],
          },
        ],
      }),
    });

    const rohtext = await antwort.text();
    if (!antwort.ok) throw new AnalyseFehler(fehlerText(antwort.status, rohtext));

    const daten = JSON.parse(rohtext);
    if (daten.error) throw new AnalyseFehler(String(daten.error.message || daten.error).slice(0, 140));
    const text = (((daten.choices || [])[0] || {}).message || {}).content;
    if (!text) throw new AnalyseFehler('Modell hat nichts geliefert.');
    return text;
  }

  async function ueberServer(blob, prompt, e) {
    const basis = (e.server_url || location.origin).replace(/\/+$/, '');
    const daten = new FormData();
    daten.append('bild', blob, 'kopf.jpg');
    daten.append('prompt', prompt);

    const antwort = await fetch(`${basis}/api/shisha/proxy`, { method: 'POST', body: daten });
    const rohtext = await antwort.text();
    if (!antwort.ok) throw new AnalyseFehler(fehlerText(antwort.status, rohtext));

    const inhalt = JSON.parse(rohtext);
    if (!inhalt.ok) throw new AnalyseFehler(inhalt.fehler || 'Rechner meldet einen Fehler.');
    return inhalt.text;
  }

  async function modellFragen(blob, prompt) {
    const e = einstellungen();
    try {
      if (e.anbieter === 'gemini') {
        if (!e.gemini_key) throw new AnalyseFehler('Kein Gemini-Schluessel hinterlegt.');
        return await ueberGemini(blob, prompt, e);
      }
      if (e.anbieter === 'openrouter') {
        if (!e.openrouter_key) throw new AnalyseFehler('Kein OpenRouter-Schluessel hinterlegt.');
        return await ueberOpenRouter(blob, prompt, e);
      }
      return await ueberServer(blob, prompt, e);
    } catch (fehler) {
      if (fehler instanceof AnalyseFehler) throw fehler;
      // fetch wirft bei fehlendem Netz einen nackten TypeError.
      throw new AnalyseFehler(navigator.onLine ? `Verbindung gescheitert: ${fehler.message}` : 'Kein Netz.');
    }
  }

  // ------------------------------------------------------------------------
  // Prompt bauen
  // ------------------------------------------------------------------------

  function kontextText(kontext) {
    const ziel = spec.ziele[kontext.ziel] || spec.ziele.balanced;
    const zeilenListe = [
      `- Ziel des Nutzers: ${ziel.name} — ${ziel.prioritaet}`,
      `- Bauempfehlung fuer dieses Ziel: ${ziel.bau}`,
    ];
    const felder = [
      ['Kopfmodell', kontext.kopf_modell],
      ['Tabakmarke', kontext.tabak_marke],
      ['Tabaksorte', kontext.tabak_sorte],
      ['HMD', kontext.hmd],
      ['Kohlen', kontext.kohlen],
      ['Anmerkung', kontext.notiz],
    ];
    felder.forEach(([name, wert]) => { if (wert) zeilenListe.push(`- ${name}: ${wert}`); });

    const offen = fehlendeAngaben(kontext);
    if (offen.length) zeilenListe.push(`- Nicht angegeben: ${offen.join(', ')}`);
    return zeilenListe.join('\n');
  }

  function fehlendeAngaben(kontext) {
    const offen = [];
    if (!kontext.kopf_modell) offen.push('Kopfmodell');
    if (!kontext.tabak_marke && !kontext.tabak_sorte) offen.push('Tabak');
    if (!kontext.hmd) offen.push('HMD');
    if (!kontext.kohlen) offen.push('Kohlenanzahl');
    return offen;
  }

  function promptBauen({ modus, kontext, phase, verlauf, lernen }) {
    const w = spec.wissen;
    const p = spec.prompt;
    const teile = [
      zeilen(p.rolle),
      `Kopftypen:\n${zeilen(w.kopftypen)}\n\nTabakphysik:\n${zeilen(w.tabakphysik)}\n\n` +
      `Mengen und Hoehen:\n${zeilen(w.mengen)}\n\nTypische Fehler:\n${zeilen(w.fehler)}`,
      zeilen(p.wahrheit),
      zeilen(p.sicht),
      `Angaben des Nutzers:\n${kontextText(kontext)}`,
      zeilen(p.bewertung),
      zeilen(p.marker),
    ];

    if (modus === 'live') {
      const info = spec.phasen.find((ph) => ph.key === phase) || spec.phasen[0];
      teile.push(
        `Der Nutzer baut gerade. Phase laut App: ${info.name}\n` +
        `Ziel dieser Phase: ${info.ziel}\n` +
        `Achte besonders auf: ${info.achte_auf}\n` +
        'Bewerte trotzdem alle Kategorien — was in dieser Phase noch nicht beurteilbar\n' +
        'ist, bekommt eine niedrige Sicherheit statt einer erfundenen Zahl.'
      );
    } else {
      teile.push(zeilen(p.voll));
    }

    if (lernen) teile.push(`Was du aus frueheren Sessions dieses Nutzers weisst:\n${lernen}`);
    if (verlauf) {
      teile.push(`Deine letzten Beobachtungen zu diesem Kopf (nicht wiederholen, weiterfuehren):\n${verlauf}`);
    }

    teile.push(zeilen(p.schema));
    if (modus === 'live') teile.push(zeilen(p.live_kurz));
    return teile.join('\n\n');
  }

  // ------------------------------------------------------------------------
  // Antwort auslesen und geradeziehen
  // ------------------------------------------------------------------------

  function jsonAusText(text) {
    text = String(text || '').trim();
    if (!text) throw new AnalyseFehler('leere Antwort');

    if (text.startsWith('```')) {
      const stuecke = text.split('```');
      if (stuecke.length >= 2) text = stuecke[1].replace(/^json\s*/i, '');
    }

    try {
      return JSON.parse(text);
    } catch (_) { /* weiter unten von Hand suchen */ }

    // Klammern zaehlen, damit Text drumherum nicht stoert.
    const start = text.indexOf('{');
    if (start < 0) throw new AnalyseFehler(`kein JSON in der Antwort: ${text.slice(0, 120)}`);
    let tiefe = 0;
    let imText = false;
    let maskiert = false;
    for (let i = start; i < text.length; i++) {
      const z = text[i];
      if (imText) {
        if (maskiert) maskiert = false;
        else if (z === '\\') maskiert = true;
        else if (z === '"') imText = false;
        continue;
      }
      if (z === '"') imText = true;
      else if (z === '{') tiefe++;
      else if (z === '}' && --tiefe === 0) {
        try {
          return JSON.parse(text.slice(start, i + 1));
        } catch (fehler) {
          throw new AnalyseFehler(`JSON unlesbar: ${fehler.message}`);
        }
      }
    }
    throw new AnalyseFehler('JSON unvollstaendig');
  }

  const zahl = (wert, min, max, standard = 0) => {
    const n = Number(wert);
    return Number.isFinite(n) ? Math.max(min, Math.min(max, n)) : standard;
  };

  const zahlOderNull = (wert, min, max) => {
    if (wert === null || wert === undefined || wert === '') return null;
    const n = Number(wert);
    return Number.isFinite(n) ? Math.max(min, Math.min(max, n)) : null;
  };

  const text = (wert, laenge = 240) => (typeof wert === 'string' ? wert.trim().slice(0, laenge) : '');
  const textOderNull = (wert, laenge = 80) => text(wert, laenge) || null;

  const wahl = (wert, erlaubt, standard) => {
    const k = text(wert, 32).toLowerCase().replace(/ /g, '_');
    return erlaubt.includes(k) ? k : standard;
  };

  function boolOderNull(wert) {
    if (typeof wert === 'boolean') return wert;
    if (typeof wert === 'string') {
      const k = wert.trim().toLowerCase();
      if (['true', 'ja', 'yes', '1'].includes(k)) return true;
      if (['false', 'nein', 'no', '0'].includes(k)) return false;
    }
    return null;
  }

  const objekt = (wert) => (wert && typeof wert === 'object' && !Array.isArray(wert) ? wert : {});
  const liste = (wert) => (Array.isArray(wert) ? wert : []);

  function gesamtscore(scores) {
    let summe = 0;
    Object.entries(spec.gewichte).forEach(([kategorie, gewicht]) => {
      summe += (Number(scores[kategorie]) || 0) * gewicht;
    });
    return Math.round(Math.max(0, Math.min(100, summe)));
  }

  function stufe(score) {
    const treffer = spec.stufen.find((s) => score >= s.ab) || spec.stufen[spec.stufen.length - 1];
    return treffer;
  }

  function normalisiere(roh, live) {
    const status = wahl(roh.analysis_status, spec.analyse_status, 'ok');

    // Erst die Beobachtungen, dann die Bewertung — in der Reihenfolge braucht die
    // Plausibilitaetspruefung sie auch.
    const bild = bildqualitaet(objekt(roh.bildqualitaet));
    const tabak = tabakDaten(objekt(roh.tabak));
    const luft = airflowDaten(objekt(roh.airflow));
    const haube = hmdDaten(objekt(roh.hmd));
    const gemeldet = probleme(liste(roh.probleme), live);

    const rohScores = objekt(roh.scores);
    const scores = {};
    Object.keys(spec.gewichte).forEach((feld) => { scores[feld] = Math.round(zahl(rohScores[feld], 0, 100)); });

    // Widersprueche geradeziehen, bevor gerechnet wird.
    const kappungen = plausibilitaetAnwenden(scores, { tabak, airflow: luft, hmd: haube, probleme: gemeldet });

    const gesamt = gesamtscore(scores);
    const stufeInfo = stufe(gesamt);

    const ergebnis = {
      analysis_status: status,
      befund: text(roh.befund, 400),
      bildqualitaet: bild,
      kopf: kopfDaten(objekt(roh.kopf)),
      tabak,
      airflow: luft,
      hmd: haube,
      kohle: kohleDaten(objekt(roh.kohle)),
      scores,
      kappungen,
      gesamtscore: gesamt,
      stufe: stufeInfo.key,
      stufe_text: stufeInfo.text,
      probleme: gemeldet,
      optimierungen: optimierungen(liste(roh.optimierungen), live),
      ar_marker: marker(liste(roh.ar_marker), live),
      prognose: prognose(objekt(roh.prognose), gesamt),
      confidence: confidence(objekt(roh.confidence)),
      rueckfrage: textOderNull(roh.rueckfrage, 160),
      coach_satz: text(roh.coach_satz, 200),
    };

    // Bei duennem Bild oder wackliger Sicherheit ist die Note ein Anhaltspunkt,
    // kein Urteil — sie wird gezeigt, aber nicht in den Konsens aufgenommen.
    ergebnis.vorlaeufig = status === 'ok' && (
      Math.min(bild.schaerfe, bild.licht) < spec.plausibilitaet.bildqualitaet_min
      || bild.kopf_vollstaendig === false
      || ergebnis.confidence.gesamt < 40
    );

    // Ohne erkennbaren Kopf ist eine Note bedeutungslos — dann lieber keine.
    if (status !== 'ok') {
      ergebnis.gesamtscore = null;
      ergebnis.stufe = null;
      ergebnis.stufe_text = null;
    }
    return ergebnis;
  }

  /* Zieht Widersprueche zwischen Beobachtung und Bewertung gerade.
   *
   * Modelle neigen dazu, ein kritisches Problem zu melden und die betroffene
   * Kategorie trotzdem mit 80 zu bewerten. Wer sagt "der Tabak beruehrt das HMD",
   * darf das Hitzemanagement nicht gut nennen. Die Obergrenzen stehen in
   * spec.json und werden hier angewendet — sichtbar, damit im Report steht,
   * warum eine Zahl kleiner ausfaellt als vom Modell gemeldet.
   */
  function plausibilitaetAnwenden(scores, daten) {
    const grenzen = spec.plausibilitaet;
    const kappungen = [];

    const kappen = (kategorie, hoechstens, grund) => {
      if (!(kategorie in scores) || scores[kategorie] <= hoechstens) return;
      kappungen.push({ kategorie, von: scores[kategorie], auf: hoechstens, grund });
      scores[kategorie] = hoechstens;
    };

    daten.probleme.forEach((problem) => {
      if (problem.severity === 'critical') {
        kappen(problem.kategorie, grenzen.kappe_kritisch, `kritisch gemeldet: ${problem.titel}`);
      } else if (problem.severity === 'high') {
        kappen(problem.kategorie, grenzen.kappe_hoch, `schwerwiegend gemeldet: ${problem.titel}`);
      }
    });

    if (daten.tabak.randkontakt) {
      kappen('fuellhoehe', grenzen.kappe_randkontakt, 'Tabak beruehrt den Rand');
    }
    // Ueber den Rand gebaut ist nur mit HMD sinnvoll, sonst brennt es an der Folie an.
    if (daten.tabak.ueber_rand && !daten.hmd.erkannt) {
      kappen('fuellhoehe', grenzen.kappe_ueber_rand, 'Tabak steht ueber dem Rand, ohne HMD');
    }
    if (daten.hmd.kontakt_tabak === true) {
      kappen('hitzemanagement', grenzen.kappe_hmd_kontakt, 'Tabak beruehrt das HMD');
    }
    if (daten.airflow.blockade_risiko === 'high') {
      kappen('airflow', grenzen.kappe_airflow_hoch, 'hohes Blockaderisiko');
    }
    if (daten.airflow.zentrale_oeffnung_frei === false) {
      kappen('airflow', grenzen.kappe_oeffnung_verdeckt, 'zentrale Oeffnung verdeckt');
    }

    return kappungen;
  }

  const bildqualitaet = (roh) => ({
    schaerfe: Math.round(zahl(roh.schaerfe, 0, 100)),
    licht: Math.round(zahl(roh.licht, 0, 100)),
    perspektive: wahl(roh.perspektive, ['oben', 'schraeg', 'seitlich', 'unklar'], 'unklar'),
    kopf_vollstaendig: roh.kopf_vollstaendig !== false,
    hinweis: text(roh.hinweis, 140),
  });

  const kopfDaten = (roh) => ({
    art: wahl(roh.art, spec.kopfarten, 'unbekannt'),
    modell: textOderNull(roh.modell, 60),
    geometrie: text(roh.geometrie, 140),
    zentrale_oeffnung_sichtbar: boolOderNull(roh.zentrale_oeffnung_sichtbar),
    quelle: wahl(roh.quelle, spec.quellen, 'unknown'),
    confidence: Math.round(zahl(roh.confidence, 0, 100)),
  });

  const tabakDaten = (roh) => ({
    // Negative Werte heissen: Tabak steht ueber dem Rand.
    fuellhoehe_mm: zahlOderNull(roh.fuellhoehe_mm, -15, 30),
    fuellhoehe_quelle: wahl(roh.fuellhoehe_quelle, spec.quellen, 'unknown'),
    dichte: Math.round(zahl(roh.dichte, 0, 100)),
    gleichmaessigkeit: Math.round(zahl(roh.gleichmaessigkeit, 0, 100)),
    klumpen: Boolean(roh.klumpen),
    luecken: Boolean(roh.luecken),
    randkontakt: Boolean(roh.randkontakt),
    ueber_rand: Boolean(roh.ueber_rand),
    // Gramm sind aus einem Foto nicht bestimmbar — hoechstens eine Spanne als Text.
    menge_gramm: textOderNull(roh.menge_gramm, 40),
    quelle: wahl(roh.quelle, spec.quellen, 'unknown'),
    confidence: Math.round(zahl(roh.confidence, 0, 100)),
  });

  const airflowDaten = (roh) => ({
    zentrale_oeffnung_frei: boolOderNull(roh.zentrale_oeffnung_frei),
    blockade_risiko: wahl(roh.blockade_risiko, ['low', 'medium', 'high'], 'low'),
    notiz: text(roh.notiz, 140),
    confidence: Math.round(zahl(roh.confidence, 0, 100)),
  });

  const hmdDaten = (roh) => ({
    erkannt: Boolean(roh.erkannt),
    modell: textOderNull(roh.modell, 60),
    zentriert: boolOderNull(roh.zentriert),
    abstand_mm: zahlOderNull(roh.abstand_mm, 0, 40),
    kontakt_tabak: boolOderNull(roh.kontakt_tabak),
    confidence: Math.round(zahl(roh.confidence, 0, 100)),
  });

  function kohleDaten(roh) {
    const status = wahl(roh.status, ['visible', 'not_visible'], 'not_visible');
    return {
      status,
      anzahl: status === 'not_visible' ? null : zahlOderNull(roh.anzahl, 0, 12),
      position: text(roh.position, 120),
      hotspot_risiko: wahl(roh.hotspot_risiko, ['low', 'medium', 'high', 'unknown'], 'unknown'),
      confidence: Math.round(zahl(roh.confidence, 0, 100)),
    };
  }

  function probleme(roh, live) {
    const kategorien = Object.keys(spec.gewichte);
    const aktionen = Object.keys(spec.aktionen);
    const ergebnis = [];

    roh.forEach((eintrag) => {
      if (!eintrag || typeof eintrag !== 'object') return;
      const titel = text(eintrag.titel, 80);
      if (!titel) return;
      const severity = wahl(eintrag.severity, spec.schweregrade, 'medium');
      ergebnis.push({
        id: text(eintrag.id, 40) || titel.toLowerCase().replace(/ /g, '_').slice(0, 40),
        severity,
        symbol: spec.schwere_symbol[severity],
        kategorie: wahl(eintrag.kategorie, kategorien, 'tabak_verteilung'),
        titel,
        beschreibung: text(eintrag.beschreibung, 300),
        confidence: Math.round(zahl(eintrag.confidence, 0, 100)),
        aktion: wahl(eintrag.aktion, aktionen, 'redistribute_tobacco'),
      });
    });

    // Kritisches zuerst — die Oberflaeche zeigt nur die ersten Eintraege.
    ergebnis.sort((a, b) =>
      spec.schweregrade.indexOf(a.severity) - spec.schweregrade.indexOf(b.severity) ||
      b.confidence - a.confidence);
    return ergebnis.slice(0, live ? 2 : 8);
  }

  function optimierungen(roh, live) {
    const aktionen = Object.keys(spec.aktionen);
    const ergebnis = [];

    roh.forEach((eintrag, index) => {
      if (!eintrag || typeof eintrag !== 'object') return;
      const inhalt = text(eintrag.text, 300);
      if (!inhalt) return;
      const aktion = wahl(eintrag.aktion, aktionen, 'redistribute_tobacco');
      ergebnis.push({
        schritt: Math.round(zahl(eintrag.schritt, 1, 20, index + 1)),
        aktion,
        aktion_text: spec.aktionen[aktion],
        bereich: text(eintrag.bereich, 120),
        text: inhalt,
        wirkung: text(eintrag.wirkung, 160),
      });
    });

    ergebnis.sort((a, b) => a.schritt - b.schritt);
    ergebnis.forEach((eintrag, index) => { eintrag.schritt = index + 1; });
    return ergebnis.slice(0, live ? 3 : 6);
  }

  function marker(roh, live) {
    const aktionen = Object.keys(spec.aktionen);
    const ergebnis = [];

    roh.forEach((eintrag) => {
      if (!eintrag || typeof eintrag !== 'object') return;
      let x = zahlOderNull(eintrag.x, -5, 5);
      let y = zahlOderNull(eintrag.y, -5, 5);
      if (x === null || y === null) return;
      // Knapp daneben wird zurechtgerueckt, weit daneben ist geraten und fliegt raus.
      if (x < -0.2 || x > 1.2 || y < -0.2 || y > 1.2) return;
      x = Math.min(Math.max(x, 0), 1);
      y = Math.min(Math.max(y, 0), 1);
      const breite = Math.min(zahl(eintrag.w, 0.02, 1, 0.15), 1);
      const hoehe = Math.min(zahl(eintrag.h, 0.02, 1, 0.15), 1);

      ergebnis.push({
        typ: wahl(eintrag.typ, spec.marker_typen, 'distribute'),
        x: Math.round(Math.min(x, 1 - breite) * 1e4) / 1e4,
        y: Math.round(Math.min(y, 1 - hoehe) * 1e4) / 1e4,
        w: breite,
        h: hoehe,
        label: text(eintrag.label, 40),
        aktion: wahl(eintrag.aktion, aktionen, 'redistribute_tobacco'),
      });
    });

    return ergebnis.slice(0, live ? 4 : 8);
  }

  function prognose(roh, gesamt) {
    const richtungen = ['hoch', 'gleich', 'runter'];
    const verbesserung = objekt(roh.verbesserung);
    const nachher = Math.round(zahl(roh.score_nach_optimierung, 0, 100, gesamt));
    return {
      // Nach der Optimierung soll es nicht schlechter werden.
      score_nach_optimierung: Math.max(nachher, gesamt),
      geschmack: wahl(roh.geschmack, richtungen, 'gleich'),
      rauch: wahl(roh.rauch, richtungen, 'gleich'),
      dauer: wahl(roh.dauer, richtungen, 'gleich'),
      hitzerisiko: wahl(roh.hitzerisiko, richtungen, 'gleich'),
      verbesserung: {
        tabak_verteilung: Math.round(zahl(verbesserung.tabak_verteilung, 0, 100)),
        hitzemanagement: Math.round(zahl(verbesserung.hitzemanagement, 0, 100)),
        airflow: Math.round(zahl(verbesserung.airflow, 0, 100)),
      },
    };
  }

  function confidence(roh) {
    const felder = ['gesamt', 'kopf_erkennung', 'tabak_analyse', 'fuellhoehe', 'airflow',
                    'hitzemanagement', 'optimierung'];
    const werte = {};
    felder.forEach((feld) => { werte[feld] = Math.round(zahl(roh[feld], 0, 100)); });
    if (!werte.gesamt) {
      const andere = felder.filter((f) => f !== 'gesamt').map((f) => werte[f]).filter(Boolean);
      werte.gesamt = andere.length ? Math.round(andere.reduce((a, b) => a + b, 0) / andere.length) : 0;
    }
    return werte;
  }

  // ------------------------------------------------------------------------
  // Lernspeicher (bleibt auf diesem Geraet)
  // ------------------------------------------------------------------------

  const MAX_SESSIONS = 60;

  function profilLaden() {
    try {
      const daten = JSON.parse(localStorage.getItem('shisha.profil') || '{}');
      return Array.isArray(daten.sessions) ? daten : { sessions: [] };
    } catch (_) {
      return { sessions: [] };
    }
  }

  function feedbackMerken(eintrag) {
    const daten = profilLaden();
    daten.sessions.push({ ...eintrag, zeit: Date.now() });
    daten.sessions = daten.sessions.slice(-MAX_SESSIONS);
    localStorage.setItem('shisha.profil', JSON.stringify(daten));
    return daten;
  }

  /* Rechnet das Nutzerfeedback in eine Note von 0 bis 100 um: Geschmack und Rauch
   * ziehen hoch, Kratzen und falsche Hitze ziehen runter. Grob, reicht fuer einen
   * Trend. */
  function erlebteNote(session) {
    if (session.geschmack === undefined && session.rauch === undefined) return null;
    let punkte = 0;
    let gewicht = 0;
    if (session.geschmack !== undefined) { punkte += (session.geschmack / 5) * 40; gewicht += 40; }
    if (session.rauch !== undefined) { punkte += (session.rauch / 5) * 30; gewicht += 30; }
    if (session.kratzen !== undefined) { punkte += (1 - (session.kratzen - 1) / 4) * 20; gewicht += 20; }
    if (session.hitze !== undefined) { punkte += (1 - Math.abs(session.hitze - 3) / 2) * 10; gewicht += 10; }
    return gewicht ? Math.round((punkte / gewicht) * 1000) / 10 : null;
  }

  function treffsicherheit() {
    const sessions = profilLaden().sessions.filter((s) => typeof s.score === 'number');
    const vergleichbar = sessions.filter((s) => erlebteNote(s) !== null);
    if (!vergleichbar.length) {
      return { sessions: sessions.length, vergleichbar: 0, abweichung: null, genauigkeit: null };
    }
    const abweichungen = vergleichbar.map((s) => Math.abs(s.score - erlebteNote(s)));
    const mittel = abweichungen.reduce((a, b) => a + b, 0) / abweichungen.length;
    return {
      sessions: sessions.length,
      vergleichbar: vergleichbar.length,
      abweichung: Math.round(mittel * 10) / 10,
      genauigkeit: Math.round(Math.max(0, 100 - mittel) * 10) / 10,
    };
  }

  function lernkontext() {
    const sessions = profilLaden().sessions.slice(-6);
    if (!sessions.length) return '';
    const namen = spec.feedback_felder;
    const zeilenListe = sessions.map((s) => {
      const teile = [];
      if (s.ziel && spec.ziele[s.ziel]) teile.push(`Ziel ${spec.ziele[s.ziel].name}`);
      if (typeof s.score === 'number') teile.push(`vorhergesagt ${s.score}/100`);
      Object.keys(namen).forEach((feld) => {
        if (s[feld] !== undefined && s[feld] !== '') teile.push(`${namen[feld]}: ${s[feld]}`);
      });
      return teile.length ? `- ${teile.join(', ')}` : '';
    }).filter(Boolean);

    if (!zeilenListe.length) return '';
    return `${zeilenListe.join('\n')}\n${zeilen(spec.prompt.lernen)}`;
  }

  // ------------------------------------------------------------------------
  // Sitzung — haelt Phase und Verlauf zusammen
  // ------------------------------------------------------------------------

  const HINWEIS_SPERRE = 22000;  // ms — nicht denselben Satz zweimal sagen
  const FERTIG_SCHWELLE = 2;     // so viele saubere Bilder, dann naechste Phase
  const PHASE_FERTIG_SCORE = 78;

  class Sitzung {
    constructor(kontext) {
      this.kontext = { ziel: 'balanced', kopf_modell: '', tabak_marke: '', tabak_sorte: '',
                       hmd: '', kohlen: '', notiz: '', ...(kontext || {}) };
      this.phase = spec.phasen[0].key;
      this.verlauf = [];
      this.letzte = null;
      this.analyse = null;
      this.gesagt = new Map();
      this.fertigZaehler = 0;
    }

    get phaseInfo() {
      return spec.phasen.find((p) => p.key === this.phase) || spec.phasen[0];
    }

    phaseSetzen(key) {
      if (spec.phasen.some((p) => p.key === key) && key !== this.phase) {
        this.phase = key;
        this.fertigZaehler = 0;
        this.gesagt.clear();
      }
    }

    weiter() {
      const index = spec.phasen.findIndex((p) => p.key === this.phase);
      this.phaseSetzen(spec.phasen[Math.min(index + 1, spec.phasen.length - 1)].key);
      return this.phase;
    }

    fortschritt() {
      const index = spec.phasen.findIndex((p) => p.key === this.phase);
      return (index + 1) / spec.phasen.length;
    }

    /** Ein fertig normalisiertes Ergebnis einsortieren. */
    aufnehmen(ergebnis, autoWeiter = true) {
      const eintrag = { ...ergebnis, zeit: Date.now() };
      eintrag.sprechen = this.darfSprechen(eintrag.coach_satz);

      let gewechselt = false;
      if (this.phaseErledigt(eintrag)) {
        this.fertigZaehler++;
        const letztePhase = this.phase === spec.phasen[spec.phasen.length - 1].key;
        if (autoWeiter && this.fertigZaehler >= FERTIG_SCHWELLE && !letztePhase) {
          this.weiter();
          gewechselt = true;
        }
      } else {
        this.fertigZaehler = 0;
      }

      eintrag.phase = this.phase;
      eintrag.phase_name = this.phaseInfo.name;
      eintrag.phase_gewechselt = gewechselt;
      eintrag.fortschritt = this.fortschritt();

      this.letzte = eintrag;
      this.verlauf.push(eintrag);
      if (this.verlauf.length > 12) this.verlauf = this.verlauf.slice(-12);

      // Erst nach dem Einsortieren, damit das eigene Ergebnis mitzaehlt.
      eintrag.konsens = this.konsens();
      return eintrag;
    }

    /* Urteil ueber mehrere Bilder statt ueber eines.
     *
     * Ein Einzelbild schwankt: eine Spiegelung, ein anderer Winkel, und das
     * Modell liegt fuenf Punkte daneben. Der Median der letzten Bilder ist
     * belastbarer und springt nicht bei jedem Frame. Bilder, die als vorlaeufig
     * markiert sind (unscharf, dunkel, unsicher), zaehlen nicht mit.
     */
    konsens() {
      const fenster = spec.plausibilitaet.konsens_bilder;
      const werte = this.verlauf
        .filter((e) => e.analysis_status === 'ok' && !e.vorlaeufig && e.gesamtscore !== null)
        .slice(-fenster)
        .map((e) => e.gesamtscore);

      if (!werte.length) return null;

      const sortiert = [...werte].sort((a, b) => a - b);
      const mitte = Math.floor(sortiert.length / 2);
      const median = sortiert.length % 2
        ? sortiert[mitte]
        : Math.round((sortiert[mitte - 1] + sortiert[mitte]) / 2);
      const spanne = sortiert[sortiert.length - 1] - sortiert[0];

      return {
        score: median,
        stufe_text: stufe(median).text,
        bilder: werte.length,
        spanne,
        // Wenig Streuung heisst: das Urteil traegt.
        stabil: werte.length >= 3 && spanne <= spec.plausibilitaet.konsens_spanne,
        // Positiv = es wird besser.
        trend: werte.length >= 2 ? werte[werte.length - 1] - werte[0] : 0,
      };
    }

    phaseErledigt(ergebnis) {
      if (ergebnis.analysis_status !== 'ok') return false;
      if (ergebnis.probleme.some((p) => p.severity === 'critical' || p.severity === 'high')) return false;
      return Boolean(ergebnis.gesamtscore >= PHASE_FERTIG_SCORE && ergebnis.confidence.gesamt >= 50);
    }

    darfSprechen(satz) {
      const schluessel = (satz || '').trim().toLowerCase();
      if (!schluessel) return false;
      const jetzt = Date.now();
      if (jetzt - (this.gesagt.get(schluessel) || 0) < HINWEIS_SPERRE) return false;
      this.gesagt.set(schluessel, jetzt);
      this.gesagt.forEach((zeit, alt) => {
        if (jetzt - zeit > HINWEIS_SPERRE * 4) this.gesagt.delete(alt);
      });
      return true;
    }

    verlaufstext(anzahl = 4) {
      const zeilenListe = [];
      this.verlauf.slice(-anzahl).forEach((eintrag) => {
        if (eintrag.analysis_status !== 'ok') return;
        const teile = [`Phase ${eintrag.phase}`];
        if (eintrag.gesamtscore !== null) teile.push(`Score ${eintrag.gesamtscore}`);
        if (eintrag.tabak.fuellhoehe_mm !== null) {
          teile.push(`Fuellhoehe ca. ${eintrag.tabak.fuellhoehe_mm} mm unter Rand`);
        }
        if (eintrag.tabak.dichte) teile.push(`Dichte ${eintrag.tabak.dichte}`);
        const probleme = eintrag.probleme.map((p) => p.titel);
        if (probleme.length) teile.push(`Probleme: ${probleme.join(', ')}`);
        if (eintrag.kappungen && eintrag.kappungen.length) {
          teile.push(`von der App heruntergestuft: ${eintrag.kappungen.map((k) => k.grund).join(', ')}`);
        }
        if (eintrag.coach_satz) teile.push(`gesagt: "${eintrag.coach_satz}"`);
        zeilenListe.push(`- ${teile.join('; ')}`);
      });

      const kopf = (this.letzte || {}).kopf;
      if (kopf && kopf.art && kopf.art !== 'unbekannt') {
        zeilenListe.unshift(`- Kopf bisher erkannt: ${kopf.art}${kopf.modell ? ` (${kopf.modell})` : ''}`);
      }
      return zeilenListe.join('\n');
    }

    /** Bild analysieren lassen und das Ergebnis einsortieren. */
    async analysieren(blob, modus = 'live') {
      const prompt = promptBauen({
        modus,
        kontext: this.kontext,
        phase: this.phase,
        verlauf: this.verlaufstext(modus === 'live' ? 4 : 8),
        lernen: lernkontext(),
      });

      const begonnen = performance.now();
      const rohtext = await modellFragen(blob, prompt);
      const ergebnis = normalisiere(jsonAusText(rohtext), modus === 'live');
      ergebnis.dauer = Math.round(performance.now() - begonnen) / 1000;

      if (modus === 'live') return this.aufnehmen(ergebnis);

      ergebnis.phase = this.phase;
      ergebnis.kontext = { ...this.kontext };
      this.analyse = ergebnis;
      this.letzte = ergebnis;
      return ergebnis;
    }
  }

  // ------------------------------------------------------------------------
  // Sprachbefehle
  // ------------------------------------------------------------------------

  /* Ordnet gesprochenen Text einem Befehl zu.
   *
   * Die Spracherkennung liefert selten exakt das erwartete Wort — sie hoert
   * "weiter machen bitte" statt "weiter". Deshalb wird auf Enthaltensein
   * geprueft und der laengste Treffer gewinnt, damit "kamera an" nicht von "an"
   * geschlagen wird.
   */
  function befehlErkennen(gesagt) {
    const text = String(gesagt || '').toLowerCase().trim();
    if (!text) return null;

    let treffer = null;
    Object.entries(spec.sprachbefehle || {}).forEach(([befehl, woerter]) => {
      woerter.forEach((wort) => {
        if (text.includes(wort) && (!treffer || wort.length > treffer.wort.length)) {
          treffer = { befehl, wort };
        }
      });
    });
    return treffer ? treffer.befehl : null;
  }

  // ------------------------------------------------------------------------

  return {
    specLaden,
    befehlErkennen,
    get spec() { return spec; },
    einstellungen,
    einstellungenSpeichern,
    bereit,
    anbieterName,
    fehlendeAngaben,
    promptBauen,
    jsonAusText,
    normalisiere,
    gesamtscore,
    stufe,
    Sitzung,
    AnalyseFehler,
    profil: { laden: profilLaden, merken: feedbackMerken, treffsicherheit, lernkontext, erlebteNote },
  };
})();

// Fuer den Test unter Node — im Browser gibt es kein module.
if (typeof module !== 'undefined' && module.exports) module.exports = Engine;
