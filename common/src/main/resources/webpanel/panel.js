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
      "state.running": "Running", "state.paused": "Paused", "state.cooldown": "Cooling down",
      "confirm.stopAll": "Stop every execution?",
      "confirm.stopAll.body": "%s execution(s) will be stopped.",
      "confirm.delete": "Delete '%s'?",
      "confirm.delete.body": "This permanently deletes the timer and stops every execution of it. It cannot be undone.",
      "dialog.new": "New timer", "dialog.clone": "Copy '%s'", "dialog.start": "Start '%s'",
      "dialog.edit": "Edit '%s'", "field.name": "Name", "field.newName": "New name",
      "field.hours": "Hours", "field.minutes": "Minutes", "field.seconds": "Seconds",
      "field.direction": "Direction", "field.audience": "Audience", "field.mode": "Mode",
      "field.playerNames": "Player names, separated by commas",
      countdown: "Countdown", countup: "Count up", shared: "Shared", each: "One each",
      "group.identity": "The timer", "group.display": "Where it draws",
      "group.colors": "Colours", "group.sound": "Sound", "group.titles": "Text around it",
      "group.repeat": "Repeating", "group.sequence": "Handing over",
      "group.score": "Scoreboard", "group.expression": "Expression",
      "group.trigger": "Game event", "group.commands": "Commands",
      "group.server": "Server", "group.web": "Web",
      "cmd.add": "Add", "cmd.none": "This timer runs no commands", "cmd.end": "At the end",
      "cmd.text": "Command, without the leading slash",
      on: "On", off: "Off", finish: "Finish it", startIt: "Start it", none: "Off",
      "trigger.join": "A player joins", "trigger.leave": "A player leaves",
      "trigger.death": "A player dies", "trigger.respawn": "A player respawns",
      connected: "Connected", offline: "Reconnecting", badValue: "Check the values in red",
      "default": "Default"
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
      "state.running": "En marcha", "state.paused": "En pausa", "state.cooldown": "En espera",
      "confirm.stopAll": "¿Parar todas las ejecuciones?",
      "confirm.stopAll.body": "Se pararán %s ejecución(es).",
      "confirm.delete": "¿Borrar '%s'?",
      "confirm.delete.body": "Esto elimina el contador de forma permanente y detiene todas sus ejecuciones. No se puede deshacer.",
      "dialog.new": "Contador nuevo", "dialog.clone": "Copiar '%s'", "dialog.start": "Arrancar '%s'",
      "dialog.edit": "Editar '%s'", "field.name": "Nombre", "field.newName": "Nombre nuevo",
      "field.hours": "Horas", "field.minutes": "Minutos", "field.seconds": "Segundos",
      "field.direction": "Sentido", "field.audience": "Audiencia", "field.mode": "Modo",
      "field.playerNames": "Nombres de jugador, separados por comas",
      countdown: "Cuenta atrás", countup: "Cuenta adelante", shared: "Compartido",
      each: "Uno por jugador",
      "group.identity": "El contador", "group.display": "Dónde se dibuja",
      "group.colors": "Colores", "group.sound": "Sonido", "group.titles": "Texto alrededor",
      "group.repeat": "Repetición", "group.sequence": "Cesión del turno",
      "group.score": "Marcador", "group.expression": "Expresión",
      "group.trigger": "Evento del juego", "group.commands": "Comandos",
      "group.server": "Servidor", "group.web": "Web",
      "cmd.add": "Añadir", "cmd.none": "Este contador no ejecuta ningún comando",
      "cmd.end": "Al final", "cmd.text": "Comando, sin la barra inicial",
      on: "Sí", off: "No", finish: "Terminarlo", startIt: "Arrancarlo", none: "Nada",
      "trigger.join": "Entra un jugador", "trigger.leave": "Sale un jugador",
      "trigger.death": "Muere un jugador", "trigger.respawn": "Reaparece un jugador",
      connected: "Conectado", offline: "Reconectando", badValue: "Revisa los valores en rojo",
      "default": "Por defecto"
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
      if (result && result.success === false) return false;
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

  /** The colour the counter wears in game, by the timer's own thresholds. */
  function runColour(run, ticks) {
    const target = run.targetTicks || 1;
    let pct = (ticks * 100) / target;
    if (run.countUp) pct = 100 - pct;
    const d = run.display || {};
    const hex = n => "#" + ((n ?? 0xFFFFFF) & 0xFFFFFF).toString(16).padStart(6, "0");
    if (pct >= (d.thresholdMid ?? 30)) return hex(d.colorHigh);
    if (pct >= (d.thresholdLow ?? 10)) return hex(d.colorMid);
    return hex(d.colorLow);
  }

  async function refresh() {
    try {
      state = await api("/api/state");
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
    dot.title = live ? t("connected") : t("offline");
  }

  // -------------------------------------------------------------- drawing

  function render() {
    $$(".tab").forEach(b => {
      b.setAttribute("aria-selected", String(b.dataset.tab === tab));
    });
    $("#panel-runs").hidden = tab !== "runs";
    $("#panel-timers").hidden = tab !== "timers";
    $("#panel-settings").hidden = tab !== "settings";
    if (tab === "runs") renderRuns();
    if (tab === "timers") renderTimers();
    if (tab === "settings") renderSettings();
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

  function renderRuns() {
    const host = $("#runs");
    const runs = state.runs || [];
    $("#runs-empty").hidden = runs.length > 0;
    $("#stop-all").hidden = runs.length === 0;

    host.replaceChildren(...runs.map((run, i) => {
      const card = document.createElement("article");
      const kind = stateOf(run);
      card.className = "card " + kind;
      card.style.animationDelay = Math.min(i * 30, 240) + "ms";
      card.dataset.runId = run.runId;
      card.innerHTML = `
        <div class="card-head">
          <span class="card-name"></span>
          <span class="state ${kind}"></span>
        </div>
        <div class="clock"></div>
        <div class="sub"></div>
        <div class="progress"><i></i></div>
        <div class="actions"></div>`;
      $(".card-name", card).textContent = run.timerName;
      $(".state", card).textContent = t("state." + kind);
      $(".sub", card).textContent =
        `${t("runs.seenBy")} ${audienceOf(run)} · ${t(run.mode === "EACH" ? "each" : "shared")}`;

      const actions = $(".actions", card);
      for (const [op, label, cls] of [
        ["run.pause", "pause", "ghost"], ["run.resume", "resume", "ghost"],
        ["run.reset", "reset", "ghost"], ["run.stop", "stop", "danger"]
      ]) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = cls + " small";
        button.textContent = t(label);
        const cooling = run.phase && run.phase !== "ACTIVE";
        button.disabled = (op === "run.pause" && (!run.running || cooling))
          || (op === "run.resume" && (run.running || cooling));
        button.onclick = () => act(op, { runId: run.runId });
        actions.append(button);
      }
      return card;
    }));
    tick();
  }

  /** Redraws only the numbers, sixty times a second, without touching the DOM shape. */
  function tick() {
    for (const card of $$("#runs .card")) {
      const run = (state.runs || []).find(r => r.runId === card.dataset.runId);
      if (!run) continue;
      const ticks = liveTicks(run);
      $(".clock", card).textContent = clock(ticks);
      $(".clock", card).style.color = runColour(run, ticks);
      const bar = $(".progress > i", card);
      const pct = run.targetTicks ? (ticks * 100) / run.targetTicks : 0;
      bar.style.width = Math.max(0, Math.min(100, pct)) + "%";
      bar.style.background = runColour(run, ticks);
    }
    requestAnimationFrame(tick);
  }

  function renderTimers() {
    const host = $("#timers");
    const needle = filter.trim().toLowerCase();
    const timers = (state.timers || []).filter(x => !needle || x.name.toLowerCase().includes(needle));
    $("#timers-empty").hidden = timers.length > 0;
    $("#timers-empty").textContent = (state.timers || []).length ? t("timers.noMatch") : t("timers.empty");

    host.replaceChildren(...timers.map((timer, i) => {
      const card = document.createElement("article");
      card.className = "card" + (timer.runCount > 0 ? " running" : "");
      card.style.animationDelay = Math.min(i * 30, 240) + "ms";
      card.innerHTML = `
        <div class="card-head"><span class="card-name"></span><span class="tag"></span></div>
        <div class="clock"></div>
        <div class="sub"></div>
        <div class="actions"></div>`;
      $(".card-name", card).textContent = timer.name;
      $(".tag", card).textContent = timer.resolvedPreset || "";
      $(".clock", card).textContent =
        (timer.countUp ? "↑ " : "↓ ") + clock(timer.targetTicks);
      const bits = [];
      if (timer.repeat) bits.push(t("group.repeat"));
      if (timer.nextTimer) bits.push("→ " + timer.nextTimer);
      if (timer.silent) bits.push(t("group.sound") + ": " + t("off"));
      $(".sub", card).textContent = bits.join(" · ") || " ";

      const actions = $(".actions", card);
      const add = (label, cls, fn) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = cls + " small";
        button.textContent = label;
        button.onclick = fn;
        actions.append(button);
        return button;
      };
      if (timer.runCount > 0) {
        add(t("stop"), "ghost", () => {
          for (const run of state.runs.filter(r => r.timerName === timer.name)) {
            act("run.stop", { runId: run.runId });
          }
        });
      } else {
        add(t("start"), "primary", () => startDialog(timer));
      }
      add(t("edit"), "ghost", () => editDialog(timer));
      add(t("clone"), "ghost", () => cloneDialog(timer));
      add(t("delete"), "danger", () => deleteDialog(timer));
      return card;
    }));
  }

  // -------------------------------------------------------------- editing

  /** The settings the server holds, and the twelve a timer copies from them. */
  const CONFIG_GROUPS = [
    ["display", [["positionPreset", "preset"], ["timerX", "int"], ["timerY", "int"],
      ["timerScale", "float"]]],
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

  const label = key => key
    .replace(/([A-Z])/g, " $1")
    .replace(/^./, c => c.toUpperCase())
    .trim();

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
      for (const preset of (state.presets || [])) input.append(new Option(preset, preset));
      input.value = value;
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

  function renderSettings() {
    const host = $("#settings");
    host.replaceChildren(...CONFIG_GROUPS.map(([group, keys]) => {
      const section = document.createElement("section");
      section.className = "group";
      const head = document.createElement("h3");
      // Only the three a timer takes a copy of are "defaults"; server and web
      // are global by nature and there is no per-timer version of them.
      head.textContent = ["display", "colors", "sound"].includes(group)
        ? t("default") + " · " + t("group." + group) : t("group." + group);
      section.append(head);
      for (const [key, kind] of keys) {
        section.append(field(key, kind, state.config[key], markSettings));
      }
      return section;
    }));
    markSettings();
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
    build(body);
    const menu = $("#modal-actions");
    menu.replaceChildren(...actions.map(([label, cls, fn]) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = cls;
      button.textContent = label;
      button.onclick = async () => {
        if (!fn || (await fn()) !== false) dialog.close();
      };
      return button;
    }));
    dialog.showModal();
  }

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
      [t("cancel"), "ghost", null],
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
      [t("cancel"), "ghost", null],
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
      [t("cancel"), "ghost", null],
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
    }, [
      [t("cancel"), "ghost", null],
      [t("save"), "primary", () => {
        const get = key => $(`#modal-body [data-key='${key}']`);
        return act("timer.create", {
          name: get("name").value.trim(),
          hours: parseInt(get("hours").value, 10) || 0,
          minutes: parseInt(get("minutes").value, 10) || 0,
          seconds: parseInt(get("seconds").value, 10) || 0,
          countUp: get("countUp").value === "true"
        });
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
  function editDialog(timer) {
    const display = timer.display || {};
    modal(t("dialog.edit", timer.name), body => {
      const group = (name, build) => {
        const section = document.createElement("section");
        section.className = "group";
        const head = document.createElement("h3");
        head.textContent = t("group." + name);
        section.append(head);
        build(section);
        body.append(section);
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
          s.append(field("t:" + slot, "text", titles[slot] || ""));
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
        s.append(field("nextTimer", "text", timer.nextTimer || ""));
        s.append(field("sequenceCooldown", "int", Math.floor((timer.sequenceCooldownTicks || 0) / 20)));
      });

      group("score", s => {
        s.append(field("objective", "text", timer.conditionObjective || ""));
        s.append(field("score", "int", timer.conditionScore ?? 0));
        s.append(field("target", "text", timer.conditionTarget || "*"));
        const action = field("scoreAction", "bool", timer.conditionAction || "finish");
        $("select", action).replaceChildren(new Option(t("finish"), "finish"),
          new Option(t("startIt"), "start"));
        $("select", action).value = timer.conditionAction || "finish";
        s.append(action);
      });

      group("expression", s => {
        s.append(field("expression", "text", timer.conditionExpression || ""));
        const action = field("expressionAction", "bool", timer.conditionExpressionAction || "finish");
        $("select", action).replaceChildren(new Option(t("finish"), "finish"),
          new Option(t("startIt"), "start"));
        $("select", action).value = timer.conditionExpressionAction || "finish";
        s.append(action);
      });

      group("trigger", s => {
        const type = field("trigger", "bool", timer.triggerType || "");
        $("select", type).replaceChildren(new Option(t("none"), ""),
          ...["join", "leave", "death", "respawn"].map(x => new Option(t("trigger." + x), x)));
        $("select", type).value = timer.triggerType || "";
        s.append(type);
        const action = field("triggerAction", "bool", timer.triggerAction || "finish");
        $("select", action).replaceChildren(new Option(t("finish"), "finish"),
          new Option(t("startIt"), "start"));
        $("select", action).value = timer.triggerAction || "finish";
        s.append(action);
      });

      group("commands", s => {
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
        adder.className = "cmd-row";
        const cells = document.createElement("div");
        cells.className = "row";
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
      [t("cancel"), "ghost", null],
      [t("apply"), "primary", async () => {
        const get = key => $(`#modal-body [data-key='${key}']`);
        const num = key => parseInt(get(key).value, 10) || 0;

        await act("timer.setTime",
          { name: timer.name, hours: num("hours"), minutes: num("minutes"), seconds: num("seconds") });
        await act("timer.setSilent", { name: timer.name, silent: get("silent").value === "true" });

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
        await act("timer.setCondition", {
          name: timer.name, objective: get("objective").value.trim(), score: num("score"),
          target: get("target").value.trim(), scoreAction: get("scoreAction").value,
          expression: get("expression").value.trim(),
          expressionAction: get("expressionAction").value
        });
        await act("timer.setTrigger", {
          name: timer.name, type: get("trigger").value, action: get("triggerAction").value
        });
      }]
    ]);
  }

  // ---------------------------------------------------------------- wiring

  $$(".tab").forEach(b => b.onclick = () => { tab = b.dataset.tab; render(); });
  $("#new-timer").onclick = newDialog;
  $("#search").oninput = e => { filter = e.target.value; renderTimers(); };
  $("#stop-all").onclick = () => {
    modal(t("confirm.stopAll"), body => {
      const p = document.createElement("p");
      p.className = "muted";
      p.textContent = t("confirm.stopAll.body", (state.runs || []).length);
      body.append(p);
    }, [[t("cancel"), "ghost", null], [t("stop"), "danger", () => act("run.stopAll")]]);
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
