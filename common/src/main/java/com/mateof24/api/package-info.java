/**
 * The public API of OnTime — what another mod is meant to compile against.
 *
 * <h2>What is here</h2>
 *
 * <p>{@link com.mateof24.api.OnTimeAPI} is the entry point; everything else in
 * this package is either a snapshot it hands out
 * ({@link com.mateof24.api.TimerDefinition},
 * {@link com.mateof24.api.TimerRunInfo}) or vocabulary those snapshots are
 * written in ({@link com.mateof24.api.Audience},
 * {@link com.mateof24.api.RunMode}, {@link com.mateof24.api.RunPhase}).</p>
 *
 * <p>The vocabulary lives here rather than in the mod's internals on purpose:
 * the internals reference these types, not the other way round, so refactoring
 * the mod cannot move a type out from under a consumer.</p>
 *
 * <h2>Definitions and executions</h2>
 *
 * <p>A <b>definition</b> is what an operator configured. An <b>execution</b> is
 * one running instance of it, with its own clock and its own audience, and a
 * definition can have none, one, or one per player. Nearly every mistake made
 * against this API comes from treating those as the same thing — asking a
 * definition what time it shows, when three executions of it show three
 * different times.</p>
 *
 * <h2>What is guaranteed</h2>
 *
 * <ul>
 *   <li>{@link com.mateof24.api.OnTimeAPI#API_VERSION} is bumped whenever a
 *       member is removed or changes meaning. Check it if you support more than
 *       one OnTime.</li>
 *   <li>Nothing here is deprecated. 5.0.0 is a fresh API rather than the
 *       old one with parts crossed out, and what it does differently is
 *       announced in the changelog.</li>
 *   <li>Anything outside this package is internal and may change in any
 *       release, including {@code com.mateof24.manager},
 *       {@code com.mateof24.timer} and {@code com.mateof24.event}. The one
 *       exception is {@code com.mateof24.render.ITimerRenderer}, which is API
 *       and stays where it is because it is per-Minecraft-version by
 *       necessity.</li>
 *   <li>Every call touches server state and belongs on the server thread.</li>
 * </ul>
 *
 * <p>What changed in 5.0.0 and what to use instead is in
 * {@code API-MIGRATION.md} in the repository.</p>
 */
package com.mateof24.api;
