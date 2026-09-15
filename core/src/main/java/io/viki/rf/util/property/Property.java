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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A named property key with a default value and a collection of allowed values.
 *
 * <p>Properties are used as keys in a {@link PropertyMap}. The values
 * collection defines the legal namespace for this property; unbounded
 * collections cannot be used in {@link PropertyDef} for state-space generation.
 *
 * @param <T> the value type of this property
 * @see PropertyMap
 * @see PropertyDef
 */
public final class Property<T> {
  private final String name;
  private final T defaultValue;
  private final Collection<T> values;

  private Property(String name, T defaultValue, Collection<T> values) {
    this.name = name;
    this.defaultValue = defaultValue;
    this.values = values;
  }

  /**
   * Creates a new property with the given name, default value, and allowed values.
   *
   * @param name         the property name
   * @param defaultValue the fallback value returned when no value is set
   * @param values       the collection of legal values for this property
   * @param <T>          the value type
   * @return a new property
   */
  public static <T> Property<T> of(String name, T defaultValue, Collection<T> values) {
    return new Property<>(name, defaultValue, values);
  }

  /**
   * Returns a collection of integers from {@code min} to {@code max}, inclusive.
   *
   * @param min the minimum value, inclusive
   * @param max the maximum value, inclusive
   * @return a collection containing all integers in the range
   */
  public static Collection<Integer> rangedInt(int min, int max) {
    List<Integer> vals = new ArrayList<>(max - min + 1);
    for (int i = min; i <= max; i++) {
      vals.add(i);
    }
    return vals;
  }

  /**
   * Returns a collection containing {@code false} and {@code true}.
   *
   * @return a collection of both boolean values
   */
  public static Collection<Boolean> bool() {
    return List.of(false, true);
  }

  /**
   * Returns the name of this property.
   *
   * @return the property name
   */
  public String name() {
    return name;
  }

  /**
   * Returns the default value for this property.
   *
   * @return the default value
   */
  public T defaultValue() {
    return defaultValue;
  }

  /**
   * Returns the collection of legal values for this property.
   *
   * @return the allowed values
   */
  public Collection<T> values() {
    return values;
  }

  @Override
  public String toString() {
    return name;
  }
}
