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
      "tab.runs": "In progress", "tab.timers": "Timers",
      "tab.settings": "Settings", apply: "Apply", discard: "Discard", cancel: "Cancel",
      save: "Save", close: "Close", add: "Add", "runs.title": "Executions in progress",
      "runs.stopAll": "Stop all", "runs.empty": "No timers are running",
      "runs.seenBy": "Seen by", "runs.everyone": "Everyone", "runs.nobody": "Nobody",
      "runs.players": "%s players", "timers.new": "New",
      "timers.search": "Search", "timers.empty": "No timers exist yet",
      "timers.noMatch": "Nothing matches that", "settings.title": "Server defaults",
      pause: "Pause", resume: "Resume", reset: "Reset", stop: "Stop", start: "Start",
      clone: "Clone", accept: "Accept", "delete": "Delete", "state.running": "Running", "state.paused": "Paused", "state.cooldown": "In cooldown",
      "confirm.stopAll": "Stop every execution?",
      "confirm.stopAll.body": "%s execution(s) will be stopped.",
      "confirm.delete": "Delete '%s'?",
      "confirm.delete.body": "This permanently deletes the timer and stops every execution of it. It cannot be undone.",
      "dialog.new": "New timer", "dialog.clone": "Clone '%s'", "dialog.start": "Start '%s'",
      "editor.timer": "Timer", "editor.look": "Appearance",
      "editor.triggers": "Triggers", "editor.commands": "Commands",
      "field.hours": "Hours", "field.minutes": "Minutes", "field.seconds": "Seconds",
      "field.playerNames": "Player names, separated by commas",
      countdown: "Countdown", countup: "Count up", shared: "Shared", each: "One each",
      "group.identity": "The timer", "group.display": "Where it draws",
      "group.colors": "Colours", "group.sound": "Sound", "group.titles": "Text around it",
      "group.repeat": "Repeating", "group.sequence": "Handing over",
      "group.triggers": "What starts or ends it", "group.commands": "Commands",
      "trg.none": "Nothing starts or ends this timer early",
      "trg.value": "Id", "trg.score": "Score",
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
      "trg.dropBranch": "Remove this alternative", "trg.dropGroup": "Remove this bracket",
      "trg.startsWhen": "Starts it when...", "trg.endsWhen": "Ends it when...",
      "trg.noStart": "Nothing starts it early", "trg.noFinish": "Nothing ends it early",
      "trg.say.when": "when", "trg.say.here": "joins this alternative",
      "trg.say.newRule": "an alternative of its own",
      "trg.v.dimension_change": "Dimension, e.g. minecraft:the_nether",
      "trg.v.advancement": "Advancement", "trg.v.ftb_quest": "Quest id",
      "trg.v.ftb_reward": "Reward id", "trg.v.scoreboard": "Objective",
      "trg.groupAll": "all of these hold at once", "trg.groupAny": "any of these holds",
      "trg.groupAtLeast": "at least %s of these hold",
      "trg.orGroup": "or", "trg.and": "and",
      "trg.startsIt": "Starts it", "trg.endsIt": "Ends it",
      "group.server": "Server", "group.web": "Web", "group.reset": "Back to defaults",
      "reset.what": "Every setting above, back to what the mod ships with",
      "reset.do": "Restore defaults", "reset.title": "Restore every default?",
      "reset.body": "Applied at once, and it cannot be undone. Timers that already exist keep their own values.",
      "cmd.none": "This timer runs no commands", "cmd.end": "At the end",
      "cmd.wait": "Wait", "cmd.waits": "then waits %s",
      "cmd.text": "Command, without the leading slash",
      on: "On", off: "Off", finish: "Finish it", startIt: "Start it", none: "None",
      connected: "Connected", offline: "Reconnecting", badValue: "Check the values in red",
      applied: "%s change(s) applied", nothingToApply: "Nothing has changed",
      "default": "Default", "runs.of": "of %s",
      "lead.runs": "%s running right now",
      "lead.timers": "%s defined on this server",
      "lead.settings": "What a new timer starts with, and what the server itself does",

      // The explanation layer. Same ideas the in-game tooltips carry, written
      // for a surface with room to show them: no colour codes, and a backtick
      // around a value to type or pick.
      "hint.position": "Where the counter sits. Pick `CUSTOM` to place it yourself.",
      "hint.customPosition": "The spot `CUSTOM` puts the counter in.",
      "hint.scale": "How big the counter is drawn, from `0.1` to `5.0`.",
      "hint.hideOnCooldown": "A counter waiting out a repeat disappears instead of sitting there stopped.",
      "hint.colorHigh": "Colour while plenty of time is left.",
      "hint.colorMid": "Colour between the two thresholds.",
      "hint.colorLow": "Colour once it is nearly out.",
      "hint.thresholdMid": "Below this percentage the middle colour takes over.",
      "hint.thresholdLow": "Below this percentage the last colour takes over.",
      "hint.soundId": "Sound played each second, for example `minecraft:block.note_block.hat`.",
      "hint.soundVolume": "How loud that sound is, from `0.0` to `1.0`.",
      "hint.soundPitch": "How high that sound is, from `0.5` to `2.0`.",
      "hint.maxTimerSeconds": "The longest a timer may be, in seconds.",
      "hint.confirmRunThreshold": "Ask before creating this many executions at once. `0` always asks, `-1` never does.",
      "hint.webSocketEnabled": "Whether the event feed accepts connections.",
      "hint.webSocketPort": "Port the event feed listens on. Applies next start.",
      "hint.webPanelPort": "Port this panel listens on. Applies next start.",
      "hint.silent": "No tick sound from this timer, whatever the settings say.",
      "hint.repeat": "Start again on its own when it ends.",
      "hint.repeatCount": "How many more times. `-1` for always.",
      "hint.repeatCooldown": "Seconds of pause before it starts again. `0` for none.",
      "hint.nextTimer": "A timer to hand over to when this one ends. It cannot be itself.",
      "hint.sequenceCooldown": "Seconds of pause before that one starts. `0` for none.",
      "hint.titleAbove": "Text drawn over the counter. Plain text or tellraw JSON.",
      "hint.titleBelow": "Text drawn under the counter. Plain text or tellraw JSON.",
      "hint.titleSide": "Text drawn beside the counter.",
      "hint.name": "Letters, digits and `_ . + -`, up to 32 characters.",
      "hint.newName": "The copy needs a name of its own.",
      "hint.countUp": "Counts down to zero, or up to its length.",
      "hint.length": "How long it runs for, added up.",
      "hint.finishCommand": "Runs when the timer reaches its end. Optional; more can be added later.",
      "hint.audience": "Who sees this execution.",
      "hint.mode": "One clock everybody shares, or a clock each.",

      "label.position": "Position", "label.customX": "Custom X",
      "label.customY": "Custom Y", "label.scale": "Scale",
      "label.hideOnCooldown": "Hide during cooldown",
      "label.colorHigh": "Plenty left", "label.colorMid": "Running low",
      "label.colorLow": "Almost out",
      "label.thresholdMid": "Running low below (%)",
      "label.thresholdLow": "Almost out below (%)",
      "label.soundId": "Tick sound", "label.soundVolume": "Tick volume",
      "label.soundPitch": "Tick pitch",
      "label.maxTimerSeconds": "Longest timer (s)",
      "label.confirmRunThreshold": "Confirm above N executions",
      "label.webSocketEnabled": "WebSocket", "label.webSocketPort": "WebSocket port",
      "label.webPanelPort": "Web panel port",
      "label.silent": "Silent", "label.repeat": "Repeat",
      "label.repeatCount": "How many more times",
      "label.repeatCooldown": "Pause between repeats (s)",
      "label.nextTimer": "Hands over to",
      "label.sequenceCooldown": "Pause before that one (s)",
      "label.above": "Above", "label.below": "Below",
      "label.left": "Left", "label.right": "Right",
      "label.name": "Name", "label.dest": "New name",
      "label.countUp": "Direction", "label.finishCommand": "Command when it ends",
      "label.audience": "Audience", "label.mode": "Mode", "label.players": "Players"
    },
    es: {
      "tab.runs": "En curso", "tab.timers": "Contadores",
      "tab.settings": "Ajustes", apply: "Aplicar", discard: "Descartar", cancel: "Cancelar",
      save: "Guardar", close: "Cerrar", add: "Añadir", "runs.title": "Ejecuciones en curso",
      "runs.stopAll": "Parar todo", "runs.empty": "No hay contadores en marcha",
      "runs.seenBy": "Lo ven", "runs.everyone": "Todos", "runs.nobody": "Nadie",
      "runs.players": "%s jugadores", "timers.new": "Nuevo",
      "timers.search": "Buscar", "timers.empty": "Todavía no hay contadores",
      "timers.noMatch": "Nada coincide con eso", "settings.title": "Valores por defecto",
      pause: "Pausar", resume: "Reanudar", reset: "Reiniciar", stop: "Parar", start: "Arrancar",
      clone: "Clonar", accept: "Aceptar", "delete": "Borrar", "state.running": "En marcha", "state.paused": "En pausa", "state.cooldown": "En cooldown",
      "confirm.stopAll": "¿Parar todas las ejecuciones?",
      "confirm.stopAll.body": "Se pararán %s ejecución(es).",
      "confirm.delete": "¿Borrar '%s'?",
      "confirm.delete.body": "Esto elimina el contador de forma permanente y detiene todas sus ejecuciones. No se puede deshacer.",
      "dialog.new": "Contador nuevo", "dialog.clone": "Clonar '%s'", "dialog.start": "Arrancar '%s'",
      "editor.timer": "Contador", "editor.look": "Apariencia",
      "editor.triggers": "Disparadores", "editor.commands": "Comandos",
      "field.hours": "Horas", "field.minutes": "Minutos", "field.seconds": "Segundos",
      "field.playerNames": "Nombres de jugador, separados por comas",
      countdown: "Cuenta atrás", countup: "Cuenta adelante", shared: "Compartido",
      each: "Uno por jugador",
      "group.identity": "El contador", "group.display": "Dónde se dibuja",
      "group.colors": "Colores", "group.sound": "Sonido", "group.titles": "Texto alrededor",
      "group.repeat": "Repetición", "group.sequence": "Cesión del turno",
      "group.triggers": "Qué lo arranca o lo termina", "group.commands": "Comandos",
      "trg.none": "Nada arranca ni termina este contador antes de tiempo",
      "trg.value": "Id", "trg.score": "Puntuación",
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
      "trg.dropBranch": "Quitar esta alternativa", "trg.dropGroup": "Quitar este bloque",
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
      "trg.orGroup": "o", "trg.and": "y",
      "trg.startsIt": "Lo arranca", "trg.endsIt": "Lo termina",
      "group.server": "Servidor", "group.web": "Web", "group.reset": "Restablecer",
      "reset.what": "Todos los ajustes de arriba, a los que trae el mod",
      "reset.do": "Restaurar valores", "reset.title": "¿Restaurar todos los valores?",
      "reset.body": "Se aplica al momento y no se puede deshacer. Los contadores ya creados conservan los suyos.",
      "cmd.none": "Este contador no ejecuta ningún comando",
      "cmd.wait": "Espera", "cmd.waits": "luego espera %s",
      "cmd.end": "Al final", "cmd.text": "Comando, sin la barra inicial",
      on: "Sí", off: "No", finish: "Terminarlo", startIt: "Arrancarlo", none: "Nada",
      connected: "Conectado", offline: "Reconectando", badValue: "Revisa los valores en rojo",
      applied: "%s cambio(s) aplicados", nothingToApply: "No hay nada que aplicar",
      "default": "Por defecto", "runs.of": "de %s",
      "lead.runs": "%s en marcha ahora mismo",
      "lead.timers": "%s definidos en este servidor",
      "lead.settings": "Con qué arranca un contador nuevo, y qué hace el servidor",

      "hint.position": "Dónde se sitúa el contador. Elige `CUSTOM` para colocarlo tú.",
      "hint.customPosition": "El punto donde `CUSTOM` pone el contador.",
      "hint.scale": "Tamaño del contador, de `0.1` a `5.0`.",
      "hint.hideOnCooldown": "Un contador esperando una repetición desaparece en vez de quedarse ahí parado.",
      "hint.colorHigh": "Color mientras queda tiempo de sobra.",
      "hint.colorMid": "Color entre los dos umbrales.",
      "hint.colorLow": "Color cuando ya casi no queda.",
      "hint.thresholdMid": "Por debajo de este porcentaje entra el color intermedio.",
      "hint.thresholdLow": "Por debajo de este porcentaje entra el último color.",
      "hint.soundId": "Sonido que suena cada segundo, por ejemplo `minecraft:block.note_block.hat`.",
      "hint.soundVolume": "Volumen de ese sonido, de `0.0` a `1.0`.",
      "hint.soundPitch": "Tono de ese sonido, de `0.5` a `2.0`.",
      "hint.maxTimerSeconds": "Lo más largo que puede ser un contador, en segundos.",
      "hint.confirmRunThreshold": "Preguntar antes de crear tantas ejecuciones de golpe. `0` pregunta siempre, `-1` nunca.",
      "hint.webSocketEnabled": "Si el canal de eventos acepta conexiones.",
      "hint.webSocketPort": "Puerto del canal de eventos. Se aplica al reiniciar.",
      "hint.webPanelPort": "Puerto de este panel. Se aplica al reiniciar.",
      "hint.silent": "Este contador no suena, digan lo que digan los ajustes.",
      "hint.repeat": "Vuelve a empezar solo cuando termina.",
      "hint.repeatCount": "Cuántas veces más. `-1` para siempre.",
      "hint.repeatCooldown": "Segundos de pausa antes de volver a empezar. `0` para ninguna.",
      "hint.nextTimer": "Contador al que cede el turno cuando éste termina. No puede ser él mismo.",
      "hint.sequenceCooldown": "Segundos de pausa antes de que arranque ése. `0` para ninguna.",
      "hint.titleAbove": "Texto dibujado sobre el contador. Texto plano o JSON de tellraw.",
      "hint.titleBelow": "Texto dibujado bajo el contador. Texto plano o JSON de tellraw.",
      "hint.titleSide": "Texto dibujado al lado del contador.",
      "hint.name": "Letras, dígitos y `_ . + -`, hasta 32 caracteres.",
      "hint.newName": "La copia necesita un nombre propio.",
      "hint.countUp": "Cuenta hacia cero, o hacia su duración.",
      "hint.length": "Lo que dura, todo sumado.",
      "hint.finishCommand": "Se ejecuta cuando el contador llega al final. Opcional; puedes añadir más después.",
      "hint.audience": "Quién ve esta ejecución.",
      "hint.mode": "Un reloj que todos comparten, o un reloj para cada uno.",

      "label.position": "Posición", "label.customX": "X personalizada",
      "label.customY": "Y personalizada", "label.scale": "Escala",
      "label.hideOnCooldown": "Ocultar en cooldown",
      "label.colorHigh": "Queda mucho", "label.colorMid": "Queda poco",
      "label.colorLow": "Casi nada",
      "label.thresholdMid": "Queda poco por debajo de (%)",
      "label.thresholdLow": "Casi nada por debajo de (%)",
      "label.soundId": "Sonido del tic", "label.soundVolume": "Volumen del tic",
      "label.soundPitch": "Tono del tic",
      "label.maxTimerSeconds": "Contador más largo (s)",
      "label.confirmRunThreshold": "Confirmar a partir de N ejecuciones",
      "label.webSocketEnabled": "WebSocket", "label.webSocketPort": "Puerto del WebSocket",
      "label.webPanelPort": "Puerto del panel web",
      "label.silent": "Silencioso", "label.repeat": "Repetir",
      "label.repeatCount": "Cuántas veces más",
      "label.repeatCooldown": "Pausa entre repeticiones (s)",
      "label.nextTimer": "Cede el turno a",
      "label.sequenceCooldown": "Pausa antes de ése (s)",
      "label.above": "Arriba", "label.below": "Abajo",
      "label.left": "Izquierda", "label.right": "Derecha",
      "label.name": "Nombre", "label.dest": "Nombre nuevo",
      "label.countUp": "Sentido", "label.finishCommand": "Comando al terminar",
      "label.audience": "Audiencia", "label.mode": "Modo", "label.players": "Jugadores"
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

  let drawnRuns = "", drawnTimers = "", drawnSettings = "", drawnEditor = "";

  function render() {
    $$(".nav-item").forEach(b => {
      b.setAttribute("aria-selected", String(b.dataset.tab === tab));
    });
    // Timers is one of two things: the list, or the editor that replaced it.
    $("#panel-runs").hidden = tab !== "runs";
    $("#panel-timers").hidden = tab !== "timers" || editing !== null;
    $("#panel-editor").hidden = tab !== "timers" || editing === null;
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
    if (tab === "timers" && editing === null) {
      const key = filter + "\u0000" + JSON.stringify(state.timers || []);
      if (key !== drawnTimers) { drawnTimers = key; renderTimers(); }
    }
    if (tab === "timers" && editing !== null) {
      // Only when the timer itself moved. The board arrives four times a
      // second and rebuilding the form that often would take the caret out of
      // whatever box it was in.
      const key = JSON.stringify((state.timers || []).find(x => x.name === editing));
      if (key !== drawnEditor) { drawnEditor = key; renderEditor(); }
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
      card.onclick = e => { if (!e.target.closest("button")) openEditor(timer.name); };
      card.onkeydown = e => {
        if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openEditor(timer.name); }
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
    ["server", [["maxTimerSeconds", "int"],
      ["confirmRunThreshold", "int"]]],
    ["web", [["webSocketEnabled", "bool"], ["webSocketPort", "int"], ["webPanelPort", "int"]]]
  ];

  const DISPLAY_KEYS = [
    ["display", [["preset", "preset"], ["x", "int"], ["y", "int"], ["scale", "float"]]],
    ["colors", [["colorHigh", "color"], ["colorMid", "color"], ["colorLow", "color"],
      ["thresholdMid", "int"], ["thresholdLow", "int"]]],
    ["sound", [["soundId", "text"], ["soundVolume", "float"], ["soundPitch", "float"]]]
  ];

  /**
   * What a field is called, in the language the panel is in.
   *
   * <p>One name per idea, and not one per screen: the settings a timer takes a
   * copy of are the same idea in Settings and in the editor, so they answer to
   * the same key. They used to answer to two -- "Position Preset" in one and
   * "Position" in the other -- because the two forms spell the key
   * differently.</p>
   *
   * <p>Falling back to the key with its camel case broken up, which is
   * English. Every field has a name below, so the fallback should never show;
   * it is there so that adding a field spells it out rather than throwing.</p>
   */
  const LABELS = {
    positionPreset: "position", "d:preset": "position",
    timerX: "customX", "d:x": "customX",
    timerY: "customY", "d:y": "customY",
    timerScale: "scale", "d:scale": "scale",
    "d:colorHigh": "colorHigh", "d:colorMid": "colorMid", "d:colorLow": "colorLow",
    "d:thresholdMid": "thresholdMid", "d:thresholdLow": "thresholdLow",
    timerSoundId: "soundId", "d:soundId": "soundId",
    timerSoundVolume: "soundVolume", "d:soundVolume": "soundVolume",
    timerSoundPitch: "soundPitch", "d:soundPitch": "soundPitch",
    "t:above": "above", "t:below": "below", "t:left": "left", "t:right": "right",
  };

  const label = key => {
    const named = LABELS[key] || key.replace(/^[a-z]:/, "");
    const written = t("label." + named);
    if (written !== "label." + named) return written;
    return named
      .replace(/([A-Z])/g, " $1")
      .replace(/^./, c => c.toUpperCase())
      .trim();
  };

  /**
   * Which explanation a field carries, decided in one place.
   *
   * <p>One idea, one key, not one per screen: the settings a timer takes a
   * copy of say the same thing in Settings and in the editor, so they share
   * the line rather than keeping two that drift. The same decision the
   * in-game panel makes in fieldTipKey, made once here.</p>
   */
  const HINTS = {
    positionPreset: "position", "d:preset": "position",
    timerX: "customPosition", timerY: "customPosition",
    "d:x": "customPosition", "d:y": "customPosition",
    timerScale: "scale", "d:scale": "scale",
    colorHigh: "colorHigh", "d:colorHigh": "colorHigh",
    colorMid: "colorMid", "d:colorMid": "colorMid",
    colorLow: "colorLow", "d:colorLow": "colorLow",
    thresholdMid: "thresholdMid", "d:thresholdMid": "thresholdMid",
    thresholdLow: "thresholdLow", "d:thresholdLow": "thresholdLow",
    timerSoundId: "soundId", "d:soundId": "soundId",
    timerSoundVolume: "soundVolume", "d:soundVolume": "soundVolume",
    timerSoundPitch: "soundPitch", "d:soundPitch": "soundPitch",
    "t:above": "titleAbove", "t:below": "titleBelow",
    "t:left": "titleSide", "t:right": "titleSide",
    dest: "newName",
  };

  const hintKey = key => "hint." + (HINTS[key] || key.replace(/^[a-z]:/, ""));

  /**
   * Which boxes complete, and from what.
   *
   * <p>Decided in one table for the same reason the labels and the hints are:
   * the sound a timer plays and the sound the server hands new timers are the
   * same idea, so they complete from the same list without anybody having to
   * remember to wire the second one.</p>
   */
  const COMPLETES = {
    timerSoundId: sounds, "d:soundId": sounds,
    nextTimer: () => (state.timers || []).map(x => x.name),
  };

  /**
   * The explanation, as a line under the field.
   *
   * <p>Where the game has to hide this behind a hover -- there is no room on a
   * 320-pixel screen -- a browser can simply show it, so it does. Same idea,
   * and deliberately not the same shape.</p>
   *
   * <p>Backticks mark a value to type or pick, which is what the game paints
   * yellow. Written as text nodes either way: a hint is translated copy and
   * must never be able to carry markup into the page.</p>
   */
  function hint(key) {
    const text = t(hintKey(key));
    if (text === hintKey(key)) return null;
    const line = document.createElement("small");
    line.className = "hint";
    text.split(/`([^`]+)`/).forEach((part, i) => {
      if (!part) return;
      if (i % 2 === 0) return void line.append(document.createTextNode(part));
      const code = document.createElement("code");
      code.textContent = part;
      line.append(code);
    });
    return line;
  }

  function field(key, kind, value, onInput) {
    const wrap = document.createElement("div");
    wrap.className = "field";
    const lab = document.createElement("label");
    lab.textContent = label(key);
    wrap.append(lab);

    let input;
    /** Only a colour has one: the box beside the swatch. */
    let hex = null;
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
      // A picker is the browser's advantage and the reason there is no colour
      // wheel in game -- but a picker cannot be pasted into, and a colour is
      // very often something one already has written down. So both, side by
      // side, each following the other. The text does not need to wear the
      // colour the way it does in game: the swatch beside it is the reference.
      hex = document.createElement("input");
      hex.type = "text";
      hex.className = "hex";
      hex.spellcheck = false;
      hex.maxLength = 7;
      hex.value = input.value.toUpperCase();
      input.addEventListener("input", () => { hex.value = input.value.toUpperCase(); });
      hex.addEventListener("input", () => {
        const typed = hex.value.trim().replace(/^#/, "");
        const ok = /^[0-9a-fA-F]{6}$/.test(typed);
        hex.classList.toggle("bad", !ok && typed !== "");
        if (ok) input.value = "#" + typed.toLowerCase();
      });
      // Tidied when it is let go, so a half-typed value does not stay on
      // screen disagreeing with the swatch beside it.
      hex.addEventListener("blur", () => {
        hex.value = input.value.toUpperCase();
        hex.classList.remove("bad");
      });
    } else {
      input = document.createElement("input");
      input.type = kind === "int" || kind === "float" ? "number" : "text";
      if (kind === "float") input.step = "0.1";
      input.value = value ?? "";
    }
    input.dataset.key = key;
    input.dataset.kind = kind;
    if (COMPLETES[key]) suggestFrom(input, COMPLETES[key]);
    input.addEventListener("input", () => {
      input.classList.toggle("bad", !parses(input));
      if (onInput) onInput();
    });
    if (hex) {
      // The two of them fill the lane the control sits in, which was a swatch
      // and a wide stretch of nothing.
      const pair = document.createElement("div");
      pair.className = "colour";
      pair.append(input, hex);
      wrap.append(pair);
    } else {
      wrap.append(input);
    }
    const explanation = hint(key);
    if (explanation) wrap.append(explanation);
    return wrap;
  }

  /**
   * The five selectors and the keys one can be narrowed by.
   *
   * <p>Written out rather than read from anywhere: the parser that knows them
   * wants a live command source and a whole context, which a text box has
   * neither of. The list is small and the game fixes it, not any pack. The
   * same list FieldAssist keeps in game, for the same reason.</p>
   */
  const SELECTOR_WORDS = ["@a", "@e", "@p", "@r", "@s",
    "advancements=", "distance=", "dx=", "dy=", "dz=", "gamemode=", "level=",
    "limit=", "name=", "nbt=", "predicate=", "scores=", "sort=", "tag=",
    "team=", "type=", "x=", "y=", "z=", "x_rotation=", "y_rotation="];

  /** Sounds are the one list the board does not carry; asked for once. */
  let soundIds = null;

  async function sounds() {
    if (soundIds) return soundIds;
    try {
      const answer = await api("/api/suggest?kind=sounds");
      soundIds = answer.list || [];
    } catch (ignored) {
      soundIds = [];
    }
    return soundIds;
  }

  /**
   * Matching as a command argument matches.
   *
   * <p>Text with no namespace is matched against the path, which is what lets
   * "bell" find "minecraft:block.note_block.bell" -- the same rule the game
   * follows, and the reason a list of a thousand sounds is usable at all.</p>
   */
  function matching(all, typed) {
    const needle = typed.trim().toLowerCase();
    if (!needle) return all.slice(0, 60);
    const bare = !needle.includes(":");
    return all.filter(one => {
      const id = one.toLowerCase();
      if (id.startsWith(needle)) return true;
      return bare && (id.split(":")[1] || id).includes(needle);
    }).slice(0, 60);
  }

  /**
   * The list under a box, and everything that happens to it.
   *
   * <p>Given to any box that has something to offer. What differs between one
   * and the next is only {@code ask} -- what could come next, and which part
   * of the text it would replace. The keyboard, the scrolling, the placing and
   * the taking are the same everywhere, which is why they live here and not in
   * each caller.</p>
   *
   * @param ask   text -> {suggestions, start, end, cursor}
   * @param paint optional: draws the text underneath, for a box that colours
   */
  let suggestCount = 0;

  function attachSuggest(input, ask, paint) {
    const list = document.createElement("ul");
    list.className = "suggest";
    list.hidden = true;
    // The box keeps the focus and the arrows move a highlight inside a list it
    // never enters, so the list has to say what it is for that to be
    // followable by anything but the eye.
    list.setAttribute("role", "listbox");
    list.setAttribute("id", "suggest-" + (++suggestCount));
    input.setAttribute("role", "combobox");
    input.setAttribute("aria-autocomplete", "list");
    input.setAttribute("aria-expanded", "false");
    // The list is not beside the box in the document -- it is placed against
    // the window -- so this is the only thing tying the two together, for a
    // screen reader and for anything else that has to ask which list is whose.
    input.setAttribute("aria-controls", list.getAttribute("id"));
    input.spellcheck = false;
    input.autocomplete = "off";

    let offers = [];
    let range = { start: 0, end: 0 };
    let picked = -1;
    let timer = 0;
    let asked = 0;

    /** Under the box, or over it when the box is near the bottom of the page. */
    const place = () => {
      const box = input.getBoundingClientRect();
      list.style.left = box.left + "px";
      list.style.width = box.width + "px";
      const below = window.innerHeight - box.bottom;
      if (below < 180 && box.top > below) {
        list.style.top = "auto";
        list.style.bottom = (window.innerHeight - box.top + 4) + "px";
      } else {
        list.style.bottom = "auto";
        list.style.top = (box.bottom + 4) + "px";
      }
    };

    // Anchored to the window means nothing moves it when the sheet under it
    // scrolls -- it would hang in the air over whatever slid beneath. Only
    // listened to while it is up, and let go the moment it is not.
    const follow = () => { if (!list.hidden) place(); };

    const draw = () => {
      list.replaceChildren();
      const was = list.hidden;
      list.hidden = offers.length === 0;
      if (was !== list.hidden) {
        const how = list.hidden ? "removeEventListener" : "addEventListener";
        window[how]("scroll", follow, true);
        window[how]("resize", follow);
        // Placed against the window, so it cannot live inside the sheet: that
        // sheet scrolls, and anything positioned inside a scrolling box is
        // clipped by it. It goes to the dialog it belongs to, or to the page
        // when there is no dialog.
        //
        // The dialog and not the document: a dialog is drawn in the top layer,
        // above everything the page can put anywhere, so a list left on the
        // body was painted underneath it. Which is why the composer offered
        // dimensions and none of them could be seen.
        if (list.hidden) list.remove();
        else (input.closest("dialog") || document.body).append(list);
      }
      input.setAttribute("aria-expanded", String(!list.hidden));
      if (!list.hidden) place();
      offers.forEach((offer, i) => {
        const item = document.createElement("li");
        item.className = i === picked ? "on" : "";
        item.setAttribute("role", "option");
        item.setAttribute("aria-selected", String(i === picked));
        const word = document.createElement("span");
        word.textContent = offer.text;
        item.append(word);
        if (offer.tip) {
          const tip = document.createElement("em");
          tip.textContent = offer.tip;
          item.append(tip);
        }
        // Pressed rather than clicked: a click lands after the box has already
        // lost focus, and losing focus is what puts the list away.
        item.onmousedown = e => { e.preventDefault(); take(i); };
        list.append(item);
      });
    };

    /**
     * Moves the highlight, and the box with it.
     *
     * <p>Apart from draw() because drawing builds the rows again, and building
     * them again puts the box back to the top: the arrows would have undone
     * their own scrolling on every press.</p>
     */
    const highlight = () => {
      [...list.children].forEach((row, i) => {
        row.className = i === picked ? "on" : "";
        row.setAttribute("aria-selected", String(i === picked));
      });
      const row = list.children[picked];
      if (!row) return;
      const top = row.offsetTop || 0;
      const height = row.offsetHeight || 0;
      if (top < list.scrollTop) {
        list.scrollTop = top;
      } else if (top + height > list.scrollTop + list.clientHeight) {
        list.scrollTop = top + height - list.clientHeight;
      }
    };

    const take = i => {
      const offer = offers[i];
      if (!offer) return;
      const text = input.value;
      input.value = text.slice(0, range.start) + offer.text + text.slice(range.end);
      if (input.setSelectionRange) {
        input.setSelectionRange(input.value.length, input.value.length);
      }
      offers = [];
      picked = -1;
      draw();
      // What was taken may itself be half of something: one argument of a
      // command, one name of a list.
      run();
      input.dispatchEvent({ type: "input", target: input });
    };

    async function run() {
      const mine = ++asked;
      try {
        const answer = await ask(input.value);
        // Answers do not come back in order, and an older one landing last
        // would offer completions for text that is no longer there.
        if (mine < asked) return;
        offers = answer.suggestions || [];
        range = {
          start: answer.start ?? input.value.length,
          end: answer.end ?? input.value.length,
        };
        picked = offers.length ? 0 : -1;
        if (paint) paint(answer.cursor);
        draw();
      } catch (ignored) {
        // No suggestions is a box that still works.
        offers = [];
        draw();
      }
    }

    input.addEventListener("input", () => {
      if (paint) paint();
      clearTimeout(timer);
      timer = setTimeout(run, 120);
    });
    input.addEventListener("focus", run);
    input.addEventListener("blur", () => {
      offers = [];
      draw();
    });
    input.addEventListener("keydown", e => {
      if (list.hidden) return;
      if (e.key === "ArrowDown" || e.key === "ArrowUp") {
        e.preventDefault();
        // Stops at the ends rather than wrapping round. Coming back to the top
        // from the bottom of a list taller than its box looks exactly like a
        // list that scrolled, except nothing moved.
        picked = Math.max(0, Math.min(offers.length - 1,
          picked + (e.key === "ArrowDown" ? 1 : -1)));
        highlight();
      } else if (e.key === "Tab" || e.key === "Enter") {
        // Enter takes the highlighted one; with nothing highlighted it is a
        // plain Enter and the dialog can have it.
        if (picked >= 0) { e.preventDefault(); take(picked); }
      } else if (e.key === "Escape") {
        // The list only. A dialog closes itself on Escape and the browser is
        // the one doing it, so stopping the event travelling is not enough --
        // the default action has to go as well, or putting the list away took
        // the whole dialog with it. Escape again is then the dialog's, which is
        // how every other box here behaves.
        e.preventDefault();
        e.stopPropagation();
        offers = [];
        draw();
      }
    });

    return list;
  }

  /**
   * A box that completes from a list of ids.
   *
   * <p>{@code source} is asked each time rather than read once: what exists is
   * not fixed. The timers are whatever the board says right now, the players
   * are whoever is online, and a datapack can add an advancement while the
   * page is open.</p>
   *
   * @param words true when the box holds several comma-separated things, so
   *              only the one under the cursor is completed
   */
  function suggestFrom(input, source, words = false) {
    const at = () => {
      if (!words) return [0, input.value.length];
      const text = input.value;
      const from = text.lastIndexOf(",") + 1;
      const lead = text.slice(from).length - text.slice(from).trimStart().length;
      return [from + lead, text.length];
    };
    attachSuggest(input, async () => {
      const [from, to] = at();
      const all = await source();
      return {
        suggestions: matching(all, input.value.slice(from, to)).map(text => ({ text })),
        start: from,
        end: to,
      };
    });
    return input;
  }

  /**
   * A box for a command, with the server answering what comes next.
   *
   * <p>In game this is the command block's own field and the game completes
   * it. A browser has no dispatcher, so the same question goes over the wire
   * to the one that does. Nothing here knows a thing about Minecraft commands,
   * which is the point: the list is always whatever this server actually has,
   * datapacks and other mods included.</p>
   *
   * <p>The text itself is drawn twice — a layer underneath carrying the colour,
   * and the real box on top with its own text made invisible. It is the only
   * way a plain input can show half a line in red, and the red half is the half
   * brigadier could not read.</p>
   */
  function commandField(value = "") {
    const wrap = document.createElement("div");
    wrap.className = "cmd-field";

    const ghost = document.createElement("div");
    ghost.className = "cmd-ghost";
    ghost.setAttribute("aria-hidden", "true");

    const input = document.createElement("input");
    input.type = "text";
    input.value = value;
    input.placeholder = t("cmd.text");

    /** Repaints the layer underneath: what parsed, and what did not. */
    const paint = cursor => {
      const text = input.value;
      ghost.replaceChildren();
      const cut = Math.max(0, Math.min(cursor ?? text.length, text.length));
      const good = document.createElement("span");
      good.textContent = text.slice(0, cut);
      ghost.append(good);
      if (cut < text.length) {
        const bad = document.createElement("span");
        bad.className = "unparsed";
        bad.textContent = text.slice(cut);
        ghost.append(bad);
      }
      ghost.scrollLeft = input.scrollLeft;
    };

    attachSuggest(input, text => api("/api/suggest?q=" + encodeURIComponent(text)), paint);
    input.addEventListener("scroll", () => { ghost.scrollLeft = input.scrollLeft; });

    wrap.append(ghost, input);
    paint();
    return { wrap, input };
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

  /** What Apply would send from the settings sheets, if pressed now. */
  function settingsChanges() {
    return $$("#settings [data-key]")
      .map(input => [input.dataset.key, valueOf(input)])
      .filter(([key, value]) => String(state.config[key]) !== String(value));
  }

  /**
   * The two buttons, in the state the page is actually in.
   *
   * <p>Both were always pressable. Discard with nothing to discard does
   * nothing and says nothing, and Apply with nothing to apply looked exactly
   * like Apply with everything to apply. The in-game panel settles this by
   * greying the button out, and it settles it the same way here.</p>
   */
  function markSettings() {
    const bad = $$("#settings input").some(i => !parses(i));
    const changed = settingsChanges().length;
    $("#settings-apply").disabled = busy || bad || changed === 0;
    $("#settings-discard").disabled = busy || changed === 0;
  }

  /** True while something is on its way to the server. */
  let busy = false;

  $("#settings-apply").onclick = async () => {
    const changes = settingsChanges();
    if (!changes.length) return;
    busy = true;
    markSettings();
    let done = 0;
    for (const [key, value] of changes) {
      if (await act("config.set", { key, value })) done++;
    }
    busy = false;
    // Said once, with a number, rather than a line per setting: the server
    // answers routine changes with nothing at all, by design.
    if (done) toast(t("applied", done));
    markSettings();
  };
  $("#settings-discard").onclick = () => renderSettings();

  // --------------------------------------------------------------- modals

  /**
   * What every box in the open dialog held when it opened.
   *
   * <p>Read off the page rather than out of the timer, so "changed" means what
   * somebody looking at the dialog would call changed, and one place decides
   * it for both the button and for what gets sent.</p>
   */
  let baseline = {};

  const dirty = () => $$("#modal-body [data-key]")
    .some(i => baseline[i.dataset.key] !== undefined && i.value !== baseline[i.dataset.key]);

  /**
   * @param watch true for a dialog whose last button applies changes, which
   *              should be dead until there are changes to apply
   */
  function modal(title, build, actions, watch = false) {
    const dialog = $("#modal");
    $("#modal-title").textContent = title;
    const body = $("#modal-body");
    const place = keepPlace ? body.scrollTop : 0;
    body.replaceChildren();
    build(body);
    body.scrollTop = place;

    baseline = {};
    if (watch) {
      for (const input of $$("#modal-body [data-key]")) {
        baseline[input.dataset.key] = input.value;
      }
    }
    const menu = $("#modal-actions");
    menu.replaceChildren(...actions.map(([text, cls, fn]) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "btn " + cls;
      button.textContent = text;
      button.onclick = async () => {
        // Every button in the row, not just this one: a second press while the
        // first is still going sends the whole form twice.
        const all = [...menu.children];
        all.forEach(b => { b.dataset.was = String(b.disabled); b.disabled = true; });
        try {
          if (!fn || (await fn()) !== false) dialog.close();
        } finally {
          all.forEach(b => { b.disabled = b.dataset.was === "true"; });
        }
      };
      return button;
    }));

    if (watch) {
      // The last button is the one that applies; the others cancel or close,
      // and those are always available.
      const primary = menu.children[menu.children.length - 1];
      const mark = () => { primary.disabled = !dirty(); };
      body.addEventListener("input", mark);
      body.addEventListener("change", mark);
      mark();
    }
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
        new Option(t("label.players"), "players"));
      select.onchange = () => {
        audience = select.value;
        names.hidden = audience === "global";
      };
      body.append(scope);

      const names = field("players", "text", "");
      // The comma is a thing about what to type, so it goes in the box rather
      // than in the name of the box.
      $("input", names).placeholder = t("field.playerNames");
      suggestFrom($("input", names), () => (state.players || []).map(p => p.name), true);
      names.hidden = true;
      body.append(names);

      const modeField = field("mode", "bool", "true");
      const modeSelect = $("select", modeField);
      modeSelect.replaceChildren(new Option(t("shared"), "shared"), new Option(t("each"), "each"));
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

  /**
   * The name a copy is offered, the way the in-game panel offers it: the
   * original keeps its own and the copy takes the lowest free number, so
   * "test" and "test_2" leave "test_1" free.
   *
   * <p>Underscore and no brackets, and never past thirty-two characters: a
   * timer name is [A-Za-z0-9_.+-] and the server refuses anything else.</p>
   */
  function copyName(name) {
    const base = /^(.+?)_(\d+)$/.exec(name);
    const stem = base ? base[1] : name;
    const taken = new Set((state.timers || []).map(x => x.name));
    for (let copy = 1; copy < 1000; copy++) {
      const tail = "_" + copy;
      const candidate = stem.slice(0, 32 - tail.length) + tail;
      if (!taken.has(candidate)) return candidate;
    }
    return stem.slice(0, 30) + "_1";
  }

  function cloneDialog(timer) {
    modal(t("dialog.clone", timer.name), body => {
      body.append(field("dest", "text", copyName(timer.name)));
    }, [
      [t("cancel"), "", null],
      [t("accept"), "primary", () =>
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

  /**
   * A timer that does not exist yet, asked about the way an existing one is.
   *
   * <p>It used to ask four things and leave the rest for a second visit to the
   * editor, which is not how the in-game panel does it: there the creation
   * form draws every field a timer has. Two of them belong to creation alone
   * -- the name, which nothing can change afterwards, and the first command,
   * which saves that second visit.</p>
   *
   * <p>Made first and then adjusted, because everything but the four the
   * server needs up front is an operation on a timer, and there is not one
   * until it has been made.</p>
   */
  function newDialog() {
    modal(t("dialog.new"), body => {
      timerSheets(body, blankTimer(), true);
    }, [
      [t("cancel"), "", null],
      [t("save"), "primary", async () => {
        const get = key => $(`#modal-body [data-key='${key}']`);
        const num = key => parseInt(get(key).value, 10) || 0;

        const name = get("name").value.trim();
        if (!name) {
          get("name").classList.add("bad");
          get("name").focus();
          toast(t("badValue"), true);
          return false;
        }
        const made = await act("timer.create", {
          name,
          hours: num("hours"), minutes: num("minutes"), seconds: num("seconds"),
          countUp: get("countUp").value === "true"
        });
        if (made === false) return false;

        // Now that it exists, everything else the form asked about. The
        // baseline these are measured against is what a new timer copies from
        // the settings, so what goes is what was deliberately moved.
        await applyTimer(name);
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


  /**
   * One alternative written as a line: everything in it, joined by what its
   * group means. A lone condition is an alternative of one.
   */
  function describeAlternative(group) {
    if (group.node !== "group") return describeTrigger(group);
    const parts = (group.children || []).map(describeAlternative);
    if (group.mode === "at_least") {
      return t("trg.groupAtLeast", group.count ?? 1) + ": " + parts.join(", ");
    }
    return parts.join(" " + t(group.mode === "any" ? "trg.orGroup" : "trg.and") + " ");
  }

  /** The groups of a rule. A rule that is one plain condition counts as one. */
  function groupsOf(rule) {
    const root = rule.condition;
    if (!root) return [];
    if (root.node === "group" && root.mode === "any") return root.children || [];
    return [root];
  }

  /**
   * One alternative, drawn as what it is: a tree.
   *
   * <p>A group holds conditions and it can hold groups, so this draws itself
   * again for each one it finds. The old version walked one level and skipped
   * anything deeper with a bare {@code continue}: a nested group was not shown
   * as wrong, or as anything -- it simply was not on the page, while the
   * conditions inside it went on deciding when the timer ran.</p>
   *
   * <p>Nesting and indentation, not drawn lines. The game paints its tree
   * because it has nothing else to put a thing inside a thing with; HTML does,
   * and a rule down the left says where each level begins. Same structure,
   * deliberately not the same picture.</p>
   */
  function groupBlock(timer, rule, group, depth = 0) {
    const box = document.createElement("div");
    box.className = "trg-group" + (depth ? " nested" : "");

    const head = document.createElement("div");
    head.className = "trg-group-head";
    head.textContent = group.node !== "group" ? ""
      : group.mode === "any" ? t("trg.groupAny")
      : group.mode === "at_least" ? t("trg.groupAtLeast", group.count ?? 1)
      : t("trg.groupAll");
    if (head.textContent) box.append(head);

    const conditions = group.node === "group" ? (group.children || []) : [group];
    for (const condition of conditions) {
      if (condition.node === "group") {
        box.append(groupBlock(timer, rule, condition, depth + 1));
        continue;
      }
      const row = document.createElement("div");
      row.className = "cmd-row";
      const text = document.createElement("span");
      text.className = "cmd-text";
      text.textContent = describeTrigger(condition);
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "danger small";
      remove.textContent = "×";
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
      const foot = document.createElement("div");
      foot.className = "trg-group-foot";

      const add = document.createElement("button");
      add.type = "button";
      add.className = "btn small";
      add.textContent = t("add");
      add.onclick = () => openComposer(box, add, timer, rule.action, group.id);

      const drop = document.createElement("button");
      drop.type = "button";
      drop.className = "danger small";
      // What it takes away depends on where it is: the whole alternative at
      // the top, and only this bracket of it further in.
      drop.textContent = t(depth ? "trg.dropGroup" : "trg.dropBranch");
      drop.onclick = () => act("timer.removeCondition",
        { name: timer.name, conditionId: group.id })
        .then(ok => ok && reopen(timer.name));

      foot.append(add, drop);
      box.append(foot);
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
    what.className = "trg-when " + (action === "start" ? "starts" : "ends");
    // The whole phrase, which is what the builder line says in game. The short
    // form belongs to the run summary, where it labels a row rather than
    // opening a sentence.
    what.textContent = t(action === "start" ? "trg.startsWhen" : "trg.endsWhen");
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
    // Names or a selector, depending on what has been chosen above it, so the
    // list is asked for at the moment it is opened rather than fixed here.
    suggestFrom(subject,
      () => scope.value === "selector"
        ? SELECTOR_WORDS
        : (state.players || []).map(p => p.name),
      true);

    const kind = document.createElement("select");
    for (const name of TRIGGER_KINDS) kind.append(new Option(t("trg." + name), name));

    const value = document.createElement("input");
    value.type = "text";
    // An advancement or a dimension: both arrive with every snapshot and the
    // panel had never once read them. The other kinds are ids no list here can
    // know -- an objective, a quest, an expression -- and they get none.
    suggestFrom(value, () => {
      if (kind.value === "advancement") return state.advancements || [];
      if (kind.value === "dimension_change") return state.dimensions || [];
      return [];
    });

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
    add.textContent = t("add");

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

  /**
   * Reopens the editor on fresh data, which is how every list here refreshes.
   *
   * <p>Built again from the top, so without this the scroll went back to the
   * top with it. The commands sheet is the last one in the dialog: adding a
   * second command meant scrolling the whole editor a second time, and a third
   * meant a third.</p>
   */
  let keepPlace = false;

  function reopen(name) {
    if (!state.timers.find(x => x.name === name)) return;
    keepPlace = true;
    try {
      drawnEditor = "";
      renderEditor();
    } finally {
      keepPlace = false;
    }
  }

  /**
   * Which server setting a timer's display value is copied from when it is
   * made. What "unchanged" means for a timer that does not exist yet.
   */
  const COPIED_FROM = {
    preset: "positionPreset", x: "timerX", y: "timerY", scale: "timerScale",
    colorHigh: "colorHigh", colorMid: "colorMid", colorLow: "colorLow",
    thresholdMid: "thresholdMid", thresholdLow: "thresholdLow",
    soundId: "timerSoundId", soundVolume: "timerSoundVolume",
    soundPitch: "timerSoundPitch",
  };

  /** A timer that does not exist yet, as it would be if it did. */
  function blankTimer() {
    const display = {};
    for (const [key, setting] of Object.entries(COPIED_FROM)) {
      display[key] = state.config[setting];
    }
    return {
      name: "", targetTicks: 1200, silent: false, display, titles: {},
      repeat: false, repeatCount: -1, repeatCooldownTicks: 0,
      nextTimer: "", sequenceCooldownTicks: 0,
    };
  }

  /**
   * The groups a timer's values live in.
   *
   * <p>Named separately so a page with tabs can deal them out, and so the
   * creation dialog can take the ones that make sense before a timer exists.
   * Both surfaces build the same fields from the same place: a setting that
   * moves has one home, not two.</p>
   */
  const SHEETS = {
    identity(body, timer, creating) {
      sheetInto(body, "identity", s => {
        if (creating) s.append(field("name", "text", ""));
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
        row.append(lab, cells, hint("length"));
        s.append(row);
        if (creating) {
          // Only while creating. Nothing can turn a timer around afterwards,
          // so on an existing one this is a control that does nothing.
          const dir = field("countUp", "bool", "false");
          $("select", dir).replaceChildren(
            new Option(t("countdown"), "false"), new Option(t("countup"), "true"));
          s.append(dir);
        }
        const silent = field("silent", "bool", String(!!timer.silent));
        $("select", silent).replaceChildren(new Option(t("on"), "true"), new Option(t("off"), "false"));
        $("select", silent).value = String(!!timer.silent);
        s.append(silent);
      });
    },

    display(body, timer) {
      const display = timer.display || {};
      for (const [name, keys] of DISPLAY_KEYS) {
        sheetInto(body, name, s => {
          for (const [key, kind] of keys) s.append(field("d:" + key, kind, display[key]));
        });
      }
    },

    titles(body, timer) {
      sheetInto(body, "titles", s => {
        const titles = timer.titles || {};
        for (const slot of ["above", "below", "left", "right"]) {
          const f = field("t:" + slot, "text", titles[slot] || "");
          // A title that is empty is a title that is not there, which is a
          // perfectly good answer.
          $("input", f).dataset.optional = "1";
          s.append(f);
        }
      });
    },

    flow(body, timer) {
      sheetInto(body, "repeat", s => {
        const repeat = field("repeat", "bool", String(!!timer.repeat));
        $("select", repeat).replaceChildren(new Option(t("on"), "true"), new Option(t("off"), "false"));
        $("select", repeat).value = String(!!timer.repeat);
        s.append(repeat);
        s.append(field("repeatCount", "int", timer.repeatCount ?? -1));
        s.append(field("repeatCooldown", "int", Math.floor((timer.repeatCooldownTicks || 0) / 20)));
      });
      sheetInto(body, "sequence", s => {
        const next = field("nextTimer", "text", timer.nextTimer || "");
        $("input", next).dataset.optional = "1";
        s.append(next);
        s.append(field("sequenceCooldown", "int", Math.floor((timer.sequenceCooldownTicks || 0) / 20)));
      });
    },

    /** The one command you always know at creation, saving a second visit. */
    firstCommand(body) {
      sheetInto(body, "commands", s => {
        const row = document.createElement("div");
        // A command is a line of text, not a value: it gets the width.
        row.className = "field wide";
        const lab = document.createElement("label");
        lab.textContent = label("finishCommand");
        const { wrap, input } = commandField();
        input.dataset.key = "finishCommand";
        input.dataset.optional = "1";
        row.append(lab, wrap, hint("finishCommand"));
        s.append(row);
      });
    },

    /**
     * What starts a timer and what ends it.
     *
     * <p>Two headings, always both, because everything a timer can be told is
     * one or the other. Under a heading sit alternatives, and any one of them
     * holding is enough; inside an alternative everything has to hold at once.
     * There is no third level: a rule and an alternative were both an "or", so
     * the page used to show one idea twice.</p>
     */
    triggers(body, timer) {
      closeComposer();
      for (const action of ["start", "finish"]) {
        const section = document.createElement("div");
        section.className = "trg-section";

        const head = document.createElement("div");
        head.className = "trg-section-head";
        const what = document.createElement("span");
        what.className = "trg-when " + (action === "start" ? "starts" : "ends");
        what.textContent = t(action === "start" ? "trg.startsWhen" : "trg.endsWhen");

        const add = document.createElement("button");
        add.type = "button";
        add.className = "btn small";
        add.textContent = t("add");
        add.onclick = () => openComposer(section, null, timer, action, null);
        head.append(what, add);
        section.append(head);

        const list = document.createElement("div");
        list.className = "trg-list";
        let branches = 0;
        for (const rule of (timer.rules || [])) {
          if (rule.action !== action) continue;
          for (const group of groupsOf(rule)) {
            if (branches > 0) {
              const or = document.createElement("div");
              or.className = "trg-or";
              or.textContent = t("trg.orGroup");
              list.append(or);
            }
            branches++;
            list.append(groupBlock(timer, rule, group));
          }
        }
        if (!branches) {
          const p = document.createElement("p");
          p.className = "muted";
          p.textContent = t(action === "start" ? "trg.noStart" : "trg.noFinish");
          list.append(p);
        }
        section.append(list);
        body.append(section);
      }
    },

    /**
     * The commands, by the moment they run at.
     *
     * <p>The moment is said once and its commands hang under it, which is how
     * the game draws the same list. Repeated beside every row, two commands
     * running at the same instant read as two unrelated facts.</p>
     */
    commands(body, timer) {
      const list = document.createElement("div");
      list.className = "cmd-list";
      const entries = timer.commandList || [];
      if (!entries.length) {
        const p = document.createElement("p");
        p.className = "muted";
        p.textContent = t("cmd.none");
        list.append(p);
      }
      let moment;
      let under = null;
      entries.forEach((entry, index) => {
        // A new heading when the moment changes, exactly as commandLines
        // decides it in game: the end is its own moment, once.
        if (under === null || entry.at !== moment) {
          moment = entry.at;
          const head = document.createElement("div");
          head.className = "cmd-moment";
          const when = document.createElement("span");
          when.className = "cmd-at" + (entry.at === undefined ? " end" : "");
          when.textContent = entry.at === undefined ? t("cmd.end") : clock(entry.at * 20);
          head.append(when);
          list.append(head);
          under = document.createElement("div");
          under.className = "cmd-under";
          list.append(under);
        }
        const row = document.createElement("div");
        row.className = "cmd-row";
        const text = document.createElement("span");
        text.className = "cmd-text";
        text.textContent = entry.delay > 0
          ? entry.command + "   " + t("cmd.waits", entry.delay)
          : entry.command;
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "danger small";
        remove.textContent = "\u00d7";
        remove.onclick = async () => {
          // Zero-based, exactly as the server counts them.
          if (await act("timer.removeCommand", { name: timer.name, index })) {
            reopen(timer.name);
          }
        };
        row.append(text, remove);
        under.append(row);
      });
      body.append(list);

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
      const { wrap: commandBox, input: command } = commandField();
      command.dataset.key = "cc";

      // What waits after this one before the next in the same batch. Beside
      // the command, because that is whose pause it is.
      const wait = document.createElement("input");
      wait.type = "number";
      wait.min = "0";
      wait.value = "0";
      wait.className = "cmd-wait";
      wait.title = t("cmd.wait");
      const add = document.createElement("button");
      add.type = "button";
      add.className = "primary small";
      add.textContent = t("add");
      add.onclick = async () => {
        if (!command.value.trim()) return;
        const n = key => parseInt($(`[data-key='${key}']`, formHost()).value, 10) || 0;
        const seconds = n("ch") * 3600 + n("cm") * 60 + n("cs");
        const args = { name: timer.name, command: command.value.trim() };
        if (seconds > 0) args.atSeconds = seconds;
        args.delayTicks = parseInt(wait.value, 10) || 0;
        if (await act("timer.addCommand", args)) {
          reopen(timer.name);
        }
      };
      adder.append(cells, commandBox, wait, add);
      body.append(adder);
    },
  };

  /** A titled sheet, built into whatever is hosting the form. */
  function sheetInto(body, name, build) {
    const sheeted = sheet(t("group." + name));
    build(sheeted.body);
    body.append(sheeted.section);
  }

  /** Everything a timer that does not exist yet can be asked. */
  function timerSheets(body, timer, creating) {
    SHEETS.identity(body, timer, creating);
    SHEETS.display(body, timer);
    SHEETS.titles(body, timer);
    SHEETS.flow(body, timer);
    if (creating) SHEETS.firstCommand(body);
  }

  /**
   * Where the form being edited lives: the page, or the dialog over it.
   *
   * <p>One question with one answer, rather than every caller reaching for
   * "#modal-body" and being wrong the moment the editor stopped being one.</p>
   */
  const formHost = () => (editing ? $("#editor-body") : $("#modal-body"));

  /**
   * What Apply sends: the boxes that differ from the ones the form opened
   * with, grouped the way the operations are.
   *
   * @return how many operations were accepted
   */
  async function applyTimer(name) {
    const get = key => $(`[data-key='${key}']`, formHost());
    const num = key => parseInt(get(key).value, 10) || 0;
    const moved = key => get(key) && get(key).value !== baseline[key];
    let done = 0;

    if (["hours", "minutes", "seconds"].some(moved)) {
      if (await act("timer.setTime", { name, hours: num("hours"),
        minutes: num("minutes"), seconds: num("seconds") })) done++;
    }
    if (moved("silent")) {
      if (await act("timer.setSilent",
        { name, silent: get("silent").value === "true" })) done++;
    }
    for (const [, keys] of DISPLAY_KEYS) {
      for (const [key] of keys) {
        if (!moved("d:" + key)) continue;
        if (await act("timer.setDisplay",
          { name, key, value: valueOf(get("d:" + key)) })) done++;
      }
    }
    for (const slot of ["above", "below", "left", "right"]) {
      if (!moved("t:" + slot)) continue;
      if (await act("timer.setTitle",
        { name, slot, text: get("t:" + slot).value })) done++;
    }
    if (["repeat", "repeatCount", "repeatCooldown"].some(moved)) {
      if (await act("timer.setRepeat", {
        name, repeat: get("repeat").value === "true",
        count: num("repeatCount"), cooldownSeconds: num("repeatCooldown")
      })) done++;
    }
    if (["nextTimer", "sequenceCooldown"].some(moved)) {
      if (await act("timer.setSequence", {
        name, next: get("nextTimer").value.trim(),
        cooldownSeconds: num("sequenceCooldown")
      })) done++;
    }
    return done;
  }

  /** Every box Apply would send, which is not every box on the page. */
  const APPLIES = () => [
    "hours", "minutes", "seconds", "silent",
    "repeat", "repeatCount", "repeatCooldown", "nextTimer", "sequenceCooldown",
    ...DISPLAY_KEYS.flatMap(([, keys]) => keys.map(([key]) => "d:" + key)),
    ...["above", "below", "left", "right"].map(slot => "t:" + slot)
  ];

  /**
   * Refuses, loudly and in the right place, rather than letting the server
   * answer "Unknown preset" to somebody who cannot tell which box it meant.
   */
  function badFields() {
    const get = key => $(`[data-key='${key}']`, formHost());
    const bad = APPLIES().map(get).filter(i =>
      i && (!parses(i) || (i.value.trim() === "" && !i.dataset.optional)));
    if (!bad.length) return false;
    bad.forEach(i => i.classList.add("bad"));
    bad[0].focus();
    toast(t("badValue"), true);
    return true;
  }

  // ------------------------------------------------------------ the editor

  /** The timer being edited, which is what turns Timers into the editor. */
  let editing = null;
  let editorTab = "timer";

  /** The tabs, and which groups each one is made of. */
  const EDITOR_TABS = [
    ["timer", (body, timer) => {
      SHEETS.identity(body, timer, false);
      SHEETS.titles(body, timer);
      SHEETS.flow(body, timer);
    }],
    ["display", (body, timer) => SHEETS.display(body, timer)],
    ["triggers", (body, timer) => SHEETS.triggers(body, timer)],
    ["commands", (body, timer) => SHEETS.commands(body, timer)],
  ];

  function openEditor(name) {
    editing = name;
    editorTab = "timer";
    render();
  }

  function closeEditor() {
    editing = null;
    baseline = {};
    render();
  }

  /**
   * The editor, in the space the list was using.
   *
   * <p>Every tab is built, and the ones not being looked at are hidden rather
   * than left unbuilt. Building on demand would throw away whatever had been
   * typed on the tab being left, which is not what changing tabs means -- and
   * Apply has to be able to see the whole form at once, since a single press
   * sends what changed anywhere in it.</p>
   */
  function renderEditor() {
    const timer = (state.timers || []).find(x => x.name === editing);
    if (!timer) {
      // Deleted from under us, or renamed by somebody else.
      closeEditor();
      return;
    }
    $("#editor-name").textContent = timer.name;
    $("#editor-lead").textContent =
      (timer.countUp ? "\u2191 " : "\u2193 ") + clock(timer.targetTicks);

    const tabs = $("#editor-tabs");
    tabs.replaceChildren(...EDITOR_TABS.map(([name]) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "subtab";
      button.textContent = t("editor." + (name === "display" ? "look" : name));
      button.setAttribute("aria-selected", String(name === editorTab));
      button.onclick = () => {
        editorTab = name;
        // Only the panels change hands; nothing is rebuilt, so nothing typed
        // on the way out is lost.
        [...$("#editor-body").children].forEach(panel => {
          panel.hidden = panel.dataset.tab !== editorTab;
        });
        [...tabs.children].forEach((b, i) =>
          b.setAttribute("aria-selected", String(EDITOR_TABS[i][0] === editorTab)));
      };
      return button;
    }));

    const body = $("#editor-body");
    const place = keepPlace ? body.scrollTop : 0;
    body.replaceChildren(...EDITOR_TABS.map(([name, build]) => {
      const panel = document.createElement("div");
      panel.className = "editor-panel";
      panel.dataset.tab = name;
      panel.hidden = name !== editorTab;
      build(panel, timer);
      return panel;
    }));
    body.scrollTop = place;

    baseline = {};
    for (const input of $$("[data-key]", body)) baseline[input.dataset.key] = input.value;
    markEditor();
  }

  /** Apply and Discard, in the state the page is actually in. */
  function markEditor() {
    const changed = $$("[data-key]", $("#editor-body"))
      .some(i => baseline[i.dataset.key] !== undefined && i.value !== baseline[i.dataset.key]);
    $("#editor-apply").disabled = busy || !changed;
    $("#editor-discard").disabled = busy || !changed;
  }

  $("#editor-body").addEventListener("input", markEditor);
  $("#editor-body").addEventListener("change", markEditor);
  $("#editor-back").onclick = closeEditor;
  $("#editor-discard").onclick = () => renderEditor();
  $("#editor-apply").onclick = async () => {
    if (badFields()) return;
    busy = true;
    markEditor();
    const done = await applyTimer(editing);
    busy = false;
    toast(done ? t("applied", done) : t("nothingToApply"), false);
    renderEditor();
  };

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
        [label("countUp"), t(run.countUp ? "countup" : "countdown"), true],
        [label("mode"), t(run.mode === "EACH" ? "each" : "shared")],
        [label("audience"), audienceOf(run)],
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

      // Every alternative of every rule. The filter here used to drop any rule
      // whose condition was a group, and after conditions became combinable a
      // group is what a rule normally is -- so a timer that starts on two
      // things at once reported that nothing starts it.
      const shown = (timer.rules || []).flatMap(rule =>
        groupsOf(rule).map(group => [
          t(rule.action === "start" ? "startIt" : "finish"),
          describeAlternative(group), true]));
      facts(t("group.triggers"), shown.length ? shown : [[t("trg.none"), "", true]]);

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
          // The same line the editor draws. Left out here, a command with a
          // pause read as one without.
          text.textContent = entry.delay > 0
            ? entry.command + "   " + t("cmd.waits", entry.delay)
            : entry.command;
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
    editing = null;
    // The map and the page have to be forgotten together. Emptying the map
    // alone left the old cards in the document and built a second set beside
    // them, so every visit to the tab doubled the board -- and pressing Delete
    // on one of the ghosts asked the server about a timer it had already
    // removed.
    forget();
    render();
  });

  function forget() {
    drawnRuns = drawnTimers = drawnSettings = drawnEditor = "";
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
