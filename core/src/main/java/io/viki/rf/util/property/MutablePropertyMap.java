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

/**
 * A {@link PropertyMap} that supports in-place mutation via {@link #put(Property, Object)}.
 *
 * <p>The value is validated against the property's allowed values before being stored.
 * The returned {@code this} reference allows fluent chaining of put calls.
 *
 * @see PropertyMap
 * @see ImmutablePropertyMap
 * @see MutablePropertyMapImpl
 */
public interface MutablePropertyMap extends PropertyMap {
  /**
   * Stores a value for the given property, validating it against the property's allowed values.
   *
   * @param property the property to set
   * @param value    the value to store
   * @param <T>      the value type
   * @return this map, for chaining
   * @throws IllegalArgumentException if the value is not among the property's allowed values
   */
  <T> MutablePropertyMap put(Property<T> property, T value);
}
