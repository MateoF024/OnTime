package com.mateof24.gui;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
 * <p>Full screen, like a vanilla options screen: one header band carrying the
 * title and the global actions, a tab row, and the content. The world stays
 * visible through the usual dimming, which is what says at a glance that this
 * is a tool over the game rather than a menu that replaced it.</p>
 *
 * <p>The content is master and detail — the list says which executions exist,
 * the pane beside it says everything about the one you picked, and the actions
 * apply to that one. Below a certain width the two columns stack, because two
 * columns in three hundred pixels is worse than one.</p>
 *
 * <h2>Text</h2>
 *
 * <p>Every string is white with a shadow. The panel floats over the world, and
 * unshadowed grey vanishes against a bright sky. Colour is spent on one thing
 * only: what state an execution is in.</p>
 *
 * <h2>Drawing order</h2>
 *
 * <p>Split in two on purpose. {@link #drawBands} runs <em>before</em> the
 * screen renders, so its filled band lands under vanilla's dimming and under
 * every widget. {@link #drawContent} runs after, and draws only text and small
 * colour marks — nothing large or opaque. Get that backwards and the panel
 * paints over its own buttons, which is what it did the first time.</p>
 *
 * <h2>Why the rows are buttons</h2>
 *
 * <p>{@code mouseClicked} cannot be overridden here: its signature changed in
 * 1.21.10, and the {@code v1.21.6} family compiles Fabric against 1.21.10 and
 * NeoForge against 1.21.6, so one shared file cannot satisfy both.
 * {@code Button.builder} is identical on every version in range, so clicks
 * arrive through it and the drift never reaches this file.</p>
 */
public final class AdminPanel {

    /** Sits under vanilla's dimming, so it only needs to be a hint. */
    private static final int COLOR_BAND = 0x50000000;
    /** Drawn over the world after the widgets, so it has to carry on its own. */
    private static final int COLOR_RULE = 0x70FFFFFF;
    private static final int COLOR_SCRIM = 0xC0000000;
    private static final int COLOR_DIALOG = 0xF0141418;

    private static final int COLOR_TEXT = 0xFFFFFFFF;

    // The one thing colour is spent on.
    private static final int COLOR_RUNNING = 0xFF57C25F;
    private static final int COLOR_PAUSED = 0xFFE0A536;
    private static final int COLOR_COOLDOWN = 0xFF4E9FE3;
    private static final int COLOR_ERROR = 0xFFE06A6A;
    private static final int COLOR_OK = 0xFF57C25F;

    private static final int GUTTER = 12;
    private static final int HEADER_HEIGHT = 30;
    private static final int TAB_HEIGHT = 20;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;
    private static final int MARK_WIDTH = 3;
    private static final int LINE = 11;

    /** Below this the two columns stack instead of sitting side by side. */
    private static final int TWO_COLUMN_MIN_WIDTH = 460;

    private final PanelHost host;
    private final AdminModel model = new AdminModel();

    private int width, height;
    private int tabY, headerRowY, contentTop, contentBottom;
    private int dividerTop, detailTitleY, detailRuleY, detailBodyTop;
    private int listX, listWidth, listTop, listBottom;
    private int detailX, detailWidth, detailTop;
    private int colName, colAudience, colTimeRight;
    private boolean twoColumn;

    private int scroll = 0;
    private int visibleRows = 1;

    /**
     * The operation waiting to be confirmed, or null.
     *
     * <p>Only one thing needs it so far, but it is a field rather than a
     * boolean because the next destructive action will want the same door.</p>
     */
    private String confirmOp = null;

    private final SettingsForm settings = new SettingsForm();

    /** Row height of the settings form: a control plus air. */
    private static final int SETTING_HEIGHT = 22;
    private int settingsRows = 1;

    /** {@code {x, y, colour}} per visible row, for the state mark in the gutter. */
    private final List<int[]> rowMarks = new ArrayList<>();
    /** The rows currently on screen, parallel to {@link #rowMarks}. */
    private final List<AdminModel.RunRow> rowData = new ArrayList<>();

    public AdminPanel(PanelHost host) {
        this.host = host;
    }

    public AdminModel model() { return model; }

    public void refresh(JsonObject state) {
        model.apply(state);
    }

    /**
     * A snapshot landed while the panel is open.
     *
     * <p>The runs list is rebuilt, because its content is exactly what
     * changed. The settings form is not: rebuilding it once a second would
     * take the caret out of whichever field is being typed in. It reloads when
     * the tab is entered or the edits are discarded, which is when its values
     * can have moved without the operator doing it.</p>
     */
    public void onSnapshot(JsonObject state) {
        model.apply(state);
        if (model.tab() != AdminModel.Tab.SETTINGS) init();
    }

    // ==================================================================
    // Layout
    // ==================================================================

    public void init() {
        width = host.panelWidth();
        height = host.panelHeight();

        tabY = HEADER_HEIGHT + 6;
        // The column headers sit straight under the tabs. The action result
        // lives in the header band instead of on a line of its own here: a
        // reserved line is a permanent gap, and an unreserved one would shove
        // the list down under the cursor the moment a message appeared.
        headerRowY = tabY + TAB_HEIGHT + 6;
        contentTop = headerRowY + LINE + 2;
        contentBottom = height - GUTTER;
        // Just clear of the header band and a shade above the tab row, so the
        // two columns read as columns for the full height rather than only
        // where the content happens to be.
        dividerTop = tabY - 2;

        twoColumn = width >= TWO_COLUMN_MIN_WIDTH;
        if (twoColumn) {
            listX = GUTTER + MARK_WIDTH + 3;
            listWidth = (int) ((width - 3 * GUTTER) * 0.56f);
            detailX = listX + listWidth + GUTTER;
            detailWidth = width - GUTTER - detailX;
            listTop = contentTop;
            // One line at the foot for the "x-y of n" indicator, so turning
            // the list from unscrollable to scrollable never moves a row.
            listBottom = contentBottom - LINE;
            detailTop = contentTop;
            // Level with the tab row, and its rule on the tabs' bottom edge:
            // the two columns then start their content at the same height
            // instead of the right one hanging a row lower than the left.
            detailTitleY = tabY + (TAB_HEIGHT - 9) / 2;
            detailRuleY = tabY + TAB_HEIGHT - 1;
        } else {
            listX = GUTTER + MARK_WIDTH + 3;
            listWidth = width - listX - GUTTER;
            detailX = GUTTER;
            detailWidth = width - 2 * GUTTER;
            int split = contentTop + (contentBottom - contentTop) * 55 / 100;
            listTop = contentTop;
            listBottom = split - 6 - LINE;
            detailTop = split + 4;
            // Stacked, the detail has no tab row to line up with.
            detailTitleY = detailTop;
            detailRuleY = detailTop + LINE - 1;
        }

        // Under the detail column's own heading and rule.
        detailBodyTop = detailRuleY + 7;

        colName = listX + 6;
        colAudience = listX + Math.max(70, (int) (listWidth * 0.42f));
        colTimeRight = listX + listWidth - 6;

        visibleRows = Math.max(1, (listBottom - listTop + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
        clampScroll();

        host.clearWidgets();
        rowMarks.clear();
        rowData.clear();

        // A confirmation owns the screen while it is up: with nothing else
        // built there is nothing behind it to click by accident.
        if (confirmOp != null) {
            buildConfirm();
            return;
        }

        buildHeader();
        buildTabs();
        if (model.tab() == AdminModel.Tab.RUNS) {
            buildRunRows();
            buildRunActions();
        } else if (model.tab() == AdminModel.Tab.SETTINGS) {
            buildSettings();
        }
    }

    // ---- the settings form ----

    private int settingsTop() {
        return headerRowY;
    }

    private void buildSettings() {
        int top = settingsTop();
        settingsRows = Math.max(1, (contentBottom - top) / SETTING_HEIGHT);
        List<SettingsForm.Row> rows = SettingsForm.rows();
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - settingsRows), scroll));

        int controlWidth = Math.min(140, (width - 2 * GUTTER) / 2);
        int controlX = width - GUTTER - controlWidth;

        for (int i = 0; i < settingsRows && scroll + i < rows.size(); i++) {
            SettingsForm.Row row = rows.get(scroll + i);
            if (row.isHeader()) continue;
            int y = top + i * SETTING_HEIGHT;
            String tooltipKey = "ontime.config." + snake(row.key()) + ".tooltip";

            if (row.kind() == SettingsForm.Kind.BOOL || row.kind() == SettingsForm.Kind.PRESET) {
                // A button that shows its value and advances on click. That is
                // what CycleButton looks like, without CycleButton's drift:
                // 26.2 changed its builder and dropped withInitialValue.
                String value = settings.displayed(model, row);
                host.addWidget(Button.builder(cycleLabel(row, value), b -> {
                            settings.put(row.key(), settings.cycled(row, value));
                            init();
                        })
                        .bounds(controlX, y, controlWidth, 18)
                        .tooltip(Tooltip.create(Component.translatable(tooltipKey)))
                        .build());
            } else {
                EditBox box = new EditBox(host.font(), controlX, y, controlWidth, 18,
                        Component.translatable("ontime.config." + snake(row.key())));
                box.setMaxLength(64);
                box.setValue(settings.displayed(model, row));
                box.setResponder(text -> settings.put(row.key(), text));
                box.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
                host.addWidget(box);
            }
        }
    }

    private Component cycleLabel(SettingsForm.Row row, String value) {
        if (row.kind() == SettingsForm.Kind.BOOL) {
            return Component.translatable(Boolean.parseBoolean(value) ? "options.on" : "options.off");
        }
        return Component.literal(value);
    }

    /** {@code timerSoundId} to {@code timer_sound_id}, which is how the keys read. */
    private static String snake(String camel) {
        StringBuilder out = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) out.append('_').append(Character.toLowerCase(c));
            else out.append(c);
        }
        return out.toString();
    }

    // ---- the confirmation ----

    private static final int DIALOG_WIDTH = 280;
    private static final int DIALOG_HEIGHT = 88;

    private int dialogX() { return (width - DIALOG_WIDTH) / 2; }

    private int dialogY() { return (height - DIALOG_HEIGHT) / 2; }

    private void buildConfirm() {
        int buttonWidth = 96;
        int y = dialogY() + DIALOG_HEIGHT - 28;
        int gap = 8;
        int startX = dialogX() + (DIALOG_WIDTH - 2 * buttonWidth - gap) / 2;

        host.addWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
                    confirmOp = null;
                    init();
                })
                .bounds(startX, y, buttonWidth, 20)
                .build());

        host.addWidget(Button.builder(
                        Component.translatable("ontime.gui.confirm.accept").withStyle(ChatFormatting.RED),
                        b -> {
                            String op = confirmOp;
                            confirmOp = null;
                            init();
                            if (op != null) send(op, new JsonObject());
                        })
                .bounds(startX + buttonWidth + gap, y, buttonWidth, 20)
                .build());
    }

    /** Title on the left; everything that acts on the whole panel on the right. */
    private void buildHeader() {
        int doneWidth = 54;
        int doneX = width - GUTTER - doneWidth;
        host.addWidget(Button.builder(Component.translatable("gui.done"), b -> host.closePanel())
                .bounds(doneX, 5, doneWidth, 20)
                .build());

        if (model.tab() == AdminModel.Tab.SETTINGS) {
            int applyWidth = 62;
            int discardWidth = 62;
            Button apply = Button.builder(Component.translatable("ontime.gui.settings.apply"),
                            b -> applySettings())
                    .bounds(doneX - 6 - applyWidth, 5, applyWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable("ontime.gui.settings.apply.tip")))
                    .build();
            apply.active = settings.isDirty();
            host.addWidget(apply);

            Button discard = Button.builder(Component.translatable("ontime.gui.settings.discard"), b -> {
                        settings.discard();
                        model.clearMessage();
                        init();
                    })
                    .bounds(doneX - 12 - applyWidth - discardWidth, 5, discardWidth, 20)
                    .build();
            discard.active = settings.isDirty();
            host.addWidget(discard);
        }

        if (model.tab() == AdminModel.Tab.RUNS && !model.runs().isEmpty()) {
            int stopAllWidth = 76;
            // Red, and it asks first: it sits next to Done, it ends every
            // execution on the server, and a slip of the mouse between the two
            // should not be able to do that.
            host.addWidget(Button.builder(
                            Component.translatable("ontime.gui.runs.stop_all").withStyle(ChatFormatting.RED),
                            b -> {
                                confirmOp = "run.stopAll";
                                init();
                            })
                    .bounds(doneX - 6 - stopAllWidth, 5, stopAllWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable("ontime.gui.runs.stop_all.tip")))
                    .build());
        }
    }

    private void buildTabs() {
        AdminModel.Tab[] tabs = AdminModel.Tab.values();
        // Kept inside the list column so the divider between the columns can
        // run past them instead of through them.
        int available = listX + listWidth - GUTTER;
        int tabWidth = Math.min(110, (available - 2 * (tabs.length - 1)) / tabs.length);
        for (int i = 0; i < tabs.length; i++) {
            AdminModel.Tab tab = tabs[i];
            Button button = Button.builder(
                            Component.translatable("ontime.gui.tab." + key(tab)),
                            b -> {
                                model.setTab(tab);
                                model.clearMessage();
                                settings.discard();
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
     * One button per visible execution.
     *
     * <p>The label is empty and the columns are drawn on top, because a button
     * centres its text and three centred fields read as a blob rather than as a
     * table. The button is still what makes the row a row: it brings the frame,
     * the hover highlight, the click and the tooltip, and the columns line up
     * under a header that says what each one is.</p>
     */
    private void buildRunRows() {
        List<AdminModel.RunRow> rows = model.runs();
        for (int i = 0; i < visibleRows && scroll + i < rows.size(); i++) {
            AdminModel.RunRow row = rows.get(scroll + i);
            int y = listTop + i * (ROW_HEIGHT + ROW_GAP);

            Button button = Button.builder(Component.empty(), b -> {
                        model.selectRun(row.runId());
                        model.clearMessage();
                        init();
                    })
                    .bounds(listX, y, listWidth, ROW_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("ontime.gui.runs.row.tip",
                            Component.literal(row.timerName()))))
                    .build();
            button.active = !row.runId().equals(model.selectedRunId());
            host.addWidget(button);

            rowMarks.add(new int[]{listX - MARK_WIDTH - 3, y, stateColor(row)});
            rowData.add(row);
        }
    }

    private void buildRunActions() {
        AdminModel.RunRow selected = model.selectedRun();
        if (selected == null) return;

        int buttonWidth = Math.min(96, (detailWidth - 6) / 2);
        int gap = 6;
        // Centred in the column rather than shoved against its left margin.
        int startX = detailX + (detailWidth - (2 * buttonWidth + gap)) / 2;
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
                        .bounds(startX + column * (buttonWidth + gap),
                                y + rowIndex * 22, buttonWidth, 20)
                        .tooltip(Tooltip.create(Component.translatable("ontime.gui.action." + op + ".tip")))
                        .build();
                button.active = usable;
                host.addWidget(button);
            }
        }
    }

    /** Clear of the four text lines above them, with room to breathe. */
    private int actionsTop() {
        return Math.min(detailBodyTop + 62, contentBottom - 44);
    }

    private static String key(AdminModel.Tab tab) {
        return tab.name().toLowerCase(Locale.ROOT);
    }

    // ==================================================================
    // Drawing, before the widgets
    // ==================================================================

    /**
     * The header band. Drawn before the screen renders, so vanilla's dimming
     * and every widget land on top of it.
     */
    public void drawBands(Painter painter) {
        painter.rect(0, 0, width, HEADER_HEIGHT, COLOR_BAND);

        // The dialog's fills go here for the same reason as the band: they are
        // large and opaque, and after the widgets they would bury its buttons.
        if (confirmOp != null) {
            painter.rect(0, 0, width, height, COLOR_SCRIM);
            painter.rect(dialogX(), dialogY(), DIALOG_WIDTH, DIALOG_HEIGHT, COLOR_DIALOG);
        }
    }

    // ==================================================================
    // Drawing, after the widgets
    // ==================================================================

    /**
     * Text and colour marks. Nothing here is large or opaque, so it cannot bury
     * a widget — and the row columns are meant to sit on top of their button.
     */
    public void drawContent(Painter painter) {
        painter.text(Component.translatable("ontime.gui.title"), GUTTER, 11, COLOR_TEXT);

        if (confirmOp != null) {
            drawConfirm(painter);
            return;
        }

        // In the band, between the title and the global buttons: visible
        // without costing the content a line it would keep empty.
        if (model.message() != null) {
            centered(painter, Component.literal(model.message()), width / 2, 11,
                    model.messageIsError() ? COLOR_ERROR : COLOR_OK);
        }

        switch (model.tab()) {
            case RUNS -> drawRuns(painter);
            case TIMERS -> painter.text(Component.translatable("ontime.gui.timers.pending"),
                    GUTTER, contentTop, COLOR_TEXT);
            case SETTINGS -> drawSettings(painter);
        }
    }

    private void drawSettings(Painter painter) {
        int top = settingsTop();
        List<SettingsForm.Row> rows = SettingsForm.rows();

        for (int i = 0; i < settingsRows && scroll + i < rows.size(); i++) {
            SettingsForm.Row row = rows.get(scroll + i);
            int y = top + i * SETTING_HEIGHT;

            if (row.isHeader()) {
                painter.text(Component.translatable("ontime.gui.settings.group." + row.header()),
                        GUTTER, y + 6, COLOR_TEXT);
                painter.rect(GUTTER, y + 6 + LINE - 1, width - 2 * GUTTER, 1, COLOR_RULE);
                continue;
            }

            // An edited row is marked in the gutter the same way a running
            // execution is: colour, in the margin, no glyph.
            if (settings.isEdited(row.key())) {
                painter.rect(GUTTER - 6, y + 2, MARK_WIDTH, 14, COLOR_PAUSED);
            }
            painter.text(Component.translatable("ontime.config." + snake(row.key())),
                    GUTTER, y + 5, COLOR_TEXT);
        }

        if (rows.size() > settingsRows) {
            Component range = Component.translatable("ontime.gui.runs.scroll",
                    scroll + 1, Math.min(scroll + settingsRows, rows.size()), rows.size());
            painter.text(range, width - GUTTER - painter.textWidth(range),
                    contentBottom - LINE, COLOR_TEXT);
        }
    }

    /** The question and the frame; the box itself was filled before the widgets. */
    private void drawConfirm(Painter painter) {
        int x = dialogX(), y = dialogY();
        painter.outline(x, y, DIALOG_WIDTH, DIALOG_HEIGHT, COLOR_RULE);

        centered(painter, Component.translatable("ontime.gui.confirm.stop_all.title"),
                x + DIALOG_WIDTH / 2, y + 16, COLOR_TEXT);
        centered(painter, Component.translatable("ontime.gui.confirm.stop_all.body", model.runs().size()),
                x + DIALOG_WIDTH / 2, y + 34, COLOR_TEXT);
    }

    private void centered(Painter painter, Component text, int centerX, int y, int argb) {
        painter.text(text, centerX - painter.textWidth(text) / 2, y, argb);
    }

    private void drawRuns(Painter painter) {
        List<AdminModel.RunRow> rows = model.runs();

        if (rows.isEmpty()) {
            painter.text(Component.translatable("ontime.gui.runs.empty"), GUTTER, headerRowY, COLOR_TEXT);
            painter.text(Component.translatable("ontime.gui.runs.none_hint"),
                    GUTTER, headerRowY + LINE + 2, COLOR_TEXT);
            if (twoColumn) {
                painter.rect(detailX - GUTTER / 2, dividerTop, 1, contentBottom - dividerTop, COLOR_RULE);
            }
            return;
        }

        // The column header: says what each field is, once, instead of
        // repeating a label on every row.
        painter.text(Component.translatable("ontime.gui.runs.col.timer"), colName, headerRowY, COLOR_TEXT);
        painter.text(Component.translatable("ontime.gui.runs.col.audience"), colAudience, headerRowY, COLOR_TEXT);
        Component timeHeader = Component.translatable("ontime.gui.runs.col.time");
        painter.text(timeHeader, colTimeRight - painter.textWidth(timeHeader), headerRowY, COLOR_TEXT);
        painter.rect(listX, headerRowY + LINE - 1, listWidth, 1, COLOR_RULE);

        // State marks in the gutter, then the row contents over their buttons.
        for (int[] mark : rowMarks) {
            painter.rect(mark[0], mark[1], MARK_WIDTH, ROW_HEIGHT, mark[2]);
        }
        for (int i = 0; i < rowData.size(); i++) {
            AdminModel.RunRow row = rowData.get(i);
            int y = rowMarks.get(i)[1] + (ROW_HEIGHT - 8) / 2;
            painter.text(Component.literal(row.timerName()), colName, y, COLOR_TEXT);
            painter.text(audienceOf(row), colAudience, y, COLOR_TEXT);
            // The arrow is the same one /timer list uses, and it says which
            // way the clock is going without spending a column on the word.
            Component clock = clockWithArrow(row);
            painter.text(clock, colTimeRight - painter.textWidth(clock), y, COLOR_TEXT);
        }

        if (rows.size() > visibleRows) {
            Component range = Component.translatable("ontime.gui.runs.scroll",
                    scroll + 1, Math.min(scroll + visibleRows, rows.size()), rows.size());
            painter.text(range, listX + listWidth - painter.textWidth(range), listBottom + 2, COLOR_TEXT);
        }

        if (twoColumn) {
            painter.rect(detailX - GUTTER / 2, dividerTop, 1, contentBottom - dividerTop, COLOR_RULE);
        } else {
            painter.rect(GUTTER, detailTop - 8, width - 2 * GUTTER, 1, COLOR_RULE);
        }

        drawDetail(painter);
    }

    private void drawDetail(Painter painter) {
        int centerX = detailX + detailWidth / 2;
        centered(painter, Component.translatable("ontime.gui.detail.title"), centerX, detailTitleY, COLOR_TEXT);
        painter.rect(detailX, detailRuleY, detailWidth, 1, COLOR_RULE);

        AdminModel.RunRow row = model.selectedRun();
        if (row == null) {
            // Dead centre of the pane, not tucked into a corner: with nothing
            // selected it is the only thing the column has to say.
            Component hint = Component.translatable("ontime.gui.runs.pick_hint");
            centered(painter, hint, centerX,
                    (detailBodyTop + contentBottom) / 2 - painter.lineHeight() / 2, COLOR_TEXT);
            return;
        }

        int y = detailBodyTop;
        painter.text(Component.literal(row.timerName()), detailX, y, COLOR_TEXT);

        Component state = Component.translatable(stateKey(row));
        painter.text(state, detailX + detailWidth - painter.textWidth(state), y, stateColor(row));

        painter.text(Component.translatable("ontime.gui.runs.detail.audience",
                        audienceOf(row),
                        Component.translatable(row.each() ? "ontime.mode.each" : "ontime.mode.shared")),
                detailX, y + 14, COLOR_TEXT);

        painter.text(Component.translatable("ontime.gui.runs.detail.clock",
                        com.mateof24.render.ClientTimerState.formatTicks(row.currentTicks()),
                        com.mateof24.render.ClientTimerState.formatTicks(row.targetTicks()),
                        arrowOf(row),
                        Component.translatable(row.countUp()
                                ? "ontime.mode.countup" : "ontime.mode.countdown")),
                detailX, y + 14 + LINE, COLOR_TEXT);

        painter.text(Component.translatable("ontime.gui.runs.detail.id", row.runId().substring(0, 8)),
                detailX, y + 14 + 2 * LINE, COLOR_TEXT);

        drawAudienceList(painter, row);
    }

    /**
     * Who exactly is watching, one per line, under the actions.
     *
     * <p>Only for an audience there is something to list: a global execution
     * reaches whoever is connected, and naming them would be a snapshot that
     * stops being true the moment somebody joins. The column header on the
     * left keeps saying "Seen by" either way.</p>
     */
    private void drawAudienceList(Painter painter, AdminModel.RunRow row) {
        if (row.audienceGlobal() || row.audienceNames().isEmpty()) return;

        int top = actionsTop() + 2 * 22 + 8;
        if (top + 2 * LINE > contentBottom) return;

        painter.text(Component.translatable("ontime.gui.detail.audience_heading"), detailX, top, COLOR_TEXT);
        painter.rect(detailX, top + LINE - 1, detailWidth, 1, COLOR_RULE);

        List<String> names = row.audienceNames();
        int firstY = top + LINE + 3;
        int room = Math.max(1, (contentBottom - firstY) / LINE);
        int shown = names.size() <= room ? names.size() : Math.max(1, room - 1);

        for (int i = 0; i < shown; i++) {
            painter.text(Component.literal(names.get(i)), detailX + 4, firstY + i * LINE, COLOR_TEXT);
        }
        if (shown < names.size()) {
            painter.text(Component.translatable("ontime.gui.detail.more", names.size() - shown),
                    detailX + 4, firstY + shown * LINE, COLOR_TEXT);
        }
    }

    /**
     * The clock with the direction arrow {@code /timer list} uses, so the way
     * a countdown is going reads without spending a word on it.
     */
    private static Component clockWithArrow(AdminModel.RunRow row) {
        return Component.literal(arrowOf(row) + " "
                + com.mateof24.render.ClientTimerState.formatTicks(row.currentTicks()));
    }

    private static String arrowOf(AdminModel.RunRow row) {
        return row.countUp() ? "↑" : "↓";
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

    /** Uppercase in the panel: it reads as a status field rather than as prose. */
    private static String stateKey(AdminModel.RunRow row) {
        if ("REPEAT_COOLDOWN".equals(row.phase())) return "ontime.gui.state.repeat_cooldown";
        if ("SEQUENCE_COOLDOWN".equals(row.phase())) return "ontime.gui.state.sequence_cooldown";
        return row.running() ? "ontime.gui.state.running" : "ontime.gui.state.paused";
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
        int total;
        int shown;
        if (model.tab() == AdminModel.Tab.RUNS) {
            total = model.runs().size();
            shown = visibleRows;
        } else if (model.tab() == AdminModel.Tab.SETTINGS) {
            total = SettingsForm.rows().size();
            shown = settingsRows;
        } else {
            return false;
        }
        if (total <= shown) return false;

        int before = scroll;
        scroll = Math.max(0, Math.min(total - shown, scroll - (int) Math.signum(amount)));
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

    /**
     * Sends every pending edit, one {@code config.set} each.
     *
     * <p>One key at a time is deliberate on the server side: a panel that sent
     * the whole config back would silently undo whatever another admin changed
     * between the snapshot it drew and the button that was pressed.</p>
     */
    private void applySettings() {
        SettingsForm.Result result = settings.build();
        for (JsonObject args : result.requests()) {
            JsonObject request = new JsonObject();
            request.addProperty("op", "config.set");
            request.add("args", args);
            host.sendAction(request.toString());
        }
        if (!result.rejected().isEmpty()) {
            model.setMessage(String.join(", ", result.rejected()), true);
        }
        settings.discard();
        init();
    }

    /** Tells the server the panel is gone, so it stops pushing state. */
    public void onClosed() {
        JsonObject request = new JsonObject();
        request.addProperty("op", "panel.close");
        host.sendAction(request.toString());
    }
}
