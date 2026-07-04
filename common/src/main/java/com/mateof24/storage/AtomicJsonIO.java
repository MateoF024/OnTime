package com.mateof24.storage;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Shared JSON file I/O for all OnTime persistence (timers, config, player
 * preferences, history). Two invariants every caller relies on:
 *
 * <ul>
 *   <li><b>Atomic writes</b>: content goes to a sibling {@code .tmp} file that
 *       is then moved over the target, so a crash mid-write can never leave a
 *       truncated/corrupt file behind.</li>
 *   <li><b>Always UTF-8</b>: the JVM default charset is not UTF-8 on Windows.</li>
 * </ul>
 */
public final class AtomicJsonIO {

    private AtomicJsonIO() {}

    public static void write(Gson gson, Path target, JsonElement json) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            gson.toJson(json, writer);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static Reader newReader(Path source) throws IOException {
        return Files.newBufferedReader(source, StandardCharsets.UTF_8);
    }
}
