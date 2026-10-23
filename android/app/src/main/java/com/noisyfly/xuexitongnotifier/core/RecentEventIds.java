package com.noisyfly.xuexitongnotifier.core;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class RecentEventIds {
    private final int capacity;
    private final ArrayDeque<String> orderedIds = new ArrayDeque<>();
    private final Set<String> knownIds = new HashSet<>();

    public RecentEventIds(int capacity, String serialized) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        if (serialized != null) {
            for (String value : serialized.split("\\n")) {
                accept(value);
            }
        }
    }

    public boolean accept(String value) {
        String id = value == null ? "" : value.trim();
        if (id.isEmpty() || id.indexOf('\r') >= 0 || id.indexOf('\n') >= 0 || knownIds.contains(id)) {
            return false;
        }
        orderedIds.addLast(id);
        knownIds.add(id);
        while (orderedIds.size() > capacity) {
            knownIds.remove(orderedIds.removeFirst());
        }
        return true;
    }

    public String serialize() {
        return String.join("\n", orderedIds);
    }
}
