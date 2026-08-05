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
    private int timerFormRows = 1;
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

    /** The selected timer's own twelve settings. */
    private final TimerForm timerForm = new TimerForm();

    /** Text fields a dialog is asking for, in the order it asks. */
    private final List<EditBox> dialogFields = new ArrayList<>();
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
        } else if (awaitingApply || model.tab() == AdminModel.Tab.TIMERS && !timerForm.isDirty(
                model.timer(model.selectedTimer()))) {
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
            listX = GUTTER + MARK_WIDTH + 3;
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
            listX = GUTTER + MARK_WIDTH + 3;
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
        colAudience = listX + Math.max(70, (int) (listWidth * 0.42f));
        colTimeRight = listX + listWidth - 6;

        visibleRows = Math.max(1, (listBottom - listTop + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
        clampScroll();

        host.clearWidgets();
        rowMarks.clear();
        rowData.clear();
        assist.clear();
        assist.setHost(host);
        timerMarks.clear();
        timerData.clear();
        dialogFields.clear();
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
            buildTimerRows();
            buildTimerDetail();
        }
    }

    // ---- the timers tab ----

    /** The search box, then one button per visible definition. */
    private void buildTimerRows() {
        EditBox search = new EditBox(host.font(), listX, listTop, listWidth, 16,
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
        assist.add(search, text -> true);

        List<AdminModel.TimerRow> rows = model.filteredTimers();
        timerRowsShown = Math.max(1, (listBottom - listTop - SEARCH_HEIGHT + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - timerRowsShown), scroll));

        for (int i = 0; i < timerRowsShown && scroll + i < rows.size(); i++) {
            AdminModel.TimerRow row = rows.get(scroll + i);
            int y = listTop + SEARCH_HEIGHT + i * (ROW_HEIGHT + ROW_GAP);

            Button button = Button.builder(Component.empty(), b -> {
                        model.selectTimer(row.name());
                        timerForm.focus(model.selectedTimer());
                        model.clearMessage();
                        init();
                    })
                    .bounds(listX, y, listWidth, ROW_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("ontime.gui.timers.row.tip",
                            Component.literal(row.name()))))
                    .build();
            button.active = !row.name().equals(model.selectedTimer());
            host.addWidget(button);

            timerMarks.add(new int[]{listX - MARK_WIDTH - 3, y, row.runCount() > 0 ? COLOR_RUNNING : COLOR_BAND});
            timerData.add(row);
        }
    }

    /**
     * The selected timer's twelve settings, then what can be done to it.
     *
     * <p>The same twelve the Settings tab serves as defaults, which is the
     * point: a default is only a starting value, and every one of them can be
     * changed here for this timer alone.</p>
     */
    private void buildTimerDetail() {
        AdminModel.TimerRow timer = model.timer(model.selectedTimer());
        timerForm.focus(timer == null ? null : timer.name());
        if (timer == null) return;

        int actionsY = contentBottom - 20;
        int buttonWidth = Math.min(84, (detailWidth - 12) / 3);
        int startX = detailX + (detailWidth - (3 * buttonWidth + 12)) / 2;
        String[] ops = {"start", "clone", "delete"};
        for (int i = 0; i < ops.length; i++) {
            String op = ops[i];
            host.addWidget(Button.builder(Component.translatable("ontime.gui.timers.action." + op),
                            b -> openDialog(op))
                    .bounds(startX + i * (buttonWidth + 6), actionsY, buttonWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable("ontime.gui.timers.action." + op + ".tip")))
                    .build());
        }

        int top = detailBodyTop + LINE + 4;
        int room = actionsY - 6 - top;
        timerFormRows = Math.max(1, room / SETTING_HEIGHT);
        List<SettingsForm.Row> rows = SettingsForm.displayRows();
        detailScroll = Math.max(0, Math.min(Math.max(0, rows.size() - timerFormRows), detailScroll));

        int controlWidth = Math.min(120, detailWidth / 2);
        int controlX = detailX + detailWidth - controlWidth;

        for (int i = 0; i < timerFormRows && detailScroll + i < rows.size(); i++) {
            SettingsForm.Row row = rows.get(detailScroll + i);
            if (row.isHeader()) continue;
            int y = top + i * SETTING_HEIGHT;
            String tooltipKey = "ontime.config." + snake(row.key()) + ".tooltip";
            Tooltip tip = Tooltip.create(Component.translatable(tooltipKey));

            if (row.kind() == SettingsForm.Kind.PRESET) {
                String value = timerForm.displayed(timer, row);
                host.addWidget(Button.builder(Component.literal(value), b -> {
                            timerForm.put(row.displayKey(), timerForm.cycled(row, value));
                            init();
                        })
                        .bounds(controlX, y, controlWidth, 18)
                        .tooltip(tip)
                        .build());
            } else {
                EditBox box = new EditBox(host.font(), controlX, y, controlWidth, 18,
                        Component.translatable("ontime.config." + snake(row.key())));
                box.setMaxLength(64);
                box.setValue(timerForm.displayed(timer, row));
                box.setResponder(text -> timerForm.put(row.displayKey(), text));
                host.addWidget(box);
                registerDisplayField(box, row, tip);
            }
        }
    }

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
        return "new".equals(confirmOp) || "clone".equals(confirmOp)
                || "start".equals(confirmOp) || "delete".equals(confirmOp);
    }

    private int dialogHeight() {
        return switch (confirmOp == null ? "" : confirmOp) {
            case "new" -> 132;
            case "start" -> 116;
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
            case "new" -> {
                dialogFields.add(host.addWidget(field(x, y, fieldWidth, "ontime.gui.timers.field.name", "")));
                y += 24;
                // Hours, minutes and seconds side by side: a duration is one
                // thing, and three boxes on three lines would not read as one.
                int third = (fieldWidth - 12) / 3;
                dialogFields.add(host.addWidget(field(x, y, third, "ontime.gui.timers.field.hours", "0")));
                dialogFields.add(host.addWidget(field(x + third + 6, y, third,
                        "ontime.gui.timers.field.minutes", "0")));
                dialogFields.add(host.addWidget(field(x + 2 * (third + 6), y, third,
                        "ontime.gui.timers.field.seconds", "30")));
                y += 24;
                host.addWidget(Button.builder(Component.translatable(dialogCountUp
                                        ? "ontime.mode.countup" : "ontime.mode.countdown"),
                                b -> { dialogCountUp = !dialogCountUp; init(); })
                        .bounds(x, y, fieldWidth, 20)
                        .build());
            }
            case "clone" -> {
                dialogFields.add(host.addWidget(field(x, y, fieldWidth,
                        "ontime.gui.timers.field.name", name == null ? "" : name + "2")));
            }
            case "start" -> {
                host.addWidget(Button.builder(Component.translatable(dialogGlobal
                                        ? "ontime.audience.global" : "ontime.gui.timers.field.players"),
                                b -> { dialogGlobal = !dialogGlobal; init(); })
                        .bounds(x, y, fieldWidth, 20)
                        .build());
                y += 24;
                if (!dialogGlobal) {
                    dialogFields.add(host.addWidget(field(x, y, fieldWidth,
                            "ontime.gui.timers.field.players", "")));
                }
                y += dialogGlobal ? 0 : 24;
                host.addWidget(Button.builder(Component.translatable("shared".equals(dialogMode)
                                        ? "ontime.mode.shared" : "ontime.mode.each"),
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
        String name = model.selectedTimer();
        JsonObject args = new JsonObject();

        switch (kind == null ? "" : kind) {
            case "new" -> {
                args.addProperty("name", value(0));
                args.addProperty("hours", numberIn(1));
                args.addProperty("minutes", numberIn(2));
                args.addProperty("seconds", numberIn(3));
                args.addProperty("countUp", dialogCountUp);
                confirmOp = null;
                init();
                send("timer.create", args);
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
                model.selectTimer(null);
                init();
                send("timer.delete", args);
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

    private void applyTimerForm() {
        AdminModel.TimerRow timer = model.timer(model.selectedTimer());
        SettingsForm.Result result = timerForm.build(timer);
        for (JsonObject args : result.requests()) send("timer.setDisplay", args);
        if (!result.rejected().isEmpty()) {
            model.setMessage(String.join(", ", result.rejected()), true);
        }
        timerForm.discard();
        awaitingApply = !result.requests().isEmpty();
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
                                timerForm.discard();
                                confirmOp = null;
                                host.closePanel();
                            })
                    .bounds(startX + buttonWidth + gap, y, buttonWidth, 20)
                    .build());

            host.addWidget(Button.builder(Component.translatable("ontime.gui.confirm.exit.save"), b -> {
                        if (timerForm.isDirty(model.timer(model.selectedTimer()))) applyTimerForm();
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
        host.addWidget(Button.builder(Component.translatable("gui.done"), b -> {
                    // Closing on top of unapplied edits throws them away in
                    // silence. Asking costs one click and is the only way the
                    // answer is the operator's rather than the panel's.
                    if (settings.isDirty(model) || timerForm.isDirty(model.timer(model.selectedTimer()))) {
                        confirmOp = CONFIRM_EXIT;
                        init();
                    } else {
                        host.closePanel();
                    }
                })
                .bounds(doneX, 5, doneWidth, 20)
                .build());

        if (model.tab() == AdminModel.Tab.TIMERS) {
            int newWidth = 50;
            int applyWidth = 62;
            int discardWidth = 62;
            AdminModel.TimerRow selected = model.timer(model.selectedTimer());
            boolean dirty = timerForm.isDirty(selected);

            Button apply = Button.builder(Component.translatable("ontime.gui.settings.apply"),
                            b -> applyTimerForm())
                    .bounds(doneX - 6 - applyWidth, 5, applyWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable("ontime.gui.timers.apply.tip")))
                    .build();
            apply.active = dirty;
            applyButton = apply;
            host.addWidget(apply);

            Button discard = Button.builder(Component.translatable("ontime.gui.settings.discard"), b -> {
                        timerForm.discard();
                        model.clearMessage();
                        init();
                    })
                    .bounds(doneX - 12 - applyWidth - discardWidth, 5, discardWidth, 20)
                    .build();
            discard.active = dirty;
            discardButton = discard;
            host.addWidget(discard);

            host.addWidget(Button.builder(Component.translatable("ontime.gui.timers.action.new"),
                            b -> openDialog("new"))
                    .bounds(doneX - 18 - applyWidth - discardWidth - newWidth, 5, newWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable("ontime.gui.timers.action.new.tip")))
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
            case TIMERS -> drawTimers(painter);
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
    private void drawTimers(Painter painter) {
        List<AdminModel.TimerRow> rows = model.filteredTimers();

        painter.text(Component.translatable("ontime.gui.timers.col.name"), colName, headerRowY, COLOR_TEXT);
        Component runsHeader = Component.translatable("ontime.gui.timers.col.runs");
        painter.text(runsHeader, colAudience, headerRowY, COLOR_TEXT);
        Component lengthHeader = Component.translatable("ontime.gui.timers.col.length");
        painter.text(lengthHeader, colTimeRight - painter.textWidth(lengthHeader), headerRowY, COLOR_TEXT);
        painter.rect(listX, headerRowY + LINE - 1, listWidth, 1, COLOR_RULE);

        if (rows.isEmpty()) {
            centered(painter, Component.translatable(model.filter().isEmpty()
                            ? "ontime.gui.timers.empty" : "ontime.gui.timers.no_match"),
                    listX + listWidth / 2,
                    (listTop + SEARCH_HEIGHT + listBottom) / 2 - painter.lineHeight() / 2, COLOR_TEXT);
            drawDivider(painter);
            drawTimerDetail(painter);
            return;
        }

        for (int[] mark : timerMarks) {
            painter.rect(mark[0], mark[1], MARK_WIDTH, ROW_HEIGHT, mark[2]);
        }
        for (int i = 0; i < timerData.size(); i++) {
            AdminModel.TimerRow row = timerData.get(i);
            int y = timerMarks.get(i)[1] + (ROW_HEIGHT - 8) / 2;
            painter.text(Component.literal(row.name()), colName, y, COLOR_TEXT);
            if (row.runCount() > 0) {
                painter.text(Component.literal(String.valueOf(row.runCount())),
                        colAudience, y, COLOR_RUNNING);
            }
            Component length = Component.literal(arrow(row.countUp()) + " "
                    + com.mateof24.render.ClientTimerState.formatTicks(row.targetTicks()));
            painter.text(length, colTimeRight - painter.textWidth(length), y, COLOR_TEXT);
        }

        int listRight = twoColumn ? detailX - GUTTER / 2 : width;
        drawScrollbar(painter, (listX + listWidth + listRight) / 2 - 1,
                listTop + SEARCH_HEIGHT, listBottom, rows.size(), timerRowsShown);

        drawDivider(painter);
        drawTimerDetail(painter);
    }

    private void drawTimerDetail(Painter painter) {
        int centerX = detailX + detailWidth / 2;
        centered(painter, Component.translatable("ontime.gui.timers.detail.title"),
                centerX, detailTitleY, COLOR_TEXT);
        painter.rect(detailX, detailRuleY, detailWidth, 1, COLOR_RULE);

        AdminModel.TimerRow timer = model.timer(model.selectedTimer());
        if (timer == null) {
            centered(painter, Component.translatable("ontime.gui.timers.pick_hint"), centerX,
                    (detailBodyTop + contentBottom) / 2 - painter.lineHeight() / 2, COLOR_TEXT);
            return;
        }

        // Enabled state follows the form, not the last layout.
        boolean dirty = timerForm.isDirty(timer);
        if (applyButton != null) applyButton.active = dirty;
        if (discardButton != null) discardButton.active = dirty;

        painter.text(Component.literal(timer.name()), detailX, detailBodyTop, COLOR_TEXT);
        Component length = Component.literal(arrow(timer.countUp()) + " "
                + com.mateof24.render.ClientTimerState.formatTicks(timer.targetTicks()));
        painter.text(length, detailX + detailWidth - painter.textWidth(length), detailBodyTop, COLOR_TEXT);

        int top = detailBodyTop + LINE + 4;
        List<SettingsForm.Row> rows = SettingsForm.displayRows();
        for (int i = 0; i < timerFormRows && detailScroll + i < rows.size(); i++) {
            SettingsForm.Row row = rows.get(detailScroll + i);
            int y = top + i * SETTING_HEIGHT;

            if (row.isHeader()) {
                painter.text(Component.translatable("ontime.gui.settings.group." + row.header()),
                        detailX, y + 6, COLOR_TEXT);
                painter.rect(detailX, y + 6 + LINE - 1, detailWidth, 1, COLOR_RULE);
                continue;
            }

            if (timerForm.isEdited(timer, row.displayKey())) {
                painter.rect(detailX - 6, y + 2, MARK_WIDTH, 14, COLOR_PAUSED);
            }
            painter.text(Component.translatable("ontime.config." + snake(row.key())),
                    detailX, y + 5, COLOR_TEXT);
        }

        drawScrollbar(painter, detailX + detailWidth + 4, top, contentBottom - 26,
                rows.size(), timerFormRows);
    }

    private static String arrow(boolean countUp) {
        return countUp ? "\u2191" : "\u2193";
    }

    private void drawSettings(Painter painter) {
        int top = settingsTop();
        List<SettingsForm.Row> rows = SettingsForm.rows();

        // Enabled state follows the form, not the last layout.
        boolean dirty = settings.isDirty(model);
        if (applyButton != null) applyButton.active = dirty;
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

            // An edited row is marked in the gutter the same way a running
            // execution is: colour, in the margin, no glyph.
            if (settings.isEdited(model, row.key())) {
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
                            Component.literal(model.selectedTimer() == null ? "" : model.selectedTimer())),
                    x + DIALOG_WIDTH / 2, y + 14, COLOR_TEXT);
            if ("delete".equals(confirmOp)) {
                centered(painter, Component.translatable("ontime.gui.timers.dialog.delete.body"),
                        x + DIALOG_WIDTH / 2, y + 34, COLOR_ERROR);
            }
            return;
        }

        boolean exiting = CONFIRM_EXIT.equals(confirmOp);
        centered(painter, Component.translatable(exiting
                        ? "ontime.gui.confirm.exit.title" : "ontime.gui.confirm.stop_all.title"),
                x + DIALOG_WIDTH / 2, y + 16, COLOR_TEXT);
        centered(painter, exiting
                        ? Component.translatable("ontime.gui.confirm.exit.body",
                                settings.pendingCount() + timerForm.pendingCount())
                        : Component.translatable("ontime.gui.confirm.stop_all.body", model.runs().size()),
                x + DIALOG_WIDTH / 2, y + 34, COLOR_TEXT);
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
        int timeWidth = 0;
        for (AdminModel.Scheduled entry : timer.scheduled()) {
            timeWidth = Math.max(timeWidth, painter.textWidth(atLabel(entry)));
        }
        int commandX = indent + timeWidth + 8;

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
        if (!timer.scheduled().isEmpty()) y += 4;
        painter.text(Component.translatable("ontime.gui.detail.on_finish"), indent, y, COLOR_COOLDOWN);
        y += LINE;

        for (String command : timer.finishCommands()) {
            if (y + LINE > contentBottom) {
                painter.text(Component.translatable("ontime.gui.detail.more", 1), indent + 8, y, COLOR_TEXT);
                return;
            }
            painter.text(trimmed(painter, Component.literal(command),
                            detailX + detailWidth - indent - 8),
                    indent + 8, y, COLOR_TEXT);
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
        if (total <= shown) return;
        int height = bottom - top;
        if (height < 16) return;

        painter.rect(x, top, 2, height, COLOR_BAND);
        int thumb = Math.max(12, height * shown / total);
        int travel = height - thumb;
        int offset = total == shown ? 0 : travel * scroll / (total - shown);
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
    public boolean mouseClicked(double mouseX, double mouseY) {
        return assist.mouseClicked(mouseX, mouseY);
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
            total = SettingsForm.rows().size();
            shown = settingsRows;
        } else if (model.tab() == AdminModel.Tab.TIMERS) {
            // Two lists side by side, so the wheel goes to whichever the
            // pointer is over. Anything else guesses, and guesses wrong.
            if (twoColumn && pointerX >= detailX - GUTTER / 2 && model.selectedTimer() != null) {
                int rows = SettingsForm.displayRows().size();
                if (rows <= timerFormRows) return false;
                int before = detailScroll;
                detailScroll = Math.max(0, Math.min(rows - timerFormRows,
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
