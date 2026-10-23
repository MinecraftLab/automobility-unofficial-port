package io.github.foundationgames.automobility.screen;

import java.util.List;
import java.util.Map;

/** Default categories may be visible even when a datapack supplies no recipes. */
final class RecipeCategoryLookup {
    private RecipeCategoryLookup() {
    }

    static <K, T> List<T> entries(Map<K, List<T>> recipes, List<K> categories, int category) {
        if (category < 0 || category >= categories.size()) {
            return List.of();
        }
        var entries = recipes.get(categories.get(category));
        return entries == null ? List.of() : entries;
    }
}
