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

import io.viki.momentum.util.Palette;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * Immutable implementation of {@link ImmutablePropertyMap} backed by an unmodifiable map.
 *
 * <p>Instances are created by {@link PropertyDef#collectStates(Palette)} and hold a
 * pre-computed neighbor table. {@link #with(Property, Object)} performs an
 * {@code O(1)} map lookup — no hash reconstruction or palette search at call time.
 *
 * <p>Two instances are considered equal if their value maps are equal, regardless
 * of identity.
 *
 * @see ImmutablePropertyMap
 * @see PropertyDef
 */
final class ImmutablePropertyMapImpl implements ImmutablePropertyMap {
  private final Map<Property<?>, Object> values;
  private final int identity;
  private Map<Property<?>, Map<Object, ImmutablePropertyMap>> neighbors;

  /**
   * Creates a new instance with the given values and identity.
   *
   * @param values   the property-to-value mappings
   * @param identity the globally unique identity
   */
  ImmutablePropertyMapImpl(Map<Property<?>, Object> values, int identity) {
    this.values = Map.copyOf(values);
    this.identity = identity;
    this.neighbors = Map.of();
  }

  /**
   * Sets the pre-computed neighbor table.
   *
   * <p>Called by {@link PropertyDef} after all states have been registered
   * in the palette.
   *
   * @param neighbors the neighbor lookup table
   */
  void setNeighbors(Map<Property<?>, Map<Object, ImmutablePropertyMap>> neighbors) {
    this.neighbors = Collections.unmodifiableMap(neighbors);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T get(Property<T> property) {
    Object v = values.get(property);
    return v != null ? (T) v : property.defaultValue();
  }

  @Override
  public boolean has(Property<?> property) {
    return values.containsKey(property);
  }

  @Override
  public <T> ImmutablePropertyMap with(Property<T> property, T value) {
    Map<Object, ImmutablePropertyMap> propNeighbors = neighbors.get(property);
    if (propNeighbors == null) {
      throw new IllegalStateException("No such neighbors for property " + property + ": " + value);
    }
    ImmutablePropertyMap next = propNeighbors.get(value);
    return next != null ? next : this;
  }

  @Override
  public int identity() {
    return identity;
  }

  @Override
  public Iterator<Map.Entry<Property<?>, Object>> iterator() {
    return values.entrySet().iterator();
  }

  @Override
  public int hashCode() {
    return values.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ImmutablePropertyMapImpl that)) {
      return false;
    }
    return values.equals(that.values);
  }

  @Override
  public String toString() {
    return values.toString();
  }

  /** Exposed for {@link PropertyDef} — lookup during neighbor construction. */
  Map<Property<?>, Object> values() {
    return values;
  }
}
