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

package io.viki.rf.world.util;

public interface Locatable {
  /**
   * Returns the Manhattan distance to another block position.
   *
   * @param a first position
   * @param b second position
   * @return the distance
   */
  static double manhattanDistance(Locatable a, Locatable b) {
    return Math.sqrt(distanceSquared(a, b));
  }

  /**
   * Returns the Euclidean distance between two positions.
   *
   * @param a first position
   * @param b second position
   * @return the distance
   */
  static double distance(Locatable a, Locatable b) {
    return Math.sqrt(distanceSquared(a, b));
  }

  /**
   * Returns the squared Euclidean distance between two positions.
   *
   * @param a first position
   * @param b second position
   * @return the squared distance
   */
  static double distanceSquared(Locatable a, Locatable b) {
    double dx = a.getX() - b.getX();
    double dy = a.getY() - b.getY();
    return dx * dx + dy * dy;
  }

  double getX();

  double getY();
}
