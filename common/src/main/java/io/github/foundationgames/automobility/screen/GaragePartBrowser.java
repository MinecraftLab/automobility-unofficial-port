package io.github.foundationgames.automobility.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** Pure presentation ordering; option availability and actions remain authoritative in the menu. */
public final class GaragePartBrowser {
    private GaragePartBrowser() {}

    public record State(String typedId, boolean installed, boolean selected, int available, int required) {
        public State { Objects.requireNonNull(typedId, "Missing typed part ID"); }
        public boolean hasRequired() { return required > 0 && available >= required; }
        private int rank() { return installed ? 0 : selected ? 1 : hasRequired() ? 2 : 3; }
        private boolean visibleWhenOwnedOnly() { return installed || selected || hasRequired(); }
    }

    private record Row<T>(T value, State state) {}

    public static <T> List<T> visible(List<T> options, boolean ownedOnly, Function<T, State> stateOf) {
        var rows = new ArrayList<Row<T>>(options.size());
        for (var option : options) {
            // Capture each option once; changing a draft cannot make the sorting comparator inconsistent.
            var state = Objects.requireNonNull(stateOf.apply(option), "Missing part presentation state");
            if (!ownedOnly || state.visibleWhenOwnedOnly()) rows.add(new Row<>(option, state));
        }
        rows.sort(Comparator.<Row<T>>comparingInt(row -> row.state.rank())
                .thenComparing(row -> row.state.typedId));
        return rows.stream().map(Row::value).toList();
    }

    public static int pageCount(int count, int rowsPerPage) {
        if (rowsPerPage <= 0) throw new IllegalArgumentException("Positive part page size required");
        return (int) Math.max(1L, (Math.max(0, count) + (long) rowsPerPage - 1) / rowsPerPage);
    }

    public static int clampPage(int page, int count, int rowsPerPage) {
        return Math.clamp(page, 0, pageCount(count, rowsPerPage) - 1);
    }

    public static <T> List<T> page(List<T> options, int requestedPage, int rowsPerPage) {
        int page = clampPage(requestedPage, options.size(), rowsPerPage);
        int start = page * rowsPerPage;
        int end = (int) Math.min(options.size(), start + (long) rowsPerPage);
        return List.copyOf(options.subList(start, end));
    }

    /** A filter toggle can keep the chosen draft on screen after its list index changes. */
    public static <T> int pageOf(List<T> options, int rowsPerPage, Predicate<T> chosen) {
        pageCount(options.size(), rowsPerPage);
        for (int index = 0; index < options.size(); index++) if (chosen.test(options.get(index))) return index / rowsPerPage;
        return 0;
    }
}
