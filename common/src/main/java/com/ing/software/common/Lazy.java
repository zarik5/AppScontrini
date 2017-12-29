package com.ing.software.common;

import com.annimon.stream.function.Supplier;

/**
 * Lazily instantiate an object.
 * Useful if in an array of objects, only some fields of some elements are accessed.
 * Please mind that everything before :: operator is executed in place.
 * @param <T> object
 */
public class Lazy<T> {
    private T value;
    private Supplier<T> inst;

    /**
     * @param instantiation code to instantiate the object
     */
    public Lazy(Supplier<T> instantiation) {
        value = null;
        inst = instantiation;
    }

    /**
     * Get object value, instantiate if necessary.
     * @return object
     */
    public T get() {
        if (value == null) {
            value = inst.get();
        }
        return value;
    }
}
