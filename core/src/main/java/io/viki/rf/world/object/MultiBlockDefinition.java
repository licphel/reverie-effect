/*
 * MIT License
 * Copyright (c) 2026 Licphel
 */
package io.viki.rf.world.object;

import io.viki.momentum.registry.RegistryContext;
import io.viki.momentum.registry.RegistryEntry;
import io.viki.momentum.util.Identifier;
import io.viki.rf.world.util.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Immutable registered definition of a multi-block object. */
public final class MultiBlockDefinition implements RegistryEntry {
  private final RegistryContext registryContext = new RegistryContext();
  private final ObjectLayout layout;
  private final int rootPartIndex;
  private final List<BlockPos> anchors;
  private final boolean requireAnyAnchor;
  private final ObjectBreakPolicy breakPolicy;
  private final List<ObjectPartRender> renderDefinitions;
  private final ObjectRenderProvider renderProvider;
  private final ConcurrentHashMap<Integer, List<MultiBlockPart>> layouts = new ConcurrentHashMap<>();

  public MultiBlockDefinition(ObjectLayout layout, int rootPartIndex,
                              List<BlockPos> anchors, boolean requireAnyAnchor,
                              ObjectBreakPolicy breakPolicy,
                              List<ObjectPartRender> renderDefinitions,
                              ObjectRenderProvider renderProvider) {
    this.layout = Objects.requireNonNull(layout, "layout");
    this.rootPartIndex = rootPartIndex;
    this.anchors = List.copyOf(Objects.requireNonNull(anchors, "anchors"));
    this.requireAnyAnchor = requireAnyAnchor;
    this.breakPolicy = Objects.requireNonNull(breakPolicy, "breakPolicy");
    this.renderDefinitions = List.copyOf(
        Objects.requireNonNull(renderDefinitions, "renderDefinitions"));
    this.renderProvider = Objects.requireNonNull(renderProvider, "renderProvider");
    parts(0);
  }

  public MultiBlockDefinition(ObjectLayout layout, int rootPartIndex,
                              List<BlockPos> anchors, boolean requireAnyAnchor,
                              ObjectBreakPolicy breakPolicy,
                              List<ObjectPartRender> renderDefinitions) {
    this(layout, rootPartIndex, anchors, requireAnyAnchor, breakPolicy, renderDefinitions,
        (variant, rootPosition, partPosition, partIndex, part, gameTicks) -> part.renderPieces());
  }

  public static MultiBlockDefinition fixed(List<MultiBlockPart> parts, int rootPartIndex,
                                           List<BlockPos> anchors, boolean requireAnyAnchor,
                                           ObjectBreakPolicy breakPolicy) {
    List<MultiBlockPart> fixedParts = List.copyOf(parts);
    List<ObjectPartRender> renders = new ArrayList<>();
    for (MultiBlockPart part : fixedParts) {
      renders.addAll(part.renderPieces());
    }
    return new MultiBlockDefinition(ignored -> fixedParts, rootPartIndex, anchors,
        requireAnyAnchor, breakPolicy, renders,
        (variant, rootPosition, partPosition, partIndex, part, gameTicks) -> part.renderPieces());
  }

  @Override
  public RegistryContext getRegistryContext() {
    return registryContext;
  }

  public List<MultiBlockPart> parts(int variant) {
    validateVariant(variant);
    return layouts.computeIfAbsent(variant, ignored -> validateLayout(
        Objects.requireNonNull(layout.create(variant), "object layout result")));
  }

  public MultiBlockPart part(int variant, int partIndex) {
    List<MultiBlockPart> parts = parts(variant);
    if (partIndex < 0 || partIndex >= parts.size()) {
      throw new IndexOutOfBoundsException("Object part index " + partIndex
          + " outside [0, " + parts.size() + ")");
    }
    return parts.get(partIndex);
  }

  public int rootPartIndex() {
    return rootPartIndex;
  }

  public List<BlockPos> anchors() {
    return anchors;
  }

  public boolean requireAnyAnchor() {
    return requireAnyAnchor;
  }

  public ObjectBreakPolicy breakPolicy() {
    return breakPolicy;
  }

  public List<ObjectPartRender> renderDefinitions() {
    return renderDefinitions;
  }

  public List<ObjectPartRender> renderPieces(int variant, BlockPos rootPosition,
                                              BlockPos partPosition, int partIndex,
                                              MultiBlockPart part, long gameTicks) {
    validateVariant(variant);
    return Objects.requireNonNull(renderProvider.create(variant, rootPosition, partPosition,
        partIndex, part, gameTicks), "object render provider result");
  }

  public BlockPos partPosition(BlockPos rootPosition, boolean mirrorX,
                               int variant, int partIndex) {
    List<MultiBlockPart> activeParts = parts(variant);
    MultiBlockPart rootPart = activeParts.get(rootPartIndex);
    MultiBlockPart part = activeParts.get(partIndex);
    BlockPos rootOffset = rootPart.offset();
    BlockPos relative = part.offset().offset(-rootOffset.x(), -rootOffset.y());
    BlockPos transformed = mirrorX
        ? new BlockPos(-relative.x(), relative.y()) : relative;
    return rootPosition.offset(transformed.x(), transformed.y());
  }

  public List<BlockPos> footprint(BlockPos rootPosition, boolean mirrorX, int variant) {
    List<MultiBlockPart> activeParts = parts(variant);
    List<BlockPos> result = new ArrayList<>(activeParts.size());
    for (int i = 0; i < activeParts.size(); i++) {
      result.add(partPosition(rootPosition, mirrorX, variant, i));
    }
    return List.copyOf(result);
  }

  private List<MultiBlockPart> validateLayout(List<MultiBlockPart> candidate) {
    if (candidate.isEmpty()) {
      throw new IllegalArgumentException("An object layout must contain at least one Part");
    }
    if (rootPartIndex < 0 || rootPartIndex >= candidate.size()) {
      throw new IllegalArgumentException("Object root Part index is outside the layout: "
          + rootPartIndex);
    }
    Set<BlockPos> offsets = new HashSet<>();
    for (MultiBlockPart part : candidate) {
      if (!offsets.add(part.offset())) {
        throw new IllegalArgumentException("Object layout contains duplicate offset: " + part.offset());
      }
    }
    return List.copyOf(candidate);
  }

  private static void validateVariant(int variant) {
    if (variant < 0) {
      throw new IllegalArgumentException("Object variant must be non-negative: " + variant);
    }
  }
}
