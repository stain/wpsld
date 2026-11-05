package no.s11.wpsld.util;

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
}
