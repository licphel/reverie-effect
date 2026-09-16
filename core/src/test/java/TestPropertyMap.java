import io.viki.momentum.util.Palette;
import io.viki.rf.util.property.ImmutablePropertyMap;
import io.viki.rf.util.property.Property;
import io.viki.rf.util.property.PropertyDef;

public class TestPropertyMap {

  private static final Property<Integer> FACING =
      Property.of("facing", 0, Property.rangedInt(0, 3));
  private static final Property<Boolean> WATERLOGGED =
      Property.of("waterlogged", false, Property.bool());
  private static final Property<Boolean> LIT =
      Property.of("lit", false, Property.bool());

  public static void main(String[] args) {
    testEmptyDef();
    testBoolOnly();
    testCartesianProduct();
    testDefaultMap();
    testEquality();
    testWithStateTransition();
    testGlobalPalette();

    System.out.println("ALL TESTS PASSED");
  }

  private static void testEmptyDef() {
    Palette<ImmutablePropertyMap> global = new Palette<>();
    PropertyDef def = new PropertyDef();
    def.collectStates(global);

    assert def.stateCount() == 1;

    System.out.println("  ✓ testEmptyDef");
  }

  private static void testBoolOnly() {
    Palette<ImmutablePropertyMap> global = new Palette<>();
    PropertyDef def = new PropertyDef().put(WATERLOGGED);
    def.collectStates(global);

    assert def.stateCount() == 2;
    assert def.defaultMap().get(WATERLOGGED).equals(false);

    System.out.println("  ✓ testBoolOnly");
  }

  private static void testCartesianProduct() {
    Palette<ImmutablePropertyMap> global = new Palette<>();
    PropertyDef def = new PropertyDef().put(FACING).put(LIT);
    def.collectStates(global);

    assert def.stateCount() == 8;
    // global IDs: 0..7
    assert global.get(0).get(FACING) == 0;
    assert global.get(0).get(LIT).equals(false);
    assert global.get(4).get(FACING) == 0;
    assert global.get(4).get(LIT).equals(true);

    System.out.println("  ✓ testCartesianProduct");
  }

  private static void testDefaultMap() {
    PropertyDef def = new PropertyDef().put(FACING).put(WATERLOGGED).put(LIT);
    def.collectStates(new  Palette<>());

    ImmutablePropertyMap defaults = def.defaultMap();
    assert defaults.get(FACING) == 0;
    assert defaults.get(WATERLOGGED).equals(false);
    assert defaults.get(LIT).equals(false);

    System.out.println("  ✓ testDefaultMap");
  }

  private static void testEquality() {
    Palette<ImmutablePropertyMap> global = new Palette<>();
    PropertyDef def = new PropertyDef().put(FACING).put(WATERLOGGED);
    def.collectStates(global);

    assert global.get(0).equals(global.get(0));
    assert !global.get(0).equals(global.get(1));
    assert def.stateCount() == 8;

    System.out.println("  ✓ testEquality");
  }

  private static void testWithStateTransition() {
    Palette<ImmutablePropertyMap> global = new Palette<>();
    PropertyDef def = new PropertyDef().put(FACING).put(LIT);
    def.collectStates(global);

    // FACING(0..3) × LIT(false,true): 0:(0,F), 1:(1,F), 2:(2,F), 3:(3,F), 4:(0,T), 5:(1,T), 6:(2,T), 7:(3,T)
    ImmutablePropertyMap state = global.get(0); // (0, F)
    assert state.get(FACING) == 0 && !state.get(LIT) && state.identity() == 0;

    ImmutablePropertyMap next = state.with(LIT, true);
    assert next != state;
    assert next.get(FACING) == 0 && next.get(LIT) && next.identity() == 4;

    ImmutablePropertyMap next2 = next.with(FACING, 3);
    assert next2.get(FACING) == 3 && next2.get(LIT) && next2.identity() == 7;

    ImmutablePropertyMap next3 = next2.with(LIT, false);
    assert next3.get(FACING) == 3 && !next3.get(LIT) && next3.identity() == 3;

    System.out.println("  ✓ testWithStateTransition");
  }

  private static void testGlobalPalette() {
    Palette<ImmutablePropertyMap> global = new Palette<>();

    PropertyDef stone = new PropertyDef();
    stone.collectStates(global);
    assert stone.stateCount() == 1;

    PropertyDef torch = new PropertyDef().put(LIT);
    torch.collectStates(global);
    assert torch.stateCount() == 2;

    PropertyDef fence = new PropertyDef().put(FACING).put(WATERLOGGED);
    fence.collectStates(global);
    assert fence.stateCount() == 8;

    // IDs are globally contiguous
    assert global.size() == 11;
    assert global.get(0).identity() == 0;  // stone
    assert global.get(1).identity() == 1;  // torch (false)
    assert global.get(2).identity() == 2;  // torch (true)
    assert global.get(3).identity() == 3;  // fence (0, false)

    // neighbor transitions work
    ImmutablePropertyMap fenceState = global.get(3); // fence (0, F)
    assert fenceState.with(FACING, 3).identity() == 6; // (3, F)

    System.out.println("  ✓ testGlobalPalette");
  }
}
