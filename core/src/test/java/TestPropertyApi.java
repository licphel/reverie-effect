import io.viki.rf.util.property.ImmutablePropertyMap;
import io.viki.rf.util.property.MutablePropertyMap;
import io.viki.rf.util.property.MutablePropertyMapImpl;
import io.viki.rf.util.property.Property;
import io.viki.rf.util.property.PropertyDef;
import io.viki.momentum.util.Palette;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TestPropertyApi {

  private static final Property<Integer> FACING =
      Property.of("facing", 0, Property.rangedInt(0, 3));
  private static final Property<Boolean> LIT =
      Property.of("lit", false, Property.bool());
  private static final Property<Boolean> WATERLOGGED =
      Property.of("waterlogged", false, Property.bool());
  private static final Property<Integer> LEVEL =
      Property.of("level", 0, Property.rangedInt(0, 15));
  private static final Property<String> NAMES =
      Property.of("names", "a", List.of("a", "b", "c"));

  public static void main(String[] args) {
    testMutableGetPut();
    testMutableHas();
    testMutableDefaultFallback();
    testMutableIteration();
    testMutableFluentChaining();

    testPropertyValues();
    testImmutableHas();
    testImmutableLargeCartesian();
    testImmutableWithChain();
    testPaletteSearchIndex();
    testPaletteValues();
    testPropertyDefDefaultMap();
    testGlobalPalette();

    System.out.println("ALL TESTS PASSED");
  }

  // ================================================================
  // MutablePropertyMap
  // ================================================================

  private static void testMutableGetPut() {
    MutablePropertyMap map = new MutablePropertyMapImpl();
    map.put(FACING, 2).put(LIT, true);
    assert map.get(FACING) == 2 && map.get(LIT);
    map.put(FACING, 3);
    assert map.get(FACING) == 3;
    System.out.println("  ✓ testMutableGetPut");
  }

  private static void testMutableHas() {
    MutablePropertyMap map = new MutablePropertyMapImpl();
    assert !map.has(FACING);
    map.put(FACING, 0);
    assert map.has(FACING);
    System.out.println("  ✓ testMutableHas");
  }

  private static void testMutableDefaultFallback() {
    MutablePropertyMap map = new MutablePropertyMapImpl();
    assert map.get(FACING) == 0 && !map.get(LIT);
    System.out.println("  ✓ testMutableDefaultFallback");
  }

  private static void testMutableIteration() {
    MutablePropertyMap map = new MutablePropertyMapImpl()
        .put(FACING, 1).put(LIT, true).put(LEVEL, 10);
    List<Property<?>> keys = new ArrayList<>();
    for (Map.Entry<Property<?>, Object> entry : map) keys.add(entry.getKey());
    assert keys.size() == 3;
    System.out.println("  ✓ testMutableIteration");
  }

  private static void testMutableFluentChaining() {
    MutablePropertyMap map = new MutablePropertyMapImpl()
        .put(FACING, 2).put(LIT, true).put(LEVEL, 8);
    assert map.get(FACING) == 2 && map.get(LIT) && map.get(LEVEL) == 8;
    System.out.println("  ✓ testMutableFluentChaining");
  }

  // ================================================================
  // Property
  // ================================================================

  private static void testPropertyValues() {
    assert Property.rangedInt(0, 3).size() == 4;
    assert Property.bool().size() == 2;
    assert Property.bool().contains(false) && Property.bool().contains(true);
    System.out.println("  ✓ testPropertyValues");
  }

  // ================================================================
  // ImmutablePropertyMap (via PropertyDef + global Palette)
  // ================================================================

  private static void testImmutableHas() {
    Palette<ImmutablePropertyMap> p = new Palette<>();
    PropertyDef def = new PropertyDef().put(FACING).put(LIT);
    def.collectStates(p);
    ImmutablePropertyMap state = p.get(0);
    assert state.has(FACING) && state.has(LIT) && !state.has(WATERLOGGED);
    System.out.println("  ✓ testImmutableHas");
  }

  private static void testImmutableLargeCartesian() {
    Palette<ImmutablePropertyMap> p = new Palette<>();
    PropertyDef def = new PropertyDef().put(FACING).put(LIT).put(WATERLOGGED);
    def.collectStates(p);
    assert def.stateCount() == 16;
    for (int i = 0; i < 16; i++) {
      assert p.get(i).identity() == i;
    }
    System.out.println("  ✓ testImmutableLargeCartesian");
  }

  private static void testImmutableWithChain() {
    Palette<ImmutablePropertyMap> p = new Palette<>();
    PropertyDef def = new PropertyDef().put(FACING).put(LIT).put(LEVEL);
    def.collectStates(p);
    assert def.stateCount() == 128;

    ImmutablePropertyMap s = p.get(0);
    assert s.get(FACING) == 0 && !s.get(LIT) && s.get(LEVEL) == 0;
    s = s.with(LIT, true).with(FACING, 3).with(LEVEL, 15);
    assert s.get(FACING) == 3 && s.get(LIT) && s.get(LEVEL) == 15;
    s = s.with(FACING, 0).with(LIT, false).with(LEVEL, 0);
    assert s.identity() == 0;
    System.out.println("  ✓ testImmutableWithChain");
  }

  // ================================================================
  // Palette
  // ================================================================

  private static void testPaletteSearchIndex() {
    Palette<ImmutablePropertyMap> p = new Palette<>();
    new PropertyDef().put(FACING).collectStates(p);
    assert p.searchIndex(p.get(0)) == 0;
    assert p.searchIndex(p.get(3)) == 3;
    System.out.println("  ✓ testPaletteSearchIndex");
  }

  private static void testPaletteValues() {
    Palette<ImmutablePropertyMap> p = new Palette<>();
    new PropertyDef().put(LIT).collectStates(p);
    assert p.values().size() == 2;
    System.out.println("  ✓ testPaletteValues");
  }

  // ================================================================
  // PropertyDef.defaultMap()
  // ================================================================

  private static void testPropertyDefDefaultMap() {
    PropertyDef def = new PropertyDef().put(FACING).put(LIT).put(NAMES);
    def.collectStates(new Palette<>());
    ImmutablePropertyMap defaults = def.defaultMap();
    assert defaults.get(FACING) == 0;
    assert defaults.get(LIT).equals(false);
    assert defaults.get(NAMES).equals("a");

    System.out.println("  ✓ testPropertyDefDefaultMap");
  }

  // ================================================================
  // Global palette
  // ================================================================

  private static void testGlobalPalette() {
    Palette<ImmutablePropertyMap> global = new Palette<>();

    PropertyDef stone = new PropertyDef();
    stone.collectStates(global);
    assert stone.stateCount() == 1;
    assert global.get(0).identity() == 0;

    PropertyDef torch = new PropertyDef().put(LIT);
    torch.collectStates(global);
    assert torch.stateCount() == 2;
    assert global.get(1).identity() == 1;

    PropertyDef fence = new PropertyDef().put(FACING).put(WATERLOGGED);
    fence.collectStates(global);
    assert fence.stateCount() == 8;
    assert global.size() == 11;

    // neighbor transitions
    ImmutablePropertyMap fenceState = global.get(3); // fence (0, F), global ID 3
    assert fenceState.with(FACING, 3).identity() == 6;

    System.out.println("  ✓ testGlobalPalette");
  }
}
