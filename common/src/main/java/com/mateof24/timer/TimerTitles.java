package com.mateof24.timer;

/**
 * Immutable snapshot of a timer's four decorative titles (4.0.0), as the raw
 * strings the user typed (tellraw-style JSON or plain text). Empty string =
 * slot unset. Used as the network-facing DTO: parsing to Component happens
 * on each side with the version-specific compat helper.
 */
public record TimerTitles(String above, String below, String left, String right) {

    public static final TimerTitles EMPTY = new TimerTitles("", "", "", "");

    public static TimerTitles of(Timer timer) {
        return new TimerTitles(
                nullToEmpty(timer.getTitleAbove()),
                nullToEmpty(timer.getTitleBelow()),
                nullToEmpty(timer.getTitleLeft()),
                nullToEmpty(timer.getTitleRight()));
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
