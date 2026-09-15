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

package io.viki.rf.world.inventory;

import io.viki.momentum.codec.Codec;
import io.viki.momentum.codec.nbt.CompoundNBT;
import io.viki.momentum.registry.Registry;
import io.viki.momentum.util.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A category of stack types, such as items or liquids.
 *
 * <p>A category owns the concrete stack class of its domain ({@code S},
 * e.g. {@code ItemStack}): the globally shared empty stack and the factory
 * for new stacks. There is exactly one empty stack per category, and every
 * caller receives the same instance. {@link Stack} enforces this by throwing
 * {@link IllegalStateException} on any mutating operation of an empty stack.
 *
 * <p>A category also owns the registry of its types, which maps type
 * identifiers to types. {@link #stackCodec()} derives the per-stack codec
 * from that registry; whole-container serialization composes it into a
 * self-contained container codec (see {@link BuiltinContainerCodecs}).
 *
 * <p>The empty stack and the stack codec are derived lazily on first access
 * and cached, so every caller receives stable, shared instances.
 *
 * @param <T> the stack type, e.g. an item or a liquid
 * @param <S> the concrete stack class of this category
 * @see Stack
 * @see Stackable
 */
public abstract class StackCategory<T extends Stackable, S extends Stack<T, S>> {
  private volatile @Nullable S cachedEmptyStack;
  private volatile @Nullable Codec<S> cachedCodec;

  /**
   * Returns the shared empty stack of this category. Implementations return
   * their domain's global empty singleton (e.g. {@code ItemStack.EMPTY}).
   *
   * @return the shared empty stack
   */
  protected abstract S createEmptyStack();

  /**
   * Creates a new stack of this category's concrete class.
   *
   * @param type  the stack type
   * @param count the unit count
   * @return a new stack
   */
  public abstract S stackOf(T type, int count);

  /**
   * Returns the registry of this category's stack types.
   *
   * @return the category's type registry
   */
  public abstract Registry<T> registry();

  /**
   * Returns this category's globally shared empty stack, computed once and
   * cached.
   *
   * @return the shared empty stack
   */
  public final S emptyStack() {
    S empty = cachedEmptyStack;
    if (empty == null) {
      empty = createEmptyStack();
      cachedEmptyStack = empty;
    }
    return empty;
  }

  /**
   * Returns a codec for a single stack of this category, resolving types
   * through the category's registry. Computed once and cached.
   *
   * <p>A stack encodes as a compound tag holding the type's registry
   * identifier, the {@code count}, and an optional {@code data} entry.
   * Empty stacks encode as {@code count 0} without an identifier. Encoding
   * an unregistered type throws {@link IllegalArgumentException}; decoding
   * an unknown identifier does the same. The resulting codec is stateless
   * and thread-safe.
   *
   * @return the codec
   */
  public final Codec<S> stackCodec() {
    Codec<S> codec = cachedCodec;
    if (codec == null) {
      codec = buildStackCodec();
      cachedCodec = codec;
    }
    return codec;
  }

  private Codec<S> buildStackCodec() {
    Registry<T> registry = registry();
    return Codec.of(stack -> {
          CompoundNBT tag = new CompoundNBT();
          if (!stack.isEmpty()) {
            T type = stack.type();
            Identifier id = registry.getId(type);
            if (id == null) {
              throw new IllegalArgumentException(
                  "Cannot encode unregistered stack type " + type + " in " + registry.key());
            }
            tag.putString("id", id.toString());
            tag.putInt("count", stack.count());
            if (stack.getData() != null) {
              tag.put("data", stack.getData());
            }
          } else {
            tag.putInt("count", 0);
          }
          return tag;
        },
        nbt -> {
          CompoundNBT tag = (CompoundNBT) nbt;
          int count = tag.getInt("count", 0);
          if (count <= 0) {
            return emptyStack();
          }
          T type = registry.get(Identifier.of(tag.getString("id", "")));
          if (type == null) {
            throw new IllegalArgumentException(
                "Unknown stack type id " + tag.getString("id", "") + " in " + registry.key());
          }
          S stack = stackOf(type, count);
          CompoundNBT data = tag.get("data");
          if (data != null) {
            stack.setData(data);
          }
          return stack;
        });
  }
}
