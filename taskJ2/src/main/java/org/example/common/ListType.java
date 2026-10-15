package org.example.common;

/**
 * Перечисляет доступные реализации списка, чтобы приложению было удобно
 * переключаться между своей и библиотечной версией.
 */
public enum ListType {
    /** Собственный двусвязный список с поузловыми блокировками. */
    LINKED,
    /** Стандартный {@link java.util.ArrayList} в обёртке Collections.synchronizedList(). */
    LIBRARY;

    /**
     * Преобразует строковое имя аргумента в тип списка.
     *
     * @param name имя типа (например, {@code linked} или {@code library})
     * @return соответствующий тип списка
     * @throws IllegalArgumentException если имя не распознано
     */
    public static ListType fromName(String name) {
        return switch (name.toLowerCase()) {
            case "linked", "list", "own" -> LINKED;
            case "library", "array", "arraylist", "sync" -> LIBRARY;
            default -> throw new IllegalArgumentException("Неизвестный тип списка: " + name);
        };
    }
}
