package com.mateof24.gui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The administration panel: layout, drawing and actions, written once.
 *
 * <p>Only the drawing calls and the screen lifecycle differ between Minecraft
 * versions, and those are behind {@link Painter} and {@link PanelHost}. What is
 * here is the same code on every version the mod ships for.</p>
 *
 * <h2>Why it looks like this</h2>
 *
 * <p>Not a list of label-and-control rows generated from a config file — that
 * shape is what a settings library produces, and it answers "what can I
 * change" rather than "what is happening". The tabs are the three jobs an
 * operator comes here to do, and the first thing on screen is what is running
 * right now.</p>
 *
 * <p>Master and detail: the list says which executions exist, the pane below
 * says everything about the one you picked, and the actions apply to it. A
 * fixed handful of buttons however many executions there are.</p>
 *
 * <h2>Why the rows are buttons</h2>
 *
 * <p>Because {@code mouseClicked} cannot be overridden here. Its signature
 * changed in 1.21.10, and the {@code v1.21.6} family compiles Fabric against
 * 1.21.10 and NeoForge against 1.21.6 — one shared file cannot satisfy both.
 * {@code Button.builder} is identical on every version in range, so clicks
 * arrive through it and the drift never reaches this file. It also means rows
 * get vanilla's focus, hover and narration behaviour for free.</p>
 */
public final class AdminPanel {

    // Sober and deliberately few: hierarchy comes from weight and spacing
    // rather than from boxes and colours.
    private static final int COLOR_PANEL = 0xC8101012;
    private static final int COLOR_BORDER = 0xFF3A3A3E;
    private static final int COLOR_DIVIDER = 0xFF2A2A2E;
    private static final int COLOR_HEADER = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF909096;
    private static final int COLOR_RUNNING = 0xFF7FD07F;
    private static final int COLOR_PAUSED = 0xFFE0C060;
    private static final int COLOR_COOLDOWN = 0xFF7FB0E0;
    private static final int COLOR_ERROR = 0xFFE07070;
    private static final int COLOR_OK = 0xFF7FD07F;

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 232;
    private static final int MARGIN = 8;
    private static final int TAB_TOP = 22;
    private static final int TAB_HEIGHT = 18;
    private static final int ROW_HEIGHT = 20;
    private static final int DETAIL_LINES = 3;

    private final PanelHost host;
    private final AdminModel model = new AdminModel();

    private int left, top, width, height;
    private int listTop, listBottom, detailTop;
    private int scroll = 0;
    private int visibleRows = 1;

    private final List<Button> selectionActions = new ArrayList<>();

    public AdminPanel(PanelHost host) {
        this.host = host;
    }

    public AdminModel model() { return model; }

    /** Reloads from the server's snapshot. */
    public void refresh(JsonObject state) {
        model.apply(state);
    }

    // ==================================================================
    // Layout
    // ==================================================================

    public void init() {
        width = Math.min(PANEL_WIDTH, host.panelWidth() - 2 * MARGIN);
        height = Math.min(PANEL_HEIGHT, host.panelHeight() - 2 * MARGIN);
        left = (host.panelWidth() - width) / 2;
        top = (host.panelHeight() - height) / 2;

        listTop = top + TAB_TOP + TAB_HEIGHT + 14;
        detailTop = top + height - 26 - DETAIL_LINES * 10 - 24;
        listBottom = detailTop - 4;

        visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        clampScroll();

        host.clearWidgets();
        selectionActions.clear();
        buildTabs();

        if (model.tab() == AdminModel.Tab.RUNS) buildRunRows();

        buildFooter();
    }

    private void buildTabs() {
        AdminModel.Tab[] tabs = AdminModel.Tab.values();
        int tabWidth = (width - 2 * MARGIN - 2 * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            AdminModel.Tab tab = tabs[i];
            Button button = Button.builder(
                            Component.translatable("ontime.gui.tab." + key(tab)),
                            b -> {
                                model.setTab(tab);
                                model.clearMessage();
                                scroll = 0;
                                init();
                            })
                    .bounds(left + MARGIN + i * (tabWidth + 2), top + TAB_TOP, tabWidth, TAB_HEIGHT)
                    .build();
            // The current tab is the one you cannot press, which is how vanilla
            // marks a selected option too.
            button.active = model.tab() != tab;
            host.addWidget(button);
        }
    }

    /**
     * One button per visible execution.
     *
     * <p>Rebuilt whenever the list or the scroll changes, which is at most once
     * a second — the snapshot cadence — and never during a frame.</p>
     */
    private void buildRunRows() {
        List<AdminModel.RunRow> rows = model.runs();
        for (int i = 0; i < visibleRows && scroll + i < rows.size(); i++) {
            AdminModel.RunRow row = rows.get(scroll + i);
            Button button = Button.builder(rowLabel(row), b -> {
                        model.selectRun(row.runId());
                        model.clearMessage();
                        init();
                    })
                    .bounds(left + MARGIN, listTop + i * ROW_HEIGHT, width - 2 * MARGIN, ROW_HEIGHT - 2)
                    .build();
            // The selected row is the one that cannot be pressed again.
            button.active = !row.runId().equals(model.selectedRunId());
            host.addWidget(button);
        }
    }

    /** {@code race · shared · everyone · 04:32} — one line, widest field last. */
    private Component rowLabel(AdminModel.RunRow row) {
        return Component.translatable("ontime.gui.runs.row",
                Component.literal(row.timerName()),
                Component.translatable(row.each() ? "ontime.mode.each" : "ontime.mode.shared"),
                audienceOf(row),
                Component.literal(com.mateof24.render.ClientTimerState.formatTicks(row.currentTicks())));
    }

    private static Component audienceOf(AdminModel.RunRow row) {
        if (row.audienceGlobal()) return Component.translatable("ontime.audience.global");
        if (row.each() && row.ownerName() != null) return Component.literal(row.ownerName());
        if (row.audienceNames().size() > 3) {
            return Component.translatable("ontime.audience.count", row.audienceNames().size());
        }
        return Component.literal(String.join(", ", row.audienceNames()));
    }

    private void buildFooter() {
        int actionsY = top + height - 46;

        if (model.tab() == AdminModel.Tab.RUNS) {
            boolean hasSelection = model.selectedRun() != null;
            int buttonWidth = (width - 2 * MARGIN - 12) / 4;
            int x = left + MARGIN;
            for (String op : new String[]{"pause", "resume", "reset", "stop"}) {
                Button button = Button.builder(
                                Component.translatable("ontime.gui.action." + op),
                                b -> runAction("run." + op))
                        .bounds(x, actionsY, buttonWidth, 18)
                        .build();
                button.active = hasSelection;
                selectionActions.add(host.addWidget(button));
                x += buttonWidth + 4;
            }
        }

        int closeWidth = 56;
        host.addWidget(Button.builder(Component.translatable("gui.done"), b -> host.closePanel())
                .bounds(left + width - MARGIN - closeWidth, top + height - 24, closeWidth, 20)
                .build());

        if (model.tab() == AdminModel.Tab.RUNS && !model.runs().isEmpty()) {
            host.addWidget(Button.builder(Component.translatable("ontime.gui.runs.stop_all"),
                            b -> send("run.stopAll", new JsonObject()))
                    .bounds(left + MARGIN, top + height - 24, 78, 20)
                    .build());
        }
    }

    private static String key(AdminModel.Tab tab) {
        return tab.name().toLowerCase(Locale.ROOT);
    }

    // ==================================================================
    // Drawing — chrome and the parts that are not widgets
    // ==================================================================

    public void draw(Painter painter, int mouseX, int mouseY) {
        painter.rect(left, top, width, height, COLOR_PANEL);
        painter.outline(left, top, width, height, COLOR_BORDER);

        painter.text(Component.translatable("ontime.gui.title"), left + MARGIN, top + 7, COLOR_HEADER);
        painter.flatText(Component.translatable("ontime.gui.tab." + key(model.tab()) + ".desc"),
                left + MARGIN, top + TAB_TOP + TAB_HEIGHT + 3, COLOR_DIM);

        switch (model.tab()) {
            case RUNS -> drawRuns(painter);
            case TIMERS -> drawPending(painter, "ontime.gui.timers.pending");
            case SETTINGS -> drawPending(painter, "ontime.gui.settings.pending");
        }
    }

    private void drawRuns(Painter painter) {
        List<AdminModel.RunRow> rows = model.runs();

        if (rows.isEmpty()) {
            painter.centeredText(Component.translatable("ontime.gui.runs.empty"),
                    left + width / 2, listTop + 16, COLOR_DIM);
        } else if (rows.size() > visibleRows) {
            Component range = Component.translatable("ontime.gui.runs.scroll",
                    scroll + 1, Math.min(scroll + visibleRows, rows.size()), rows.size());
            painter.flatText(range, left + width - MARGIN - painter.textWidth(range),
                    top + TAB_TOP + TAB_HEIGHT + 3, COLOR_DIM);
        }

        painter.rect(left + MARGIN, detailTop - 2, width - 2 * MARGIN, 1, COLOR_DIVIDER);
        drawDetail(painter);
    }

    /** Everything about the selected execution, or a hint to pick one. */
    private void drawDetail(Painter painter) {
        AdminModel.RunRow row = model.selectedRun();
        int y = detailTop + 3;

        if (row == null) {
            painter.flatText(Component.translatable(model.runs().isEmpty()
                            ? "ontime.gui.runs.none_hint" : "ontime.gui.runs.pick_hint"),
                    left + MARGIN, y, COLOR_DIM);
            drawMessage(painter, y + 20);
            return;
        }

        painter.text(Component.literal(row.timerName()), left + MARGIN, y, COLOR_TEXT);
        Component state = Component.translatable(stateKey(row));
        painter.text(state, left + width - MARGIN - painter.textWidth(state), y, stateColor(row));

        painter.flatText(Component.translatable("ontime.gui.runs.detail.audience",
                        audienceOf(row),
                        Component.translatable(row.each() ? "ontime.mode.each" : "ontime.mode.shared")),
                left + MARGIN, y + 10, COLOR_DIM);

        painter.flatText(Component.translatable("ontime.gui.runs.detail.clock",
                        com.mateof24.render.ClientTimerState.formatTicks(row.currentTicks()),
                        com.mateof24.render.ClientTimerState.formatTicks(row.targetTicks()),
                        Component.translatable(row.countUp()
                                ? "ontime.mode.countup" : "ontime.mode.countdown"),
                        row.runId().substring(0, 8)),
                left + MARGIN, y + 20, COLOR_DIM);

        drawMessage(painter, y + 30);
    }

    /**
     * The outcome of the last action, shown here rather than in chat.
     *
     * <p>An admin acting in a screen should not have to close it to find out
     * whether the thing worked.</p>
     */
    private void drawMessage(Painter painter, int y) {
        if (model.message() == null) return;
        painter.flatText(Component.literal(model.message()), left + MARGIN, y,
                model.messageIsError() ? COLOR_ERROR : COLOR_OK);
    }

    private void drawPending(Painter painter, String key) {
        painter.centeredText(Component.translatable(key), left + width / 2, listTop + 24, COLOR_DIM);
    }

    private static String stateKey(AdminModel.RunRow row) {
        if ("REPEAT_COOLDOWN".equals(row.phase())) return "ontime.run.state.repeat_cooldown";
        if ("SEQUENCE_COOLDOWN".equals(row.phase())) return "ontime.run.state.sequence_cooldown";
        return row.running() ? "ontime.run.state.running" : "ontime.run.state.paused";
    }

    private static int stateColor(AdminModel.RunRow row) {
        if (row.inCooldown()) return COLOR_COOLDOWN;
        return row.running() ? COLOR_RUNNING : COLOR_PAUSED;
    }

    // ==================================================================
    // Input
    // ==================================================================

    /**
     * Mouse wheel. The one input signature that is identical on every version
     * in range, which is why it is the only one the screen overrides.
     */
    public boolean mouseScrolled(double amount) {
        if (model.tab() != AdminModel.Tab.RUNS) return false;
        if (model.runs().size() <= visibleRows) return false;
        int before = scroll;
        scroll -= (int) Math.signum(amount);
        clampScroll();
        if (scroll == before) return false;
        init();
        return true;
    }

    private void clampScroll() {
        int max = Math.max(0, model.runs().size() - visibleRows);
        scroll = Math.max(0, Math.min(max, scroll));
    }

    // ==================================================================
    // Actions
    // ==================================================================

    private void runAction(String op) {
        AdminModel.RunRow row = model.selectedRun();
        if (row == null) return;
        JsonObject args = new JsonObject();
        args.addProperty("runId", row.runId());
        send(op, args);
    }

    private void send(String op, JsonObject args) {
        JsonObject request = new JsonObject();
        request.addProperty("op", op);
        request.add("args", args);
        host.sendAction(request.toString());
    }

    /** Tells the server the panel is gone, so it stops pushing state. */
    public void onClosed() {
        JsonObject request = new JsonObject();
        request.addProperty("op", "panel.close");
        host.sendAction(request.toString());
    }
}
