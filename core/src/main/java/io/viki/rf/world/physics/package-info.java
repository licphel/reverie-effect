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

/**
 * Compound-polygon collision shapes and SAT movement for world bodies.
 *
 * <p>{@link VoxelClip} is block-local collision data. {@link VoxelBox} and
 * {@link VoxelBoxes} describe rectangular components, {@link VoxelSlope}
 * describes a triangular surface, and {@link VoxelPlatform} describes a
 * one-way surface. {@link io.viki.rf.world.block.TileShape} maps a tile's
 * shape byte to these clips.
 *
 * <p>{@link Collision} is the independent entity-side shape. It wraps a
 * {@link io.viki.momentum.math.shape.Poly}, which may contain concave or
 * disconnected compound geometry, and does not implement {@link VoxelClip}.
 * Physical constants are expressed in SI and converted to world tiles through
 * {@link Ruler}; one tile represents half a metre.
 *
 * <p>The runtime pipeline is deliberately one-way: {@link VoxelCollisionQuery}
 * converts loaded tiles into immutable {@link WorldCollider} values;
 * {@link SatSolver} performs pure convex-component SAT and swept SAT; and
 * {@link MotionSolver} applies bounded paths, contact sliding, and an explicit
 * three-phase stair candidate. {@link DynamicObject} owns entity state and
 * delegates those world/geometry operations.
 */
@NullMarked
package io.viki.rf.world.physics;

import org.jspecify.annotations.NullMarked;
