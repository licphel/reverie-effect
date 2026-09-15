/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.viki.rf.util.property;

import java.util.Map;

/**
 * A read-only map of {@link Property} keys to typed values, iterable in insertion order.
 *
 * <p>When a property has no explicitly stored value, {@link #get(Property)}
 * returns its default. Use {@link MutablePropertyMap} to mutate values and
 * {@link ImmutablePropertyMap} for a pre-computed read-only snapshot.
 *
 * @see Property
 * @see MutablePropertyMap
 * @see ImmutablePropertyMap
 */
public interface PropertyMap extends Iterable<Map.Entry<Property<?>, Object>> {
  /**
   * Returns the value for the given property, or its default if no value is set.
   *
   * @param property the property to look up
   * @param <T>      the value type
   * @return the current value, or the property's default
   */
  <T> T get(Property<T> property);

  /**
   * Returns whether a value is explicitly stored for the given property.
   *
   * @param property the property to check
   * @return {@code true} if a value is set, {@code false} otherwise
   */
  boolean has(Property<?> property);
}
