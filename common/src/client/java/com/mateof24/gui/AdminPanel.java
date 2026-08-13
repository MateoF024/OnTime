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
    /** How many rows the band spends fading, and how far past itself it runs. */
    private static final int BAND_FADE = 14;
    private static final int BAND_OVERHANG = 8;
    /** Drawn over the world after the widgets, so it has to carry on its own. */
    private static final int COLOR_RULE = 0x70FFFFFF;
    private static final int COLOR_SCRIM = 0xC0000000;

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
    /**
     * Every warning this panel can raise, and where each one is answered.
     *
     * <p>{@code confirmOp} holds which one is up, or null for none. All of
     * them draw the same way — the screen dims, a title, a body, and vanilla
     * buttons along the bottom — and they fall into two shapes:</p>
     *
     * <ul>
     *   <li><b>Asking for something</b>, listed by {@link #isTimerDialog()}:
     *       {@code clone}, {@code start}, {@code delete}. These carry fields,
     *       are built by {@link #buildTimerDialog()} and answered by
     *       {@link #submitTimerDialog()}.</li>
     *   <li><b>Only confirming</b>: {@link #CONFIRM_EXIT}, {@code config.reset},
     *       {@code run.stopAll}, {@code run.stop} and {@code timer.stop}. These
     *       carry nothing, and the accept button at the foot of
     *       {@link #buildConfirm()} does what their name says.</li>
     * </ul>
     *
     * <p>The two stops are one warning with two subjects: {@code run.stop}
     * ends the chosen execution, {@code timer.stop} ends every execution of
     * the chosen timer, and {@link #stopSubject()} and {@link #stopCount()}
     * are the whole difference.</p>
     *
     * <p>One more lives outside this class: the placement screen's, in
     * {@code PositionPicker.drawDialog}. It is drawn the same way and its
     * buttons are placed by the screen that owns it.</p>
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

    /**
     * What has been typed into the boxes that are rebuilt on every layout.
     *
     * <p>A resize rebuilds every widget, and minimising the game is a resize.
     * Anything kept only in the box itself was gone by the time the window
     * came back — so it is kept here and put back as the box is made, which
     * is what the trigger builder's boxes have always done.</p>
     */
    private final String[] atText = {"", "", ""};
    private String commandText = "";
    private String commandWaitText = "0";
    private final List<String> dialogText = new ArrayList<>();
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

    /**
     * What the runs page is built from, as far as its widgets care.
     *
     * <p>Which executions exist and which state each is in. Not their clocks:
     * those are drawn from the model every frame and never decide what a
     * widget is.</p>
     */
    private List<String> runShape() {
        List<String> out = new ArrayList<>();
        for (AdminModel.RunRow run : model.runs()) {
            out.add(run.runId() + " " + run.running() + " " + run.phase() + " " + run.timerName());
        }
        out.add("sel:" + model.selectedRunId());
        return out;
    }

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
        List<String> before = runShape();
        model.apply(state);
        clock.onSnapshot(model.runs());
        // Only when the list itself changed. A snapshot arrives every second
        // and almost all of them differ by a clock reading alone, which the
        // draw pass reads live; rebuilding for those threw away every widget
        // once a second, and a tooltip counting down to its own appearance
        // started over each time — which is why it blinked in step with the
        // timer.
        if (model.tab() == AdminModel.Tab.RUNS) {
            if (!before.equals(runShape())) init();
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
            // A little under half, not a little over: the right column
            // carries the whole of a timer and the left carries a name and
            // three buttons, so the pixels are worth more on the right. The
            // gaps between everything on the left are constants, so narrowing
            // this narrows the rows rather than spreading them.
            listWidth = (int) ((width - 3 * GUTTER) * 0.52f);
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

        // Room at the foot of the list for the button that stops everything
        // in it. Kept whether anything is running or not, so the list does not
        // grow a row and lose it again as the last execution ends.
        if (model.tab() == AdminModel.Tab.RUNS) listBottom -= 26;

        visibleRows = Math.max(1, (listBottom - listTop + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
        clampScroll();

        host.clearWidgets();
        // Whatever was bound belonged to a box that has just been thrown away.
        // The page that wants one binds it again as it builds.
        host.bindCommandField(null);
        rowMarks.clear();
        rowData.clear();
        assist.clear();
        assist.setHost(host);
        // Refreshed every layout: a timer created a second ago should be
        // offered a second ago.
        assist.setTimerNames(model.timers().stream().map(AdminModel.TimerRow::name).toList());
        assist.setAdvancementIds(model.advancements());
        assist.setDimensionIds(model.dimensions());
        assist.setPlayerNames(model.players().stream().map(AdminModel.PlayerRow::name).toList());
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
                    model.select(null);
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
                        model.toggleTimer(row.name());
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
            // Stopping from here ends every execution of this timer and
            // stopping from the runs page ends one; they are the same warning
            // with a different subject, so they are one warning.
            String[] ops = {running ? "stop" : "start", "clone", "delete"};
            for (int a = 0; a < ops.length; a++) {
                String op = ops[a];
                host.addWidget(Button.builder(Component.translatable("ontime.gui.timers.action." + op),
                                b -> {
                                    model.select(row.name());
                                    editor.open(row.name());
                                    openDialog("stop".equals(op) ? "timer.stop" : op);
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

        // While a timer is being filled in there is nothing to discard back
        // to: the button leaves the creation instead, which is why it is
        // called Cancel there and is always available. Discarding an existing
        // timer's edits only makes sense while there are any.
        boolean creating = editor.isCreating();
        Button discard = Button.builder(Component.translatable(creating
                                ? "gui.cancel" : "ontime.gui.settings.discard"),
                        b -> {
                            if (creating) {
                                editor.close();
                                model.select(null);
                            } else {
                                editor.discard();
                            }
                            model.clearMessage();
                            init();
                        })
                .bounds(startX + 2 * (buttonWidth + 6), footerY, buttonWidth, 20)
                .build();
        discard.active = creating || editor.isDirty(timer);
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

        // No Apply and no Discard. Both pages here are lists, and a list acts
        // the moment a button on it is pressed: there is nothing pending to
        // apply and nothing to throw away. They belong to the column beside
        // the list, which is where the values are.
        applyButton = null;
        discardButton = null;

        editorFieldX = railX + RAIL_WIDTH + GUTTER + 6;
        editorFieldTop = advancedTop();
        editorControlWidth = Math.min(180, (width - editorFieldX - GUTTER) / 2);
        editorControlX = width - GUTTER - editorControlWidth;

        if (editor.section() == TimerEditor.Section.COMMANDS) {
            buildCommandRows(timer, contentBottom - 62);
        } else if (editor.section() == TimerEditor.Section.TRIGGERS) {
            buildTriggerRows(timer, contentBottom - 26);
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

    /**
     * The name of a field, as a label.
     *
     * <p>With the colon here rather than in each of the four language files:
     * it is punctuation this panel puts on every field name, not part of what
     * any of them is called.</p>
     */
    private static Component labelled(String key) {
        return Component.translatable(key).copy().append(":");
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
    /** The kinds the server accepts, in the order the builder offers them. */
    private static final String[] TRIGGER_KINDS = {
            "player_join", "player_leave", "player_death", "player_respawn",
            "dimension_change", "advancement", "ftb_quest", "ftb_reward",
            "scoreboard", "expression"};

    /** How many of the watched players it takes, and who they are. */
    private static final String[] QUANTIFIERS = {"any", "all", "at_least"};
    private static final String[] SCOPES = {"audience", "everyone", "players", "selector"};

    private int triggerKind = 0;
    private int triggerQuantifier = 0;
    private int triggerScope = 0;
    private EditBox triggerSubject;
    private EditBox triggerCount;
    private EditBox triggerValue;
    private EditBox triggerScore;
    private EditBox triggerTarget;


    // ------------------------------------------------------------------
    // Triggers
    // ------------------------------------------------------------------

    /**
     * The page is two fixed sections, and that is the whole of the notation.
     *
     * <p>Everything a timer can be told is either "start it when..." or "end
     * it when...", so both headings are always there whether anything is
     * under them or not. Under a heading sit branches, and any one of them
     * being true is enough; inside a branch sit conditions, and all of them
     * have to hold. Two levels, which is every combination there is.</p>
     *
     * <p>There is no rule level on screen. A rule and a branch were both an
     * "or", so the page showed one idea twice and asked which one you
     * wanted — a question with no meaning behind it.</p>
     */
    private enum Row { SECTION, BRANCH, CONDITION, NOTHING, SEPARATOR }

    /**
     * One drawn line.
     *
     * @param nodeId what a remove button on this line would take away, or null
     * @param groupId the branch a new condition on this line would join
     */
    private record TriggerLine(Row row, int depth, String nodeId, String groupId,
                               Component text, boolean startsIt) {}

    private List<TriggerLine> triggerLines(AdminModel.TimerRow timer) {
        List<TriggerLine> out = new ArrayList<>();
        for (boolean starts : new boolean[] {true, false}) {
            out.add(new TriggerLine(Row.SECTION, 0, null, null,
                    Component.translatable(starts
                            ? "ontime.gui.editor.trigger.startsIt"
                            : "ontime.gui.editor.trigger.endsIt"),
                    starts));

            boolean any = false;
            if (timer != null) {
                for (AdminModel.TimerRow.Rule rule : timer.rules()) {
                    if (rule.startsIt() != starts) continue;
                    for (AdminModel.TimerRow.Group group : rule.groups()) {
                        if (any) {
                            out.add(new TriggerLine(Row.SEPARATOR, 1, null, null,
                                    Component.translatable("ontime.gui.editor.trigger.or"), starts));
                        }
                        any = true;
                        out.add(new TriggerLine(Row.BRANCH, 1, group.id(), group.id(),
                                Component.translatable(
                                        "ontime.gui.editor.trigger.group." + group.mode(),
                                        group.count()),
                                starts));
                        for (AdminModel.TimerRow.Trigger condition : group.conditions()) {
                            out.add(new TriggerLine(Row.CONDITION, 2, condition.id(), group.id(),
                                    describeTrigger(condition), starts));
                        }
                    }
                }
            }
            if (!any) {
                out.add(new TriggerLine(Row.NOTHING, 1, null, null,
                        Component.translatable(starts
                                ? "ontime.gui.editor.trigger.noStart"
                                : "ontime.gui.editor.trigger.noFinish"),
                        starts));
            }
        }
        return out;
    }

    /** True while the chosen kind needs no value at all. */
    private boolean kindIsBare() {
        return TRIGGER_KINDS[triggerKind].startsWith("player_");
    }

    private boolean kindIsScoreboard() {
        return "scoreboard".equals(TRIGGER_KINDS[triggerKind]);
    }

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

    // ---- the builder -------------------------------------------------

    /**
     * Which question is up, or -1 while the list is.
     *
     * <p>It never asks what the condition should do to the timer: the button
     * that opened it was under one of the two headings, and that heading is
     * the answer. Nor does it ask where it goes — the button that opened it
     * was that place.</p>
     */
    private int builderStep = -1;

    /** Fixed when it opens: the heading it was opened under. */
    private boolean builderStarts;

    /** The branch it joins, or null to open a new one. */
    private String builderGroup;

    private static final int STEP_KIND = 0;
    private static final int STEP_WHO = 1;
    private static final int STEP_HOW_MANY = 2;
    /** The last one. There is no page after it: answering it is creating it. */
    private static final int STEP_DETAILS = 3;

    /** What has been typed, kept out of the boxes, which are rebuilt constantly. */
    private String builderValue = "";
    private String builderScore = "0";
    private String builderSubject = "";
    private String builderCount = "1";

    private boolean stepIsEmpty(int step) {
        return switch (step) {
            case STEP_WHO, STEP_HOW_MANY -> !kindHasSubject();
            case STEP_DETAILS -> detailKeys().isEmpty();
            default -> false;
        };
    }

    /**
     * The next question in that direction.
     *
     * <p>-1 when there is nothing behind, which closes the builder, and past
     * {@link #STEP_DETAILS} when there is nothing ahead, which creates the
     * condition. There is no summary page in between: it was a page that
     * asked for a press and changed nothing.</p>
     */
    private int stepAfter(int step, int by) {
        int next = step + by;
        while (next >= 0 && next <= STEP_DETAILS && stepIsEmpty(next)) next += by;
        return next;
    }

    /** True when this question is the last one this kind will ask. */
    private boolean onLastStep() {
        return stepAfter(builderStep, 1) > STEP_DETAILS;
    }

    /** The boxes this kind still needs filled, in the order they are asked. */
    private List<String> detailKeys() {
        List<String> out = new ArrayList<>();
        if (kindIsScoreboard()) {
            out.add("objective");
            out.add("score");
        } else if (!kindIsBare()) {
            out.add("value");
        }
        if (kindHasSubject() && scopeNeedsValue()) out.add("subject");
        if (kindHasSubject() && countIsUsed()) out.add("count");
        return out;
    }

    private String[] stepOptions() {
        return switch (builderStep) {
            case STEP_KIND -> TRIGGER_KINDS;
            case STEP_WHO -> SCOPES;
            case STEP_HOW_MANY -> QUANTIFIERS;
            default -> new String[0];
        };
    }

    private int stepChoice() {
        return switch (builderStep) {
            case STEP_KIND -> triggerKind;
            case STEP_WHO -> triggerScope;
            case STEP_HOW_MANY -> triggerQuantifier;
            default -> -1;
        };
    }

    private void chooseStep(int index) {
        switch (builderStep) {
            case STEP_KIND -> triggerKind = index;
            case STEP_WHO -> triggerScope = index;
            case STEP_HOW_MANY -> triggerQuantifier = index;
            default -> { }
        }
    }

    private String optionKey(String option) {
        return switch (builderStep) {
            case STEP_KIND -> "ontime.trigger.kind." + option;
            case STEP_WHO -> "ontime.who.s." + option;
            default -> "ontime.who.q." + option;
        };
    }

    /** Opens on the first question, for that heading and that branch. */
    private void openBuilder(boolean starts, String groupId) {
        builderStarts = starts;
        builderGroup = groupId;
        builderValue = "";
        builderScore = "0";
        builderSubject = "";
        builderCount = "1";
        detailScroll = 0;
        builderStep = STEP_KIND;
        init();
    }

    private void closeBuilder() {
        builderStep = -1;
        builderGroup = null;
        detailScroll = 0;
        init();
    }

    private void buildTriggerRows(AdminModel.TimerRow timer, int bottom) {
        if (builderStep >= 0) {
            buildBuilder(timer, bottom);
            return;
        }

        List<TriggerLine> lines = triggerLines(timer);
        editorRowsShown = Math.max(1, (bottom - editorFieldTop) / TRIGGER_ROW);
        detailScroll = Math.max(0, Math.min(Math.max(0, lines.size() - editorRowsShown), detailScroll));

        int right = width - GUTTER;
        for (int i = 0; i < editorRowsShown && detailScroll + i < lines.size(); i++) {
            TriggerLine line = lines.get(detailScroll + i);
            int y = editorFieldTop + i * TRIGGER_ROW;

            // One button, on the line whose place it means: under a heading it
            // opens a new branch, on a branch it joins that one. Pressing it
            // starts the builder there and then -- it used to only mark the
            // spot and leave you to find a second button somewhere else.
            if (line.row() == Row.SECTION || line.row() == Row.BRANCH) {
                boolean branch = line.row() == Row.BRANCH;
                host.addWidget(Button.builder(
                                Component.translatable("ontime.gui.editor.trigger.add"),
                                b -> openBuilder(line.startsIt(), line.groupId()))
                        // The row it is on says where it adds. Saying it again
                        // on the button only makes the button longer.
                        .bounds(right - 44 - (branch ? 26 : 0), y, 44, 18)
                        .tooltip(Tooltip.create(Component.translatable(branch
                                ? "ontime.gui.editor.trigger.addToBranch.tip"
                                : "ontime.gui.editor.trigger.addBranch.tip")))
                        .build());
            }

            if (line.nodeId() == null) continue;
            host.addWidget(Button.builder(Component.translatable("ontime.gui.editor.trigger.remove"),
                            b -> {
                                JsonObject args = new JsonObject();
                                args.addProperty("name", timer.name());
                                args.addProperty("conditionId", line.nodeId());
                                send("timer.removeCondition", args);
                                awaitingApply = true;
                            })
                    .bounds(right - 20, y, 20, 18)
                    .tooltip(Tooltip.create(Component.translatable(
                            line.row() == Row.BRANCH
                                    ? "ontime.gui.editor.trigger.removeBranch.tip"
                                    : "ontime.gui.editor.trigger.remove.tip")))
                    .build());
        }
    }

    /** How many rows the page has, whichever of its two screens is up. */
    private int triggerRowCount() {
        if (builderStep < 0) return triggerLines(model.timer(editor.timerName())).size();
        if (builderStep == STEP_DETAILS) return detailKeys().size();
        return stepOptions().length;
    }

    /** One question, its answers, and the way forwards and back. */
    private void buildBuilder(AdminModel.TimerRow timer, int bottom) {
        int right = width - GUTTER;
        int top = editorFieldTop + 22;
        editorRowsShown = Math.max(1, (bottom - top) / TRIGGER_ROW);

        if (builderStep == STEP_DETAILS) {
            buildDetailFields(top, right);
        } else {
            String[] options = stepOptions();
            detailScroll = Math.max(0,
                    Math.min(Math.max(0, options.length - editorRowsShown), detailScroll));
            for (int i = 0; i < editorRowsShown && detailScroll + i < options.length; i++) {
                int index = detailScroll + i;
                Button option = Button.builder(
                                Component.translatable(optionKey(options[index])),
                                // Answering does not move on. Changing your
                                // mind a second later is the common case, and
                                // it should not cost a trip backwards.
                                b -> { chooseStep(index); init(); })
                        .bounds(editorFieldX, top + i * TRIGGER_ROW, right - editorFieldX - 2, 18)
                        .tooltip(Tooltip.create(
                                Component.translatable(optionKey(options[index]) + ".help")))
                        .build();
                // The chosen one is the one you cannot press, exactly as the
                // tab you are already on cannot be pressed. Colour is spoken
                // for: green and red mean starts and ends everywhere else.
                option.active = index != stepChoice();
                host.addWidget(option);
            }
        }

        int y = bottom + 4;
        host.addWidget(Button.builder(Component.translatable("ontime.gui.editor.trigger.back"),
                        b -> {
                            int previous = stepAfter(builderStep, -1);
                            if (previous < 0) {
                                closeBuilder();
                                return;
                            }
                            builderStep = previous;
                            detailScroll = 0;
                            init();
                        })
                .bounds(editorFieldX, y, 70, 20)
                .build());

        // One button forwards, and on the last question forwards is done.
        // A separate "create" page asked for a press and changed nothing.
        boolean last = onLastStep();
        Button next = Button.builder(
                        Component.translatable(last
                                ? "ontime.gui.editor.trigger.add"
                                : "ontime.gui.editor.trigger.next"),
                        b -> {
                            if (last) {
                                submitBuilder(timer);
                                return;
                            }
                            builderStep = stepAfter(builderStep, 1);
                            detailScroll = 0;
                            init();
                        })
                .bounds(right - 70, y, 70, 20)
                .build();
        // Set again every frame in drawBuilder: this runs once, when the page
        // is laid out, and typing into a box does not lay the page out again.
        // It was the reason a filled-in field left the button dead until you
        // went back and forward over it.
        next.active = detailsAreComplete();
        builderNext = next;
        host.addWidget(next);
    }

    /** Kept from the build pass so typing can enable or disable it. */
    private Button builderNext;

    /**
     * True once every box this kind asked for holds something it accepts.
     *
     * <p>The same rule the box tints itself by, so a red field and a dead
     * forward button always mean the same thing. Blank was the only test
     * before, which let an advancement called "asdf" through to a server that
     * would never match it.</p>
     */
    private boolean detailsAreComplete() {
        if (builderStep != STEP_DETAILS) return true;
        for (String key : detailKeys()) {
            if (!detailRule(key).test(detailText(key))) return false;
        }
        return true;
    }

    private String detailText(String key) {
        return switch (key) {
            case "objective", "value" -> builderValue;
            case "score" -> builderScore;
            case "subject" -> builderSubject;
            default -> builderCount;
        };
    }

    /**
     * What a box accepts, in one place.
     *
     * <p>Used both to tint the box and to decide whether the page can be left,
     * because two copies of a rule are two rules.</p>
     */
    private java.util.function.Predicate<String> detailRule(String key) {
        return switch (key) {
            case "score" -> FieldAssist.intBetween(-999999, 999999);
            case "count" -> FieldAssist.intBetween(1, 9999);
            case "objective" -> text -> !text.trim().isEmpty() && !text.contains(" ");
            case "subject" -> "selector".equals(SCOPES[triggerScope])
                    ? FieldAssist.selector() : FieldAssist.nameList();
            default -> switch (TRIGGER_KINDS[triggerKind]) {
                // A quest id is a hex string FTB made up, and an expression is
                // an expression: neither is a resource location.
                case "ftb_quest", "ftb_reward" -> text -> !text.trim().isEmpty();
                case "expression" -> text -> !text.trim().isEmpty();
                default -> FieldAssist.id();
            };
        };
    }

    /** Where a box's completions come from, when it has any. */
    private FieldAssist.Source detailSource(String key) {
        if ("subject".equals(key)) {
            return "players".equals(SCOPES[triggerScope])
                    ? FieldAssist.Source.PLAYERS : FieldAssist.Source.SELECTORS;
        }
        if (!"value".equals(key)) return FieldAssist.Source.NONE;
        return switch (TRIGGER_KINDS[triggerKind]) {
            case "advancement" -> FieldAssist.Source.ADVANCEMENTS;
            case "dimension_change" -> FieldAssist.Source.DIMENSIONS;
            default -> FieldAssist.Source.NONE;
        };
    }

    /**
     * Height of one row of the trigger page.
     *
     * <p>Two more than a list row elsewhere. The rows carry buttons, and at
     * twenty they sat in one unbroken column of identical grey rectangles with
     * two pixels between them.</p>
     */
    private static final int TRIGGER_ROW = 22;

    /**
     * The tree, and the shadow it carries.
     *
     * <p>White with a shadow under it, drawn the way every piece of text in
     * this panel is drawn and for the same reason: the panel floats over the
     * world, and one flat grey is legible against a bright sky or against dark
     * terrain but never against both.</p>
     */
    private static final int COLOR_TREE = 0xFFFFFFFF;
    private static final int COLOR_TREE_SHADOW = 0xFF3F3F3F;

    /** How tall one detail field is: its name, then the box under it. */
    private static final int DETAIL_HEIGHT = 32;

    /**
     * The boxes this kind needs, each one under its own name.
     *
     * <p>Above rather than beside: a name beside a box has only the room the
     * box leaves it, and "Names, separated by commas" ran straight under the
     * box and out the other side.</p>
     */
    private void buildDetailFields(int top, int right) {
        triggerTarget = null;
        triggerValue = null;
        triggerScore = null;
        triggerSubject = null;
        triggerCount = null;

        List<String> keys = detailKeys();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            int y = top + i * DETAIL_HEIGHT + 11;
            EditBox box = new EditBox(host.font(), editorFieldX, y,
                    Math.max(80, right - editorFieldX - 2), 18,
                    Component.translatable(detailLabelKey(key)));
            box.setMaxLength("value".equals(key) ? 256 : 64);
            // The example goes in the box, where an empty box needs it, rather
            // than into the name, which then stops being a name.
            String hint = detailHintKey(key);
            if (hint != null) box.setHint(Component.translatable(hint));
            host.addWidget(box);

            box.setValue(detailText(key));
            switch (key) {
                case "objective", "value" -> {
                    box.setResponder(text -> builderValue = text);
                    triggerValue = box;
                }
                case "score" -> {
                    box.setResponder(text -> builderScore = text);
                    triggerScore = box;
                }
                case "subject" -> {
                    box.setResponder(text -> builderSubject = text);
                    triggerSubject = box;
                }
                default -> {
                    box.setResponder(text -> builderCount = text);
                    triggerCount = box;
                }
            }
            assist.add(box, detailRule(key), detailSource(key));
        }
    }

    /** The name of a field, as a label: it ends in a colon, like every label. */
    private Component detailLabel(String key) {
        return Component.translatable(detailLabelKey(key)).copy().append(":");
    }

    /** What an empty box shows, when an example says more than the name can. */
    private String detailHintKey(String key) {
        return switch (key) {
            case "subject" -> "selector".equals(SCOPES[triggerScope])
                    ? "ontime.gui.editor.trigger.selector.hint"
                    : "ontime.gui.editor.trigger.names.hint";
            default -> null;
        };
    }

    private String detailLabelKey(String key) {
        return switch (key) {
            case "objective" -> "ontime.gui.editor.trigger.objective";
            case "score" -> "ontime.gui.editor.trigger.score";
            case "count" -> "ontime.gui.editor.trigger.count";
            case "subject" -> "selector".equals(SCOPES[triggerScope])
                    ? "ontime.gui.editor.trigger.selector"
                    : "ontime.gui.editor.trigger.names";
            default -> "ontime.gui.editor.trigger.value." + TRIGGER_KINDS[triggerKind];
        };
    }

    /** The answers so far, as the trigger they describe. */
    private AdminModel.TimerRow.Trigger builderPreview() {
        return new AdminModel.TimerRow.Trigger("", TRIGGER_KINDS[triggerKind],
                kindIsBare() ? "" : builderValue.trim(), numberOr(builderScore, 0),
                kindHasSubject() ? SCOPES[triggerScope] : "audience",
                kindHasSubject() && scopeNeedsValue() ? builderSubject.trim() : "",
                kindHasSubject() ? QUANTIFIERS[triggerQuantifier] : "any",
                numberOr(builderCount, 1));
    }

    private void submitBuilder(AdminModel.TimerRow timer) {
        if (timer == null) return;
        String kind = TRIGGER_KINDS[triggerKind];
        JsonObject args = new JsonObject();
        args.addProperty("name", timer.name());
        args.addProperty("kind", kind);
        args.addProperty("action", builderStarts ? "start" : "finish");
        if (!kindIsBare()) {
            if (builderValue.isBlank()) return;
            args.addProperty("value", builderValue.trim());
        }
        if (kindIsScoreboard()) args.addProperty("threshold", numberOr(builderScore, 0));
        if (kindHasSubject()) {
            args.addProperty("quantifier", QUANTIFIERS[triggerQuantifier]);
            args.addProperty("subject", SCOPES[triggerScope]);
            if (countIsUsed()) args.addProperty("count", numberOr(builderCount, 1));
            if (scopeNeedsValue()) {
                if (builderSubject.isBlank()) return;
                args.addProperty("subjectValue", builderSubject.trim());
            }
        }
        // Into the branch whose button opened this, or a branch of its own
        // when the button was the heading's.
        if (builderGroup != null) args.addProperty("groupId", builderGroup);
        send("timer.addTrigger", args);
        awaitingApply = true;
        closeBuilder();
    }

    /** The two headings, their branches, and what is inside each one. */
    private void drawTriggerRows(Painter painter, AdminModel.TimerRow timer) {
        if (builderStep >= 0) {
            drawBuilder(painter);
            return;
        }
        List<TriggerLine> lines = triggerLines(timer);
        drawTriggerTree(painter, lines);
        for (int i = 0; i < editorRowsShown && detailScroll + i < lines.size(); i++) {
            TriggerLine line = lines.get(detailScroll + i);
            int y = editorFieldTop + i * TRIGGER_ROW;
            // Green and red mean starts and ends, here as everywhere else in
            // this panel, and they are on the two headings only -- the rows
            // under a heading already belong to it.
            int colour = switch (line.row()) {
                case SECTION -> line.startsIt() ? COLOR_RUNNING : COLOR_ERROR;
                case CONDITION -> COLOR_TEXT;
                default -> COLOR_MUTED_TEXT;
            };
            int x = editorFieldX + line.depth() * 10;
            // Whatever the buttons on this row have left, less a little air.
            int taken = switch (line.row()) {
                case SECTION -> 44;
                case BRANCH -> 66;
                case CONDITION -> 20;
                default -> 0;
            };
            elidedText(painter, line.text(), x, y + 6, width - GUTTER - taken - 6 - x, colour);
        }
        drawScrollbar(painter, width - GUTTER + (GUTTER - 2) / 2, editorFieldTop,
                editorFieldTop + editorRowsShown * TRIGGER_ROW,
                lines.size(), editorRowsShown, detailScroll);
    }

    /**
     * The lines that join a row to the row it belongs to.
     *
     * <p>Indentation alone left it to the eye to work out which conditions sat
     * under which alternative, and with a dozen of them the eye stops trying.
     * A spine runs down each heading past everything under it, and a stub
     * reaches from the spine into each row.</p>
     *
     * <p>Drawn from the visible window rather than from the whole list, so it
     * follows the scroll, and rebuilt every frame from the rows themselves, so
     * adding or removing a condition redraws it with no bookkeeping at all. A
     * spine never crosses a heading: the two headings are separate trees.</p>
     */
    /** One arm of the tree, with the shadow a glyph of text would cast. */
    private void treeLine(Painter painter, int x, int y, int width, int height) {
        painter.rect(x + 1, y + 1, width, height, COLOR_TREE_SHADOW);
        painter.rect(x, y, width, height, COLOR_TREE);
    }

    private void drawTriggerTree(Painter painter, List<TriggerLine> lines) {
        int last = Math.min(lines.size(), detailScroll + editorRowsShown);
        int sectionSpine = editorFieldX + 4;
        int branchSpine = editorFieldX + 14;

        for (int i = detailScroll; i < last; i++) {
            TriggerLine line = lines.get(i);
            int y = editorFieldTop + (i - detailScroll) * TRIGGER_ROW;
            int middle = y + 9;

            switch (line.row()) {
                case SECTION -> {
                    // Down to the last row this heading joins to, and no
                    // further: the next heading is another tree entirely.
                    // That is the last alternative, not the last condition --
                    // the conditions hang off their own alternative, and a
                    // spine carrying on past it points at nothing.
                    int end = -1;
                    for (int j = i + 1; j < last; j++) {
                        Row row = lines.get(j).row();
                        if (row == Row.SECTION) break;
                        if (row == Row.BRANCH || row == Row.NOTHING) end = j;
                    }
                    if (end < 0) break;
                    int endY = editorFieldTop + (end - detailScroll) * TRIGGER_ROW + 9;
                    // From under the heading's own text, not through it.
                    int from = y + TRIGGER_ROW - 4;
                    treeLine(painter, sectionSpine, from, 1, endY - from);
                }
                case BRANCH -> {
                    treeLine(painter, sectionSpine, middle, editorFieldX + 8 - sectionSpine, 1);
                    int end = i;
                    for (int j = i + 1; j < last; j++) {
                        if (lines.get(j).row() != Row.CONDITION) break;
                        end = j;
                    }
                    if (end == i) break;
                    int endY = editorFieldTop + (end - detailScroll) * TRIGGER_ROW + 9;
                    int from = y + TRIGGER_ROW - 4;
                    treeLine(painter, branchSpine, from, 1, endY - from);
                }
                case CONDITION ->
                        treeLine(painter, branchSpine, middle, editorFieldX + 18 - branchSpine, 1);
                case NOTHING ->
                        treeLine(painter, sectionSpine, middle, editorFieldX + 8 - sectionSpine, 1);
                // The "or" names the spine it sits on; a stub into it would be
                // pointing at a word rather than joining anything.
                default -> { }
            }
        }
    }

    /** The question, how far through it is, and on the last one the whole rule. */
    private void drawBuilder(Painter painter) {
        int right = width - GUTTER;
        // Live, because typing does not lay the page out again.
        if (builderNext != null) builderNext.active = detailsAreComplete();

        Component question = Component.translatable(switch (builderStep) {
            case STEP_KIND -> "ontime.gui.editor.trigger.ask.kind";
            case STEP_WHO -> "ontime.gui.editor.trigger.ask.who";
            case STEP_HOW_MANY -> "ontime.gui.editor.trigger.ask.howMany";
            default -> "ontime.gui.editor.trigger.ask.details";
        });
        painter.text(question, editorFieldX, editorFieldTop + 4, COLOR_TEXT);

        Component counter = Component.translatable("ontime.gui.editor.trigger.step",
                stepsBefore() + 1, stepsTotal());
        painter.text(counter, right - painter.textWidth(counter), editorFieldTop + 4, COLOR_MUTED_TEXT);

        int top = editorFieldTop + 22;
        drawBuilderSoFar(painter, contentBottom - 34);
        if (builderStep == STEP_DETAILS) {
            List<String> keys = detailKeys();
            for (int i = 0; i < keys.size(); i++) {
                painter.text(detailLabel(keys.get(i)),
                        editorFieldX, top + i * DETAIL_HEIGHT, COLOR_TEXT);
            }
            return;
        }
        drawScrollbar(painter, width - GUTTER + (GUTTER - 2) / 2, top,
                top + editorRowsShown * TRIGGER_ROW,
                stepOptions().length, editorRowsShown, detailScroll);
    }

    /**
     * What has been answered so far, on one line above the buttons.
     *
     * <p>The summary used to be a page of its own with a button on it, which
     * is a press that changes nothing. Here it costs no press and it is
     * visible while the answer that shapes it is still being given.</p>
     */
    private void drawBuilderSoFar(Painter painter, int y) {
        Component action = Component.translatable(builderStarts
                ? "ontime.gui.editor.trigger.startsIt"
                : "ontime.gui.editor.trigger.endsIt");
        painter.text(action, editorFieldX, y, builderStarts ? COLOR_RUNNING : COLOR_ERROR);
        int soFarX = editorFieldX + painter.textWidth(action) + 6;
        elidedText(painter, describeTrigger(builderPreview()), soFarX, y,
                width - GUTTER - 80 - soFarX, COLOR_MUTED_TEXT);
    }

    /** How many questions this kind will have asked by the one it is on. */
    private int stepsBefore() {
        int count = 0;
        for (int step = STEP_KIND; step < builderStep; step++) {
            if (!stepIsEmpty(step)) count++;
        }
        return count;
    }

    private int stepsTotal() {
        int count = 0;
        for (int step = STEP_KIND; step <= STEP_DETAILS; step++) {
            if (!stepIsEmpty(step)) count++;
        }
        return count;
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


    /**
     * One line of the commands page.
     *
     * <p>A time reading is a line of its own, and the commands that fire at it
     * hang off it — the same shape the triggers have. Every row used to repeat
     * the reading beside the command, so five commands at the end said "At the
     * end" five times and nothing said they were one batch.</p>
     *
     * @param index which entry the remove button takes, or -1 on a reading
     */
    private record CommandLine(int index, int indent, Component text, int colour) {}

    private List<CommandLine> commandLines(AdminModel.TimerRow timer) {
        List<CommandLine> out = new ArrayList<>();
        if (timer == null) return out;
        List<AdminModel.Scheduled> entries = timer.commandList();

        Long at = null;
        boolean finishSeen = false;
        for (int i = 0; i < entries.size(); i++) {
            AdminModel.Scheduled entry = entries.get(i);
            if (entry.atSeconds() < 0) {
                if (!finishSeen) {
                    finishSeen = true;
                    out.add(new CommandLine(-1, SECTION_INDENT,
                            Component.translatable("ontime.gui.detail.on_finish")
                                    .copy().withStyle(ChatFormatting.ITALIC), COLOR_COOLDOWN));
                }
            } else if (at == null || at != entry.atSeconds()) {
                at = entry.atSeconds();
                out.add(new CommandLine(-1, SECTION_INDENT, when(entry), COLOR_COOLDOWN));
            }
            for (String command : entry.commands()) {
                out.add(new CommandLine(i, BRANCH_INDENT,
                        withWait(command, entry.delayTicks()), COLOR_TEXT));
            }
        }
        return out;
    }

    /**
     * How much room the boxes take at the top of the commands page.
     *
     * <p>The command on a line of its own, then the four numbers under it,
     * each with its name above it. It sat at the foot before, which put the
     * completion list over the tree it was meant to be adding to.</p>
     */
    private static final int COMMAND_FORM_HEIGHT = 62;

    private void buildCommandRows(AdminModel.TimerRow timer, int bottom) {
        buildCommandForm(timer);

        int listTop = editorFieldTop + COMMAND_FORM_HEIGHT;
        List<CommandLine> entries = commandLines(timer);
        editorRowsShown = Math.max(1, (contentBottom - listTop) / 20);
        detailScroll = Math.max(0, Math.min(Math.max(0, entries.size() - editorRowsShown), detailScroll));

        for (int i = 0; i < editorRowsShown && detailScroll + i < entries.size(); i++) {
            final int index = entries.get(detailScroll + i).index();
            if (index < 0) continue;
            int y = listTop + i * 20;
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
    }

    /**
     * The boxes that add a command, above the list they add to.
     *
     * <p>The command on a line of its own because a command is long, then the
     * four numbers under it, each with its whole name above it rather than a
     * letter inside it. A box with its own name written in it is empty and
     * looks filled.</p>
     */
    private void buildCommandForm(AdminModel.TimerRow timer) {
        int y = editorFieldTop;
        int addWidth = 48;
        int right = width - GUTTER;

        EditBox command = new EditBox(host.font(), editorFieldX, y,
                right - addWidth - 6 - editorFieldX, 18,
                Component.translatable("ontime.gui.editor.command.text"));
        command.setMaxLength(256);
        // No hint and no completion list of our own: it is the command block's
        // field, and the first thing it does is offer every command there is.
        command.setValue(commandText);
        command.setResponder(text -> {
            commandText = text;
            host.refreshCommandField();
        });
        host.addWidget(command);
        host.bindCommandField(command);
        commandBox = command;

        // Names above, boxes below, all four on one line.
        int boxY = y + 33;
        int unit = 52;
        int gap = 8;
        for (int i = 0; i < 3; i++) {
            EditBox box = new EditBox(host.font(), editorFieldX + i * (unit + gap), boxY, unit, 18,
                    Component.translatable("ontime.gui.editor.field." + AT_UNITS[i]));
            box.setMaxLength(4);
            box.setValue(atText[i]);
            final int slot = i;
            box.setResponder(text -> atText[slot] = text);
            host.addWidget(box);
            assist.add(box, FieldAssist.intBetween(0, 9999));
            atFields.add(box);
        }

        commandWait = new EditBox(host.font(), editorFieldX + 3 * (unit + gap), boxY, unit, 18,
                Component.translatable("ontime.gui.editor.command.delay"));
        commandWait.setMaxLength(5);
        commandWait.setValue(commandWaitText);
        commandWait.setResponder(text -> commandWaitText = text);
        host.addWidget(commandWait);
        assist.add(commandWait, FieldAssist.intBetween(0, 72000), FieldAssist.Source.NONE,
                Tooltip.create(Component.translatable("ontime.gui.editor.command.delay.tip")), null);
        commandFormLabelY = y + 22;

        Button add = Button.builder(Component.translatable("ontime.gui.editor.command.add"), b -> {
                    if (timer == null || command.getValue().isBlank()) return;
                    JsonObject args = new JsonObject();
                    args.addProperty("name", timer.name());
                    args.addProperty("command", command.getValue().trim());
                    args.addProperty("delayTicks", numberOr(
                            commandWait == null ? "0" : commandWait.getValue(), 0));
                    // All three boxes empty means a finish command, which is
                    // what empty should mean here: "when it ends".
                    long at = atSeconds();
                    if (at > 0) args.addProperty("atSeconds", at);
                    send("timer.addCommand", args);
                    awaitingApply = true;
                    // Emptied only once it has gone somewhere, so a rebuild in
                    // between never looks like a successful add.
                    commandText = "";
                    commandWaitText = "0";
                    for (int i = 0; i < atText.length; i++) atText[i] = "";
                })
                .bounds(right - addWidth, y, addWidth, 18)
                .tooltip(Tooltip.create(Component.translatable("ontime.gui.editor.command.add.tip")))
                .build();
        // Dead until the dispatcher would accept it, so a typo is refused here
        // rather than at the moment the timer ends and nobody is watching.
        commandAdd = add;
        add.active = CommandField.parses(command.getValue());
        host.addWidget(add);
    }

    /** Where the four names are drawn, kept from the build pass. */
    private int commandFormLabelY = -1;


    /** Kept from the build pass, because typing does not lay the page out again. */
    private Button commandAdd;
    private EditBox commandBox;
    private EditBox commandWait;

    /** Whether the command box has been typed into since it took the caret. */
    private boolean commandTyped;

    /** The whole name of each box, above it, in the reading order of the row. */
    private void drawCommandFormLabels(Painter painter) {
        if (commandFormLabelY < 0) return;
        int unit = 52;
        int gap = 8;
        for (int i = 0; i < 3; i++) {
            painter.text(Component.translatable("ontime.gui.editor.field." + AT_UNITS[i]),
                    editorFieldX + i * (unit + gap), commandFormLabelY, COLOR_TEXT);
        }
        painter.text(Component.translatable("ontime.gui.editor.command.delay"),
                editorFieldX + 3 * (unit + gap), commandFormLabelY, COLOR_TEXT);
    }

    /**
     * The arms joining each command to the reading it fires at.
     *
     * <p>The same drawing the trigger page and the execution column do, so a
     * command under a time and a condition under an alternative are the same
     * picture in three places.</p>
     */
    private void drawCommandTree(Painter painter, List<CommandLine> lines, int last) {
        int listTop = editorFieldTop + COMMAND_FORM_HEIGHT;
        for (int i = detailScroll; i < last; i++) {
            if (lines.get(i).index() >= 0) continue;

            int spine = editorFieldX + SECTION_INDENT + 4;
            int end = -1;
            for (int j = i + 1; j < last; j++) {
                if (lines.get(j).index() < 0) break;
                end = j;
            }
            if (end < 0) continue;

            int from = listTop + (i - detailScroll) * 20 + 18;
            int endY = listTop + (end - detailScroll) * 20 + 9;
            treeLine(painter, spine, from, 1, endY - from);
            for (int j = i + 1; j <= end; j++) {
                treeLine(painter, spine, listTop + (j - detailScroll) * 20 + 9,
                        editorFieldX + BRANCH_INDENT - 2 - spine, 1);
            }
        }
    }

    /** Same rules as the defaults form; only the source of the value differs. */

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

    private static final int DIALOG_WIDTH = 360;
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
    /** Both ways of stopping: one execution, or every execution of a timer. */
    private boolean isStopConfirm() {
        return "run.stop".equals(confirmOp) || "timer.stop".equals(confirmOp);
    }

    /** What the warning is about: an execution's timer, or the chosen timer. */
    private String stopSubject() {
        if ("run.stop".equals(confirmOp)) {
            AdminModel.RunRow row = model.selectedRun();
            return row == null ? "" : row.timerName();
        }
        String name = editor.timerName();
        return name == null ? "" : name;
    }

    /** How many executions it ends, which is the whole difference between them. */
    private int stopCount() {
        if ("run.stop".equals(confirmOp)) return 1;
        int count = 0;
        String name = editor.timerName();
        for (AdminModel.RunRow row : model.runs()) {
            if (row.timerName().equals(name)) count++;
        }
        return count;
    }

    /**
     * Closes whatever dialog is open and forgets what was typed into it.
     *
     * <p>Cleared on the way out as well as on the way in: what a dialog
     * collected belongs to that dialog, and a later one keying off the same
     * positions would otherwise read it.</p>
     */
    private void closeDialog() {
        confirmOp = null;
        dialogText.clear();
        init();
    }

    private void openDialog(String kind) {
        confirmOp = kind;
        // A fresh dialog starts from its suggestion; only a rebuild of the one
        // already open keeps what was typed over it.
        dialogText.clear();
        dialogMode = "shared";
        dialogCountUp = false;
        dialogGlobal = true;
        model.clearMessage();
        init();
    }

    /** The dialogs that ask for something rather than just confirming. */
    private boolean isTimerDialog() {
        return "clone".equals(confirmOp) || "start".equals(confirmOp)
                || "delete".equals(confirmOp);
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

    /**
     * A name for the copy that nothing else is using.
     *
     * <p>It always said {@code name + "2"}, so copying the same timer twice
     * offered the same name twice and the second one was refused by the
     * server after the dialog had already been accepted.</p>
     */
    private String freeCopyName(String name) {
        if (name == null) return "";
        // The way a file system names a copy: the original keeps its name and
        // each copy takes the lowest free number. Lowest free, not next after
        // the highest -- with "Timer" and "Timer (2)" about, the new one is
        // "Timer (1)", because that gap is a name nothing is using.
        String base = name;
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("^(.*) \\((\\d+)\\)$").matcher(name);
        if (matcher.matches()) base = matcher.group(1);

        for (int copy = 1; copy < 1000; copy++) {
            String candidate = base + " (" + copy + ")";
            if (model.timer(candidate) == null) return candidate;
        }
        return base + " (1)";
    }

    private void buildTimerDialog() {
        int x = dialogX() + 14;
        int fieldWidth = DIALOG_WIDTH - 28;
        int y = dialogY() + 34;
        String name = model.selectedTimer();

        switch (confirmOp) {
            case "clone" -> {
                dialogFields.add(host.addWidget(field(x, y, fieldWidth,
                        "ontime.gui.timers.field.name", freeCopyName(name))));
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
                    closeDialog();
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
        // The suggestion only the first time. After that it is whatever was
        // typed over it, which a rebuild must not throw away.
        int slot = dialogFields.size();
        while (dialogText.size() <= slot) dialogText.add(null);
        box.setValue(dialogText.get(slot) == null ? initial : dialogText.get(slot));
        box.setResponder(text -> dialogText.set(slot, text));
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
            case "clone" -> {
                args.addProperty("name", name);
                args.addProperty("dest", value(0));
                confirmOp = null;
                init();
                send("timer.clone", args);
                // Without this the copy existed on the server and the list
                // went on showing what it had, until something else happened
                // to ask for a fresh one.
                awaitingApply = true;
            }
            case "delete" -> {
                args.addProperty("name", name);
                confirmOp = null;
                editor.close();
                // Off the list now, not at whatever moment the next snapshot
                // happens to arrive.
                model.forgetTimer(name);
                model.select(null);
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
            model.select(name);
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
                            if (op == null) return;
                            // One of these acts on the execution that is
                            // selected; the rest act on the server.
                            if ("run.stop".equals(op)) {
                                runAction(op);
                            } else if ("timer.stop".equals(op)) {
                                stopEveryRunOfSelected();
                            } else {
                                send(op, new JsonObject());
                            }
                        })
                .bounds(startX + buttonWidth + gap, y, buttonWidth, 20)
                .build());
    }

    /** Title on the left; everything that acts on the whole panel on the right. */
    private void buildHeader() {
        int doneWidth = 54;
        int doneX = width - GUTTER - doneWidth;
        // Inside the advanced editor there is no Exit at all: Back stands
        // where it stood, so a reflex press leaves the page rather than the
        // whole panel.
        boolean inAdvanced = model.tab() == AdminModel.Tab.TIMERS && editor.advanced();
        if (!inAdvanced) host.addWidget(Button.builder(Component.translatable("ontime.gui.exit"), b -> {
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

        if (inAdvanced) {
            int backWidth = doneWidth;
            host.addWidget(Button.builder(Component.translatable("ontime.gui.editor.back"), b -> {
                        // Back to the list, not out of the timer: what was
                        // typed here is still pending, and the column beside
                        // the list is where it gets applied.
                        editor.setAdvanced(false);
                        detailScroll = 0;
                        init();
                    })
                    .bounds(doneX, 5, backWidth, 20)
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
                    // At the foot of the list it acts on, not beside Exit. The
                // list gives up the room for it in layout, so a screen full of
                // executions cannot reach down and sit under it.
                .bounds(listX, listBottom + 6, stopAllWidth, 20)
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
        int tabWidth = Math.min(100, (available - 2 * (tabs.length - 1)) / tabs.length);
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
                        // A different execution is a different list; carrying
                        // the last one's offset over would open it partway down.
                        runDetailScroll = 0;
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
                                // Stop ends an execution and cannot be undone,
                                // so it asks — the same as Stop all, which sits
                                // two panels away and always has.
                                b -> {
                                    if ("stop".equals(op)) {
                                        confirmOp = "run.stop";
                                        init();
                                    } else {
                                        runAction("run." + op);
                                    }
                                })
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
    /**
     * The band behind the title, fading out downwards.
     *
     * <p>It has no bottom border, so a flat fill ended in a hard line across
     * the screen with nothing to explain it. Four rows of fade were no better:
     * that is still an edge, only a blurry one. It fades over fourteen now,
     * beginning above where the band ends and running on below it, and the
     * buttons still sit in solid dark because the fade starts under them.</p>
     *
     * <p>Drawn as rows rather than as a gradient call: {@link Painter} has one
     * fill and adding a gradient to it would mean a version-specific
     * implementation in each of the three screens, for something four lines of
     * arithmetic do here.</p>
     */
    private void drawHeaderBand(Painter painter) {
        int solid = HEADER_HEIGHT + BAND_OVERHANG - BAND_FADE;
        painter.rect(0, 0, width, solid, COLOR_BAND);

        int alpha = COLOR_BAND >>> 24;
        for (int row = 0; row < BAND_FADE; row++) {
            // Smoothstep: flat where it leaves the solid fill and flat again
            // where it reaches nothing, so neither end of the fade is a line.
            float at = (row + 1) / (float) BAND_FADE;
            float eased = at * at * (3f - 2f * at);
            int faded = Math.round(alpha * (1f - eased));
            painter.rect(0, solid + row, width, 1, (faded << 24) | (COLOR_BAND & 0xFFFFFF));
        }
    }

    public void drawBands(Painter painter, int mouseX, int mouseY) {
        pointerX = mouseX;
        pointerY = mouseY;
        // Before the widgets draw: the field reads its own colour and ghost
        // text as it paints itself, so deciding them afterwards would show the
        // previous frame's answer.
        assist.update(mouseX, mouseY);

        drawHeaderBand(painter);

        // The dialog's fills go here for the same reason as the band: they are
        // large and opaque, and after the widgets they would bury its buttons.
        if (confirmOp != null) {
            // The dimming and nothing else. A panel drawn on top of a screen
            // that is already dimmed to near black is a second box around
            // something that was already separate, and its edges were what
            // the text kept running into.
            painter.rect(0, 0, width, height, COLOR_SCRIM);
        }
    }

    // ==================================================================
    // Drawing, after the widgets
    // ==================================================================

    /**
     * Text and colour marks. Nothing here is large or opaque, so it cannot bury
     * a widget — and the row columns are meant to sit on top of their button.
     */
    /**
     * A line that did not fit, and the whole of what it said.
     *
     * <p>Collected while drawing rather than laid out in advance: whether a
     * line fits depends on the width it was given, and only the code that
     * draws it knows that.</p>
     */
    private record Elided(int x, int y, int width, int height, Component full) {}

    private final List<Elided> elided = new ArrayList<>();

    public void drawContent(Painter painter) {
        elided.clear();
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

        // Over everything, including the fields it overlaps.
        drawElidedTooltip(painter);
        assist.render(painter);
    }

    /**
     * Draws text, cut short if it has to be, and remembers when it was.
     *
     * <p>A line that is cut keeps a tooltip with the whole of it, which is the
     * only way a long advancement id is readable at all: the alternative is a
     * row that runs off the side of the screen, which is what it did.</p>
     */
    private void elidedText(Painter painter, Component text, int x, int y, int limit, int colour) {
        Component shown = trimmed(painter, text, limit);
        painter.text(shown, x, y, colour);
        if (shown != text) {
            elided.add(new Elided(x, y - 2, painter.textWidth(shown), painter.lineHeight() + 4, text));
        }
    }

    /**
     * The whole of a line that had to be cut, as vanilla's own tooltip.
     *
     * <p>Handed to the game rather than drawn here: it wraps, it stays on
     * screen, and it looks like every other tooltip because it is one. The
     * box this used to draw got all three of those approximately right.</p>
     */
    private void drawElidedTooltip(Painter painter) {
        for (Elided one : elided) {
            if (pointerX >= one.x() && pointerX <= one.x() + one.width()
                    && pointerY >= one.y() && pointerY <= one.y() + one.height()) {
                painter.tooltip(one.full(), pointerX, pointerY);
                return;
            }
        }
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
            painter.text(labelled("ontime.gui.editor.field." + field.label()),
                    x + MARK_WIDTH + 5, y + 5, COLOR_TEXT);
        }

        drawScrollbar(painter, x + columnWidth + GUTTER / 2 - 1, editorFieldTop,
                contentBottom - 26, entries.size(), editorRowsShown, detailScroll);
    }

    private void drawCommandRows(Painter painter, AdminModel.TimerRow timer) {
        // Live, because typing into a box does not lay the page out again.
        if (commandAdd != null && commandBox != null) {
            commandAdd.active = CommandField.parses(commandBox.getValue());
        }
        List<CommandLine> entries = commandLines(timer);
        if (entries.isEmpty()) {
            drawCommandFormLabels(painter);
            painter.text(Component.translatable("ontime.gui.editor.command.none"),
                    editorFieldX, editorFieldTop + COMMAND_FORM_HEIGHT + 4, COLOR_MUTED_TEXT);
            return;
        }
        int last = Math.min(entries.size(), detailScroll + editorRowsShown);
        drawCommandTree(painter, entries, last);
        drawCommandFormLabels(painter);

        for (int i = 0; i < editorRowsShown && detailScroll + i < entries.size(); i++) {
            CommandLine line = entries.get(detailScroll + i);
            int x = editorFieldX + line.indent();
            int y = editorFieldTop + COMMAND_FORM_HEIGHT + i * 20 + 5;
            // The reading in the accent colour, the command in plain white:
            // one glance finds the times, the next reads the command.
            elidedText(painter, line.text(), x, y, width - GUTTER - 26 - x, line.colour());
        }

        drawScrollbar(painter, width - GUTTER - 24, editorFieldTop + COMMAND_FORM_HEIGHT,
                contentBottom, entries.size(), editorRowsShown, detailScroll);

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
            painter.text(labelled("ontime.config." + snake(row.key())),
                    GUTTER, y + 5, COLOR_TEXT);
        }

        // Centred between the controls and the edge of the screen.
        drawScrollbar(painter, width - GUTTER + (GUTTER - 2) / 2, top, contentBottom,
                rows.size(), settingsRows);
    }

    /** The question and the frame; the box itself was filled before the widgets. */
    private void drawConfirm(Painter painter) {
        int x = dialogX(), y = dialogY();

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
        boolean stopping = isStopConfirm();
        String title = exiting ? "ontime.gui.confirm.exit.title"
                : resetting ? "ontime.gui.confirm.reset.title"
                : stopping ? "ontime.gui.confirm.stop.title"
                : "ontime.gui.confirm.stop_all.title";
        centered(painter, Component.translatable(title), x + DIALOG_WIDTH / 2, y + 16, COLOR_TEXT);

        Component body = exiting
                ? Component.translatable("ontime.gui.confirm.exit.body",
                        settings.pendingCount() + editor.pendingCount())
                : resetting
                        ? Component.translatable("ontime.gui.confirm.reset.body")
                        : stopping
                                ? Component.translatable("ontime.gui.confirm.stop.body",
                                        stopSubject(), stopCount())
                                : Component.translatable("ontime.gui.confirm.stop_all.body",
                                        model.runs().size());
        // Grey, with the subject and the count picked out in white by the
        // text itself: the sentence is context and the numbers are the thing
        // being agreed to.
        centered(painter, body, x + DIALOG_WIDTH / 2, y + 34, COLOR_MUTED_TEXT);
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

        drawDetailBody(painter, row, timer);
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
     * One drawn line of the scrolling half of the column.
     *
     * <p>The three sections used to draw themselves, each clipping at the
     * bottom of the pane and each ending in "+N more" — which is a way of
     * saying there is more without offering it. Built as a list instead, the
     * pane can show a window of that list, and the window can move.</p>
     *
     * @param heading true for a section name, which carries a rule under it
     */
    private record DetailLine(int indent, Component text, int colour, boolean heading) {}

    /**
     * Where a line of the detail column sits, and where its tree arm reaches.
     *
     * <p>The same two steps the trigger page uses, so a command hanging off
     * "At the end" and a condition hanging off an alternative line up.</p>
     */
    private static final int SECTION_INDENT = 6;
    private static final int INDENT_STEP = 10;
    private static final int BRANCH_INDENT = SECTION_INDENT + INDENT_STEP;

    /** How far the scrolling half has been moved, in lines. */
    private int runDetailScroll = 0;

    /** Lines shown at once, from the last layout; the wheel needs it too. */
    private int runDetailRows = 1;

    /**
     * Everything under the actions, in order, as lines.
     *
     * <p>A section that has nothing to say is not here at all — no heading, no
     * empty space. That is the same rule the audience list has always had, now
     * applied to all three.</p>
     */
    private List<DetailLine> detailLines(Painter painter, AdminModel.RunRow row,
                                         AdminModel.TimerRow timer) {
        List<DetailLine> out = new ArrayList<>();

        // ---- who is watching -----------------------------------------
        // Only for an audience there is something to list: a global execution
        // reaches whoever is connected, and naming them would be a snapshot
        // that stops being true the moment somebody joins.
        if (!row.audienceGlobal() && !row.audienceNames().isEmpty()) {
            out.add(new DetailLine(0,
                    Component.translatable("ontime.gui.detail.audience_heading"), COLOR_TEXT, true));
            out.add(new DetailLine(0, Component.empty(), COLOR_TEXT, false));
            for (String name : row.audienceNames()) {
                out.add(new DetailLine(BRANCH_INDENT, Component.literal(name), COLOR_TEXT, false));
            }
        }

        // ---- what it runs, and when ----------------------------------
        if (timer != null && timer.hasCommands()) {
            if (!out.isEmpty()) out.add(new DetailLine(0, Component.empty(), COLOR_TEXT, false));
            out.add(new DetailLine(0,
                    Component.translatable("ontime.gui.detail.commands_heading"), COLOR_TEXT, true));
            out.add(new DetailLine(0, Component.empty(), COLOR_TEXT, false));

            // A time reading, and the commands that fire at it hanging off
            // it — the same shape the triggers have, so the column reads as
            // one diagram rather than as two lists that happen to be stacked.
            Component onFinish = Component.translatable("ontime.gui.detail.on_finish")
                    .copy().withStyle(ChatFormatting.ITALIC);

            Long at = null;
            for (AdminModel.Scheduled entry : timer.scheduled()) {
                if (at == null || at != entry.atSeconds()) {
                    at = entry.atSeconds();
                    out.add(new DetailLine(SECTION_INDENT, atLabel(entry), COLOR_COOLDOWN, false));
                }
                for (String command : entry.commands()) {
                    out.add(new DetailLine(BRANCH_INDENT, withWait(command, entry.delayTicks()),
                            COLOR_TEXT, false));
                }
            }
            boolean first = true;
            for (AdminModel.Scheduled entry : timer.finishCommands()) {
                if (first) out.add(new DetailLine(SECTION_INDENT, onFinish, COLOR_COOLDOWN, false));
                first = false;
                for (String command : entry.commands()) {
                    out.add(new DetailLine(BRANCH_INDENT, withWait(command, entry.delayTicks()),
                            COLOR_TEXT, false));
                }
            }
        }

        // ---- and what starts or ends it -------------------------------
        if (timer != null && !timer.rules().isEmpty()) {
            List<TriggerLine> tree = new ArrayList<>();
            for (TriggerLine line : triggerLines(timer)) {
                if (line.row() == Row.SECTION && !sectionHasAny(timer, line.startsIt())) continue;
                if (line.row() == Row.NOTHING) continue;
                tree.add(line);
            }
            if (!tree.isEmpty()) {
                if (!out.isEmpty()) out.add(new DetailLine(0, Component.empty(), COLOR_TEXT, false));
                out.add(new DetailLine(0,
                        Component.translatable("ontime.gui.detail.triggers_heading"),
                        COLOR_TEXT, true));
                out.add(new DetailLine(0, Component.empty(), COLOR_TEXT, false));
                for (TriggerLine line : tree) {
                    int colour = switch (line.row()) {
                        case SECTION -> line.startsIt() ? COLOR_RUNNING : COLOR_ERROR;
                        case CONDITION -> COLOR_TEXT;
                        default -> COLOR_MUTED_TEXT;
                    };
                    out.add(new DetailLine(SECTION_INDENT + line.depth() * 10,
                            line.text(), colour, false));
                }
            }
        }
        return out;
    }

    /** A command, and what waits after it when anything does. */
    private static Component withWait(String command, int delayTicks) {
        Component text = Component.literal(command);
        if (delayTicks <= 0) return text;
        return text.copy().append(Component.literal("  "))
                .append(Component.translatable("ontime.gui.editor.command.waits", delayTicks)
                        .copy().withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * The window of those lines that fits, and a bar when there is more.
     *
     * <p>The bar is drawn only while something is out of view, which is what
     * makes it worth looking at: a bar that is always there says nothing, and
     * one that appears is the only notice that the list goes on.</p>
     */
    private void drawDetailBody(Painter painter, AdminModel.RunRow row, AdminModel.TimerRow timer) {
        int top = actionsTop() + 2 * 22 + 8;
        List<DetailLine> all = detailLines(painter, row, timer);
        runDetailTotal = all.size();
        runDetailRows = Math.max(1, (contentBottom - top) / LINE);
        runDetailScroll = Math.max(0, Math.min(Math.max(0, all.size() - runDetailRows), runDetailScroll));
        if (all.isEmpty()) return;

        int last = Math.min(all.size(), runDetailScroll + runDetailRows);
        drawDetailTree(painter, all, top, last);

        for (int i = 0; i < runDetailRows && runDetailScroll + i < all.size(); i++) {
            DetailLine line = all.get(runDetailScroll + i);
            int y = top + i * LINE;
            elidedText(painter, line.text(), detailX + line.indent(), y,
                    detailX + detailWidth - 8 - (detailX + line.indent()), line.colour());
            if (line.heading()) {
                painter.rect(detailX, y + LINE - 1, detailWidth - 8, 1, COLOR_RULE);
            }
        }
        drawScrollbar(painter, detailX + detailWidth - 3, top, top + runDetailRows * LINE,
                all.size(), runDetailRows, runDetailScroll);
    }

    /** Whether that heading has anything under it worth drawing. */
    private boolean sectionHasAny(AdminModel.TimerRow timer, boolean starts) {
        for (AdminModel.TimerRow.Rule rule : timer.rules()) {
            if (rule.startsIt() == starts && !rule.groups().isEmpty()) return true;
        }
        return false;
    }

    /**
     * The arms that join a line to the one it belongs to.
     *
     * <p>The same drawing the trigger page does, against the same two indents:
     * a spine down from every origin — a heading, a time reading, an
     * alternative — and a stub into each line that hangs off it.</p>
     */
    private void drawDetailTree(Painter painter, List<DetailLine> all, int top, int last) {
        for (int i = runDetailScroll; i < last; i++) {
            DetailLine line = all.get(i);
            if (line.heading() || line.text().getString().isEmpty()) continue;

            int y = top + (i - runDetailScroll) * LINE;
            int spine = detailX + line.indent() + 4;

            // Everything under this one, until something at the same level or
            // shallower — that is where this branch of the tree ends. A blank
            // line ends it too: it is the gap between two sections.
            int end = -1;
            for (int j = i + 1; j < last; j++) {
                DetailLine below = all.get(j);
                if (below.heading() || below.text().getString().isEmpty()) break;
                if (below.indent() <= line.indent()) break;
                if (below.indent() == line.indent() + INDENT_STEP) end = j;
            }
            if (end < 0) continue;

            // The spine down to the last child, and a stub into each one.
            int from = y + LINE - 3;
            int endY = top + (end - runDetailScroll) * LINE + 4;
            treeLine(painter, spine, from, 1, endY - from);
            for (int j = i + 1; j <= end; j++) {
                if (all.get(j).indent() != line.indent() + INDENT_STEP) continue;
                int childY = top + (j - runDetailScroll) * LINE + 4;
                treeLine(painter, spine, childY,
                        detailX + all.get(j).indent() - 2 - spine, 1);
            }
        }
    }

    /**
     * How many lines the scrolling half held when it was last drawn.
     *
     * <p>Kept from the draw rather than recomputed for the wheel: measuring a
     * line needs the painter, and the wheel does not have one.</p>
     */
    private int runDetailTotal = 0;


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
        // What separates "typed something" from "looked at it". Tab counts:
        // it is how a suggestion is taken, and taking one is a decision.
        if (commandBox != null && commandBox.isFocused()) commandTyped = true;
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

        // A click anywhere but in the command box lets it go, even when it
        // lands on nothing at all: vanilla only moves focus between widgets,
        // so clicking bare background left the caret — and the completion
        // list — exactly where they were.
        if (commandBox != null && commandBox.isFocused()
                && !(mouseX >= commandBox.getX()
                        && mouseX < commandBox.getX() + commandBox.getWidth()
                        && mouseY >= commandBox.getY()
                        && mouseY < commandBox.getY() + commandBox.getHeight())) {
            // Nothing typed into it means nothing to keep: opening a box and
            // leaving it is not editing, and the completion list had been
            // filling it in on the way past.
            if (!commandTyped) commandText = "";
            commandTyped = false;
            host.clearFocus();
        }

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
            // Two lists side by side, and the wheel belongs to whichever the
            // pointer is over. The detail's half scrolls without laying the
            // panel out again: nothing in it is a widget.
            if (twoColumn && pointerX >= detailX - GUTTER / 2 && model.selectedRun() != null) {
                int rows = runDetailTotal;
                if (rows <= runDetailRows) return false;
                int before = runDetailScroll;
                runDetailScroll = Math.max(0, Math.min(rows - runDetailRows,
                        runDetailScroll - (int) Math.signum(amount)));
                return runDetailScroll != before;
            }
            total = model.runs().size();
            shown = visibleRows;
        } else if (model.tab() == AdminModel.Tab.SETTINGS) {
            total = SettingsForm.rows(defaultsAreCustom()).size();
            shown = settingsRows;
        } else if (model.tab() == AdminModel.Tab.TIMERS) {
            if (editor.advanced()) {
                int rows = switch (editor.section()) {
                    case COMMANDS -> commandCount();
                    case TRIGGERS -> triggerRowCount();
                    default -> TimerEditor.laidOut(editor.section(), editor.isCreating(),
                            timerIsCustom(model.timer(editor.timerName()))).size();
                };
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

    /** Rows the page has, readings included, which is what the wheel measures. */
    private int commandCount() {
        return commandLines(model.timer(editor.timerName())).size();
    }

    private void clampScroll() {
        if (model.tab() != AdminModel.Tab.RUNS) return;
        scroll = Math.max(0, Math.min(Math.max(0, model.runs().size() - visibleRows), scroll));
    }

    // ==================================================================
    // Actions
    // ==================================================================

    /** Every execution of the chosen timer, which is what Stop means on a list row. */
    private void stopEveryRunOfSelected() {
        String name = editor.timerName();
        for (AdminModel.RunRow run : model.runs()) {
            if (!run.timerName().equals(name)) continue;
            JsonObject one = new JsonObject();
            one.addProperty("runId", run.runId());
            send("run.stop", one);
        }
        awaitingApply = true;
    }

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
