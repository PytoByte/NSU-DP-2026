package org.example.common;

public enum ListType {
    LINKED,
    ARRAY;

    public static ListType fromName(String name) {
        return switch (name.toLowerCase()) {
            case "linked" -> LINKED;
            case "array" -> ARRAY;
            default -> throw new IllegalArgumentException("Unknown list type: " + name);
        };
    }
}
