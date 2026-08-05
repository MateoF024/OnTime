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
 * versions, and those are behind {@link Painter} and {@link PanelHost}.</p>
 *
 * <h2>Layout</h2>
 *
 * <p>Full screen, like every vanilla options screen: a header band, a tab row,
 * the content, and a footer band. The world stays visible behind the usual
 * dimming, which is what tells you at a glance that this is a tool over the
 * game rather than a menu that replaced it.</p>
 *
 * <p>The content is master and detail — the list says which executions exist,
 * the pane beside it says everything about the one you picked, and the actions
 * apply to that one. A fixed handful of buttons however many executions there
 * are. Below a certain width the two columns stack, because two columns in
 * three hundred pixels is worse than one.</p>
 *
 * <h2>Drawing order</h2>
 *
 * <p>Split in two on purpose. {@link #drawBands} runs <em>before</em> the
 * screen renders, so its filled bands land under vanilla's dimming and under
 * every widget. {@link #drawContent} runs after, and draws only text, hairlines
 * and small colour marks — nothing large or opaque. Get that backwards and the
 * panel paints over its own buttons, which is exactly what it did the first
 * time.</p>
 *
 * <h2>Why the rows are buttons</h2>
 *
 * <p>{@code mouseClicked} cannot be overridden here: its signature changed in
 * 1.21.10, and the {@code v1.21.6} family compiles Fabric against 1.21.10 and
 * NeoForge against 1.21.6, so one shared file cannot satisfy both.
 * {@code Button.builder} is identical on every version in range, so clicks
 * arrive through it and the drift never reaches this file — and rows get
 * vanilla's focus, hover and narration for free.</p>
 */
public final class AdminPanel {

    // Bands sit under vanilla's dimming, so they only need to be a hint.
    private static final int COLOR_BAND = 0x50000000;
    private static final int COLOR_RULE = 0x30FFFFFF;
    private static final int COLOR_RULE_SOFT = 0x18FFFFFF;

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFFE6E6E6;
    private static final int COLOR_DIM = 0xFF9A9AA2;

    // State is carried by colour, never by a glyph.
    private static final int COLOR_RUNNING = 0xFF57C25F;
    private static final int COLOR_PAUSED = 0xFFE0A536;
    private static final int COLOR_COOLDOWN = 0xFF4E9FE3;
    private static final int COLOR_ERROR = 0xFFE06A6A;
    private static final int COLOR_OK = 0xFF57C25F;

    private static final int GUTTER = 12;
    private static final int HEADER_HEIGHT = 30;
    private static final int TAB_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 30;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;
    private static final int MARK_WIDTH = 3;

    /** Below this the two columns stack instead of sitting side by side. */
    private static final int TWO_COLUMN_MIN_WIDTH = 460;

    private final PanelHost host;
    private final AdminModel model = new AdminModel();

    private int width, height;
    private int tabY, subtitleY, contentTop, contentBottom, footerY;
    private int listX, listWidth, listTop, listBottom;
    private int detailX, detailWidth, detailTop;
    private boolean twoColumn;

    private int scroll = 0;
    private int visibleRows = 1;

    private final List<int[]> rowMarks = new ArrayList<>();

    public AdminPanel(PanelHost host) {
        this.host = host;
    }

    public AdminModel model() { return model; }

    public void refresh(JsonObject state) {
        model.apply(state);
    }

    // ==================================================================
    // Layout
    // ==================================================================

    public void init() {
        width = host.panelWidth();
        height = host.panelHeight();

        tabY = HEADER_HEIGHT + 6;
        subtitleY = tabY + TAB_HEIGHT + 7;
        contentTop = subtitleY + 14;
        footerY = height - FOOTER_HEIGHT;
        contentBottom = footerY - 6;

        twoColumn = width >= TWO_COLUMN_MIN_WIDTH;
        if (twoColumn) {
            listX = GUTTER + MARK_WIDTH + 3;
            listWidth = (int) ((width - 3 * GUTTER) * 0.56f);
            detailX = listX + listWidth + GUTTER;
            detailWidth = width - GUTTER - detailX;
            listTop = contentTop;
            listBottom = contentBottom;
            detailTop = contentTop;
        } else {
            listX = GUTTER + MARK_WIDTH + 3;
            listWidth = width - listX - GUTTER;
            detailX = GUTTER;
            detailWidth = width - 2 * GUTTER;
            int split = contentTop + (contentBottom - contentTop) * 55 / 100;
            listTop = contentTop;
            listBottom = split - 6;
            detailTop = split + 4;
        }

        visibleRows = Math.max(1, (listBottom - listTop + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
        clampScroll();

        host.clearWidgets();
        rowMarks.clear();
        buildHeader();
        buildTabs();
        if (model.tab() == AdminModel.Tab.RUNS) {
            buildRunRows();
            buildRunActions();
        }
        buildFooter();
    }

    private void buildHeader() {
        int doneWidth = 54;
        host.addWidget(Button.builder(Component.translatable("gui.done"), b -> host.closePanel())
                .bounds(width - GUTTER - doneWidth, 5, doneWidth, 20)
                .build());
    }

    private void buildTabs() {
        AdminModel.Tab[] tabs = AdminModel.Tab.values();
        int tabWidth = Math.min(110, (width - 2 * GUTTER - 2 * (tabs.length - 1)) / tabs.length);
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
                    .bounds(GUTTER + i * (tabWidth + 2), tabY, tabWidth, TAB_HEIGHT)
                    .build();
            // The current tab is the one you cannot press — how vanilla marks a
            // chosen option too, and it needs no glyph.
            button.active = model.tab() != tab;
            host.addWidget(button);
        }
    }

    /**
     * One button per visible execution, plus the coordinates of its state mark.
     *
     * <p>Rebuilt when the list or the scroll changes, which is at most once a
     * second — the snapshot cadence — and never mid-frame.</p>
     */
    private void buildRunRows() {
        List<AdminModel.RunRow> rows = model.runs();
        for (int i = 0; i < visibleRows && scroll + i < rows.size(); i++) {
            AdminModel.RunRow row = rows.get(scroll + i);
            int y = listTop + i * (ROW_HEIGHT + ROW_GAP);

            Button button = Button.builder(rowLabel(row), b -> {
                        model.selectRun(row.runId());
                        model.clearMessage();
                        init();
                    })
                    .bounds(listX, y, listWidth, ROW_HEIGHT)
                    .build();
            button.active = !row.runId().equals(model.selectedRunId());
            host.addWidget(button);

            rowMarks.add(new int[]{listX - MARK_WIDTH - 3, y, stateColor(row)});
        }
    }

    /** {@code race   shared   everyone   04:32}, widest field in the middle. */
    private Component rowLabel(AdminModel.RunRow row) {
        return Component.translatable("ontime.gui.runs.row",
                Component.literal(row.timerName()),
                audienceOf(row),
                Component.literal(com.mateof24.render.ClientTimerState.formatTicks(row.currentTicks())));
    }

    private static Component audienceOf(AdminModel.RunRow row) {
        if (row.audienceGlobal()) return Component.translatable("ontime.audience.global");
        if (row.each() && row.ownerName() != null) return Component.literal(row.ownerName());
        if (row.audienceNames().isEmpty()) return Component.translatable("ontime.audience.nobody");
        if (row.audienceNames().size() > 2) {
            return Component.translatable("ontime.audience.count", row.audienceNames().size());
        }
        return Component.literal(String.join(", ", row.audienceNames()));
    }

    private void buildRunActions() {
        AdminModel.RunRow selected = model.selectedRun();
        if (selected == null) return;

        int buttonWidth = Math.min(96, (detailWidth - 6) / 2);
        int y = actionsTop();
        String[][] grid = {{"pause", "resume"}, {"reset", "stop"}};
        for (int rowIndex = 0; rowIndex < grid.length; rowIndex++) {
            for (int column = 0; column < grid[rowIndex].length; column++) {
                String op = grid[rowIndex][column];
                boolean usable = switch (op) {
                    // A run inside a cooldown has no clock to pause or resume;
                    // offering it would be offering a no-op.
                    case "pause" -> selected.running() && !selected.inCooldown();
                    case "resume" -> !selected.running() && !selected.inCooldown();
                    default -> true;
                };
                Button button = Button.builder(
                                Component.translatable("ontime.gui.action." + op),
                                b -> runAction("run." + op))
                        .bounds(detailX + column * (buttonWidth + 6),
                                y + rowIndex * 22, buttonWidth, 20)
                        .build();
                button.active = usable;
                host.addWidget(button);
            }
        }
    }

    private int actionsTop() {
        return Math.min(detailTop + 58, contentBottom - 44);
    }

    private void buildFooter() {
        if (model.tab() == AdminModel.Tab.RUNS && !model.runs().isEmpty()) {
            int stopAllWidth = 76;
            host.addWidget(Button.builder(Component.translatable("ontime.gui.runs.stop_all"),
                            b -> send("run.stopAll", new JsonObject()))
                    .bounds(width - GUTTER - stopAllWidth, footerY + 5, stopAllWidth, 20)
                    .build());
        }
    }

    private static String key(AdminModel.Tab tab) {
        return tab.name().toLowerCase(Locale.ROOT);
    }

    // ==================================================================
    // Drawing, before the widgets
    // ==================================================================

    /**
     * The structural bands. Drawn before the screen renders, so vanilla's
     * dimming and every widget land on top of them.
     */
    public void drawBands(Painter painter) {
        painter.rect(0, 0, width, HEADER_HEIGHT, COLOR_BAND);
        painter.rect(0, footerY, width, height - footerY, COLOR_BAND);
    }

    // ==================================================================
    // Drawing, after the widgets
    // ==================================================================

    /**
     * Text, hairlines and colour marks. Nothing here is large or opaque, so it
     * cannot bury a widget.
     */
    public void drawContent(Painter painter) {
        painter.text(Component.translatable("ontime.gui.title"), GUTTER, 11, COLOR_TITLE);
        painter.rect(0, HEADER_HEIGHT - 1, width, 1, COLOR_RULE);
        painter.rect(0, footerY, width, 1, COLOR_RULE);

        painter.flatText(Component.translatable("ontime.gui.tab." + key(model.tab()) + ".desc"),
                GUTTER, subtitleY, COLOR_DIM);

        switch (model.tab()) {
            case RUNS -> drawRuns(painter);
            case TIMERS -> drawPending(painter, "ontime.gui.timers.pending");
            case SETTINGS -> drawPending(painter, "ontime.gui.settings.pending");
        }

        drawMessage(painter);
    }

    private void drawRuns(Painter painter) {
        // The state mark: a colour, in the gutter, aligned to its row. No glyph
        // to learn and nothing to translate.
        for (int[] mark : rowMarks) {
            painter.rect(mark[0], mark[1], MARK_WIDTH, ROW_HEIGHT, mark[2]);
        }

        List<AdminModel.RunRow> rows = model.runs();
        if (rows.isEmpty()) {
            painter.flatText(Component.translatable("ontime.gui.runs.empty"),
                    listX, listTop + 4, COLOR_DIM);
            painter.flatText(Component.translatable("ontime.gui.runs.none_hint"),
                    listX, listTop + 16, COLOR_DIM);
        } else if (rows.size() > visibleRows) {
            Component range = Component.translatable("ontime.gui.runs.scroll",
                    scroll + 1, Math.min(scroll + visibleRows, rows.size()), rows.size());
            painter.flatText(range, listX + listWidth - painter.textWidth(range), subtitleY, COLOR_DIM);
        }

        if (twoColumn) {
            painter.rect(detailX - GUTTER / 2, contentTop, 1, contentBottom - contentTop, COLOR_RULE_SOFT);
        } else if (!rows.isEmpty()) {
            painter.rect(GUTTER, detailTop - 8, width - 2 * GUTTER, 1, COLOR_RULE_SOFT);
        }

        drawDetail(painter);
    }

    private void drawDetail(Painter painter) {
        AdminModel.RunRow row = model.selectedRun();
        if (row == null) {
            if (!model.runs().isEmpty()) {
                painter.flatText(Component.translatable("ontime.gui.runs.pick_hint"),
                        detailX, detailTop, COLOR_DIM);
            }
            return;
        }

        int y = detailTop;
        painter.text(Component.literal(row.timerName()), detailX, y, COLOR_TITLE);

        Component state = Component.translatable(stateKey(row));
        painter.text(state, detailX + detailWidth - painter.textWidth(state), y, stateColor(row));

        painter.flatText(Component.translatable("ontime.gui.runs.detail.audience",
                        audienceOf(row),
                        Component.translatable(row.each() ? "ontime.mode.each" : "ontime.mode.shared")),
                detailX, y + 14, COLOR_TEXT);

        painter.flatText(Component.translatable("ontime.gui.runs.detail.clock",
                        com.mateof24.render.ClientTimerState.formatTicks(row.currentTicks()),
                        com.mateof24.render.ClientTimerState.formatTicks(row.targetTicks()),
                        Component.translatable(row.countUp()
                                ? "ontime.mode.countup" : "ontime.mode.countdown")),
                detailX, y + 25, COLOR_DIM);

        painter.flatText(Component.translatable("ontime.gui.runs.detail.id", row.runId().substring(0, 8)),
                detailX, y + 36, COLOR_DIM);
    }

    /**
     * The outcome of the last action, in the footer.
     *
     * <p>An admin acting inside a screen should not have to close it to find
     * out whether the thing worked.</p>
     */
    private void drawMessage(Painter painter) {
        if (model.message() == null) return;
        painter.flatText(Component.literal(model.message()), GUTTER, footerY + 11,
                model.messageIsError() ? COLOR_ERROR : COLOR_OK);
    }

    private void drawPending(Painter painter, String key) {
        painter.flatText(Component.translatable(key), GUTTER, contentTop, COLOR_DIM);
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
     * Mouse wheel — the one input signature identical on every version in
     * range, which is why it is the only one the screen overrides.
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
        scroll = Math.max(0, Math.min(Math.max(0, model.runs().size() - visibleRows), scroll));
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
