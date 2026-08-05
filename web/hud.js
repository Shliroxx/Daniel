/* JARVIS HUD — WebSocket-Client, Push-to-Talk, Audio-Visualizer. */
(() => {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const el = {
    log: $("log"),
    activity: $("activity"),
    stateLabel: $("state-label"),
    stateHint: $("state-hint"),
    reactor: $("reactor"),
    linkStatus: $("link-status"),
    linkText: $("link-text"),
    modelLabel: $("model-label"),
    input: $("input"),
    composer: $("composer"),
    talk: $("btn-talk"),
    reset: $("btn-reset"),
    stop: $("btn-stop"),
    player: $("player"),
    viz: $("viz"),
  };

  const STATE_TEXT = {
    idle: ["Bereit", "Tippe unten oder halte die Taste gedrückt"],
    listening: ["Höre zu", "Sag „Hey Jarvis“"],
    recording: ["Aufnahme", "Ich höre …"],
    thinking: ["Denke nach", "Einen Moment"],
    speaking: ["Spreche", ""],
  };

  let socket = null;
  let reconnectDelay = 800;
  let streamingEntry = null;
  let level = 0;

  /* ---------------- WebSocket ---------------- */

  function connect() {
    const proto = location.protocol === "https:" ? "wss" : "ws";
    socket = new WebSocket(`${proto}://${location.host}/ws`);

    socket.onopen = () => {
      reconnectDelay = 800;
      el.linkStatus.dataset.state = "up";
      el.linkText.textContent = "verbunden";
      loadStatus();
    };

    socket.onclose = () => {
      el.linkStatus.dataset.state = "down";
      el.linkText.textContent = "getrennt";
      setTimeout(connect, reconnectDelay);
      reconnectDelay = Math.min(reconnectDelay * 1.6, 10000);
    };

    socket.onmessage = (event) => {
      let msg;
      try { msg = JSON.parse(event.data); } catch { return; }
      handle(msg);
    };
  }

  function send(payload) {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(payload));
      return true;
    }
    return false;
  }

  function handle(msg) {
    switch (msg.type) {
      case "state":
        setState(msg.state);
        break;
      case "user":
        finishStreaming();
        addEntry("user", "Du", msg.text);
        break;
      case "delta":
        appendStreaming(msg.text);
        break;
      case "assistant":
        finishStreaming(msg.text);
        break;
      case "thinking":
        addActivity("think", msg.text, true);
        break;
      case "tool_start":
        addActivity("tool", `→ ${msg.name} ${compact(msg.input)}`);
        break;
      case "tool_end":
        addActivity(`tool ${msg.ok ? "ok" : "fail"}`, `${msg.ok ? "✓" : "✕"} ${msg.name}: ${trim(msg.result, 220)}`);
        break;
      case "error":
        finishStreaming();
        addEntry("error", "Fehler", msg.message);
        break;
      case "level":
        level = Math.min(1, msg.rms * 14);
        break;
      case "audio":
        playAudio(msg.data);
        break;
      case "wake":
        pulse();
        break;
      case "reset":
        el.log.innerHTML = "";
        el.activity.innerHTML = '<p class="muted">Neu gestartet.</p>';
        break;
    }
  }

  async function loadStatus() {
    try {
      const res = await fetch("/api/status");
      const data = await res.json();
      el.modelLabel.textContent = data.fehler
        ? "nicht einsatzbereit"
        : `${data.model} · ${data.werkzeuge.length} Werkzeuge`;
      if (data.fehler && !el.log.querySelector(".entry.error")) {
        addEntry("error", "Setup", data.fehler);
      }
    } catch { /* egal */ }
  }

  /* ---------------- Darstellung ---------------- */

  function setState(state) {
    const [label, hint] = STATE_TEXT[state] || [state, ""];
    el.stateLabel.textContent = label;
    el.stateHint.textContent = hint;
    el.reactor.dataset.state = state;
  }

  function addEntry(cls, who, text) {
    const node = document.createElement("div");
    node.className = `entry ${cls}`;
    node.innerHTML = `<span class="who"></span><span class="body"></span>`;
    node.querySelector(".who").textContent = who;
    node.querySelector(".body").textContent = text;
    el.log.appendChild(node);
    el.log.scrollTop = el.log.scrollHeight;
    return node;
  }

  function appendStreaming(text) {
    if (!streamingEntry) {
      streamingEntry = addEntry("jarvis", "Jarvis", "");
    }
    streamingEntry.querySelector(".body").textContent += text;
    el.log.scrollTop = el.log.scrollHeight;
  }

  function finishStreaming(finalText) {
    if (streamingEntry) {
      if (finalText) streamingEntry.querySelector(".body").textContent = finalText;
      streamingEntry = null;
    } else if (finalText) {
      addEntry("jarvis", "Jarvis", finalText);
    }
  }

  let lastThink = null;
  function addActivity(cls, text, isThinking) {
    if (el.activity.querySelector(".muted")) el.activity.innerHTML = "";
    if (isThinking && lastThink) {
      lastThink.textContent += text;
      el.activity.scrollTop = el.activity.scrollHeight;
      return;
    }
    const node = document.createElement("div");
    node.className = `act ${cls}`;
    node.textContent = text;
    el.activity.appendChild(node);
    lastThink = isThinking ? node : null;
    while (el.activity.children.length > 120) el.activity.removeChild(el.activity.firstChild);
    el.activity.scrollTop = el.activity.scrollHeight;
  }

  const compact = (obj) => {
    if (!obj || !Object.keys(obj).length) return "";
    return trim(JSON.stringify(obj), 120);
  };
  const trim = (s, n) => (s && s.length > n ? s.slice(0, n) + "…" : s || "");

  function pulse() {
    level = 1;
    el.reactor.animate(
      [{ filter: "brightness(1)" }, { filter: "brightness(1.9)" }, { filter: "brightness(1)" }],
      { duration: 520, easing: "ease-out" }
    );
  }

  /* ---------------- Audio-Ausgabe ---------------- */

  function playAudio(base64) {
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const url = URL.createObjectURL(new Blob([bytes], { type: "audio/wav" }));
    el.player.src = url;
    el.player.onended = () => URL.revokeObjectURL(url);
    // Auf iOS schlägt Autoplay ohne vorherige Nutzerinteraktion fehl — das ist ok,
    // der Text steht im Verlauf.
    el.player.play().catch(() => {});
  }

  /* ---------------- Push-to-Talk ---------------- */

  let recorder = null;
  let chunks = [];
  let micStream = null;

  async function startRecording() {
    if (recorder) return;
    try {
      micStream = micStream || (await navigator.mediaDevices.getUserMedia({ audio: true }));
    } catch (err) {
      addEntry("error", "Fehler", "Kein Mikrofonzugriff: " + err.message);
      return;
    }
    chunks = [];
    const mime = MediaRecorder.isTypeSupported("audio/webm") ? "audio/webm" : "audio/mp4";
    recorder = new MediaRecorder(micStream, { mimeType: mime });
    recorder.ondataavailable = (e) => e.data.size && chunks.push(e.data);
    recorder.onstop = uploadRecording;
    recorder.start();
    el.talk.classList.add("recording");
    setState("recording");
  }

  function stopRecording() {
    if (!recorder) return;
    recorder.stop();
    recorder = null;
    el.talk.classList.remove("recording");
  }

  async function uploadRecording() {
    el.talk.classList.remove("recording");
    if (!chunks.length) { setState("listening"); return; }
    const blob = new Blob(chunks, { type: chunks[0].type });
    chunks = [];
    if (blob.size < 2000) { setState("listening"); return; }

    setState("thinking");
    const form = new FormData();
    form.append("audio", blob, blob.type.includes("mp4") ? "aufnahme.m4a" : "aufnahme.webm");
    try {
      const res = await fetch("/api/stt", { method: "POST", body: form });
      const data = await res.json();
      if (!data.text) addActivity("act", "Nichts verstanden.");
    } catch (err) {
      addEntry("error", "Fehler", "Upload fehlgeschlagen: " + err.message);
    }
  }

  ["pointerdown"].forEach((ev) =>
    el.talk.addEventListener(ev, (e) => { e.preventDefault(); startRecording(); })
  );
  ["pointerup", "pointercancel", "pointerleave"].forEach((ev) =>
    el.talk.addEventListener(ev, (e) => { e.preventDefault(); stopRecording(); })
  );

  // Leertaste = Push-to-Talk am PC
  document.addEventListener("keydown", (e) => {
    if (e.code === "Space" && document.activeElement !== el.input && !e.repeat) {
      e.preventDefault();
      startRecording();
    }
  });
  document.addEventListener("keyup", (e) => {
    if (e.code === "Space" && document.activeElement !== el.input) stopRecording();
  });

  /* ---------------- Eingaben ---------------- */

  el.composer.addEventListener("submit", (e) => {
    e.preventDefault();
    const text = el.input.value.trim();
    if (!text) return;
    el.input.value = "";
    if (!send({ type: "chat", text })) {
      fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text }),
      });
    }
  });

  el.reset.addEventListener("click", () => send({ type: "reset" }) || fetch("/api/reset", { method: "POST" }));
  el.stop.addEventListener("click", () => {
    el.player.pause();
    send({ type: "stop" }) || fetch("/api/stop", { method: "POST" });
  });

  /* ---------------- Visualizer ---------------- */

  const ctx = el.viz.getContext("2d");
  const BARS = 96;
  const bars = new Float32Array(BARS);

  function draw(time) {
    const { width: w, height: h } = el.viz;
    ctx.clearRect(0, 0, w, h);

    const cx = w / 2;
    const cy = h / 2;
    const base = Math.min(w, h) * 0.3;
    const state = el.reactor.dataset.state;
    const active = state === "recording" || state === "speaking" || state === "thinking";
    const energy = active ? Math.max(level, state === "thinking" ? 0.22 : 0.1) : 0.045;

    const color =
      state === "recording" ? "125, 255, 178" :
      state === "thinking" ? "255, 180, 84" :
      "95, 227, 255";

    for (let i = 0; i < BARS; i++) {
      const wobble =
        Math.sin(time / 320 + i * 0.42) * 0.5 +
        Math.sin(time / 137 + i * 1.13) * 0.5;
      const target = energy * (0.45 + 0.55 * Math.abs(wobble));
      bars[i] += (target - bars[i]) * 0.16;
    }

    ctx.lineCap = "round";
    for (let i = 0; i < BARS; i++) {
      const angle = (i / BARS) * Math.PI * 2 - Math.PI / 2;
      const len = base * 0.1 + bars[i] * base * 0.85;
      const x1 = cx + Math.cos(angle) * base;
      const y1 = cy + Math.sin(angle) * base;
      const x2 = cx + Math.cos(angle) * (base + len);
      const y2 = cy + Math.sin(angle) * (base + len);

      ctx.strokeStyle = `rgba(${color}, ${0.18 + bars[i] * 0.75})`;
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(x1, y1);
      ctx.lineTo(x2, y2);
      ctx.stroke();
    }

    level *= 0.9;
    requestAnimationFrame(draw);
  }

  function sizeCanvas() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const rect = el.viz.getBoundingClientRect();
    el.viz.width = rect.width * dpr;
    el.viz.height = rect.height * dpr;
  }

  window.addEventListener("resize", sizeCanvas);
  sizeCanvas();
  requestAnimationFrame(draw);
  connect();
})();
