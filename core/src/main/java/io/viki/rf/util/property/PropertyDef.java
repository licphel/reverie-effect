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

import io.viki.momentum.math.util.Mutil;
import io.viki.momentum.util.Palette;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * A set of bounded {@link Property} keys that defines the state space for a block or config type.
 *
 * <p>After adding properties with {@link #put(Property)}, call
 * {@link #collectStates(Palette)} to compute all valid state combinations via cartesian
 * product and register them in the global palette. Each state is assigned a
 * globally unique identity.
 *
 * <p>A definition can only be populated once. After {@code collectStates} is called,
 * further calls to {@code put} are rejected.
 *
 * @see Property
 * @see Palette
 * @see ImmutablePropertyMap
 */
public final class PropertyDef {
  private final Set<Property<?>> properties = new LinkedHashSet<>();
  private int stateCount;
  private boolean collected;
  private @Nullable ImmutablePropertyMap defaultMap;
  private List<ImmutablePropertyMap> maps = List.of();

  /**
   * Adds a property to this definition.
   *
   * @param property the property to add
   * @return this definition, for chaining
   * @throws IllegalStateException    if {@link #collectStates(Palette)} has already been called
   * @throws IllegalArgumentException if the property's values are not bounded
   */
  public PropertyDef put(Property<?> property) {
    if (collected) {
      throw new IllegalStateException("Already collected");
    }
    properties.add(property);
    return this;
  }

  /**
   * Returns the set of properties in this definition.
   *
   * @return the properties, in insertion order
   */
  public Set<Property<?>> properties() {
    return properties;
  }

  /**
   * Returns the number of valid states for this definition.
   *
   * @return the state count
   */
  public int stateCount() {
    return stateCount;
  }

  /**
   * Computes all valid state combinations and adds them to the given global palette.
   *
   * <p>States are generated as the cartesian product of all property domains.
   * Each state is assigned a globally unique sequential ID. Neighbor tables
   * are pre-computed for {@code O(1)} state transitions.
   *
   * <p>If already collected, this method is a no-op.
   *
   * @param globalPalette the shared global palette to populate
   */
  @SuppressWarnings("unchecked")
  public void collectStates(Palette<ImmutablePropertyMap> globalPalette) {
    if (collected) {
      return;
    }

    List<Map.Entry<Property<?>, List<Object>>> domains = new ArrayList<>();
    for (Property<?> prop : properties) {
      Collection<?> bv = prop.values();
      domains.add(Map.entry(prop, List.copyOf(bv)));
    }

    List<Map<Property<?>, Object>> combinations = Mutil.cartesianDot(domains);

    int stateStart = globalPalette.size();
    stateCount = combinations.size();
    List<ImmutablePropertyMap> builtMaps = new ArrayList<>(stateCount);
    for (int i = 0; i < combinations.size(); i++) {
      ImmutablePropertyMap map = new ImmutablePropertyMapImpl(combinations.get(i), stateStart + i);
      builtMaps.add(map);
      globalPalette.assign(map);
    }
    this.maps = List.copyOf(builtMaps);

    for (int i = 0; i < stateCount; i++) {
      ImmutablePropertyMapImpl state = (ImmutablePropertyMapImpl) globalPalette.get(stateStart + i);
      state.setNeighbors(buildNeighbors(globalPalette, state, domains));
    }

    // generate default map
    defaultMap = globalPalette.get(stateStart);

    for (Property<?> prop : properties) {
      defaultMap = defaultMap.with((Property<Object>) prop, prop.defaultValue());
    }

    collected = true;
  }

  /**
   * Returns a map of all properties to their default values.
   *
   * @return the default value map
   */
  public ImmutablePropertyMap defaultMap() {
    if (defaultMap == null) {
      throw new IllegalStateException("Not collected yet");
    }
    return defaultMap;
  }

  /**
   * Returns all property maps generated by this definition.
   *
   * <p>Only available after {@link #collectStates(Palette)} has been called.
   *
   * @return an unmodifiable list of all maps
   * @throws IllegalStateException if not yet collected
   */
  public List<ImmutablePropertyMap> maps() {
    if (!collected) {
      throw new IllegalStateException("Not collected yet");
    }
    return maps;
  }

  private Map<Property<?>, Map<Object, ImmutablePropertyMap>> buildNeighbors(
      Palette<ImmutablePropertyMap> palette,
      ImmutablePropertyMapImpl state,
      List<Map.Entry<Property<?>, List<Object>>> domains) {
    Map<Property<?>, Map<Object, ImmutablePropertyMap>> neighbors = new LinkedHashMap<>();

    for (Map.Entry<Property<?>, List<Object>> domain : domains) {
      Property<?> prop = domain.getKey();
      List<Object> legalValues = domain.getValue();
      Map<Object, ImmutablePropertyMap> propNeighbors = new LinkedHashMap<>();

      for (Object val : legalValues) {
        Map<Property<?>, Object> next = new LinkedHashMap<>(state.values());
        next.put(prop, val);
        int idx = palette.searchIndex(new ImmutablePropertyMapImpl(next, -1));
        propNeighbors.put(val, palette.get(idx));
      }
      neighbors.put(prop, propNeighbors);
    }

    return neighbors;
  }
}
