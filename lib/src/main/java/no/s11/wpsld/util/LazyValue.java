/*
 * Copyright 2026 The University of Manchester, UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package no.s11.wpsld.util;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * See {@link Value#lazy(Supplier)}. A named class rather than an anonymous
 * one, which would also keep the supplier it captures after it is released.
 */
final class LazyValue<T> implements Value<T> {
    private volatile T value;
    // Released once computed, so whatever it refers to can be freed
    private Supplier<T> supplier;

    LazyValue(Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier);
    }

    @Override
    public T get() {
        T v = value;
        if (v == null) {
            synchronized (this) {
                v = value;
                if (v == null) {
                    v = Objects.requireNonNull(supplier.get(), "Lazy value must be non-null");
                    value = v;
                    supplier = null;
                }
            }
        }
        return v;
    }
}
