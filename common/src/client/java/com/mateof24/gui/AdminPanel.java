package com.mateof24.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * <h2>Input</h2>
 *
 * <p>The rows are buttons, which is what brings them their frame, their hover
 * highlight and their tooltip for free. The completion list is not — it is
 * drawn, and takes its clicks and keys through the three methods at the foot of
 * this file, which the screen calls before vanilla dispatches anything. The
 * screen is the only file that has to know that those signatures changed shape
 * at 1.21.10; this one is handed plain numbers.</p>
 */
public final class AdminPanel {

    /** Sits under vanilla's dimming, so it only needs to be a hint. */
    private static final int COLOR_BAND = 0x50000000;
    /** Drawn over the world after the widgets, so it has to carry on its own. */
    private static final int COLOR_RULE = 0x70FFFFFF;
    private static final int COLOR_SCRIM = 0xC0000000;
    private static final int COLOR_DIALOG = 0xF0141418;

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    /** For the line under a dialog title: present, but not competing with it. */
    private static final int COLOR_MUTED_TEXT = 0xFFA0A0A8;

    // The one thing colour is spent on.
    private static final int COLOR_RUNNING = 0xFF57C25F;
    private static final int COLOR_PAUSED = 0xFFE0A536;
    private static final int COLOR_COOLDOWN = 0xFF4E9FE3;
    private static final int COLOR_ERROR = 0xFFE06A6A;
    private static final int COLOR_OK = 0xFF57C25F;

    /** What the defaults page shows in place of a timer's own titles. */
    private static final String[] SAMPLE_TITLES = {"Title", "Title", "Title", "Title"};

    private static final int GUTTER = 12;
    private static final int HEADER_HEIGHT = 30;
    private static final int TAB_HEIGHT = 20;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;
    private static final int MARK_WIDTH = 3;
    private static final int LINE = 11;
    /** Air between two headed sections of the detail column. */
    private static final int SECTION_GAP = 14;

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

    /** The detail column scrolls on its own in the timers tab. */
    private int detailScroll = 0;
    private int timerRowsShown = 1;
    private int nameWidth = 60, actionWidth = 40;
    private EditBox searchBox;

    /** Height the search box takes off the top of the timers list. */
    private static final int SEARCH_HEIGHT = 22;

    /** {@code {x, y, colour}} per visible definition. */
    private final List<int[]> timerMarks = new ArrayList<>();
    private final List<AdminModel.TimerRow> timerData = new ArrayList<>();

    /**
     * The operation waiting to be confirmed, or null.
     *
     * <p>Only one thing needs it so far, but it is a field rather than a
     * boolean because the next destructive action will want the same door.</p>
     */
    private String confirmOp = null;

    /**
     * The one confirmation that is not a server operation.
     *
     * <p>{@link #confirmOp} otherwise holds the op to send once confirmed;
     * this value means "ask before closing" and is handled entirely here.</p>
     */
    private static final String CONFIRM_EXIT = "$exit";

    private final SettingsForm settings = new SettingsForm();

    /** Keeps the panel's clocks reading the same as the ones on screen. */
    private final RunClock clock = new RunClock();

    /** Row height of the settings form: a control plus air. */
    private static final int SETTING_HEIGHT = 22;
    private int settingsRows = 1;

    /**
     * Apply and Discard, kept so their enabled state can follow the form.
     *
     * <p>Dirtiness changes on a keystroke, and a keystroke does not rebuild the
     * layout — deliberately, or the caret would jump out of the field. So these
     * two are refreshed while drawing instead. Without that they stayed however
     * they were at the last layout, which is the bug where Apply never lit up
     * again after reopening the panel.</p>
     */
    private Button applyButton, discardButton;

    /** Set by Apply, cleared by the snapshot that carries the answer. */
    private boolean awaitingApply = false;

    /** The whole-timer editor, open or not. */
    private final TimerEditor editor = new TimerEditor();

    private int editorRowsShown = 1;

    /** Text fields a dialog is asking for, in the order it asks. */
    private final List<EditBox> dialogFields = new ArrayList<>();

    /** Cycling buttons and how to walk each one backwards. */
    private final java.util.Map<AbstractWidget, Runnable> cycleBack = new java.util.LinkedHashMap<>();

    /** The three boxes that say when a scheduled command fires. */
    private final List<EditBox> atFields = new ArrayList<>();
    private static final String[] AT_UNITS = {"hours", "minutes", "seconds"};
    /** Answers a dialog collects by cycling rather than typing. */
    private String dialogMode = "shared";
    private boolean dialogCountUp = false;
    private boolean dialogGlobal = true;

    /** Field validation and the completion list, for every text field on the panel. */
    private final FieldAssist assist = new FieldAssist();

    /** Where the pointer was at the last frame, which is what the list highlights. */
    private int pointerX, pointerY;

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
        clock.onSnapshot(model.runs());
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
        clock.onSnapshot(model.runs());
        if (model.tab() == AdminModel.Tab.RUNS) {
            init();
        } else if (awaitingApply) {
            // Only the snapshot that carries an answer. Rebuilding on every
            // one took the caret straight back out of whatever field had just
            // been clicked, once a second, so the timers tab could not be
            // typed into at all.
            // The one snapshot the settings tab does want. Applying sends the
            // values and the server answers a moment later; without this the
            // fields keep showing what was there before the click, for good,
            // because this tab deliberately ignores every other snapshot.
            awaitingApply = false;
            init();
        }
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
            // Flush with the tab row above it. The rows used to be inset to
            // leave the state mark a margin of its own, which cost the whole
            // panel six pixels and made the two edges disagree; the mark now
            // sits on the row's own left edge instead.
            listX = GUTTER;
            listWidth = (int) ((width - 3 * GUTTER) * 0.56f);
            detailX = listX + listWidth + GUTTER;
            detailWidth = width - GUTTER - detailX;
            listTop = contentTop;
            listBottom = contentBottom;
            detailTop = contentTop;
            // Level with the tab row, and its rule on the tabs' bottom edge:
            // the two columns then start their content at the same height
            // instead of the right one hanging a row lower than the left.
            detailTitleY = tabY + (TAB_HEIGHT - 9) / 2;
            detailRuleY = tabY + TAB_HEIGHT - 1;
        } else {
            listX = GUTTER;
            listWidth = width - listX - GUTTER;
            detailX = GUTTER;
            detailWidth = width - 2 * GUTTER;
            int split = contentTop + (contentBottom - contentTop) * 55 / 100;
            listTop = contentTop;
            listBottom = split - 6;
            detailTop = split + 4;
            // Stacked, the detail has no tab row to line up with.
            detailTitleY = detailTop;
            detailRuleY = detailTop + LINE - 1;
        }

        // Under the detail column's own heading and rule.
        detailBodyTop = detailRuleY + 7;

        colName = listX + 6;
        // Level with the text inside the rows, which is what a column header
        // is for; the rows themselves are level with the tabs.
        colAudience = listX + Math.max(70, (int) (listWidth * 0.42f));
        colTimeRight = listX + listWidth - 6;

        visibleRows = Math.max(1, (listBottom - listTop + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
        clampScroll();

        host.clearWidgets();
        rowMarks.clear();
        rowData.clear();
        assist.clear();
        assist.setHost(host);
        // Refreshed every layout: a timer created a second ago should be
        // offered a second ago.
        assist.setTimerNames(model.timers().stream().map(AdminModel.TimerRow::name).toList());
        timerMarks.clear();
        timerData.clear();
        dialogFields.clear();
        atFields.clear();
        cycleBack.clear();
        searchBox = null;
        applyButton = null;
        discardButton = null;

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
        } else if (model.tab() == AdminModel.Tab.TIMERS) {
            if (editor.advanced()) {
                buildAdvanced();
            } else {
                buildTimerList();
                buildQuickColumn();
            }
        }
    }

    // ==================================================================
    // Timers: the list
    // ==================================================================

    /**
     * A search box and one button per definition, across the whole width.
     *
     * <p>No detail column: a timer has some thirty properties, and a column
     * beside a list can show about six of them. Picking one opens the editor
     * instead, which has the room to show all of it.</p>
     */
    private void buildTimerList() {
        // New belongs with the list it adds to, not up in the header among
        // the things that act on the whole panel.
        int newWidth = 44;
        host.addWidget(Button.builder(Component.translatable("ontime.gui.timers.action.new"), b -> {
                    editor.openNew();
        // Seeded from the server defaults. The creation form draws the same
        // display fields a timer has, and with nothing pending they came up
        // empty -- a new timer does copy the defaults, so the form was lying
        // about what it was going to make.
        seedCreationDefaults();
                    // Nothing is selected while a new one is being filled in,
                    // or the column would show the last timer's values under
                    // the new one's name.
                    model.selectTimer(null);
                    detailScroll = 0;
                    init();
                })
                .bounds(listX, contentTop, newWidth, 16)
                .tooltip(Tooltip.create(Component.translatable("ontime.gui.timers.action.new.tip")))
                .build());

        int searchX = listX + newWidth + 4;
        EditBox search = new EditBox(host.font(), searchX, contentTop, listWidth - newWidth - 4, 16,
                Component.translatable("ontime.gui.timers.search"));
        search.setMaxLength(32);
        search.setValue(model.filter());
        search.setHint(Component.translatable("ontime.gui.timers.search"));
        search.setResponder(text -> {
            if (text.equals(model.filter())) return;
            model.setFilter(text);
            scroll = 0;
            init();
            // init() builds a new box, so the caret has to be put back into it
            // or the second character of a search would go nowhere.
            if (searchBox != null) {
                searchBox.setFocused(true);
                searchBox.moveCursorToEnd(false);
            }
        });
        searchBox = host.addWidget(search);
        // Completes against the timers that exist, which is the only useful
        // thing to type in here.
        assist.add(search, text -> true, FieldAssist.Source.TIMERS);

        List<AdminModel.TimerRow> rows = model.filteredTimers();
        int top = contentTop + SEARCH_HEIGHT;
        timerRowsShown = Math.max(1, (contentBottom - top + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - timerRowsShown), scroll));

        // The name button is small and first; what you do to that timer sits
        // right beside it, so acting on one is not a trip to a panel and back.
        // No "Running" column: the green bar in the gutter already says it,
        // and saying it twice cost the row the width its actions wanted.
        nameWidth = Math.max(58, Math.min(140, listWidth * 32 / 100));
        int lengthRoom = 62;
        actionWidth = Math.max(30, Math.min(72,
                (listWidth - nameWidth - lengthRoom - 20) / 3));

        for (int i = 0; i < timerRowsShown && scroll + i < rows.size(); i++) {
            AdminModel.TimerRow row = rows.get(scroll + i);
            int y = top + i * (ROW_HEIGHT + ROW_GAP);

            Button name = Button.builder(Component.literal(row.name()), b -> {
                        editor.open(row.name());
                        model.selectTimer(row.name());
                        model.clearMessage();
                        init();
                    })
                    .bounds(listX, y, nameWidth, ROW_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("ontime.gui.timers.row.tip",
                            Component.literal(row.name()))))
                    .build();
            name.active = !row.name().equals(model.selectedTimer());
            host.addWidget(name);

            boolean running = row.runCount() > 0;
            String[] ops = {running ? "stop" : "start", "clone", "delete"};
            for (int a = 0; a < ops.length; a++) {
                String op = ops[a];
                host.addWidget(Button.builder(Component.translatable("ontime.gui.timers.action." + op),
                                b -> {
                                    model.selectTimer(row.name());
                                    editor.open(row.name());
                                    openDialog(op);
                                })
                        .bounds(listX + nameWidth + 4 + a * (actionWidth + 4), y, actionWidth, ROW_HEIGHT)
                        .tooltip(Tooltip.create(Component.translatable(
                                "ontime.gui.timers.action." + op + ".tip")))
                        .build());
            }

            // Beside the row rather than under it: drawn over the button it
            // read as a smear on the button's own border.
            timerMarks.add(new int[]{listX - MARK_WIDTH - 2, y, running ? COLOR_RUNNING : COLOR_BAND});
            timerData.add(row);
        }
    }

    /**
     * The selected timer, beside the list.
     *
     * <p>What gets changed often, grouped exactly as the settings tab groups
     * the defaults — they are the same things, so telling them apart by shape
     * would only be a puzzle. Everything rarer is behind <em>Advanced</em>.</p>
     */
    private void buildQuickColumn() {
        AdminModel.TimerRow timer = editor.isCreating() ? null : model.timer(model.selectedTimer());
        if (timer == null && !editor.isCreating()) return;
        editorFieldX = detailX;

        int footerY = contentBottom - 20;
        int buttonWidth = Math.max(48, Math.min(84, (detailWidth - 12) / 3));
        int startX = detailX + (detailWidth - (3 * buttonWidth + 12)) / 2;
        Button advanced = Button.builder(Component.translatable("ontime.gui.editor.advanced"), b -> {
                    editor.setAdvanced(true);
                    detailScroll = 0;
                    init();
                })
                .bounds(startX, footerY, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.advanced.tip")))
                .build();
        // Titles, commands and the rest are properties of a timer, and while
        // one is being filled in there is not one yet.
        advanced.active = !editor.isCreating();
        host.addWidget(advanced);

        Button apply = Button.builder(Component.translatable(editor.isCreating()
                                ? "ontime.gui.timers.accept.new" : "ontime.gui.settings.apply"),
                        b -> saveEditor())
                .bounds(startX + buttonWidth + 6, footerY, buttonWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.save.tip")))
                .build();
        apply.active = editor.isDirty(timer);
        applyButton = apply;
        host.addWidget(apply);

        Button discard = Button.builder(Component.translatable("ontime.gui.settings.discard"), b -> {
                    editor.discard();
                    model.clearMessage();
                    init();
                })
                .bounds(startX + 2 * (buttonWidth + 6), footerY, buttonWidth, 20)
                .build();
        discard.active = editor.isDirty(timer);
        discardButton = discard;
        host.addWidget(discard);

        editorControlWidth = Math.min(130, detailWidth / 2);
        editorControlX = detailX + detailWidth - editorControlWidth;
        editorFieldTop = detailBodyTop;
        // Right up to the footer. Four pixels of clearance is a gap; anything
        // more is a row that could have been shown and was not.
        buildFieldRows(timer, TimerEditor.Section.QUICK, footerY - 4);
    }

    // ==================================================================
    // Timers: the advanced editor
    // ==================================================================

    /** Width of the rail that names the groups. */
    private static final int RAIL_WIDTH = 96;

    /** Where the advanced page starts: it has a title, not a tab row. */
    private int advancedTop() {
        return tabY + LINE + 6;
    }

    private int editorFieldTop, editorFieldX, editorControlX, editorControlWidth;

    /**
     * The whole timer, in six groups.
     *
     * <p>A rail on the left names what a timer has; the rest of the panel is
     * whichever group is open. The footer holds the three things you do
     * <em>to</em> a timer rather than <em>with</em> it.</p>
     */
    private void buildAdvanced() {
        AdminModel.TimerRow timer = model.timer(editor.timerName());

        int railX = GUTTER;
        // The advanced page has no tab row, so it starts where the tabs would
        // have been rather than below where they are not: the band of nothing
        // across the top was that missing row still being reserved.
        int railTop = advancedTop();
        List<TimerEditor.Section> sections = TimerEditor.advancedSections();
        for (int i = 0; i < sections.size(); i++) {
            TimerEditor.Section item = sections.get(i);
            Button button = Button.builder(
                            Component.translatable("ontime.gui.editor.section." + item.name().toLowerCase(Locale.ROOT)),
                            b -> { editor.setSection(item); init(); })
                    .bounds(railX, railTop + i * 22, RAIL_WIDTH, 20)
                    .build();
            // The open group is the one you cannot press, exactly as the tabs
            // above say which tab you are on.
            button.active = editor.section() != item;
            host.addWidget(button);
        }

        // Apply and Discard live with the pages they act on, and they are
        // called what they are called everywhere else in the panel.
        AdminModel.TimerRow selected = model.timer(editor.timerName());
        boolean dirty = editor.isDirty(selected);
        Button apply = Button.builder(Component.translatable("ontime.gui.settings.apply"),
                        b -> saveEditor())
                .bounds(railX, contentBottom - 44, RAIL_WIDTH, 20)
                .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.save.tip")))
                .build();
        apply.active = dirty;
        applyButton = apply;
        host.addWidget(apply);

        Button discard = Button.builder(Component.translatable("ontime.gui.settings.discard"), b -> {
                    editor.discard();
                    model.clearMessage();
                    init();
                })
                .bounds(railX, contentBottom - 20, RAIL_WIDTH, 20)
                .build();
        discard.active = dirty;
        discardButton = discard;
        host.addWidget(discard);

        editorFieldX = railX + RAIL_WIDTH + GUTTER + 6;
        editorFieldTop = advancedTop();
        editorControlWidth = Math.min(180, (width - editorFieldX - GUTTER) / 2);
        editorControlX = width - GUTTER - editorControlWidth;

        if (editor.section() == TimerEditor.Section.COMMANDS) {
            buildCommandRows(timer, contentBottom - 30);
        } else if (editor.section() == TimerEditor.Section.TRIGGERS) {
            buildTriggerRows(timer, contentBottom - 30);
        } else {
            buildFieldRows(timer, editor.section(), contentBottom - 4);
        }
    }

    /**
     * One section's fields, with its headings taking a row each.
     *
     * <p>Shared by the quick column and the advanced pages: the difference
     * between them is which fields and how much room, not how a field works.
     * </p>
     */
    private void buildFieldRows(AdminModel.TimerRow timer, TimerEditor.Section section, int bottom) {
        List<TimerEditor.Entry> entries = TimerEditor.laidOut(section, editor.isCreating(), timerIsCustom(timer));
        // A row needs its control's height, not a whole slot: the last one fits
        // whenever there is room for the box itself.
        int room = bottom - editorFieldTop;
        editorRowsShown = Math.max(1, (room + (SETTING_HEIGHT - 18)) / SETTING_HEIGHT);
        detailScroll = Math.max(0, Math.min(Math.max(0, entries.size() - editorRowsShown), detailScroll));

        for (int i = 0; i < editorRowsShown && detailScroll + i < entries.size(); i++) {
            TimerEditor.Entry entry = entries.get(detailScroll + i);
            if (entry.isHeading()) continue;
            TimerEditor.Field field = entry.field();
            int y = editorFieldTop + i * SETTING_HEIGHT;
            String value = editor.displayed(timer, field);
            Tooltip tip = Tooltip.create(Component.translatable(
                    "ontime.gui.editor.field." + field.label() + ".tip"));

            if (field.kind() == TimerEditor.Kind.PICKER) {
                host.addWidget(Button.builder(
                                Component.translatable("ontime.gui.settings.custom_position.edit"),
                                b -> {
                                    if (timer == null) return;
                                    // Read off the timer, not off editor fields:
                                    // display.x and display.y stopped being
                                    // fields when Custom X and Custom Y went,
                                    // so looking them up returned nothing and
                                    // the button did nothing.
                                    host.openPicker(timer.name(),
                                            model.configString("positionPreset", "BOSSBAR"),
                                            displayInt(timer, "x", -1),
                                            displayInt(timer, "y", 4),
                                            displayFloat(timer, "scale", 1f),
                                            startingTime(timer), timerTitles(timer),
                                            (px, py) -> {
                                                sendDisplay(timer.name(), "x", px);
                                                sendDisplay(timer.name(), "y", py);
                                                saveEditor();
                                            });
                                })
                        .bounds(editorControlX, y, editorControlWidth, 18)
                        .tooltip(tip)
                        .build());
                continue;
            }

            switch (field.kind()) {
                case BOOL, PRESET, ACTION, TRIGGER -> {
                    Button cycle = Button.builder(cycleLabelFor(field, value), b -> {
                                editor.put(field.key(), editor.cycled(field, value, 1));
                                init();
                            })
                            .bounds(editorControlX, y, editorControlWidth, 18)
                            .tooltip(tip)
                            .build();
                    host.addWidget(cycle);
                    cycleBack.put(cycle, () -> {
                        editor.put(field.key(), editor.cycled(field, value, -1));
                        init();
                    });
                }
                default -> {
                    EditBox box = new EditBox(host.font(), editorControlX, y, editorControlWidth, 18,
                            Component.literal(field.key()));
                    box.setMaxLength(field.kind() == TimerEditor.Kind.TEXT ? 256 : 32);
                    box.setValue(value);
                    box.setResponder(text -> editor.put(field.key(), text));
                    host.addWidget(box);
                    registerEditorField(box, field, tip);
                }
            }
        }
    }

    /**
     * What a cycling button reads, in the colour of what it says.
     *
     * <p>Green for what starts or is on, red for what ends or is off. Only for
     * the words that name a state — the buttons that <em>do</em> something stay
     * plain, or the colour would stop meaning anything.</p>
     */
    private Component cycleLabelFor(TimerEditor.Field field, String value) {
        return switch (field.kind()) {
            case BOOL -> "countUp".equals(field.key())
                    ? Component.translatable(Boolean.parseBoolean(value)
                            ? "ontime.mode.countup" : "ontime.mode.countdown")
                    : state(Component.translatable(Boolean.parseBoolean(value)
                            ? "options.on" : "options.off"), Boolean.parseBoolean(value));
            case ACTION -> state(Component.translatable("ontime.gui.editor.action." + value),
                    "start".equals(value));
            case TRIGGER -> value.isEmpty()
                    ? state(Component.translatable("ontime.gui.editor.trigger.none"), false)
                    : Component.translatable("ontime.gui.editor.trigger." + value);
            default -> Component.literal(value);
        };
    }

    /** Green when it starts or is on, red when it ends or is off. */
    private static Component state(Component text, boolean on) {
        return text.copy().withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private void registerEditorField(EditBox box, TimerEditor.Field field, Tooltip tip) {
        switch (field.kind()) {
            case COLOR -> assist.add(box, FieldAssist.hexColor(), FieldAssist.Source.NONE, tip,
                    () -> {
                        Integer color = SettingsForm.colorOf(box.getValue());
                        return color == null ? 0xFFFFFFFF : 0xFF000000 | color;
                    });
            case INT -> assist.add(box, FieldAssist.intBetween(Integer.MIN_VALUE, Integer.MAX_VALUE),
                    FieldAssist.Source.NONE, tip, null);
            case FLOAT -> assist.add(box, FieldAssist.decimalBetween(-1e6f, 1e6f),
                    FieldAssist.Source.NONE, tip, null);
            default -> {
                if ("display.soundId".equals(field.key())) {
                    assist.add(box, FieldAssist.id(), FieldAssist.Source.SOUNDS, tip, null);
                } else if ("nextTimer".equals(field.key())) {
                    assist.add(box, text -> true, FieldAssist.Source.TIMERS, tip, null);
                } else {
                    assist.add(box, text -> true, FieldAssist.Source.NONE, tip, null);
                }
            }
        }
    }

    /**
     * The commands this timer runs, each with the way to take it off again,
     * and one row at the foot to add another.
     *
     * <p>These are not held until Apply like the fields are. A list is not a
     * value: adding a command and then applying five other things would make
     * the order it happened in matter, and it does not.</p>
     */
    /** The kinds the server accepts, in the order the cycler walks them. */
    private static final String[] TRIGGER_KINDS = {
            "player_join", "player_leave", "player_death", "player_respawn",
            "dimension_change", "advancement", "ftb_quest", "ftb_reward",
            "scoreboard", "expression"};

    private int triggerKind = 0;
    private boolean triggerStarts = false;
    private int triggerQuantifier = 0;
    private int triggerScope = 0;
    private EditBox triggerSubject;
    private EditBox triggerCount;

    /** How many of the watched players it takes, and who they are. */
    private static final String[] QUANTIFIERS = {"any", "all", "at_least"};
    private static final String[] SCOPES = {"audience", "everyone", "players", "selector"};

    /** The one kind that asks the server rather than a player. */
    private boolean kindHasSubject() {
        return !"expression".equals(TRIGGER_KINDS[triggerKind]);
    }

    private boolean scopeNeedsValue() {
        String scope = SCOPES[triggerScope];
        return "players".equals(scope) || "selector".equals(scope);
    }

    private boolean countIsUsed() {
        return "at_least".equals(QUANTIFIERS[triggerQuantifier]);
    }
    private EditBox triggerValue;
    private EditBox triggerScore;
    private EditBox triggerTarget;

    private int triggerCount() {
        AdminModel.TimerRow timer = model.timer(model.selectedTimer());
        return timer == null ? 0 : timer.triggers().size();
    }

    /** True while the chosen kind needs no value at all. */
    private boolean kindIsBare() {
        return TRIGGER_KINDS[triggerKind].startsWith("player_");
    }

    private boolean kindIsScoreboard() {
        return "scoreboard".equals(TRIGGER_KINDS[triggerKind]);
    }

    /**
     * The trigger list, and the row that adds one.
     *
     * <p>Shaped like the commands page rather than like a form, because that
     * is what it is now: a list of any length. It used to be three fixed
     * blocks -- a scoreboard, an expression and a select of four game events --
     * so half the kinds the server understood, advancements among them, could
     * only be reached from a command.</p>
     */
    private void buildTriggerRows(AdminModel.TimerRow timer, int bottom) {
        List<AdminModel.TimerRow.Trigger> entries = timer == null ? List.of() : timer.triggers();
        editorRowsShown = Math.max(1, (bottom - editorFieldTop) / 20);
        detailScroll = Math.max(0, Math.min(Math.max(0, entries.size() - editorRowsShown), detailScroll));

        for (int i = 0; i < editorRowsShown && detailScroll + i < entries.size(); i++) {
            final int index = detailScroll + i;
            int y = editorFieldTop + i * 20;
            host.addWidget(Button.builder(Component.translatable("ontime.gui.editor.trigger.remove"),
                            b -> {
                                JsonObject args = new JsonObject();
                                args.addProperty("name", timer.name());
                                args.addProperty("index", index);
                                send("timer.removeTrigger", args);
                                awaitingApply = true;
                            })
                    .bounds(width - GUTTER - 20, y, 20, 18)
                    .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.trigger.remove.tip")))
                    .build());
        }

        int y = bottom + 6;
        int right = width - GUTTER;

        host.addWidget(Button.builder(
                        Component.translatable("ontime.trigger.kind." + TRIGGER_KINDS[triggerKind]),
                        b -> {
                            triggerKind = (triggerKind + 1) % TRIGGER_KINDS.length;
                            init();
                        })
                .bounds(editorFieldX, y, 118, 18)
                .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.trigger.kind.tip")))
                .build());

        int cursor = editorFieldX + 122;
        int addX = right - 48;
        int actionX = addX - 62;

        if (kindIsScoreboard()) {
            triggerValue = new EditBox(host.font(), cursor, y, 90, 18,
                    Component.translatable("ontime.gui.editor.trigger.objective"));
            triggerValue.setHint(Component.translatable("ontime.gui.editor.trigger.objective"));
            triggerValue.setMaxLength(64);
            host.addWidget(triggerValue);
            assist.add(triggerValue, text -> true);

            triggerScore = new EditBox(host.font(), cursor + 94, y, 44, 18,
                    Component.translatable("ontime.gui.editor.trigger.score"));
            triggerScore.setHint(Component.translatable("ontime.gui.editor.trigger.score"));
            triggerScore.setMaxLength(9);
            host.addWidget(triggerScore);
            assist.add(triggerScore, FieldAssist.intBetween(-999999, 999999));

            triggerTarget = new EditBox(host.font(), cursor + 142, y,
                    Math.max(40, actionX - 4 - (cursor + 142)), 18,
                    Component.translatable("ontime.gui.editor.trigger.target"));
            triggerTarget.setHint(Component.translatable("ontime.gui.editor.trigger.target"));
            triggerTarget.setMaxLength(64);
            host.addWidget(triggerTarget);
            assist.add(triggerTarget, text -> true);
        } else if (!kindIsBare()) {
            triggerScore = null;
            triggerTarget = null;
            triggerValue = new EditBox(host.font(), cursor, y, Math.max(60, actionX - 4 - cursor), 18,
                    Component.translatable("ontime.gui.editor.trigger.value"));
            triggerValue.setHint(Component.translatable("ontime.gui.editor.trigger.value"));
            triggerValue.setMaxLength(256);
            host.addWidget(triggerValue);
            assist.add(triggerValue, text -> true);
        } else {
            triggerValue = null;
            triggerScore = null;
            triggerTarget = null;
        }

        host.addWidget(Button.builder(
                        Component.translatable("ontime.trigger.action." + (triggerStarts ? "start" : "finish")),
                        b -> {
                            triggerStarts = !triggerStarts;
                            init();
                        })
                .bounds(actionX, y, 58, 18)
                .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.trigger.action.tip")))
                .build());

        buildSubjectRow(y + 21, right);

        host.addWidget(Button.builder(Component.translatable("ontime.gui.editor.trigger.add"), b -> {
                    if (timer == null) return;
                    String kind = TRIGGER_KINDS[triggerKind];
                    JsonObject args = new JsonObject();
                    args.addProperty("name", timer.name());
                    args.addProperty("kind", kind);
                    args.addProperty("action", triggerStarts ? "start" : "finish");
                    if (!kindIsBare()) {
                        if (triggerValue == null || triggerValue.getValue().isBlank()) return;
                        args.addProperty("value", triggerValue.getValue().trim());
                    }
                    if (kindIsScoreboard()) {
                        args.addProperty("threshold", parseIntOr(triggerScore, 0));
                    }
                    if (kindHasSubject()) {
                        args.addProperty("quantifier", QUANTIFIERS[triggerQuantifier]);
                        args.addProperty("subject", SCOPES[triggerScope]);
                        if (countIsUsed()) args.addProperty("count", parseIntOr(triggerCount, 1));
                        if (scopeNeedsValue()) {
                            if (triggerSubject == null || triggerSubject.getValue().isBlank()) return;
                            args.addProperty("subjectValue", triggerSubject.getValue().trim());
                        }
                    }
                    send("timer.addTrigger", args);
                    awaitingApply = true;
                })
                .bounds(addX, y, 48, 18)
                .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.trigger.add.tip")))
                .build());
    }


    /**
     * Who the trigger watches, on its own line.
     *
     * <p>A second line rather than more boxes on the first: with a kind, an
     * id, a score, a quantifier, a subject, its value and an action, one row
     * would be a row of slivers at any window width worth using.</p>
     */
    private void buildSubjectRow(int y, int right) {
        if (!kindHasSubject()) {
            triggerSubject = null;
            triggerCount = null;
            return;
        }

        int x = editorFieldX;
        host.addWidget(Button.builder(
                        Component.translatable("ontime.who.q." + QUANTIFIERS[triggerQuantifier]),
                        b -> {
                            triggerQuantifier = (triggerQuantifier + 1) % QUANTIFIERS.length;
                            init();
                        })
                .bounds(x, y, 74, 18)
                .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.trigger.quantifier.tip")))
                .build());
        x += 78;

        if (countIsUsed()) {
            triggerCount = new EditBox(host.font(), x, y, 34, 18,
                    Component.translatable("ontime.gui.editor.trigger.count"));
            triggerCount.setHint(Component.translatable("ontime.gui.editor.trigger.count"));
            triggerCount.setMaxLength(4);
            triggerCount.setValue("1");
            host.addWidget(triggerCount);
            assist.add(triggerCount, FieldAssist.intBetween(1, 9999));
            x += 38;
        } else {
            triggerCount = null;
        }

        int scopeWidth = scopeNeedsValue() ? 96 : Math.max(96, right - x);
        host.addWidget(Button.builder(
                        Component.translatable("ontime.who.s." + SCOPES[triggerScope]),
                        b -> {
                            triggerScope = (triggerScope + 1) % SCOPES.length;
                            init();
                        })
                .bounds(x, y, scopeWidth, 18)
                .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.trigger.scope.tip")))
                .build());
        x += scopeWidth + 4;

        if (scopeNeedsValue()) {
            String hint = "selector".equals(SCOPES[triggerScope])
                    ? "ontime.gui.editor.trigger.selector" : "ontime.gui.editor.trigger.names";
            triggerSubject = new EditBox(host.font(), x, y, Math.max(60, right - x), 18,
                    Component.translatable(hint));
            triggerSubject.setHint(Component.translatable(hint));
            triggerSubject.setMaxLength(256);
            host.addWidget(triggerSubject);
            assist.add(triggerSubject, text -> true);
        } else {
            triggerSubject = null;
        }
    }

    private static int parseIntOr(EditBox box, int fallback) {
        if (box == null) return fallback;
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** One line per trigger: what it watches, and what it does to the timer. */
    private void drawTriggerRows(Painter painter, AdminModel.TimerRow timer) {
        List<AdminModel.TimerRow.Trigger> entries = timer == null ? List.of() : timer.triggers();
        if (entries.isEmpty()) {
            painter.text(Component.translatable("ontime.gui.editor.trigger.none"),
                    editorFieldX, editorFieldTop, COLOR_TEXT);
            return;
        }
        for (int i = 0; i < editorRowsShown && detailScroll + i < entries.size(); i++) {
            AdminModel.TimerRow.Trigger trigger = entries.get(detailScroll + i);
            int y = editorFieldTop + i * 20;
            painter.text(Component.translatable(
                            "ontime.trigger.action." + (trigger.startsIt() ? "start" : "finish")),
                    editorFieldX, y, trigger.startsIt() ? COLOR_COOLDOWN : COLOR_PAUSED);
            painter.text(describeTrigger(trigger), editorFieldX + 56, y, COLOR_TEXT);
        }
    }

    private static Component describeTrigger(AdminModel.TimerRow.Trigger trigger) {
        Component kind = Component.translatable("ontime.trigger.kind." + trigger.kind());
        Component who = describeWho(trigger);
        if ("scoreboard".equals(trigger.kind())) {
            return Component.translatable("ontime.command.trigger.describe.scoreboard",
                    kind, trigger.value(), trigger.threshold(), who);
        }
        if (trigger.value().isEmpty()) {
            return Component.translatable("ontime.command.trigger.describe.who", kind, who);
        }
        return Component.translatable("ontime.command.trigger.describe.value",
                kind, trigger.value(), who);
    }

    /** The same wording the commands print, so both read alike. */
    private static Component describeWho(AdminModel.TimerRow.Trigger trigger) {
        Component scope = Component.translatable("ontime.who.scope." + trigger.scope(),
                trigger.subject());
        if ("at_least".equals(trigger.quantifier())) {
            return Component.translatable("ontime.who.at_least", trigger.count(), scope);
        }
        return Component.translatable("ontime.who." + trigger.quantifier(), scope);
    }


    /** A typed field's value as a number, or the fallback when it is mid-edit. */
    private static int numberOr(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double decimalOr(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }


    /** True when the server default is CUSTOM, which is the only time the row applies. */
    private boolean defaultsAreCustom() {
        return "CUSTOM".equalsIgnoreCase(
                settings.displayed(model, SettingsForm.rowOf("positionPreset")));
    }

    /**
     * The same question for one timer's own copy.
     *
     * <p>Also while creating, where there is no timer yet but the form has
     * been seeded with the defaults: the row has to follow what the preset
     * field says right now, not what a timer that does not exist holds.</p>
     */
    private boolean timerIsCustom(AdminModel.TimerRow timer) {
        if (timer == null && !editor.isCreating()) return false;
        return "CUSTOM".equalsIgnoreCase(
                editor.displayed(timer, TimerEditor.fieldOf("display.preset")));
    }

    /** A number out of a timer's display block, or the fallback while it is unset. */
    private static int displayInt(AdminModel.TimerRow timer, String key, int fallback) {
        if (timer == null || timer.display() == null || !timer.display().has(key)) return fallback;
        try {
            return timer.display().get(key).getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float displayFloat(AdminModel.TimerRow timer, String key, float fallback) {
        if (timer == null || timer.display() == null || !timer.display().has(key)) return fallback;
        try {
            return timer.display().get(key).getAsFloat();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** {@code hh:mm:ss} of what this timer starts at, which is what it shows before it runs. */
    private static String startingTime(AdminModel.TimerRow timer) {
        if (timer == null) return "00:00:00";
        long total = timer.targetTicks() / 20L;
        return String.format("%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60);
    }

    /** above, below, left, right -- blank for a slot this timer does not use. */
    private static String[] timerTitles(AdminModel.TimerRow timer) {
        if (timer == null) return new String[4];
        return new String[]{timer.title("above"), timer.title("below"),
                timer.title("left"), timer.title("right")};
    }


    /** One server default, sent now rather than left pending. */
    private void sendConfig(String key, int value) {
        JsonObject args = new JsonObject();
        args.addProperty("key", key);
        args.addProperty("value", value);
        send("config.set", args);
    }

    /** One field of a timer's own display block, sent now. */
    private void sendDisplay(String name, String key, int value) {
        JsonObject args = new JsonObject();
        args.addProperty("name", name);
        args.addProperty("key", key);
        args.addProperty("value", value);
        send("timer.setDisplay", args);
    }


    /** What a timer created right now would copy, put into the form up front. */
    private void seedCreationDefaults() {
        for (SettingsForm.Row row : SettingsForm.displayRows()) {
            if (row.isHeader() || row.isAction()) continue;
            editor.put("display." + row.displayKey(), settings.displayed(model, row));
        }
    }

    private void buildCommandRows(AdminModel.TimerRow timer, int bottom) {
        List<AdminModel.Scheduled> entries = timer == null ? List.of() : timer.commandList();
        editorRowsShown = Math.max(1, (bottom - editorFieldTop) / 20);
        detailScroll = Math.max(0, Math.min(Math.max(0, entries.size() - editorRowsShown), detailScroll));

        for (int i = 0; i < editorRowsShown && detailScroll + i < entries.size(); i++) {
            final int index = detailScroll + i;
            int y = editorFieldTop + i * 20;
            host.addWidget(Button.builder(Component.translatable("ontime.gui.editor.command.remove"),
                            b -> {
                                JsonObject args = new JsonObject();
                                args.addProperty("name", timer.name());
                                args.addProperty("index", index);
                                send("timer.removeCommand", args);
                                awaitingApply = true;
                            })
                    .bounds(width - GUTTER - 20, y, 20, 18)
                    .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.command.remove.tip")))
                    .build());
        }

        // The adding row, at the foot and always there. Three boxes rather
        // than one: nobody should have to work out what an hour and thirty
        // seven minutes is in seconds to schedule a command there.
        int y = bottom + 6;
        int unit = 34;
        for (int i = 0; i < 3; i++) {
            EditBox box = new EditBox(host.font(), editorFieldX + i * (unit + 3), y, unit, 18,
                    Component.translatable("ontime.gui.editor.command." + AT_UNITS[i]));
            box.setHint(Component.translatable("ontime.gui.editor.command." + AT_UNITS[i]));
            box.setMaxLength(4);
            host.addWidget(box);
            assist.add(box, FieldAssist.intBetween(0, 9999));
            atFields.add(box);
        }

        int commandX = editorFieldX + 3 * (unit + 3) + 6;
        EditBox command = new EditBox(host.font(), commandX, y,
                width - GUTTER - 54 - commandX,
                18, Component.translatable("ontime.gui.editor.command.text"));
        command.setHint(Component.translatable("ontime.gui.editor.command.text"));
        command.setMaxLength(256);
        host.addWidget(command);
        assist.add(command, text -> true);

        host.addWidget(Button.builder(Component.translatable("ontime.gui.editor.command.add"), b -> {
                    if (timer == null || command.getValue().isBlank()) return;
                    JsonObject args = new JsonObject();
                    args.addProperty("name", timer.name());
                    args.addProperty("command", command.getValue().trim());
                    // All three boxes empty means a finish command, which is
                    // what empty should mean here: "when it ends".
                    long at = atSeconds();
                    if (at > 0) args.addProperty("atSeconds", at);
                    send("timer.addCommand", args);
                    awaitingApply = true;
                })
                .bounds(width - GUTTER - 48, y, 48, 18)
                .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.command.add.tip")))
                .build());
    }

    /** Same rules as the defaults form; only the source of the value differs. */

    /** Same rules as the defaults form; only the source of the value differs. */
    private void registerDisplayField(EditBox box, SettingsForm.Row row, Tooltip tip) {
        switch (row.kind()) {
            case COLOR -> assist.add(box, FieldAssist.hexColor(), FieldAssist.Source.NONE, tip,
                    () -> {
                        Integer color = SettingsForm.colorOf(box.getValue());
                        return color == null ? 0xFFFFFFFF : 0xFF000000 | color;
                    });
            case STRING -> assist.add(box, FieldAssist.id(), FieldAssist.Source.SOUNDS, tip, null);
            case INT -> assist.add(box, FieldAssist.intBetween(displayFloor(row.displayKey()),
                    Integer.MAX_VALUE), FieldAssist.Source.NONE, tip, null);
            case FLOAT -> assist.add(box, FieldAssist.decimalBetween(
                    "scale".equals(row.displayKey()) ? 0.1f : 0f,
                    "scale".equals(row.displayKey()) ? 5f
                            : "soundPitch".equals(row.displayKey()) ? 2f : 1f),
                    FieldAssist.Source.NONE, tip, null);
            default -> { }
        }
    }

    private static long displayFloor(String key) {
        return switch (key) {
            case "x", "y" -> Integer.MIN_VALUE;
            default -> 0;
        };
    }

    // ---- the settings form ----

    private int settingsTop() {
        return headerRowY;
    }

    private void buildSettings() {
        int top = settingsTop();
        settingsRows = Math.max(1, (contentBottom - top) / SETTING_HEIGHT);
        List<SettingsForm.Row> rows = SettingsForm.rows(defaultsAreCustom());
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - settingsRows), scroll));

        int controlWidth = Math.min(140, (width - 2 * GUTTER) / 2);
        int controlX = width - GUTTER - controlWidth;

        for (int i = 0; i < settingsRows && scroll + i < rows.size(); i++) {
            SettingsForm.Row row = rows.get(scroll + i);
            if (row.isHeader()) continue;
            int y = top + i * SETTING_HEIGHT;
            String tooltipKey = "ontime.config." + snake(row.key()) + ".tooltip";

            if (row.isAction()) {
                if ("customPosition".equals(row.key())) {
                    host.addWidget(Button.builder(
                                    Component.translatable("ontime.gui.settings.custom_position.edit"),
                                    // The defaults, so a sample counter and the
                                    // four sample titles: there is no one timer
                                    // here to show the real state of.
                                    // Falls back to whatever preset the server
                                    // defaults to, so a counter that has never
                                    // been placed opens where it draws today
                                    // rather than in the corner.
                                    b -> host.openPicker(null,
                                            model.configString("positionPreset", "BOSSBAR"),
                                            model.configInt("timerX", -1),
                                            model.configInt("timerY", 4),
                                            model.configFloat("timerScale", 1f),
                                            "00:00:00", SAMPLE_TITLES,
                                            // Save and leave saves. Writing a
                                            // pending edit instead meant the
                                            // position was only really stored
                                            // if Apply was pressed afterwards,
                                            // and reopening on an unchanged
                                            // position left nothing pending, so
                                            // Apply was greyed out and the work
                                            // was silently lost.
                                            (px, py) -> {
                                                sendConfig("timerX", px);
                                                sendConfig("timerY", py);
                                                // And whatever was already
                                                // pending, so leaving the
                                                // placement screen leaves
                                                // nothing behind to apply.
                                                applySettings();
                                            }))
                            .bounds(controlX, y, controlWidth, 18)
                            .tooltip(Tooltip.create(Component.translatable(
                                    "ontime.gui.settings.custom_position.tip")))
                            .build());
                    continue;
                }
                // Asks first. Everything above this row goes back at once, and
                // there is no undo.
                host.addWidget(Button.builder(
                                Component.translatable("ontime.gui.settings.reset")
                                        .copy().withStyle(ChatFormatting.RED),
                                b -> { confirmOp = "config.reset"; init(); })
                        .bounds(controlX, y, controlWidth, 18)
                        .tooltip(Tooltip.create(Component.translatable(
                                "ontime.gui.settings.reset.tip")))
                        .build());
                continue;
            }

            if (row.kind() == SettingsForm.Kind.BOOL || row.kind() == SettingsForm.Kind.PRESET) {
                // A button that shows its value and advances on click. That is
                // what CycleButton looks like, without CycleButton's drift:
                // 26.2 changed its builder and dropped withInitialValue.
                String value = settings.displayed(model, row);
                Button cycle = Button.builder(cycleLabel(row, value), b -> {
                            settings.put(row.key(), settings.cycled(row, value, 1));
                            init();
                        })
                        .bounds(controlX, y, controlWidth, 18)
                        .tooltip(Tooltip.create(Component.translatable(tooltipKey)))
                        .build();
                host.addWidget(cycle);
                cycleBack.put(cycle, () -> {
                    settings.put(row.key(), settings.cycled(row, value, -1));
                    init();
                });
            } else {
                EditBox box = new EditBox(host.font(), controlX, y, controlWidth, 18,
                        Component.translatable("ontime.config." + snake(row.key())));
                box.setMaxLength(64);
                box.setValue(settings.displayed(model, row));
                box.setResponder(text -> settings.put(row.key(), text));
                host.addWidget(box);
                register(box, row, Tooltip.create(Component.translatable(tooltipKey)));
            }
        }
    }

    /**
     * Tells the assist what this field accepts, which is also what it can offer.
     *
     * <p>One statement per kind, so a field cannot end up validating against
     * one rule and completing from another.</p>
     */
    private void register(EditBox box, SettingsForm.Row row, Tooltip tooltip) {
        switch (row.kind()) {
            case COLOR -> {
                // Valid text reads in the colour it names, so a wrong digit is
                // visible before it is applied. Only ever asked once the text
                // has been found to parse.
                Integer parsed = SettingsForm.colorOf(box.getValue());
                assist.add(box, FieldAssist.hexColor(), FieldAssist.Source.NONE, tooltip,
                        () -> {
                            Integer color = SettingsForm.colorOf(box.getValue());
                            return color == null ? 0xFFFFFFFF : 0xFF000000 | color;
                        });
                if (parsed == null) box.setTextColor(COLOR_ERROR);
            }
            case STRING -> assist.add(box,
                    "timerSoundId".equals(row.key()) ? FieldAssist.id() : text -> true,
                    "timerSoundId".equals(row.key()) ? FieldAssist.Source.SOUNDS : FieldAssist.Source.NONE,
                    tooltip, null);
            case INT -> assist.add(box, FieldAssist.intBetween(intFloor(row.key()), Integer.MAX_VALUE),
                    FieldAssist.Source.NONE, tooltip, null);
            case FLOAT -> assist.add(box,
                    FieldAssist.decimalBetween(floatFloor(row.key()), floatCeil(row.key())),
                    FieldAssist.Source.NONE, tooltip, null);
            default -> { }
        }
    }

    /** Only the two ports have a floor that is not zero; the rest may be negative. */
    private static long intFloor(String key) {
        return switch (key) {
            case "webSocketPort", "webPanelPort" -> 1;
            case "timerX", "timerY" -> Integer.MIN_VALUE;
            case "confirmRunThreshold" -> -1;
            default -> 0;
        };
    }

    private static float floatFloor(String key) {
        return "timerScale".equals(key) ? 0.1f : 0f;
    }

    private static float floatCeil(String key) {
        return switch (key) {
            case "timerScale" -> 5f;
            case "timerSoundPitch" -> 2f;
            default -> 1f;
        };
    }

    private Component cycleLabel(SettingsForm.Row row, String value) {
        if (row.kind() == SettingsForm.Kind.BOOL) {
            return state(Component.translatable(Boolean.parseBoolean(value)
                    ? "options.on" : "options.off"), Boolean.parseBoolean(value));
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

    private int dialogY() { return (height - dialogHeight()) / 2; }

    /**
     * Opens a dialog and lets it own the screen.
     *
     * <p>Every one of these either creates or destroys something, so none of
     * them happens on a single click: the dialog is the pause in which the
     * name is typed, or the audience chosen, or the mind changed.</p>
     */
    private void openDialog(String kind) {
        confirmOp = kind;
        dialogMode = "shared";
        dialogCountUp = false;
        dialogGlobal = true;
        model.clearMessage();
        init();
    }

    /** The dialogs that ask for something rather than just confirming. */
    private boolean isTimerDialog() {
        return "clone".equals(confirmOp) || "start".equals(confirmOp)
                || "stop".equals(confirmOp) || "delete".equals(confirmOp);
    }

    private int dialogHeight() {
        return switch (confirmOp == null ? "" : confirmOp) {
            // Grows by the row the players field takes. It was a fixed 116
            // whether or not that field was there, so the mode button below it
            // was drawn underneath Cancel and Start.
            case "start" -> dialogGlobal ? 122 : 146;
            case "clone" -> 104;
            default -> DIALOG_HEIGHT;
        };
    }

    private void buildTimerDialog() {
        int x = dialogX() + 14;
        int fieldWidth = DIALOG_WIDTH - 28;
        int y = dialogY() + 34;
        String name = model.selectedTimer();

        switch (confirmOp) {
            case "clone" -> {
                dialogFields.add(host.addWidget(field(x, y, fieldWidth,
                        "ontime.gui.timers.field.name", name == null ? "" : name + "2")));
            }
            case "start" -> {
                // Both buttons name the setting and then its value. They used
                // to show the value alone, so the first thing anybody saw was
                // "everyone" and "each" with nothing saying what either was
                // choosing.
                y += 10;
                host.addWidget(Button.builder(
                                Component.translatable("ontime.gui.timers.dialog.audience",
                                        Component.translatable(dialogGlobal
                                                ? "ontime.gui.timers.audience.everyone"
                                                : "ontime.gui.timers.audience.chosen")),
                                b -> { dialogGlobal = !dialogGlobal; init(); })
                        .bounds(x, y, fieldWidth, 20)
                        .tooltip(Tooltip.create(Component.translatable("ontime.gui.timers.audience.tip")))
                        .build());
                y += 24;
                if (!dialogGlobal) {
                    dialogFields.add(host.addWidget(field(x, y, fieldWidth,
                            "ontime.gui.timers.field.players", "")));
                    y += 24;
                }
                host.addWidget(Button.builder(
                                Component.translatable("ontime.gui.timers.dialog.mode",
                                        Component.translatable("shared".equals(dialogMode)
                                                ? "ontime.mode.shared" : "ontime.mode.each")),
                                b -> {
                                    dialogMode = "shared".equals(dialogMode) ? "each" : "shared";
                                    init();
                                })
                        .bounds(x, y, fieldWidth, 20)
                        .tooltip(Tooltip.create(Component.translatable("ontime.gui.timers.mode.tip")))
                        .build());
            }
            default -> { }
        }

        int buttonWidth = 96;
        int gap = 8;
        int buttonsY = dialogY() + dialogHeight() - 28;
        int startX = dialogX() + (DIALOG_WIDTH - 2 * buttonWidth - gap) / 2;

        host.addWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
                    confirmOp = null;
                    init();
                })
                .bounds(startX, buttonsY, buttonWidth, 20)
                .build());

        boolean destructive = "delete".equals(confirmOp);
        Component accept = Component.translatable("ontime.gui.timers.accept." + confirmOp);
        host.addWidget(Button.builder(destructive ? accept.copy().withStyle(ChatFormatting.RED) : accept,
                        b -> submitTimerDialog())
                .bounds(startX + buttonWidth + gap, buttonsY, buttonWidth, 20)
                .build());
    }

    private EditBox field(int x, int y, int width, String hintKey, String initial) {
        EditBox box = new EditBox(host.font(), x, y, width, 18, Component.translatable(hintKey));
        box.setMaxLength(64);
        box.setValue(initial);
        box.setHint(Component.translatable(hintKey));
        assist.add(box, text -> true);
        return box;
    }

    /** Reads whatever the open dialog collected and sends the one operation it means. */
    private void submitTimerDialog() {
        String kind = confirmOp;
        String name = editor.timerName();
        JsonObject args = new JsonObject();

        switch (kind == null ? "" : kind) {
            case "stop" -> {
                confirmOp = null;
                init();
                // Every execution of this timer, which is the only thing the
                // button can mean when it sits on a definition.
                for (AdminModel.RunRow run : model.runs()) {
                    if (!run.timerName().equals(name)) continue;
                    JsonObject one = new JsonObject();
                    one.addProperty("runId", run.runId());
                    send("run.stop", one);
                }
                awaitingApply = true;
            }
            case "clone" -> {
                args.addProperty("name", name);
                args.addProperty("dest", value(0));
                confirmOp = null;
                init();
                send("timer.clone", args);
            }
            case "delete" -> {
                args.addProperty("name", name);
                confirmOp = null;
                editor.close();
                // Off the list now, not at whatever moment the next snapshot
                // happens to arrive.
                model.forgetTimer(name);
                model.selectTimer(null);
                send("timer.delete", args);
                init();
            }
            case "start" -> {
                args.addProperty("name", name);
                args.addProperty("mode", dialogMode);
                if (dialogGlobal) {
                    args.addProperty("global", true);
                } else {
                    JsonArray players = new JsonArray();
                    for (String typed : value(0).split(",")) {
                        String wanted = typed.trim();
                        if (wanted.isEmpty()) continue;
                        for (AdminModel.PlayerRow player : model.players()) {
                            if (player.name().equalsIgnoreCase(wanted)) players.add(player.uuid());
                        }
                    }
                    if (players.isEmpty()) {
                        model.setMessage(Component.translatable("ontime.gui.timers.no_players").getString(), true);
                        return;
                    }
                    args.add("players", players);
                }
                confirmOp = null;
                init();
                send("run.start", args);
            }
            default -> {
                confirmOp = null;
                init();
            }
        }
    }

    private String value(int index) {
        return index < dialogFields.size() ? dialogFields.get(index).getValue().trim() : "";
    }

    private int numberIn(int index) {
        try {
            return Integer.parseInt(value(index));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Sends everything the editor changed, then stays where it is.
     *
     * <p>Creating is the one that moves: the timer did not exist a moment ago,
     * so the editor reopens on the real one, where the other five groups mean
     * something.</p>
     */
    private void saveEditor() {
        AdminModel.TimerRow timer = model.timer(editor.timerName());
        List<TimerEditor.Op> ops = editor.build(timer);
        for (TimerEditor.Op op : ops) send(op.name(), op.args());

        if (editor.isCreating() && !ops.isEmpty()) {
            String name = ops.get(0).args().get("name").getAsString();
            editor.open(name);
            model.selectTimer(name);
            detailScroll = 0;
        } else {
            editor.discard();
        }
        awaitingApply = !ops.isEmpty();
        init();
    }

    private void buildConfirm() {
        if (isTimerDialog()) {
            buildTimerDialog();
            return;
        }

        int y = dialogY() + DIALOG_HEIGHT - 28;
        int gap = 8;

        if (CONFIRM_EXIT.equals(confirmOp)) {
            // Three ways out, and Cancel first: the leftmost button is where a
            // hand goes when it is trying to undo a mis-click.
            int buttonWidth = (DIALOG_WIDTH - 2 * 12 - 2 * gap) / 3;
            int startX = dialogX() + 12;

            host.addWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
                        confirmOp = null;
                        init();
                    })
                    .bounds(startX, y, buttonWidth, 20)
                    .build());

            host.addWidget(Button.builder(
                            Component.translatable("ontime.gui.confirm.exit.discard")
                                    .withStyle(ChatFormatting.RED),
                            b -> {
                                settings.discard();
                                editor.discard();
                                confirmOp = null;
                                host.closePanel();
                            })
                    .bounds(startX + buttonWidth + gap, y, buttonWidth, 20)
                    .build());

            host.addWidget(Button.builder(Component.translatable("ontime.gui.confirm.exit.save"), b -> {
                        if (editor.isDirty(model.timer(editor.timerName()))) saveEditor();
                        applySettings();
                        confirmOp = null;
                        host.closePanel();
                    })
                    .bounds(startX + 2 * (buttonWidth + gap), y, buttonWidth, 20)
                    .build());
            return;
        }

        int buttonWidth = 96;
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
        host.addWidget(Button.builder(Component.translatable("ontime.gui.exit"), b -> {
                    // Closing on top of unapplied edits throws them away in
                    // silence. Asking costs one click and is the only way the
                    // answer is the operator's rather than the panel's.
                    if (settings.isDirty(model) || editor.isDirty(model.timer(editor.timerName()))) {
                        confirmOp = CONFIRM_EXIT;
                        init();
                    } else {
                        host.closePanel();
                    }
                })
                .bounds(doneX, 5, doneWidth, 20)
                .build());

        if (model.tab() == AdminModel.Tab.TIMERS && editor.advanced()) {
            int backWidth = 50;
            host.addWidget(Button.builder(Component.translatable("ontime.gui.editor.back"), b -> {
                        // Back to the list, not out of the timer: what was
                        // typed here is still pending, and the column beside
                        // the list is where it gets applied.
                        editor.setAdvanced(false);
                        detailScroll = 0;
                        init();
                    })
                    .bounds(doneX - 6 - backWidth, 5, backWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.back.tip")))
                    .build());
        }

        if (model.tab() == AdminModel.Tab.SETTINGS) {
            int applyWidth = 62;
            int discardWidth = 62;
            applyButton = Button.builder(Component.translatable("ontime.gui.settings.apply"),
                            b -> applySettings())
                    .bounds(doneX - 6 - applyWidth, 5, applyWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable("ontime.gui.settings.apply.tip")))
                    .build();
            // Set here as well as while drawing. A button is drawn before the
            // content pass that refreshes it, so one built enabled by default
            // is enabled for exactly one frame — which is invisible in normal
            // use and a flicker while the wheel lays the panel out repeatedly.
            applyButton.active = settings.isDirty(model);
            host.addWidget(applyButton);

            discardButton = Button.builder(Component.translatable("ontime.gui.settings.discard"), b -> {
                        settings.discard();
                        model.clearMessage();
                        init();
                    })
                    .bounds(doneX - 12 - applyWidth - discardWidth, 5, discardWidth, 20)
                    .build();
            discardButton.active = settings.isDirty(model);
            host.addWidget(discardButton);
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
        // The editor owns the whole panel: leaving the tabs up would offer a
        // way out that silently drops what has been typed.
        if (model.tab() == AdminModel.Tab.TIMERS && editor.advanced()) return;

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

            // Beside the row, not over it: drawn on the button it read as a
            // smear along the button's own border.
            rowMarks.add(new int[]{listX - MARK_WIDTH - 2, y, stateColor(row)});
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
    public void drawBands(Painter painter, int mouseX, int mouseY) {
        pointerX = mouseX;
        pointerY = mouseY;
        // Before the widgets draw: the field reads its own colour and ghost
        // text as it paints itself, so deciding them afterwards would show the
        // previous frame's answer.
        assist.update(mouseX, mouseY);

        painter.rect(0, 0, width, HEADER_HEIGHT, COLOR_BAND);

        // The dialog's fills go here for the same reason as the band: they are
        // large and opaque, and after the widgets they would bury its buttons.
        if (confirmOp != null) {
            painter.rect(0, 0, width, height, COLOR_SCRIM);
            painter.rect(dialogX(), dialogY(), DIALOG_WIDTH, dialogHeight(), COLOR_DIALOG);
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
            case TIMERS -> {
                if (editor.advanced()) {
                    drawAdvanced(painter);
                } else {
                    drawTimerList(painter);
                    drawQuickColumn(painter);
                }
            }
            case SETTINGS -> drawSettings(painter);
        }

        // Last, so it is over everything including the fields it overlaps.
        assist.render(painter);
    }

    /**
     * The definitions on the left, the selected one's own settings on the
     * right.
     *
     * <p>Deliberately the same shape as the executions tab: a list of things
     * and a column that says everything about the one you picked. The right
     * column holds the twelve settings the Settings tab serves as defaults,
     * because a default is a starting value and every one of them belongs to
     * the timer once it exists.</p>
     */
    /**
     * The list: what exists, whether it is running, and how long it runs for.
     */
    private void drawTimerList(Painter painter) {
        List<AdminModel.TimerRow> rows = model.filteredTimers();

        int headerTextX = listX + nameWidth + 4 + 3 * (actionWidth + 4) + 6;
        painter.text(Component.translatable("ontime.gui.timers.col.name"), listX + 6, headerRowY, COLOR_TEXT);
        if (colTimeRight - headerTextX > 30) {
            Component lengthHeader = Component.translatable("ontime.gui.timers.col.length");
            painter.text(lengthHeader, colTimeRight - painter.textWidth(lengthHeader),
                    headerRowY, COLOR_TEXT);
        }
        painter.rect(listX, headerRowY + LINE - 1, listWidth, 1, COLOR_RULE);

        if (rows.isEmpty()) {
            centered(painter, Component.translatable(model.filter().isEmpty()
                            ? "ontime.gui.timers.empty" : "ontime.gui.timers.no_match"),
                    listX + listWidth / 2,
                    (contentTop + SEARCH_HEIGHT + contentBottom) / 2 - painter.lineHeight() / 2, COLOR_TEXT);
            // The divider stays. An empty tab that keeps its frame reads as
            // "nothing yet"; one that loses it reads as a screen half drawn,
            // and the executions tab already keeps its own.
            drawDivider(painter);
            return;
        }

        for (int[] mark : timerMarks) {
            painter.rect(mark[0], mark[1], MARK_WIDTH, ROW_HEIGHT, mark[2]);
        }
        int textX = listX + nameWidth + 4 + 3 * (actionWidth + 4) + 6;
        boolean room = colTimeRight - textX > 30;
        for (int i = 0; i < timerData.size(); i++) {
            if (!room) break;
            AdminModel.TimerRow row = timerData.get(i);
            int y = timerMarks.get(i)[1] + (ROW_HEIGHT - 8) / 2;
            Component length = Component.literal(arrow(row.countUp()) + " "
                    + com.mateof24.render.ClientTimerState.formatTicks(row.targetTicks()));
            painter.text(length, colTimeRight - painter.textWidth(length), y, COLOR_TEXT);
        }

        int listRight = twoColumn ? detailX - GUTTER / 2 : width;
        drawScrollbar(painter, (listX + listWidth + listRight) / 2 - 1,
                contentTop + SEARCH_HEIGHT, contentBottom, rows.size(), timerRowsShown);

        drawDivider(painter);
    }

    /** The selected timer's own settings, grouped, beside the list. */
    private void drawQuickColumn(Painter painter) {
        int centerX = detailX + detailWidth / 2;
        boolean creating = editor.isCreating();
        AdminModel.TimerRow timer = creating ? null : model.timer(model.selectedTimer());
        centered(painter, creating
                        ? Component.translatable("ontime.gui.timers.dialog.new")
                        : timer == null
                                ? Component.translatable("ontime.gui.timers.detail.title")
                                : Component.literal(timer.name()),
                centerX, detailTitleY, COLOR_TEXT);
        painter.rect(detailX, detailRuleY, detailWidth, 1, COLOR_RULE);

        if (timer == null && !creating) {
            centered(painter, Component.translatable("ontime.gui.timers.pick_hint"), centerX,
                    (detailBodyTop + contentBottom) / 2 - painter.lineHeight() / 2, COLOR_TEXT);
            return;
        }

        boolean dirty = editor.isDirty(timer);
        if (applyButton != null) applyButton.active = dirty && editor.rejected(timer).isEmpty();
        if (discardButton != null) discardButton.active = dirty;

        drawFieldRows(painter, timer, TimerEditor.Section.QUICK, detailX, detailWidth);
    }

    /** The rail, the open page's headings, and its labels. */
    private void drawAdvanced(Painter painter) {
        AdminModel.TimerRow timer = model.timer(editor.timerName());

        Component title = Component.literal(editor.isCreating()
                ? Component.translatable("ontime.gui.timers.dialog.new").getString()
                : editor.timerName());
        painter.text(title, GUTTER, tabY + 4, COLOR_TEXT);
        int ruleY = advancedTop() - 4;
        painter.rect(GUTTER, ruleY, width - 2 * GUTTER, 1, COLOR_RULE);

        // The rail's own edge, so the two halves read as two halves. It never
        // touches the rule above it, and it stops the same distance short of
        // the bottom as it starts below the top: a line that clears one end by
        // four pixels and the other by nothing reads as a mistake.
        int inset = 4;
        painter.rect(GUTTER + RAIL_WIDTH + GUTTER / 2, ruleY + 1 + inset, 1,
                contentBottom - inset - (ruleY + 1 + inset), COLOR_RULE);

        boolean dirty = editor.isDirty(timer);
        if (applyButton != null) applyButton.active = dirty && editor.rejected(timer).isEmpty();
        if (discardButton != null) discardButton.active = dirty;

        if (editor.section() == TimerEditor.Section.COMMANDS) {
            drawCommandRows(painter, timer);
            return;
        }
        if (editor.section() == TimerEditor.Section.TRIGGERS) {
            drawTriggerRows(painter, timer);
            return;
        }
        drawFieldRows(painter, timer, editor.section(), editorFieldX,
                editorControlX + editorControlWidth - editorFieldX);
    }

    /**
     * A section's labels and its headings.
     *
     * <p>The edited mark sits inside the column with room to spare, rather
     * than against whatever divider is to its left, where it read as a
     * rendering fault.</p>
     */
    private void drawFieldRows(Painter painter, AdminModel.TimerRow timer,
                               TimerEditor.Section section, int x, int columnWidth) {
        List<TimerEditor.Entry> entries = TimerEditor.laidOut(section, editor.isCreating(), timerIsCustom(timer));
        for (int i = 0; i < editorRowsShown && detailScroll + i < entries.size(); i++) {
            TimerEditor.Entry entry = entries.get(detailScroll + i);
            int y = editorFieldTop + i * SETTING_HEIGHT;

            if (entry.isHeading()) {
                painter.text(Component.translatable("ontime.gui.editor.group." + entry.heading()),
                        x, y + 6, COLOR_TEXT);
                painter.rect(x, y + 6 + LINE - 1, columnWidth, 1, COLOR_RULE);
                continue;
            }

            TimerEditor.Field field = entry.field();
            // Red when what is typed cannot be used: Apply is off while any
            // field reads red, so one mistyped value can no longer take five
            // good ones down with it.
            if (editor.isRejected(timer, field)) {
                painter.rect(x, y + 2, MARK_WIDTH, 14, COLOR_ERROR);
            } else if (editor.isEdited(timer, field.key())) {
                painter.rect(x, y + 2, MARK_WIDTH, 14, COLOR_PAUSED);
            }
            painter.text(Component.translatable("ontime.gui.editor.field." + field.label()),
                    x + MARK_WIDTH + 5, y + 5, COLOR_TEXT);
        }

        drawScrollbar(painter, x + columnWidth + GUTTER / 2 - 1, editorFieldTop,
                contentBottom - 26, entries.size(), editorRowsShown, detailScroll);
    }

    private void drawCommandRows(Painter painter, AdminModel.TimerRow timer) {
        List<AdminModel.Scheduled> entries = timer == null ? List.of() : timer.commandList();
        if (entries.isEmpty()) {
            painter.text(Component.translatable("ontime.gui.editor.command.none"),
                    editorFieldX, editorFieldTop + 4, COLOR_TEXT);
            return;
        }

        // As wide as the widest reading and no wider. Sizing it to the longest
        // label instead left a hand's width of nothing between the two.
        int timeWidth = 0;
        for (AdminModel.Scheduled entry : entries) {
            if (entry.atSeconds() >= 0) timeWidth = Math.max(timeWidth, painter.textWidth(when(entry)));
        }
        timeWidth = Math.max(timeWidth, painter.textWidth(Component.translatable("ontime.gui.detail.on_finish")));
        int commandX = editorFieldX + timeWidth + 10;

        for (int i = 0; i < editorRowsShown && detailScroll + i < entries.size(); i++) {
            AdminModel.Scheduled entry = entries.get(detailScroll + i);
            int y = editorFieldTop + i * 20 + 5;
            // The reading in the accent colour, the command in plain white:
            // one glance finds the times, the next reads the command.
            painter.text(entry.atSeconds() < 0
                            ? Component.translatable("ontime.gui.detail.on_finish").copy()
                                    .withStyle(ChatFormatting.ITALIC)
                            : when(entry),
                    editorFieldX, y, COLOR_COOLDOWN);
            painter.text(trimmed(painter, Component.literal(entry.commands().get(0)),
                            width - GUTTER - 26 - commandX),
                    commandX, y, COLOR_TEXT);
        }

        drawScrollbar(painter, width - GUTTER - 24, editorFieldTop, contentBottom - 30,
                entries.size(), editorRowsShown, detailScroll);
    }

    /** A scheduled command's clock reading. */
    private static Component when(AdminModel.Scheduled entry) {
        return Component.literal(
                com.mateof24.render.ClientTimerState.formatTicks(entry.atSeconds() * 20L));
    }

    private static String arrow(boolean countUp) {
        return countUp ? "\u2191" : "\u2193";
    }

    private void drawSettings(Painter painter) {
        int top = settingsTop();
        List<SettingsForm.Row> rows = SettingsForm.rows(defaultsAreCustom());

        // Enabled state follows the form, not the last layout. Apply is off
        // while any field reads red: sending the batch would leave the bad one
        // as the server had it, which looks like the rest resetting themselves.
        boolean dirty = settings.isDirty(model);
        if (applyButton != null) applyButton.active = dirty && settings.rejected(model).isEmpty();
        if (discardButton != null) discardButton.active = dirty;

        for (int i = 0; i < settingsRows && scroll + i < rows.size(); i++) {
            SettingsForm.Row row = rows.get(scroll + i);
            int y = top + i * SETTING_HEIGHT;

            if (row.isHeader()) {
                painter.text(Component.translatable("ontime.gui.settings.group." + row.header()),
                        GUTTER, y + 6, COLOR_TEXT);
                painter.rect(GUTTER, y + 6 + LINE - 1, width - 2 * GUTTER, 1, COLOR_RULE);
                continue;
            }

            // Marked in the gutter the same way a running execution is:
            // colour, in the margin, no glyph. Red when what is typed cannot
            // be used at all.
            if (settings.isRejected(model, row.key())) {
                painter.rect(GUTTER - 6, y + 2, MARK_WIDTH, 14, COLOR_ERROR);
            } else if (settings.isEdited(model, row.key())) {
                painter.rect(GUTTER - 6, y + 2, MARK_WIDTH, 14, COLOR_PAUSED);
            }
            painter.text(Component.translatable("ontime.config." + snake(row.key())),
                    GUTTER, y + 5, COLOR_TEXT);
        }

        // Centred between the controls and the edge of the screen.
        drawScrollbar(painter, width - GUTTER + (GUTTER - 2) / 2, top, contentBottom,
                rows.size(), settingsRows);
    }

    /** The question and the frame; the box itself was filled before the widgets. */
    private void drawConfirm(Painter painter) {
        int x = dialogX(), y = dialogY();
        painter.outline(x, y, DIALOG_WIDTH, dialogHeight(), COLOR_RULE);

        if (isTimerDialog()) {
            centered(painter, Component.translatable("ontime.gui.timers.dialog." + confirmOp,
                            Component.literal(editor.timerName() == null ? "" : editor.timerName())),
                    x + DIALOG_WIDTH / 2, y + 14, COLOR_TEXT);
            if ("delete".equals(confirmOp)) {
                centered(painter, Component.translatable("ontime.gui.timers.dialog.delete.body"),
                        x + DIALOG_WIDTH / 2, y + 34, COLOR_ERROR);
            }
            if ("start".equals(confirmOp)) {
                centered(painter, Component.translatable("ontime.gui.timers.dialog.start.body"),
                        x + DIALOG_WIDTH / 2, y + 28, COLOR_MUTED_TEXT);
            }
            return;
        }

        boolean exiting = CONFIRM_EXIT.equals(confirmOp);
        boolean resetting = "config.reset".equals(confirmOp);
        String title = exiting ? "ontime.gui.confirm.exit.title"
                : resetting ? "ontime.gui.confirm.reset.title"
                : "ontime.gui.confirm.stop_all.title";
        centered(painter, Component.translatable(title), x + DIALOG_WIDTH / 2, y + 16, COLOR_TEXT);

        Component body = exiting
                ? Component.translatable("ontime.gui.confirm.exit.body",
                        settings.pendingCount() + editor.pendingCount())
                : resetting
                        ? Component.translatable("ontime.gui.confirm.reset.body")
                        : Component.translatable("ontime.gui.confirm.stop_all.body", model.runs().size());
        centered(painter, body, x + DIALOG_WIDTH / 2, y + 34, COLOR_TEXT);
    }

    private void centered(Painter painter, Component text, int centerX, int y, int argb) {
        painter.text(text, centerX - painter.textWidth(text) / 2, y, argb);
    }

    private void drawRuns(Painter painter) {
        List<AdminModel.RunRow> rows = model.runs();

        // The column header: says what each field is, once, instead of
        // repeating a label on every row. Drawn even with nothing in the list,
        // together with the divider and the detail column's own heading —
        // an empty panel that keeps its frame reads as "nothing yet" rather
        // than as a screen that failed to load.
        painter.text(Component.translatable("ontime.gui.runs.col.timer"), colName, headerRowY, COLOR_TEXT);
        painter.text(Component.translatable("ontime.gui.runs.col.audience"), colAudience, headerRowY, COLOR_TEXT);
        Component timeHeader = Component.translatable("ontime.gui.runs.col.time");
        painter.text(timeHeader, colTimeRight - painter.textWidth(timeHeader), headerRowY, COLOR_TEXT);
        painter.rect(listX, headerRowY + LINE - 1, listWidth, 1, COLOR_RULE);

        if (rows.isEmpty()) {
            // Dead centre of the list column, the same treatment the detail
            // column gets when nothing is selected.
            centered(painter, Component.translatable("ontime.gui.runs.empty"),
                    listX + listWidth / 2,
                    (listTop + listBottom) / 2 - painter.lineHeight() / 2, COLOR_TEXT);
            drawDivider(painter);
            drawDetail(painter);
            return;
        }

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
            // Coloured by the same rule as the counter on screen, because
            // the operator is looking at both and they are the same clock.
            Component reading = clockWithArrow(row);
            painter.text(reading, colTimeRight - painter.textWidth(reading), y,
                    0xFF000000 | clock.color(row));
        }

        // Centred in the space between the list and whatever bounds it on the
        // right — the divider in two columns, the screen edge in one.
        int listRight = twoColumn ? detailX - GUTTER / 2 : width;
        drawScrollbar(painter, (listX + listWidth + listRight) / 2 - 1, listTop, listBottom,
                rows.size(), visibleRows);

        drawDivider(painter);
        drawDetail(painter);
    }

    private void drawDivider(Painter painter) {
        if (twoColumn) {
            painter.rect(detailX - GUTTER / 2, dividerTop, 1, contentBottom - dividerTop, COLOR_RULE);
        } else {
            painter.rect(GUTTER, detailTop - 8, width - 2 * GUTTER, 1, COLOR_RULE);
        }
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

        AdminModel.TimerRow timer = model.timerOf(row);
        int y = detailBodyTop;
        painter.text(Component.literal(row.timerName()), detailX, y, COLOR_TEXT);
        drawState(painter, row, y);

        painter.text(Component.translatable("ontime.gui.runs.detail.audience",
                        audienceOf(row),
                        Component.translatable(row.each() ? "ontime.mode.each" : "ontime.mode.shared")),
                detailX, y + 14, COLOR_TEXT);

        painter.text(Component.translatable("ontime.gui.runs.detail.clock",
                        com.mateof24.render.ClientTimerState.formatTicks(clock.ticks(row)),
                        com.mateof24.render.ClientTimerState.formatTicks(row.targetTicks()),
                        arrowOf(row),
                        Component.translatable(row.countUp()
                                ? "ontime.mode.countup" : "ontime.mode.countdown")),
                detailX, y + 14 + LINE, COLOR_TEXT);

        painter.text(repeatLine(row, timer), detailX, y + 14 + 2 * LINE, COLOR_TEXT);
        painter.text(Component.translatable("ontime.gui.runs.detail.id", row.runId().substring(0, 8)),
                detailX, y + 14 + 3 * LINE, COLOR_TEXT);

        int next = drawAudienceList(painter, row);
        drawCommands(painter, timer, next);
    }

    /**
     * The state, and — while a cooldown is running — how much of it is left.
     *
     * <p>The countdown ticks here and only here. The line below says what the
     * cooldown is set to, which is a property of the timer and does not move;
     * putting a moving number there too would be two clocks saying almost the
     * same thing.</p>
     */
    private void drawState(Painter painter, AdminModel.RunRow row, int y) {
        Component state = Component.translatable(stateKey(row));
        int right = detailX + detailWidth;
        int color = stateColor(row);

        if (row.inCooldown() && row.cooldownRemaining() > 0) {
            Component left = Component.literal(
                    com.mateof24.render.ClientTimerState.formatTicks(clock.cooldownTicks(row)));
            painter.text(left, right - painter.textWidth(left), y, COLOR_COOLDOWN);
            right -= painter.textWidth(left) + 5;
        }
        painter.text(state, right - painter.textWidth(state), y, color);
    }

    /** What happens when this run ends: repeat, hand over, or nothing. */
    private Component repeatLine(AdminModel.RunRow row, AdminModel.TimerRow timer) {
        if (timer == null) return Component.translatable("ontime.gui.runs.detail.repeat.off");

        if (timer.nextTimer() != null && !timer.nextTimer().isEmpty()) {
            return Component.translatable("ontime.gui.runs.detail.next",
                    timer.nextTimer(), seconds(timer.sequenceCooldownTicks()));
        }
        if (!timer.repeat()) return Component.translatable("ontime.gui.runs.detail.repeat.off");
        if (timer.repeatsForever()) {
            return Component.translatable("ontime.gui.runs.detail.repeat.forever",
                    seconds(timer.repeatCooldownTicks()));
        }
        return Component.translatable("ontime.gui.runs.detail.repeat.count",
                timer.repeatCount(), seconds(timer.repeatCooldownTicks()));
    }

    private static String seconds(long ticks) {
        return com.mateof24.render.ClientTimerState.formatTicks(ticks);
    }

    /**
     * Who exactly is watching, one per line, under the actions.
     *
     * <p>Only for an audience there is something to list: a global execution
     * reaches whoever is connected, and naming them would be a snapshot that
     * stops being true the moment somebody joins. The column header on the
     * left keeps saying "Seen by" either way.</p>
     */
    private int drawAudienceList(Painter painter, AdminModel.RunRow row) {
        int top = actionsTop() + 2 * 22 + 8;
        if (row.audienceGlobal() || row.audienceNames().isEmpty()) return top;

        if (top + 2 * LINE > contentBottom) return top;

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
            return firstY + (shown + 1) * LINE + SECTION_GAP;
        }
        return firstY + shown * LINE + SECTION_GAP;
    }

    /**
     * What this timer will run, and when.
     *
     * <p>Two groups, because they are two different things and reading them as
     * one list is what made the first version awkward. A scheduled command has
     * a clock reading, and the readings line up in a column of their own so the
     * eye can run down them. A finish command has no reading — "at zero" is not
     * even true for a count-up — so rather than invent one and repeat it on
     * every row, they sit under a line that says once when they happen.</p>
     */
    private void drawCommands(Painter painter, AdminModel.TimerRow timer, int top) {
        if (timer == null || !timer.hasCommands()) return;
        if (top + 2 * LINE > contentBottom) return;

        painter.text(Component.translatable("ontime.gui.detail.commands_heading"), detailX, top, COLOR_TEXT);
        painter.rect(detailX, top + LINE - 1, detailWidth, 1, COLOR_RULE);

        int y = top + LINE + 4;
        int indent = detailX + 4;

        // The time column is as wide as the widest reading, so short and long
        // ones share an edge instead of each starting wherever they happen to.
        int timeWidth = painter.textWidth(Component.translatable("ontime.gui.detail.on_finish"));
        for (AdminModel.Scheduled entry : timer.scheduled()) {
            timeWidth = Math.max(timeWidth, painter.textWidth(atLabel(entry)));
        }
        int commandX = indent + timeWidth + 10;

        for (AdminModel.Scheduled entry : timer.scheduled()) {
            Component at = atLabel(entry);
            for (String command : entry.commands()) {
                if (y + LINE > contentBottom) {
                    painter.text(Component.translatable("ontime.gui.detail.more", 1), indent, y, COLOR_TEXT);
                    return;
                }
                // The reading in the accent colour, the command in plain white:
                // one glance finds the times, the next reads the command.
                painter.text(at, indent, y, COLOR_COOLDOWN);
                painter.text(trimmed(painter, Component.literal(command), detailX + detailWidth - commandX),
                        commandX, y, COLOR_TEXT);
                y += LINE;
            }
        }

        if (timer.finishCommands().isEmpty()) return;
        if (y + 2 * LINE > contentBottom) return;

        // A gap only when there is something above to be separated from.
        if (!timer.scheduled().isEmpty()) y += 3;

        // The finish commands line up in the same two columns as the timed
        // ones, with the marker where a reading would be. Two lists that read
        // as one list, which is what they are.
        Component marker = Component.translatable("ontime.gui.detail.on_finish")
                .copy().withStyle(ChatFormatting.ITALIC);
        boolean first = true;
        for (String command : timer.finishCommands()) {
            if (y + LINE > contentBottom) {
                painter.text(Component.translatable("ontime.gui.detail.more", 1), indent, y, COLOR_TEXT);
                return;
            }
            if (first) painter.text(marker, indent, y, COLOR_COOLDOWN);
            first = false;
            painter.text(trimmed(painter, Component.literal(command),
                            detailX + detailWidth - commandX),
                    commandX, y, COLOR_TEXT);
            y += LINE;
        }
    }

    private static Component atLabel(AdminModel.Scheduled entry) {
        return Component.literal(
                com.mateof24.render.ClientTimerState.formatTicks(entry.atSeconds() * 20L));
    }

    /** A command is arbitrarily long; the column is not. */
    private Component trimmed(Painter painter, Component text, int limit) {
        String plain = text.getString();
        if (painter.textWidth(Component.literal(plain)) <= limit) return text;
        StringBuilder out = new StringBuilder();
        for (char c : plain.toCharArray()) {
            if (painter.textWidth(Component.literal(out.toString() + c + "...")) > limit) break;
            out.append(c);
        }
        return Component.literal(out + "...");
    }

    /**
     * The clock with the direction arrow {@code /timer list} uses, so the way
     * a countdown is going reads without spending a word on it.
     */
    private Component clockWithArrow(AdminModel.RunRow row) {
        return Component.literal(arrowOf(row) + " "
                + com.mateof24.render.ClientTimerState.formatTicks(clock.ticks(row)));
    }

    /**
     * A plain bar saying how far down a list you are.
     *
     * <p>It replaces a line of text that read "1-11 of 23", which told you the
     * arithmetic but not the thing you wanted to know: that there is more below
     * and roughly how much. A bar says both at a glance, and takes no row away
     * from the list to do it.</p>
     */
    private void drawScrollbar(Painter painter, int x, int top, int bottom, int total, int shown) {
        drawScrollbar(painter, x, top, bottom, total, shown, scroll);
    }

    /**
     * @param at which row is at the top, which is <em>not</em> always
     *           {@link #scroll} — the timers tab has two lists at once, and a
     *           bar drawn from the wrong one never moves
     */
    private void drawScrollbar(Painter painter, int x, int top, int bottom,
                               int total, int shown, int at) {
        if (total <= shown) return;
        int height = bottom - top;
        if (height < 16) return;

        painter.rect(x, top, 2, height, COLOR_BAND);
        int thumb = Math.max(12, height * shown / total);
        int travel = height - thumb;
        int offset = travel * Math.max(0, Math.min(total - shown, at)) / (total - shown);
        painter.rect(x, top + offset, 2, thumb, COLOR_RULE);
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
     * A key, before vanilla sees it.
     *
     * @return true when the completion list took it, so the screen stops there
     */
    public boolean keyPressed(int keyCode) {
        return assist.keyPressed(keyCode);
    }

    /**
     * A click, before vanilla sees it.
     *
     * <p>The completion list is drawn rather than built, so it is not in the
     * widget list and would otherwise never be asked. Asking it first is also
     * what makes it safe for it to overlap a field: the row on top wins.</p>
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (assist.mouseClicked(mouseX, mouseY)) return true;
        if (button != 1) return false;

        // Right-click walks a cycle the other way. A button that only goes
        // forwards means overshooting costs a lap of everything, and with ten
        // position presets that is nine clicks to undo one.
        for (Map.Entry<AbstractWidget, Runnable> entry : cycleBack.entrySet()) {
            AbstractWidget widget = entry.getKey();
            if (!widget.visible || !widget.active) continue;
            if (mouseX < widget.getX() || mouseX >= widget.getX() + widget.getWidth()
                    || mouseY < widget.getY() || mouseY >= widget.getY() + widget.getHeight()) {
                continue;
            }
            // The click sound too: without it a right-click feels like a
            // button that did not take, which is the one thing it must not.
            widget.playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            entry.getValue().run();
            return true;
        }
        return false;
    }

    /** Mouse wheel: the list first while it is up, then the tab's own list. */
    public boolean mouseScrolled(double amount) {
        if (assist.mouseScrolled(amount)) return true;

        int total;
        int shown;
        if (model.tab() == AdminModel.Tab.RUNS) {
            total = model.runs().size();
            shown = visibleRows;
        } else if (model.tab() == AdminModel.Tab.SETTINGS) {
            total = SettingsForm.rows(defaultsAreCustom()).size();
            shown = settingsRows;
        } else if (model.tab() == AdminModel.Tab.TIMERS) {
            if (editor.advanced()) {
                int rows = editor.section() == TimerEditor.Section.COMMANDS
                        ? commandCount() : TimerEditor.laidOut(editor.section(), editor.isCreating(),
                        timerIsCustom(model.timer(editor.timerName()))).size();
                if (rows <= editorRowsShown) return false;
                int before = detailScroll;
                detailScroll = Math.max(0, Math.min(rows - editorRowsShown,
                        detailScroll - (int) Math.signum(amount)));
                if (detailScroll == before) return false;
                init();
                return true;
            }
            // Two columns again, so the wheel goes to whichever the pointer
            // is over. Anything else guesses, and guesses wrong.
            // Creating counts as having the column open: there is no selected
            // timer then, and the guard used to say there was nothing to
            // scroll, so the wheel did nothing on the creation page.
            if (twoColumn && pointerX >= detailX - GUTTER / 2
                    && (model.selectedTimer() != null || editor.isCreating())) {
                int rows = TimerEditor.laidOut(TimerEditor.Section.QUICK, editor.isCreating(),
                timerIsCustom(model.timer(model.selectedTimer()))).size();
                if (rows <= editorRowsShown) return false;
                int before = detailScroll;
                detailScroll = Math.max(0, Math.min(rows - editorRowsShown,
                        detailScroll - (int) Math.signum(amount)));
                if (detailScroll == before) return false;
                init();
                return true;
            }
            total = model.filteredTimers().size();
            shown = timerRowsShown;
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

    /**
     * Keeps the runs list from scrolling past its end.
     *
     * <p>Only the runs list: every tab shares one scroll position, and this
     * used to run on every layout regardless of which tab was up. With a
     * handful of executions and a screenful of rows the runs list is not
     * scrollable at all, so it pinned the position at zero — and since a wheel
     * turn lays the panel out again, the settings list could never move off
     * its first row. That is what made the sound settings unreachable rather
     * than merely off screen. Each tab's builder clamps its own list.</p>
     */
    /** Hours, minutes and seconds of the adding row, as one number. */
    private long atSeconds() {
        long total = 0;
        long[] scale = {3600L, 60L, 1L};
        for (int i = 0; i < atFields.size() && i < 3; i++) {
            String typed = atFields.get(i).getValue().trim();
            if (typed.isEmpty()) continue;
            try {
                total += Long.parseLong(typed) * scale[i];
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return total;
    }

    private int commandCount() {
        AdminModel.TimerRow timer = model.timer(editor.timerName());
        return timer == null ? 0 : timer.commandList().size();
    }

    private void clampScroll() {
        if (model.tab() != AdminModel.Tab.RUNS) return;
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
        SettingsForm.Result result = settings.build(model);
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
        awaitingApply = !result.requests().isEmpty();
        init();
    }

    /** Tells the server the panel is gone, so it stops pushing state. */
    public void onClosed() {
        JsonObject request = new JsonObject();
        request.addProperty("op", "panel.close");
        host.sendAction(request.toString());
    }
}
