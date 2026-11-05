package no.s11.wpsld.util;

import java.util.function.Supplier;

public interface Value<T> {
    T get();

    static <T> Value<T> of(T obj) {
        if (obj == null) { 
            throw new NullPointerException("Value must be non-null");
        }
        return new Value<T>() {
            @Override
            public T get() {
                return obj;
            }
        };
    }

    /**
     * A value computed on first {@link #get()} and then kept, e.g. for
     * traversals of an unchanging schema. The supplier must not return null.
     */
    static <T> Value<T> lazy(Supplier<T> supplier) {
        return new LazyValue<>(supplier);
    }
}
