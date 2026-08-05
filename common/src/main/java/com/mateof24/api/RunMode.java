package com.mateof24.api;

/**
 * How a selector expands into executions.
 *
 * <p>Part of the public API vocabulary: the mod's own classes reference this
 * type rather than the other way round, so an internal refactor cannot move it
 * out from under a consumer.</p>
 */
public enum RunMode {
    /** One execution, one clock, several viewers. */
    SHARED,
    /** One execution per matched player, each with a clock of its own. */
    EACH
}
