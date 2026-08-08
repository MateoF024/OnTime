/*
 * OnTime web panel.
 *
 * The third surface over AdminOps, after the commands and the in-game screen.
 * Every action here is one POST to /api/action with an operation name and its
 * arguments, which is what keeps the three able to do the same things without
 * anybody having to keep three lists in step.
 *
 * No framework and no CDN: the mod takes no dependencies and this has to work
 * on a server with no route to the internet.
 */
(() => {
  "use strict";

  const TOKEN = document.currentScript?.dataset.token || window.ONTIME_TOKEN || "";
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

  // ------------------------------------------------------------------ i18n

  const STRINGS = {
    en: {
      admin: "Administration", "tab.runs": "In progress", "tab.timers": "Timers",
      "tab.settings": "Settings", apply: "Apply", discard: "Discard", cancel: "Cancel",
      save: "Save", close: "Close", "runs.title": "Executions in progress",
      "runs.stopAll": "Stop all", "runs.empty": "No timers are running",
      "runs.seenBy": "Seen by", "runs.everyone": "Everyone", "runs.nobody": "Nobody",
      "runs.players": "%s players", "timers.title": "Timers", "timers.new": "New",
      "timers.search": "Search", "timers.empty": "No timers exist yet",
      "timers.noMatch": "Nothing matches that", "settings.title": "Server defaults",
      pause: "Pause", resume: "Resume", reset: "Reset", stop: "Stop", start: "Start",
      clone: "Clone", "delete": "Delete", edit: "Edit", advanced: "Advanced",
      "state.running": "Running", "state.paused": "Paused", "state.cooldown": "In cooldown",
      "confirm.stopAll": "Stop every execution?",
      "confirm.stopAll.body": "%s execution(s) will be stopped.",
      "confirm.delete": "Delete '%s'?",
      "confirm.delete.body": "This permanently deletes the timer and stops every execution of it. It cannot be undone.",
      "dialog.new": "New timer", "dialog.clone": "Copy '%s'", "dialog.start": "Start '%s'",
      "dialog.edit": "Edit '%s'", "field.name": "Name", "field.newName": "New name",
      "field.hours": "Hours", "field.minutes": "Minutes", "field.seconds": "Seconds",
      "field.direction": "Direction", "field.finishCommand": "Command when it ends (optional)",
      "field.audience": "Audience", "field.mode": "Mode",
      "field.playerNames": "Player names, separated by commas",
      countdown: "Countdown", countup: "Count up", shared: "Shared", each: "One each",
      "group.identity": "The timer", "group.display": "Where it draws",
      "group.colors": "Colours", "group.sound": "Sound", "group.titles": "Text around it",
      "group.repeat": "Repeating", "group.sequence": "Handing over",
      "group.triggers": "What starts or ends it", "group.commands": "Commands",
      "trg.none": "Nothing starts or ends this timer early",
      "trg.add": "Add", "trg.value": "Id", "trg.score": "Score",
      "trg.target": "Score holder, or * for anyone",
      "trg.expr": "Expression",
      "trg.player_join": "A player is online", "trg.player_leave": "A player leaves",
      "trg.player_death": "A player dies", "trg.player_respawn": "A player respawns",
      "trg.dimension_change": "A player is in a dimension",
      "trg.advancement": "An advancement is earned",
      "trg.ftb_quest": "An FTB quest is completed",
      "trg.ftb_reward": "An FTB reward is claimed",
      "trg.scoreboard": "A score reaches a value",
      "trg.expression": "An expression is true",
      "trg.who": "Watching", "trg.count": "How many",
      "trg.q.any": "any of", "trg.q.all": "all of", "trg.q.at_least": "at least",
      "trg.s.audience": "the timer's audience", "trg.s.everyone": "anybody on the server",
      "trg.s.players": "these players", "trg.s.selector": "a selector",
      "trg.names": "Names, separated by commas", "trg.selector": "@a[team=red]",
      "trg.addCond": "Add", "trg.addToBranch": "Add",
      "trg.dropBranch": "Remove this alternative",
      "trg.startsWhen": "Starts it when...", "trg.endsWhen": "Ends it when...",
      "trg.noStart": "Nothing starts it early", "trg.noFinish": "Nothing ends it early",
      "trg.say.when": "when", "trg.say.here": "joins this alternative",
      "trg.say.newRule": "an alternative of its own",
      "trg.v.dimension_change": "Dimension, e.g. minecraft:the_nether",
      "trg.v.advancement": "Advancement", "trg.v.ftb_quest": "Quest id",
      "trg.v.ftb_reward": "Reward id", "trg.v.scoreboard": "Objective",
      "trg.groupAll": "all of these hold at once", "trg.groupAny": "any of these holds",
      "trg.groupAtLeast": "at least %s of these hold",
      "trg.orGroup": "or", "trg.startsIt": "Starts it", "trg.endsIt": "Ends it",
      "group.server": "Server", "group.web": "Web", "group.reset": "Reset",
      "reset.what": "Every setting above, back to what the mod ships with",
      "reset.do": "Restore defaults", "reset.title": "Restore every default?",
      "reset.body": "Applied at once, and it cannot be undone. Timers that already exist keep their own values.",
      "cmd.add": "Add", "cmd.none": "This timer runs no commands", "cmd.end": "At the end",
      "cmd.delay": "Ticks between commands", "cmd.delay.default": "0",
      "cmd.text": "Command, without the leading slash",
      on: "On", off: "Off", finish: "Finish it", startIt: "Start it", none: "Off",
      "trigger.join": "A player joins", "trigger.leave": "A player leaves",
      "trigger.death": "A player dies", "trigger.respawn": "A player respawns",
      connected: "Connected", offline: "Reconnecting", badValue: "Check the values in red",
      "default": "Default", "runs.of": "of %s",
      "lead.runs": "%s running right now",
      "lead.timers": "%s defined on this server",
      "lead.settings": "What a new timer starts with, and what the server itself does"
    },
    es: {
      admin: "Administración", "tab.runs": "En curso", "tab.timers": "Contadores",
      "tab.settings": "Ajustes", apply: "Aplicar", discard: "Descartar", cancel: "Cancelar",
      save: "Guardar", close: "Cerrar", "runs.title": "Ejecuciones en curso",
      "runs.stopAll": "Parar todo", "runs.empty": "No hay contadores en marcha",
      "runs.seenBy": "Lo ven", "runs.everyone": "Todos", "runs.nobody": "Nadie",
      "runs.players": "%s jugadores", "timers.title": "Contadores", "timers.new": "Nuevo",
      "timers.search": "Buscar", "timers.empty": "Todavía no hay contadores",
      "timers.noMatch": "Nada coincide con eso", "settings.title": "Valores por defecto",
      pause: "Pausar", resume: "Reanudar", reset: "Reiniciar", stop: "Parar", start: "Arrancar",
      clone: "Clonar", "delete": "Borrar", edit: "Editar", advanced: "Avanzado",
      "state.running": "En marcha", "state.paused": "En pausa", "state.cooldown": "En cooldown",
      "confirm.stopAll": "¿Parar todas las ejecuciones?",
      "confirm.stopAll.body": "Se pararán %s ejecución(es).",
      "confirm.delete": "¿Borrar '%s'?",
      "confirm.delete.body": "Esto elimina el contador de forma permanente y detiene todas sus ejecuciones. No se puede deshacer.",
      "dialog.new": "Contador nuevo", "dialog.clone": "Copiar '%s'", "dialog.start": "Arrancar '%s'",
      "dialog.edit": "Editar '%s'", "field.name": "Nombre", "field.newName": "Nombre nuevo",
      "field.hours": "Horas", "field.minutes": "Minutos", "field.seconds": "Segundos",
      "field.direction": "Sentido", "field.finishCommand": "Comando al terminar (opcional)",
      "field.audience": "Audiencia", "field.mode": "Modo",
      "field.playerNames": "Nombres de jugador, separados por comas",
      countdown: "Cuenta atrás", countup: "Cuenta adelante", shared: "Compartido",
      each: "Uno por jugador",
      "group.identity": "El contador", "group.display": "Dónde se dibuja",
      "group.colors": "Colores", "group.sound": "Sonido", "group.titles": "Texto alrededor",
      "group.repeat": "Repetición", "group.sequence": "Cesión del turno",
      "group.triggers": "Qué lo arranca o lo termina", "group.commands": "Comandos",
      "trg.none": "Nada arranca ni termina este contador antes de tiempo",
      "trg.add": "Añadir", "trg.value": "Id", "trg.score": "Puntuación",
      "trg.target": "Titular de la puntuación, o * para cualquiera",
      "trg.expr": "Expresión",
      "trg.player_join": "Un jugador está conectado", "trg.player_leave": "Sale un jugador",
      "trg.player_death": "Muere un jugador", "trg.player_respawn": "Reaparece un jugador",
      "trg.dimension_change": "Un jugador está en una dimensión",
      "trg.advancement": "Se consigue un logro",
      "trg.ftb_quest": "Se completa una misión de FTB",
      "trg.ftb_reward": "Se reclama una recompensa de FTB",
      "trg.scoreboard": "Una puntuación llega a un valor",
      "trg.expression": "Una expresión se cumple",
      "trg.who": "Vigilando", "trg.count": "Cuántos",
      "trg.q.any": "cualquiera de", "trg.q.all": "todos de", "trg.q.at_least": "al menos",
      "trg.s.audience": "la audiencia del contador", "trg.s.everyone": "cualquiera del servidor",
      "trg.s.players": "estos jugadores", "trg.s.selector": "un selector",
      "trg.names": "Nombres, separados por comas", "trg.selector": "@a[team=red]",
      "trg.addCond": "Añadir", "trg.addToBranch": "Añadir",
      "trg.dropBranch": "Quitar esta alternativa",
      "trg.startsWhen": "Lo arranca cuando...", "trg.endsWhen": "Lo termina cuando...",
      "trg.noStart": "Nada lo arranca antes de tiempo",
      "trg.noFinish": "Nada lo termina antes de tiempo",
      "trg.say.when": "cuando", "trg.say.here": "se une a esta alternativa",
      "trg.say.newRule": "una alternativa por su cuenta",
      "trg.v.dimension_change": "Dimensión, p. ej. minecraft:the_nether",
      "trg.v.advancement": "Logro", "trg.v.ftb_quest": "Id de la misión",
      "trg.v.ftb_reward": "Id de la recompensa", "trg.v.scoreboard": "Objetivo",
      "trg.groupAll": "todas se cumplen a la vez", "trg.groupAny": "se cumple alguna",
      "trg.groupAtLeast": "se cumplen al menos %s",
      "trg.orGroup": "o", "trg.startsIt": "Lo arranca", "trg.endsIt": "Lo termina",
      "group.server": "Servidor", "group.web": "Web", "group.reset": "Restablecer",
      "reset.what": "Todos los ajustes de arriba, a los que trae el mod",
      "reset.do": "Restaurar valores", "reset.title": "¿Restaurar todos los valores?",
      "reset.body": "Se aplica al momento y no se puede deshacer. Los contadores ya creados conservan los suyos.",
      "cmd.add": "Añadir", "cmd.none": "Este contador no ejecuta ningún comando",
      "cmd.delay": "Ticks entre comandos", "cmd.delay.default": "0",
      "cmd.end": "Al final", "cmd.text": "Comando, sin la barra inicial",
      on: "Sí", off: "No", finish: "Terminarlo", startIt: "Arrancarlo", none: "Nada",
      "trigger.join": "Entra un jugador", "trigger.leave": "Sale un jugador",
      "trigger.death": "Muere un jugador", "trigger.respawn": "Reaparece un jugador",
      connected: "Conectado", offline: "Reconectando", badValue: "Revisa los valores en rojo",
      "default": "Por defecto", "runs.of": "de %s",
      "lead.runs": "%s en marcha ahora mismo",
      "lead.timers": "%s definidos en este servidor",
      "lead.settings": "Con qué arranca un contador nuevo, y qué hace el servidor"
    }
  };

  const LANGS = { en: "English", es: "Español" };
  let lang = localStorage.getItem("ontime.lang") || null;

  const t = (key, ...args) => {
    let text = (STRINGS[lang] || STRINGS.en)[key] ?? STRINGS.en[key] ?? key;
    for (const arg of args) text = text.replace("%s", arg);
    return text;
  };

  function applyLanguage() {
    document.documentElement.lang = lang;
    $$("[data-i18n]").forEach(el => { el.textContent = t(el.dataset.i18n); });
    $$("[data-i18n-ph]").forEach(el => { el.placeholder = t(el.dataset.i18nPh); });
    render();
  }

  // ------------------------------------------------------------ transport

  const api = async (path, options = {}) => {
    const url = path + (path.includes("?") ? "&" : "?") + "t=" + encodeURIComponent(TOKEN);
    const response = await fetch(url, {
      ...options,
      headers: { "Content-Type": "application/json", ...(options.headers || {}) }
    });
    if (!response.ok) throw new Error(await response.text().catch(() => response.status));
    return response.status === 204 ? null : response.json();
  };

  /** Every action is one operation on AdminOps; the panel never has its own. */
  async function act(op, args = {}) {
    try {
      const result = await api("/api/action", {
        method: "POST",
        body: JSON.stringify({ op, args })
      });
      if (result && result.message) toast(result.message, !result.success);
      if (result && result.success === false) {
        // The server disagreeing about what exists means this board is behind,
        // and behind by enough that its cards are lying about what is there.
        // Thrown away rather than patched: the next state is the truth.
        forget();
        await refresh();
        return false;
      }
      await refresh();
      return true;
    } catch (e) {
      toast(String(e.message || e), true);
      return false;
    }
  }

  function toast(message, bad = false) {
    if (!message) return;
    const node = document.createElement("div");
    node.className = "toast" + (bad ? " bad" : "");
    node.textContent = message;
    $("#toasts").append(node);
    setTimeout(() => {
      node.style.opacity = "0";
      setTimeout(() => node.remove(), 250);
    }, 3600);
  }

  // ---------------------------------------------------------------- state

  let state = { runs: [], timers: [], config: {}, players: [], presets: [] };
  let tab = "runs";
  let filter = "";

  /**
   * Where each run's clock was when we last heard, and when that was.
   *
   * <p>The server sends snapshots; a clock drawn straight from one is up to a
   * second behind the counter in the game, which is exactly enough to look
   * wrong next to it. Each run is anchored on arrival and predicted forward at
   * frame rate from there, so the two read the same.</p>
   */
  const anchors = new Map();

  function anchor(runs) {
    const seen = new Set();
    for (const run of runs) {
      seen.add(run.runId);
      const held = anchors.get(run.runId);
      if (!held || held.raw !== run.currentTicks) {
        anchors.set(run.runId, { raw: run.currentTicks, ticks: run.currentTicks, at: performance.now() });
      }
    }
    for (const key of [...anchors.keys()]) if (!seen.has(key)) anchors.delete(key);
  }

  function liveTicks(run) {
    const held = anchors.get(run.runId);
    if (!held) return run.currentTicks;
    if (!run.running || run.phase !== "ACTIVE") return held.ticks;
    const elapsed = Math.max(0, Math.floor((performance.now() - held.at) / 50));
    return run.countUp
      ? Math.min(held.ticks + elapsed, run.targetTicks)
      : Math.max(held.ticks - elapsed, 0);
  }

  const clock = ticks => {
    const total = Math.floor(ticks / 20);
    const h = Math.floor(total / 3600), m = Math.floor((total % 3600) / 60), s = total % 60;
    const pad = n => String(n).padStart(2, "0");
    return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
  };

  /**
   * Moves only the cards that are actually in the wrong place.
   *
   * <p>Appending a node that is already a child is not free: the browser
   * detaches it and attaches it again, which cancels and restarts its
   * animations and makes it re-enter :hover under a stationary cursor.
   * Re-appending every card on every refresh is what made them flicker, and
   * why it stopped on a hidden tab and came back on the smallest mouse
   * movement.</p>
   */
  function reorder(host, wanted) {
    wanted.forEach((node, i) => {
      if (host.children[i] !== node) host.insertBefore(node, host.children[i] || null);
    });
  }

  /**
   * The same colour, dark enough to be read on a pale surface.
   *
   * <p>The counter wears the colour it wears in game, which is chosen against
   * a dark HUD: white on white is the common case and it is invisible. Scaling
   * the channels keeps the hue, so the warning and danger colours still read as
   * yellow and red.</p>
   */
  function legible(hex) {
    if (document.documentElement.dataset.theme !== "light") return hex;
    const n = parseInt(hex.slice(1), 16);
    const r = (n >> 16) & 255, g = (n >> 8) & 255, b = n & 255;
    const lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
    if (lum <= 0.5) return hex;
    const k = 0.5 / lum;
    const ch = v => Math.round(v * k).toString(16).padStart(2, "0");
    return "#" + ch(r) + ch(g) + ch(b);
  }

  /** The colour the counter wears in game, by the timer's own thresholds. */
  function runColour(run, ticks) {
    const target = run.targetTicks || 1;
    let pct = (ticks * 100) / target;
    if (run.countUp) pct = 100 - pct;
    const d = run.display || {};
    const hex = n => "#" + ((n ?? 0xFFFFFF) & 0xFFFFFF).toString(16).padStart(6, "0");
    if (pct >= (d.thresholdMid ?? 30)) return legible(hex(d.colorHigh));
    if (pct >= (d.thresholdLow ?? 10)) return legible(hex(d.colorMid));
    return legible(hex(d.colorLow));
  }

  /**
   * Fetches the board, and ignores its own answers when they arrive late.
   *
   * <p>The stream and the poll both ask, so several are in flight at once and
   * they do not come back in order. Taking whichever landed last let an older
   * board overwrite a newer one, and that is where the ghosts came from: an
   * execution that had finished reappeared, its card said paused and could not
   * be resumed, and pressing anything on it earned "No such run" because the
   * server had been right all along. The lead saying "1 running" over an empty
   * board was the same thing caught halfway.</p>
   */
  let asked = 0;
  let newest = 0;

  async function refresh() {
    const mine = ++asked;
    try {
      const board = await api("/api/state");
      if (mine < newest) return;
      newest = mine;
      state = board;
      anchor(state.runs || []);
      render();
      setLink(true);
    } catch (e) {
      setLink(false);
    }
  }

  function setLink(live) {
    const dot = $("#link");
    dot.classList.toggle("live", live);
    const text = live ? t("connected") : t("offline");
    dot.title = text;
    $("#link-text").textContent = text;
  }

  // -------------------------------------------------------------- drawing

  let drawnRuns = "", drawnTimers = "", drawnSettings = "";

  function render() {
    $$(".nav-item").forEach(b => {
      b.setAttribute("aria-selected", String(b.dataset.tab === tab));
    });
    $("#panel-runs").hidden = tab !== "runs";
    $("#panel-timers").hidden = tab !== "timers";
    $("#panel-settings").hidden = tab !== "settings";

    const runs = (state.runs || []).length;
    const timers = (state.timers || []).length;
    $("#count-runs").textContent = runs ? String(runs) : "";
    $("#count-timers").textContent = timers ? String(timers) : "";
    $("#runs-lead").textContent = t("lead.runs", runs);
    $("#timers-lead").textContent = t("lead.timers", timers);
    $("#settings-lead").textContent = t("lead.settings");

    // Each section is redrawn only when its own slice of the board changes.
    // The board arrives four times a second, and rebuilding nodes that often
    // destroys whatever the pointer is on: a button loses its :hover and its
    // click, and a colour input is replaced in the instant it was opening its
    // picker, which is why the picker flashed and vanished. The numbers do not
    // need this — they are painted from the animation frame, not from here.
    if (tab === "runs") {
      const key = JSON.stringify((state.runs || []).map(r => [
        r.runId, r.name, r.running, r.phase, r.mode, r.targetTicks,
        r.countUp, r.audienceScope, (r.audience || []).length,
      ]));
      if (key !== drawnRuns) { drawnRuns = key; renderRuns(); }
      startTicking();
    }
    if (tab === "timers") {
      const key = filter + " " + JSON.stringify(state.timers || []);
      if (key !== drawnTimers) { drawnTimers = key; renderTimers(); }
    }
    if (tab === "settings") {
      const key = JSON.stringify(state.config || {});
      if (key !== drawnSettings) { drawnSettings = key; renderSettings(); }
    }
  }

  function audienceOf(run) {
    if (run.audienceScope === "GLOBAL") return t("runs.everyone");
    const names = (run.audience || []).map(a => a.name);
    if (!names.length) return t("runs.nobody");
    if (names.length > 3) return t("runs.players", names.length);
    return names.join(", ");
  }

  function stateOf(run) {
    if (run.phase && run.phase !== "ACTIVE") return "cooldown";
    return run.running ? "running" : "paused";
  }

  /**
   * The cards, built once and then only updated.
   *
   * <p>Rebuilding the list on every snapshot replayed the entrance animation
   * once a second, which is a board that will not sit still. A card is created
   * when its execution appears, removed when it goes, and otherwise has its
   * text set in place.</p>
   */
  const runCards = new Map();

  function renderRuns() {
    const host = $("#runs");
    const runs = state.runs || [];
    $("#runs-empty").hidden = runs.length > 0;
    $("#stop-all").hidden = runs.length === 0;

    for (const [id, card] of runCards) {
      if (!runs.some(r => r.runId === id)) {
        card.remove();
        runCards.delete(id);
      }
    }

    runs.forEach(run => {
      let card = runCards.get(run.runId);
      if (!card) {
        card = document.createElement("article");
        card.className = "card fresh";
        card.tabIndex = 0;
        card.dataset.runId = run.runId;
        card.innerHTML = `
          <div class="card-head">
            <span class="card-name"></span>
            <span class="state"></span>
          </div>
          <div class="clock"></div>
          <div class="sub"></div>
          <div class="progress"><i></i></div>
          <div class="actions"></div>`;
        // The card is the way in; the buttons on it are not.
        card.addEventListener("click", e => {
          if (!e.target.closest("button")) runDialog(run.runId);
        });
        card.addEventListener("keydown", e => {
          if (e.key === "Enter" || e.key === " ") { e.preventDefault(); runDialog(run.runId); }
        });
        card.addEventListener("animationend", () => card.classList.remove("fresh"));
        runCards.set(run.runId, card);
        host.append(card);
      }

      const kind = stateOf(run);
      card.classList.remove("running", "paused", "cooldown");
      card.classList.add(kind);
      $(".card-name", card).textContent = run.timerName;
      const state_ = $(".state", card);
      state_.className = "state " + kind;
      state_.textContent = t("state." + kind);
      $(".sub", card).textContent =
        `${t("runs.seenBy")} ${audienceOf(run)} · ${t(run.mode === "EACH" ? "each" : "shared")}`;

      const actions = $(".actions", card);
      const cooling = run.phase && run.phase !== "ACTIVE";
      const wanted = [
        ["run.pause", "pause", "", !run.running || cooling],
        ["run.resume", "resume", "", run.running || cooling],
        ["run.reset", "reset", "", false],
        ["run.stop", "stop", "danger", false]
      ];
      if (actions.children.length !== wanted.length) {
        actions.replaceChildren(...wanted.map(([op, label, cls]) => {
          const button = document.createElement("button");
          button.type = "button";
          button.className = "btn " + cls + " small";
          button.textContent = t(label);
          button.onclick = () => act(op, { runId: run.runId });
          return button;
        }));
      }
      wanted.forEach(([, label, , off], i) => {
        actions.children[i].textContent = t(label);
        actions.children[i].disabled = off;
      });
    });
    // Order can change; keep the DOM in the server's order without rebuilding.
    reorder(host, runs.map(run => runCards.get(run.runId)));
  }

  /** Redraws only the numbers, without touching the shape of the page. */
  let ticking = false;
  function tick() {
    if (tab === "runs") {
      for (const [id, card] of runCards) {
        const run = (state.runs || []).find(r => r.runId === id);
        if (!run) continue;
        const ticks = liveTicks(run);
        const colour = runColour(run, ticks);
        const face = $(".clock", card);
        const text = clock(ticks);
        // Written only when it changes: assigning the same string every frame
        // is work the browser still has to check.
        if (face.textContent !== text) face.textContent = text;
        if (face.dataset.colour !== colour) {
          face.style.color = colour;
          face.dataset.colour = colour;
        }
        const bar = $(".progress > i", card);
        const pct = run.targetTicks
          ? Math.max(0, Math.min(100, (ticks * 100) / run.targetTicks)) : 0;
        bar.style.width = pct.toFixed(1) + "%";
        if (bar.dataset.colour !== colour) {
          bar.style.background = colour;
          bar.dataset.colour = colour;
        }
      }
    }
    requestAnimationFrame(tick);
  }
  function startTicking() {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(tick);
  }

  /**
   * The timers, built once and then only updated.
   *
   * <p>Same reason as the executions: rebuilding the list on every snapshot
   * replayed the entrance on every card once a second. This one was missed the
   * first time round, and the board would not sit still.</p>
   */
  const timerCards = new Map();

  function renderTimers() {
    const host = $("#timers");
    const needle = filter.trim().toLowerCase();
    const timers = (state.timers || []).filter(x => !needle || x.name.toLowerCase().includes(needle));
    $("#timers-empty").hidden = timers.length > 0;
    $("p", $("#timers-empty")).textContent =
      (state.timers || []).length ? t("timers.noMatch") : t("timers.empty");

    for (const [name, card] of timerCards) {
      if (!timers.some(x => x.name === name)) {
        card.remove();
        timerCards.delete(name);
      }
    }

    for (const timer of timers) {
      let card = timerCards.get(timer.name);
      if (!card) {
        card = document.createElement("article");
        card.className = "card fresh";
        card.tabIndex = 0;
        card.innerHTML = `
          <div class="card-head"><span class="card-name"></span><span class="tag"></span></div>
          <div class="clock"></div>
          <div class="sub"></div>
          <div class="actions"></div>`;
        card.addEventListener("animationend", () => card.classList.remove("fresh"));
        timerCards.set(timer.name, card);
        host.append(card);
      }

      // Rebound each time: the row it closes over is a new object every
      // snapshot, and a stale one would edit yesterday's values.
      card.onclick = e => { if (!e.target.closest("button")) editDialog(timer); };
      card.onkeydown = e => {
        if (e.key === "Enter" || e.key === " ") { e.preventDefault(); editDialog(timer); }
      };

      card.classList.toggle("running", timer.runCount > 0);
      $(".card-name", card).textContent = timer.name;
      $(".tag", card).textContent = timer.resolvedPreset || "";
      $(".clock", card).textContent = (timer.countUp ? "↑ " : "↓ ") + clock(timer.targetTicks);
      const bits = [];
      if (timer.repeat) bits.push(t("group.repeat"));
      if (timer.nextTimer) bits.push("→ " + timer.nextTimer);
      if (timer.silent) bits.push(t("group.sound") + ": " + t("off"));
      $(".sub", card).textContent = bits.join(" · ") || " ";

      const actions = $(".actions", card);
      actions.replaceChildren();
      const add = (text, cls, fn) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "btn " + cls + " small";
        button.textContent = text;
        button.onclick = fn;
        actions.append(button);
      };
      if (timer.runCount > 0) {
        add(t("stop"), "", () => {
          for (const run of state.runs.filter(r => r.timerName === timer.name)) {
            act("run.stop", { runId: run.runId });
          }
        });
      } else {
        add(t("start"), "primary", () => startDialog(timer));
      }
      add(t("clone"), "", () => cloneDialog(timer));
      add(t("delete"), "danger", () => deleteDialog(timer));
    }
    reorder(host, timers.map(timer => timerCards.get(timer.name)));
  }

  // -------------------------------------------------------------- editing

  /** The settings the server holds, and the twelve a timer copies from them. */
  const CONFIG_GROUPS = [
    ["display", [["positionPreset", "preset"], ["timerX", "int"], ["timerY", "int"],
      ["timerScale", "float"], ["hideOnCooldown", "bool"]]],
    ["colors", [["colorHigh", "color"], ["colorMid", "color"], ["colorLow", "color"],
      ["thresholdMid", "int"], ["thresholdLow", "int"]]],
    ["sound", [["timerSoundId", "text"], ["timerSoundVolume", "float"], ["timerSoundPitch", "float"]]],
    ["server", [["maxTimerSeconds", "int"], ["commandDelayTicks", "int"],
      ["confirmRunThreshold", "int"]]],
    ["web", [["webSocketEnabled", "bool"], ["webSocketPort", "int"], ["webPanelPort", "int"]]]
  ];

  const DISPLAY_KEYS = [
    ["display", [["preset", "preset"], ["x", "int"], ["y", "int"], ["scale", "float"]]],
    ["colors", [["colorHigh", "color"], ["colorMid", "color"], ["colorLow", "color"],
      ["thresholdMid", "int"], ["thresholdLow", "int"]]],
    ["sound", [["soundId", "text"], ["soundVolume", "float"], ["soundPitch", "float"]]]
  ];

  /** A field's own name, without the prefix that says which form it is in. */
  const label = key => {
    const bare = key.replace(/^[a-z]:/, "");
    const named = {
      preset: "position", x: "customX", y: "customY", scale: "scale",
      soundId: "tickSound", soundVolume: "soundVolume", soundPitch: "soundPitch",
      hideOnCooldown: "hideDuringCooldown",
      above: "above", below: "below", left: "left", right: "right"
    }[bare] || bare;
    return named
      .replace(/([A-Z])/g, " $1")
      .replace(/^./, c => c.toUpperCase())
      .trim();
  };

  function field(key, kind, value, onInput) {
    const wrap = document.createElement("div");
    wrap.className = "field";
    const lab = document.createElement("label");
    lab.textContent = label(key);
    wrap.append(lab);

    let input;
    if (kind === "bool") {
      input = document.createElement("select");
      for (const [v, text] of [["true", t("on")], ["false", t("off")]]) {
        input.append(new Option(text, v));
      }
      input.value = String(value);
    } else if (kind === "preset") {
      input = document.createElement("select");
      // The server sends {name, display, anchor}; the value is the name and
      // the label is the display. Handing the object straight to Option is
      // what put "[object Object]" in the list.
      for (const preset of (state.presets || [])) {
        const name = typeof preset === "string" ? preset : preset.name;
        const shown = typeof preset === "string" ? preset : (preset.display || preset.name);
        input.append(new Option(shown, name));
      }
      if (value !== undefined && value !== null && value !== "") input.value = value;
      if (!input.value && input.options.length) input.selectedIndex = 0;
    } else if (kind === "color") {
      input = document.createElement("input");
      input.type = "color";
      input.value = typeof value === "number"
        ? "#" + (value & 0xFFFFFF).toString(16).padStart(6, "0") : (value || "#ffffff");
    } else {
      input = document.createElement("input");
      input.type = kind === "int" || kind === "float" ? "number" : "text";
      if (kind === "float") input.step = "0.1";
      input.value = value ?? "";
    }
    input.dataset.key = key;
    input.dataset.kind = kind;
    input.addEventListener("input", () => {
      input.classList.toggle("bad", !parses(input));
      if (onInput) onInput();
    });
    wrap.append(input);
    return wrap;
  }

  /** Whether what is typed can be used, asked before anything is sent. */
  function parses(input) {
    const value = input.value.trim();
    if (input.dataset.optional && value === "") return true;
    if (input.dataset.kind === "int") return /^-?\d+$/.test(value);
    if (input.dataset.kind === "float") return value !== "" && !Number.isNaN(Number(value));
    return true;
  }

  function valueOf(input) {
    const kind = input.dataset.kind;
    if (kind === "int") return parseInt(input.value, 10);
    if (kind === "float") return Number(input.value);
    if (kind === "bool") return input.value === "true";
    if (kind === "color") return parseInt(input.value.replace("#", ""), 16);
    return input.value.trim();
  }

  /** A titled sheet with its fields inside, which is how every form here reads. */
  /**
   * A titled sheet, and the box its fields go in.
   *
   * <p>Returned as a pair rather than as an element carrying a property of its
   * own. {@code section.body} is a name the DOM already has opinions about,
   * and reading it back gave nothing: every sheet rendered as a heading with
   * an empty box under it.</p>
   */
  function sheet(title) {
    const section = document.createElement("section");
    section.className = "sheet";
    const header = document.createElement("header");
    const h3 = document.createElement("h3");
    h3.textContent = title;
    header.append(h3);
    const body = document.createElement("div");
    body.className = "body";
    section.append(header, body);
    return { section, body };
  }

  function renderSettings() {
    const host = $("#settings");
    host.replaceChildren(...CONFIG_GROUPS.map(([group, keys]) => {
      // Only the three a timer takes a copy of are "defaults"; server and web
      // are global by nature and there is no per-timer version of them.
      const title = ["display", "colors", "sound"].includes(group)
        ? t("default") + " · " + t("group." + group) : t("group." + group);
      const { section, body } = sheet(title);
      for (const [key, kind] of keys) {
        body.append(field(key, kind, state.config[key], markSettings));
      }
      return section;
    }));

    // Last, on its own, and it asks first: it undoes every sheet above it.
    const reset = sheet(t("group.reset"));
    const row = document.createElement("div");
    row.className = "field";
    const label = document.createElement("label");
    label.textContent = t("reset.what");
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn danger";
    button.textContent = t("reset.do");
    button.onclick = () => confirmDialog(t("reset.title"), t("reset.body"), async () => {
      if (await act("config.reset")) { forget(); await refresh(); }
    });
    row.append(label, button);
    reset.body.append(row);
    host.append(reset.section);

    markSettings();
  }

  /** Title, one line of consequence, and a red confirm. */
  function confirmDialog(title, body, run) {
    modal(title, host => {
      const p = document.createElement("p");
      p.className = "muted";
      p.textContent = body;
      host.append(p);
    }, [[t("cancel"), "", null], [t("reset.do"), "danger", run]]);
  }

  function markSettings() {
    const bad = $$("#settings input").some(i => !parses(i));
    $("#settings-apply").disabled = bad;
  }

  $("#settings-apply").onclick = async () => {
    for (const input of $$("#settings [data-key]")) {
      const key = input.dataset.key;
      const value = valueOf(input);
      if (String(state.config[key]) === String(value)) continue;
      await act("config.set", { key, value });
    }
  };
  $("#settings-discard").onclick = () => renderSettings();

  // --------------------------------------------------------------- modals

  function modal(title, build, actions) {
    const dialog = $("#modal");
    $("#modal-title").textContent = title;
    const body = $("#modal-body");
    body.replaceChildren();
    body.scrollTop = 0;
    build(body);
    const menu = $("#modal-actions");
    menu.replaceChildren(...actions.map(([text, cls, fn]) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "btn " + cls;
      button.textContent = text;
      button.onclick = async () => {
        if (!fn || (await fn()) !== false) dialog.close();
      };
      return button;
    }));
    if (!dialog.open) dialog.showModal();
  }

  $("#modal-close").onclick = () => $("#modal").close();

  function startDialog(timer) {
    let audience = "global";
    let mode = "shared";
    modal(t("dialog.start", timer.name), body => {
      const scope = field("audience", "bool", "true");
      const select = $("select", scope);
      select.replaceChildren(new Option(t("runs.everyone"), "global"),
        new Option(t("field.playerNames"), "players"));
      $("label", scope).textContent = t("field.audience");
      select.onchange = () => {
        audience = select.value;
        names.hidden = audience === "global";
      };
      body.append(scope);

      const names = field("players", "text", "");
      $("label", names).textContent = t("field.playerNames");
      names.hidden = true;
      body.append(names);

      const modeField = field("mode", "bool", "true");
      const modeSelect = $("select", modeField);
      modeSelect.replaceChildren(new Option(t("shared"), "shared"), new Option(t("each"), "each"));
      $("label", modeField).textContent = t("field.mode");
      modeSelect.onchange = () => { mode = modeSelect.value; };
      body.append(modeField);
    }, [
      [t("cancel"), "", null],
      [t("start"), "primary", () => {
        const args = { name: timer.name, mode };
        if (audience === "global") {
          args.global = true;
        } else {
          const wanted = $("#modal-body [data-key='players']").value
            .split(",").map(s => s.trim().toLowerCase()).filter(Boolean);
          args.players = (state.players || [])
            .filter(p => wanted.includes(p.name.toLowerCase())).map(p => p.uuid);
          if (!args.players.length) { toast(t("runs.nobody"), true); return false; }
        }
        act("run.start", args);
      }]
    ]);
  }

  function cloneDialog(timer) {
    modal(t("dialog.clone", timer.name), body => {
      const name = field("dest", "text", timer.name + "2");
      $("label", name).textContent = t("field.newName");
      body.append(name);
    }, [
      [t("cancel"), "", null],
      [t("clone"), "primary", () =>
        act("timer.clone", { name: timer.name, dest: $("#modal-body [data-key='dest']").value.trim() })]
    ]);
  }

  function deleteDialog(timer) {
    modal(t("confirm.delete", timer.name), body => {
      const p = document.createElement("p");
      p.className = "muted";
      p.textContent = t("confirm.delete.body");
      body.append(p);
    }, [
      [t("cancel"), "", null],
      [t("delete"), "danger", () => act("timer.delete", { name: timer.name })]
    ]);
  }

  function newDialog() {
    modal(t("dialog.new"), body => {
      body.append(field("name", "text", ""));
      const row = document.createElement("div");
      row.className = "field";
      const lab = document.createElement("label");
      lab.textContent = t("field.hours") + " / " + t("field.minutes") + " / " + t("field.seconds");
      const cells = document.createElement("div");
      cells.className = "row";
      for (const [key, value] of [["hours", 0], ["minutes", 1], ["seconds", 0]]) {
        const input = document.createElement("input");
        input.type = "number";
        input.min = "0";
        input.value = value;
        input.dataset.key = key;
        input.dataset.kind = "int";
        cells.append(input);
      }
      row.append(lab, cells);
      body.append(row);
      const dir = field("countUp", "bool", "false");
      const select = $("select", dir);
      select.replaceChildren(new Option(t("countdown"), "false"), new Option(t("countup"), "true"));
      $("label", dir).textContent = t("field.direction");
      body.append(dir);
      // Optional, and the only chance to say it without a second visit to the
      // editor. Left empty the timer runs nothing, which is a real answer.
      const finish = field("finishCommand", "text", "");
      $("label", finish).textContent = t("field.finishCommand");
      body.append(finish);
    }, [
      [t("cancel"), "", null],
      [t("save"), "primary", async () => {
        const get = key => $(`#modal-body [data-key='${key}']`);
        const name = get("name").value.trim();
        const made = await act("timer.create", {
          name,
          hours: parseInt(get("hours").value, 10) || 0,
          minutes: parseInt(get("minutes").value, 10) || 0,
          seconds: parseInt(get("seconds").value, 10) || 0,
          countUp: get("countUp").value === "true"
        });
        if (made === false) return false;
        const command = get("finishCommand").value.trim();
        if (command) await act("timer.addCommand", { name, command });
        return made;
      }]
    ]);
  }

  /**
   * The whole timer, in one dialog.
   *
   * <p>The same operations the in-game editor sends, because both go through
   * AdminOps: length, silence, the twelve display settings, the four titles,
   * repeating, handing over, conditions, triggers and the command list.</p>
   */

  /**
   * What typed text has to look like before the composer will send it.
   *
   * <p>The same three shapes InputRules checks in game. None of them says the
   * value exists -- no list here knows which advancements a pack shipped --
   * only that it is the right shape, which is the part that can be answered
   * while somebody is still typing.</p>
   */
  const SHAPES = {
    id: value => {
      const text = value.trim();
      if (!text) return false;
      const colon = text.indexOf(":");
      const namespace = colon < 0 ? "minecraft" : text.slice(0, colon);
      const path = colon < 0 ? text : text.slice(colon + 1);
      return /^[a-z0-9_.-]+$/.test(namespace) && /^[a-z0-9_.\/-]+$/.test(path);
    },
    selector: value => {
      const text = value.trim();
      if (!/^@[apres]/.test(text)) return false;
      if (text.length === 2) return true;
      if (text[2] !== "[" || !text.endsWith("]") || text.length <= 4) return false;
      let depth = 0;
      for (const c of text) {
        if (c === "[") depth++;
        if (c === "]" && --depth < 0) return false;
      }
      return depth === 0;
    },
    names: value => {
      const text = value.trim();
      if (!text || text.endsWith(",")) return false;
      return text.split(",").every(one => /^[A-Za-z0-9_]{1,16}$/.test(one.trim()));
    },
    filled: value => value.trim() !== "",
  };

  /** The kinds the server accepts, in the order the editor offers them. */
  const TRIGGER_KINDS = ["player_join", "player_leave", "player_death", "player_respawn",
    "dimension_change", "advancement", "ftb_quest", "ftb_reward", "scoreboard", "expression"];

  /** How many of the watched players it takes, and who they are. */
  const QUANTIFIERS = ["any", "all", "at_least"];
  const SCOPES = ["audience", "everyone", "players", "selector"];

  /** The one kind that asks the server rather than a player, so it has no subject. */
  const NO_SUBJECT = "expression";

  /** One trigger in words, the same shape the commands use. */
  function describeTrigger(trigger) {
    const kind = t("trg." + trigger.kind);
    let text = kind;
    if (trigger.kind === "scoreboard") {
      text = kind + " (" + (trigger.value || "") + " \u2265 " + (trigger.threshold ?? 0) + ")";
    } else if (trigger.value) {
      text = kind + " (" + trigger.value + ")";
    }
    if (trigger.kind === NO_SUBJECT) return text;
    return text + " \u00b7 " + describeWho(trigger.who);
  }

  /** "all of these players: Bob, Ann" — the same wording the commands print. */
  function describeWho(who) {
    if (!who) return t("trg.q.any") + " " + t("trg.s.audience");
    const quantifier = who.quantifier === "at_least"
      ? t("trg.q.at_least") + " " + (who.count ?? 1)
      : t("trg.q." + (who.quantifier || "any"));
    const scope = t("trg.s." + (who.scope || "audience"));
    return quantifier + " " + scope + (who.value ? ": " + who.value : "");
  }


  /** The groups of a rule. A rule that is one plain condition counts as one. */
  function groupsOf(rule) {
    const root = rule.condition;
    if (!root) return [];
    if (root.node === "group" && root.mode === "any") return root.children || [];
    return [root];
  }

  /** One alternative: what has to hold together, and the way to add to it. */
  function groupBlock(timer, rule, group) {
    const box = document.createElement("div");
    box.className = "trg-group";

    const head = document.createElement("div");
    head.className = "trg-group-head";
    head.textContent = group.node !== "group" ? ""
      : group.mode === "any" ? t("trg.groupAny")
      : group.mode === "at_least" ? t("trg.groupAtLeast", group.count ?? 1)
      : t("trg.groupAll");
    if (head.textContent) box.append(head);

    const conditions = group.node === "group" ? (group.children || []) : [group];
    for (const condition of conditions) {
      if (condition.node === "group") continue;
      const row = document.createElement("div");
      row.className = "cmd-row";
      const text = document.createElement("span");
      text.className = "cmd-text";
      text.textContent = describeTrigger(condition);
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "danger small";
      remove.textContent = "\u00d7";
      remove.onclick = () => act("timer.removeCondition",
        { name: timer.name, conditionId: condition.id })
        .then(ok => ok && reopen(timer.name));
      row.append(text, remove);
      box.append(row);
    }

    // Only a real group can take another condition, and the composer opens
    // inside it. The button used to set a variable read by an adder at the
    // far bottom of the sheet, which nothing on screen ever mentioned: there
    // was no way to tell where the next condition was going to land.
    if (group.node === "group") {
      const drop = document.createElement("button");
      drop.type = "button";
      drop.className = "danger small";
      drop.textContent = t("trg.dropBranch");
      drop.onclick = () => act("timer.removeCondition",
        { name: timer.name, conditionId: group.id })
        .then(ok => ok && reopen(timer.name));
      box.append(drop);

      const add = document.createElement("button");
      add.type = "button";
      add.className = "btn small";
      add.textContent = t("trg.addToBranch");
      add.onclick = () => openComposer(box, add, timer, rule.action, group.id);
      box.append(add);
    }
    return box;
  }

  /**
   * The composer: one condition written out as a sentence.
   *
   * <p>It replaces a row of nine controls that stood at the bottom of the
   * sheet whatever was chosen. Here the words carry the shape — "when any of
   * the timer's audience earns an advancement ..." — and a control appears
   * only where the sentence has a blank for it.</p>
   *
   * <p>Opened in place, so where the condition will land is where you are
   * looking. Only one is open at a time.</p>
   */
  let openSay = null;

  function closeComposer() {
    if (!openSay) return;
    openSay.node.remove();
    if (openSay.trigger) openSay.trigger.hidden = false;
    openSay = null;
  }

  function openComposer(host, trigger, timer, action, groupId) {
    // Named by where it puts what it makes, so pressing the button that
    // opened it puts it away and pressing a different one moves it there --
    // comparing the buttons themselves made every "new rule" button look
    // like the same button, because neither of them is one.
    const key = groupId ? "group:" + groupId : "new:" + action;
    const wasMine = openSay && openSay.key === key;
    closeComposer();
    if (wasMine) return;

    const box = document.createElement("div");
    box.className = "trg-say";

    const head = document.createElement("div");
    head.className = "trg-say-head";
    const what = document.createElement("span");
    what.className = "cmd-at" + (action === "start" ? "" : " end");
    what.textContent = t(action === "start" ? "trg.startsIt" : "trg.endsIt");
    const where = document.createElement("span");
    where.className = "trg-say-where";
    where.textContent = groupId ? t("trg.say.here") : t("trg.say.newRule");
    head.append(what, where);
    box.append(head);

    const line = document.createElement("div");
    line.className = "trg-say-line";

    const word = text => {
      const span = document.createElement("span");
      span.className = "trg-word";
      span.textContent = text;
      return span;
    };

    const quantifier = document.createElement("select");
    for (const q of QUANTIFIERS) quantifier.append(new Option(t("trg.q." + q), q));

    const count = document.createElement("input");
    count.type = "number";
    count.min = "1";
    count.value = "1";
    count.className = "trg-num";

    const scope = document.createElement("select");
    for (const sc of SCOPES) scope.append(new Option(t("trg.s." + sc), sc));

    const subject = document.createElement("input");
    subject.type = "text";

    const kind = document.createElement("select");
    for (const name of TRIGGER_KINDS) kind.append(new Option(t("trg." + name), name));

    const value = document.createElement("input");
    value.type = "text";

    const atLeast = word("\u2265");
    const score = document.createElement("input");
    score.type = "number";
    score.value = "0";
    score.className = "trg-num";

    const when = word(t("trg.say.when"));
    line.append(when, quantifier, count, scope, subject, kind, value, atLeast, score);

    // Every blank the sentence does not have, gone. A bare event leaves
    // "when any of the timer's audience — a player joins" and nothing else.
    const shape = () => {
      const picked = kind.value;
      const scoreboard = picked === "scoreboard";
      const bare = picked.startsWith("player_");
      const subjectless = picked === NO_SUBJECT;

      when.hidden = false;
      quantifier.hidden = subjectless;
      count.hidden = subjectless || quantifier.value !== "at_least";
      scope.hidden = subjectless;
      subject.hidden = subjectless
        || (scope.value !== "players" && scope.value !== "selector");
      subject.placeholder = scope.value === "selector" ? t("trg.selector") : t("trg.names");

      value.hidden = bare;
      // Named after what it wants, rather than "Id" for a dimension, an
      // advancement and a quest alike.
      value.placeholder = bare ? ""
        : picked === "expression" ? t("trg.expr") : t("trg.v." + picked);
      atLeast.hidden = !scoreboard;
      score.hidden = !scoreboard;
    };
    kind.onchange = shape;
    scope.onchange = shape;
    quantifier.onchange = shape;
    box.append(line);

    const buttons = document.createElement("div");
    buttons.className = "trg-say-buttons";

    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "btn small";
    cancel.textContent = t("cancel");
    cancel.onclick = closeComposer;

    const add = document.createElement("button");
    add.type = "button";
    add.className = "primary small";
    add.textContent = t("trg.add");

    // What each box has to hold, given what has been chosen so far.
    const valueShape = () => {
      const picked = kind.value;
      if (picked === "expression" || picked === "ftb_quest" || picked === "ftb_reward") {
        return SHAPES.filled;
      }
      return picked === "scoreboard" ? SHAPES.filled : SHAPES.id;
    };
    const subjectShape = () =>
      scope.value === "selector" ? SHAPES.selector : SHAPES.names;

    /** Marks what is wrong and says whether anything is. */
    const valid = () => {
      let ok = true;
      for (const [box, shape] of [[value, valueShape()], [subject, subjectShape()]]) {
        const bad = !box.hidden && !shape(box.value);
        box.classList.toggle("bad", bad && box.value !== "");
        if (bad) ok = false;
      }
      if (!count.hidden && !(parseInt(count.value, 10) >= 1)) ok = false;
      if (!score.hidden && !/^-?\d+$/.test(score.value.trim())) ok = false;
      add.disabled = !ok;
      return ok;
    };
    for (const box of [value, subject, count, score]) box.addEventListener("input", valid);
    const reshape = () => { shape(); valid(); };
    kind.onchange = reshape;
    scope.onchange = reshape;
    quantifier.onchange = reshape;
    reshape();

    add.onclick = async () => {
      if (!valid()) return;
      const picked = kind.value;
      const args = { name: timer.name, kind: picked, action };
      if (!picked.startsWith("player_")) {
        args.value = value.value.trim();
      }
      if (picked === "scoreboard") args.threshold = parseInt(score.value, 10) || 0;
      if (picked !== NO_SUBJECT) {
        args.quantifier = quantifier.value;
        args.subject = scope.value;
        if (quantifier.value === "at_least") args.count = parseInt(count.value, 10) || 1;
        if (scope.value === "players" || scope.value === "selector") {
          args.subjectValue = subject.value.trim();
        }
      }
      if (groupId) args.groupId = groupId;
      if (await act("timer.addTrigger", args)) {
        closeComposer();
        reopen(timer.name);
      }
    };
    buttons.append(cancel, add);
    box.append(buttons);

    if (trigger) trigger.hidden = true;
    host.append(box);
    openSay = { node: box, trigger: trigger || null, key };
    (kind.hidden ? value : kind).focus();
  }

  /** Reopens the editor on fresh data, which is how every list here refreshes. */
  function reopen(name) {
    const fresh = state.timers.find(x => x.name === name);
    if (fresh) editDialog(fresh);
  }

  function editDialog(timer) {
    const display = timer.display || {};
    modal(t("dialog.edit", timer.name), body => {
      const group = (name, build) => {
        const sheeted = sheet(t("group." + name));
        build(sheeted.body);
        body.append(sheeted.section);
      };

      group("identity", s => {
        const row = document.createElement("div");
        row.className = "field";
        const lab = document.createElement("label");
        lab.textContent = t("field.hours") + " / " + t("field.minutes") + " / " + t("field.seconds");
        const cells = document.createElement("div");
        cells.className = "row";
        const total = Math.floor(timer.targetTicks / 20);
        for (const [key, value] of [["hours", Math.floor(total / 3600)],
          ["minutes", Math.floor((total % 3600) / 60)], ["seconds", total % 60]]) {
          const input = document.createElement("input");
          input.type = "number";
          input.min = "0";
          input.value = value;
          input.dataset.key = key;
          input.dataset.kind = "int";
          cells.append(input);
        }
        row.append(lab, cells);
        s.append(row);
        const silent = field("silent", "bool", String(!!timer.silent));
        $("select", silent).replaceChildren(new Option(t("on"), "true"), new Option(t("off"), "false"));
        $("select", silent).value = String(!!timer.silent);
        s.append(silent);
      });

      for (const [name, keys] of DISPLAY_KEYS) {
        group(name, s => {
          for (const [key, kind] of keys) s.append(field("d:" + key, kind, display[key]));
        });
      }

      group("titles", s => {
        const titles = timer.titles || {};
        for (const slot of ["above", "below", "left", "right"]) {
          const f = field("t:" + slot, "text", titles[slot] || "");
          // A title that is empty is a title that is not there, which is a
          // perfectly good answer.
          $("input", f).dataset.optional = "1";
          s.append(f);
        }
      });

      group("repeat", s => {
        const repeat = field("repeat", "bool", String(!!timer.repeat));
        $("select", repeat).replaceChildren(new Option(t("on"), "true"), new Option(t("off"), "false"));
        $("select", repeat).value = String(!!timer.repeat);
        s.append(repeat);
        s.append(field("repeatCount", "int", timer.repeatCount ?? -1));
        s.append(field("repeatCooldown", "int", Math.floor((timer.repeatCooldownTicks || 0) / 20)));
      });

      group("sequence", s => {
        const next = field("nextTimer", "text", timer.nextTimer || "");
        $("input", next).dataset.optional = "1";
        s.append(next);
        s.append(field("sequenceCooldown", "int", Math.floor((timer.sequenceCooldownTicks || 0) / 20)));
      });

      // One list, the way the commands sheet works. There were three fixed
      // forms here -- a scoreboard block, an expression block and a select of
      // four game events -- so a timer could hold one of each and no more, and
      // the select wrote "death" where the server stores "player_death", which
      // never matched anything.
      group("triggers", s => {
        closeComposer();

        // Two headings, always both, because everything a timer can be told
        // is one or the other. Under a heading sit alternatives, and any one
        // of them holding is enough; inside an alternative everything has to
        // hold at once. There is no third level: a rule and an alternative
        // were both an "or", so the page used to show one idea twice.
        for (const action of ["start", "finish"]) {
          const section = document.createElement("div");
          section.className = "trg-section";

          const head = document.createElement("div");
          head.className = "trg-section-head";
          const what = document.createElement("span");
          what.className = "cmd-at" + (action === "start" ? "" : " end");
          what.textContent = t(action === "start" ? "trg.startsWhen" : "trg.endsWhen");

          const add = document.createElement("button");
          add.type = "button";
          add.className = "btn small";
          add.textContent = t("trg.addCond");
          add.onclick = () => openComposer(section, null, timer, action, null);
          head.append(what, add);
          section.append(head);

          const body = document.createElement("div");
          let branches = 0;
          for (const rule of (timer.rules || [])) {
            if (rule.action !== action) continue;
            for (const group of groupsOf(rule)) {
              if (branches > 0) {
                const or = document.createElement("div");
                or.className = "trg-or";
                or.textContent = t("trg.orGroup");
                body.append(or);
              }
              branches++;
              body.append(groupBlock(timer, rule, group));
            }
          }
          if (!branches) {
            const p = document.createElement("p");
            p.className = "muted";
            p.textContent = t(action === "start" ? "trg.noStart" : "trg.noFinish");
            body.append(p);
          }
          section.append(body);
          s.append(section);
        }
      });

      group("commands", s => {
        // The pause this timer puts between its own commands, above the list
        // it spaces out. -1, and an empty box, mean the server default.
        const delay = field("commandDelay", "int", timer.commandDelayTicks ?? 0);
        $("label", delay).textContent = t("cmd.delay");
        $("input", delay).placeholder = t("cmd.delay.default");
        s.append(delay);

        const list = document.createElement("div");
        const entries = timer.commandList || [];
        if (!entries.length) {
          const p = document.createElement("p");
          p.className = "muted";
          p.textContent = t("cmd.none");
          list.append(p);
        }
        entries.forEach((entry, index) => {
          const row = document.createElement("div");
          row.className = "cmd-row";
          const at = document.createElement("span");
          at.className = "cmd-at" + (entry.at === undefined ? " end" : "");
          at.textContent = entry.at === undefined ? t("cmd.end") : clock(entry.at * 20);
          const text = document.createElement("span");
          text.className = "cmd-text";
          text.textContent = entry.command;
          const remove = document.createElement("button");
          remove.type = "button";
          remove.className = "danger small";
          remove.textContent = "×";
          remove.onclick = async () => {
            // Zero-based, exactly as the server counts them.
            if (await act("timer.removeCommand", { name: timer.name, index })) {
              const fresh = state.timers.find(x => x.name === timer.name);
              if (fresh) editDialog(fresh);
            }
          };
          row.append(at, text, remove);
          list.append(row);
        });
        s.append(list);

        const adder = document.createElement("div");
        adder.className = "cmd-add";
        const cells = document.createElement("div");
        cells.className = "units";
        for (const [key, ph] of [["ch", "H"], ["cm", "M"], ["cs", "S"]]) {
          const input = document.createElement("input");
          input.type = "number";
          input.min = "0";
          input.placeholder = ph;
          input.dataset.key = key;
          cells.append(input);
        }
        const command = document.createElement("input");
        command.type = "text";
        command.placeholder = t("cmd.text");
        command.dataset.key = "cc";
        const add = document.createElement("button");
        add.type = "button";
        add.className = "primary small";
        add.textContent = t("cmd.add");
        add.onclick = async () => {
          if (!command.value.trim()) return;
          const n = key => parseInt($(`#modal-body [data-key='${key}']`).value, 10) || 0;
          const at = n("ch") * 3600 + n("cm") * 60 + n("cs");
          const args = { name: timer.name, command: command.value.trim() };
          if (at > 0) args.atSeconds = at;
          if (await act("timer.addCommand", args)) {
            const fresh = state.timers.find(x => x.name === timer.name);
            if (fresh) editDialog(fresh);
          }
        };
        adder.append(cells, command, add);
        s.append(adder);
      });
    }, [
      [t("cancel"), "", null],
      [t("apply"), "primary", async () => {
        const get = key => $(`#modal-body [data-key='${key}']`);
        const num = key => parseInt(get(key).value, 10) || 0;

        await act("timer.setTime",
          { name: timer.name, hours: num("hours"), minutes: num("minutes"), seconds: num("seconds") });
        await act("timer.setSilent", { name: timer.name, silent: get("silent").value === "true" });

        // Nothing empty and nothing unparseable leaves this dialog. Sending
        // it earned an "Unknown preset" from the server, which told the person
        // reading it nothing about which box was wrong.
        const bad = $$("#modal-body [data-key]").filter(i => !parses(i) || i.value.trim() === "");
        if (bad.length) {
          bad.forEach(i => i.classList.add("bad"));
          bad[0].focus();
          toast(t("badValue"), true);
          return false;
        }

        for (const [, keys] of DISPLAY_KEYS) {
          for (const [key, kind] of keys) {
            const input = get("d:" + key);
            const value = valueOf(input);
            if (String(display[key]) === String(value)) continue;
            await act("timer.setDisplay", { name: timer.name, key, value });
          }
        }
        for (const slot of ["above", "below", "left", "right"]) {
          await act("timer.setTitle", { name: timer.name, slot, text: get("t:" + slot).value });
        }
        await act("timer.setRepeat", {
          name: timer.name, repeat: get("repeat").value === "true",
          count: num("repeatCount"), cooldownSeconds: num("repeatCooldown")
        });
        await act("timer.setSequence", {
          name: timer.name, next: get("nextTimer").value.trim(),
          cooldownSeconds: num("sequenceCooldown")
        });
        await act("timer.setDisplay", {
          name: timer.name, key: "commandDelayTicks", value: num("commandDelay")
        });
      }]
    ]);
  }

  /**
   * Everything about one execution, and nothing to press.
   *
   * <p>A summary rather than a form: what it is, who sees it, where it draws,
   * what it will do when it ends and what it runs on the way. Editing belongs
   * to the timer, not to a run of it.</p>
   */
  function runDialog(runId) {
    const run = (state.runs || []).find(r => r.runId === runId);
    if (!run) return;
    const timer = (state.timers || []).find(x => x.name === run.timerName) || {};
    const display = timer.display || {};

    modal(run.timerName, body => {
      const wrap = document.createElement("div");
      wrap.className = "detail";

      const ticks = liveTicks(run);
      const hero = document.createElement("div");
      hero.className = "hero";
      const left = document.createElement("div");
      const face = document.createElement("div");
      face.className = "clock";
      face.textContent = clock(ticks);
      face.style.color = runColour(run, ticks);
      const of = document.createElement("div");
      of.className = "of";
      of.textContent = t("runs.of", clock(run.targetTicks));
      left.append(face, of);
      const badge = document.createElement("span");
      badge.className = "state " + stateOf(run);
      badge.textContent = t("state." + stateOf(run));
      hero.append(left, badge);
      wrap.append(hero);

      const facts = (title, rows) => {
        const kept = rows.filter(([, v]) => v !== undefined && v !== null && v !== "");
        if (!kept.length) return;
        const { section, body: into } = sheet(title);
        const dl = document.createElement("dl");
        dl.className = "facts";
        for (const [key, value, strong] of kept) {
          const cell = document.createElement("div");
          cell.className = "fact";
          const dt = document.createElement("dt");
          dt.textContent = key;
          const dd = document.createElement("dd");
          dd.textContent = value;
          if (strong) dd.className = "strong";
          cell.append(dt, dd);
          dl.append(cell);
        }
        into.append(dl);
        wrap.append(section);
      };

      facts(t("group.identity"), [
        [t("field.direction"), t(run.countUp ? "countup" : "countdown"), true],
        [t("field.mode"), t(run.mode === "EACH" ? "each" : "shared")],
        [t("field.audience"), audienceOf(run)],
        ["ID", run.runId.slice(0, 8)]
      ]);

      facts(t("group.display"), [
        [label("preset"), display.preset, true],
        ["X / Y", display.x + " / " + display.y],
        [label("scale"), display.scale],
        [t("group.sound"), timer.silent ? t("off") : (display.soundId || "")]
      ]);

      facts(t("group.repeat"), [
        [t("group.repeat"), timer.repeat
          ? (timer.repeatCount < 0 ? "\u221e" : timer.repeatCount) : t("off"), true],
        [t("group.sequence"), timer.nextTimer || t("none")]
      ]);

      const shown = (timer.rules || [])
        .filter(rule => rule.condition && rule.condition.node !== "group")
        .map(rule => ({ ...rule.condition, action: rule.action }));
      facts(t("group.triggers"), shown.length
        ? shown.map(trigger => [
            t(trigger.action === "start" ? "startIt" : "finish"),
            describeTrigger(trigger), true])
        : [[t("trg.none"), "", true]]);

      const commands = timer.commandList || [];
      {
        const { section, body: into } = sheet(t("group.commands"));
        if (!commands.length) {
          const none = document.createElement("p");
          none.className = "muted";
          none.style.margin = "10px 0";
          none.textContent = t("cmd.none");
          into.append(none);
        }
        for (const entry of commands) {
          const row = document.createElement("div");
          row.className = "cmd-row";
          const at = document.createElement("span");
          at.className = "cmd-at" + (entry.at === undefined ? " end" : "");
          at.textContent = entry.at === undefined ? t("cmd.end") : clock(entry.at * 20);
          const text = document.createElement("span");
          text.className = "cmd-text";
          text.textContent = entry.command;
          row.append(at, text, document.createElement("span"));
          into.append(row);
        }
        wrap.append(section);
      }

      body.append(wrap);
    }, [[t("close"), "", null]]);
  }

  // ---------------------------------------------------------------- wiring

  $$(".nav-item").forEach(b => b.onclick = () => {
    tab = b.dataset.tab;
    // The map and the page have to be forgotten together. Emptying the map
    // alone left the old cards in the document and built a second set beside
    // them, so every visit to the tab doubled the board -- and pressing Delete
    // on one of the ghosts asked the server about a timer it had already
    // removed.
    forget();
    render();
  });

  function forget() {
    drawnRuns = drawnTimers = drawnSettings = "";
    runCards.clear();
    timerCards.clear();
    $("#runs").replaceChildren();
    $("#timers").replaceChildren();
  }
  $("#new-timer").onclick = newDialog;
  $("#search").oninput = e => {
    filter = e.target.value;
    // The cards are keyed by name, and filtering changes which names are
    // there rather than what any of them says.
    timerCards.clear();
    $("#timers").replaceChildren();
    renderTimers();
  };
  $("#stop-all").onclick = () => {
    modal(t("confirm.stopAll"), body => {
      const p = document.createElement("p");
      p.className = "muted";
      p.textContent = t("confirm.stopAll.body", (state.runs || []).length);
      body.append(p);
    }, [[t("cancel"), "", null], [t("stop"), "danger", () => act("run.stopAll")]]);
  };

  $("#theme").onclick = () => {
    const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
    document.documentElement.dataset.theme = next;
    localStorage.setItem("ontime.theme", next);
  };
  document.documentElement.dataset.theme = localStorage.getItem("ontime.theme") || "dark";

  const picker = $("#lang");
  for (const [code, name] of Object.entries(LANGS)) picker.append(new Option(name, code));
  picker.onchange = () => {
    lang = picker.value;
    localStorage.setItem("ontime.lang", lang);
    applyLanguage();
  };

  /**
   * A live feed, with polling behind it.
   *
   * <p>The stream carries the events; the poll is what catches anything that
   * happened while the stream was down. Without it a panel that lost its
   * connection for a moment would sit on a stale board looking healthy.</p>
   */
  function connect() {
    const source = new EventSource("/events?t=" + encodeURIComponent(TOKEN));
    source.onopen = () => setLink(true);
    source.onmessage = () => refresh();
    source.onerror = () => {
      setLink(false);
      source.close();
      setTimeout(connect, 3000);
    };
    for (const name of ["START", "FINISH", "PAUSE", "RESUME", "TICK", "STATE"]) {
      source.addEventListener(name, () => refresh());
    }
  }

  (async () => {
    let serverLang = "en";
    try {
      const info = await api("/api/lang");
      serverLang = info.language || "en";
    } catch (ignored) { /* the default will do */ }
    // The game's language unless somebody has chosen otherwise here.
    lang = lang || (STRINGS[serverLang] ? serverLang : "en");
    picker.value = lang;
    applyLanguage();
    await refresh();
    connect();
    setInterval(refresh, 5000);
  })();
})();
