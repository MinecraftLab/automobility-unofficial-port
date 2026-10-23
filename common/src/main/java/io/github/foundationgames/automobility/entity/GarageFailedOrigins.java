package io.github.foundationgames.automobility.entity;

import it.unimi.dsi.fastutil.longs.*;
import java.util.*;

/** Sticky, exact-storage failure registry. A failed incoming chunk is never rewritten as empty. */
public final class GarageFailedOrigins {
    private static final Map<Object,LongSet> FAILED=new IdentityHashMap<>();
    private GarageFailedOrigins() {}
    public static synchronized void fail(Object storage,long chunk){FAILED.computeIfAbsent(Objects.requireNonNull(storage),x->new LongOpenHashSet()).add(chunk);}
    public static synchronized boolean failed(Object storage,long chunk){var set=FAILED.get(storage);return set!=null&&set.contains(chunk);}
    public static synchronized LongSet filter(Object storage,LongSet original){
        var copy=new LongOpenHashSet(original);var failures=FAILED.get(storage);if(failures!=null)copy.removeAll(failures);return copy;
    }
    public static synchronized void closed(Object storage){FAILED.remove(storage);}
}
