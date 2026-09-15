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

import io.viki.momentum.util.PaletteCandidate;

/**
 * A {@link PropertyMap} with a globally unique identity and fast state transitions.
 *
 * <p>An immutable property map cannot be modified in place. Instead,
 * {@link #with(Property, Object)} returns a new map reflecting the change,
 * using pre-computed neighbor lookup for {@code O(1)} transitions.
 * Each instance has a unique {@link #identity() identity} assigned at creation.
 *
 * @see PropertyMap
 * @see MutablePropertyMap
 * @see ImmutablePropertyMapImpl
 */
public interface ImmutablePropertyMap extends PropertyMap, PaletteCandidate {
  /**
   * Returns a new map with the given property set to the given value, or
   * {@code this} if the value does not produce a different state.
   *
   * @param property the property to set
   * @param value    the value to store
   * @param <T>      the value type
   * @return the resulting map after the transition
   */
  <T> ImmutablePropertyMap with(Property<T> property, T value);
}
