package com.mateof24.trigger;

import java.util.function.Predicate;

/**
 * What typed text has to look like before it is worth sending anywhere.
 *
 * <p>Here rather than beside the boxes that use them because they are rules
 * about text, not about widgets: nothing in this file knows what a screen is,
 * which is what lets every one of them be run against a list of cases without
 * a game around it. They were written inside the field helper first, where
 * they could only be read.</p>
 *
 * <p>None of these say a value <em>exists</em> — no list here knows which
 * advancements a pack shipped or who is online. They say the value is the
 * right shape, which is the part that can be answered while somebody is still
 * typing. The server answers the rest, and says why.</p>
 */
public final class InputRules {

    private InputRules() {}

    /** A namespaced id, or a bare path, which is what vanilla accepts too. */
    public static Predicate<String> id() {
        return text -> {
            String value = text.trim();
            if (value.isEmpty()) return false;
            int colon = value.indexOf(':');
            String namespace = colon < 0 ? "minecraft" : value.substring(0, colon);
            String path = colon < 0 ? value : value.substring(colon + 1);
            return !path.isEmpty() && !namespace.isEmpty()
                    && namespace.chars().allMatch(InputRules::namespaceChar)
                    && path.chars().allMatch(InputRules::pathChar);
        };
    }

    /**
     * A target selector, as far as this can tell without a world.
     *
     * <p>The shape and the brackets, and no more: whether {@code team=red}
     * names a team that exists is a question only the server can answer, and
     * it does, by refusing it.</p>
     */
    public static Predicate<String> selector() {
        return text -> {
            String value = text.trim();
            if (value.length() < 2 || value.charAt(0) != '@') return false;
            if ("apres".indexOf(value.charAt(1)) < 0) return false;
            if (value.length() == 2) return true;
            if (value.charAt(2) != '[' || !value.endsWith("]")) return false;
            int depth = 0;
            for (char c : value.toCharArray()) {
                if (c == '[') depth++;
                if (c == ']' && --depth < 0) return false;
            }
            // Four is "@a[]", which selects on nothing and is a typo either way.
            return depth == 0 && value.length() > 4;
        };
    }

    /** One or more player names, separated by commas, none of them empty. */
    public static Predicate<String> nameList() {
        return text -> {
            String value = text.trim();
            if (value.isEmpty() || value.endsWith(",")) return false;
            for (String name : value.split(",", -1)) {
                String one = name.trim();
                if (one.isEmpty() || one.length() > 16) return false;
                if (!one.chars().allMatch(InputRules::nameChar)) return false;
            }
            return true;
        };
    }

    private static boolean namespaceChar(int c) {
        return c == '_' || c == '-' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.';
    }

    private static boolean pathChar(int c) {
        return namespaceChar(c) || c == '/';
    }

    private static boolean nameChar(int c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9');
    }
}
