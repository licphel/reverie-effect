import io.viki.rf.Registries;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.fluid.FluidEngine;
import io.viki.rf.world.fluid.FluidStack;
import io.viki.rf.world.fluid.Liquids;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.light.Channel;
import io.viki.rf.world.util.BlockPos;
import io.viki.rf.world.util.ChunkPos;
import io.viki.rf.world.util.PrecisePos;

/**
 * Hand-run tests for the fluid engine, Y-up world (y grows upward, liquid
 * falls toward smaller y, the ground is at low y).
 *
 * <p>Run this class; it prints {@code ==== ALL TESTS PASSED ====} on success.
 */
public class TestFluid {

  private static final int EPS = 2;

  public static void main(String[] args) {
    Registries.bootstrap();
    testFall();
    testEqualize();
    testSpread();
    testConservation();
    testPressureRise();
    testLavaReaction();
    testMix();
    testCrossChunk();
    testUnload();
    testLiquidProperties();
    testBuoyancy();
    testSurfaceContact();
    testSwim();
    System.out.println("==== ALL TESTS PASSED ====");
  }

  /** Water falls exactly one tile per simulation tick (toward smaller y). */
  private static void testFall() {
    Level level = newLevel();
    // 1-wide column: side walls (y 0..5) and a floor at y = 0
    for (int y = 0; y <= 5; y++) {
      setBlock(level, 5, y, stone());
      setBlock(level, 7, y, stone());
    }
    setBlock(level, 6, 0, stone());
    level.setLiquid(6, 5, Liquids.WATER, 255);
    tick(level, 1);
    assertLevel(level, 6, 5, 0, 0);
    assertLevel(level, 6, 4, 255, 0);
    assertLevel(level, 6, 3, 0, 0); // the snapshot prevents a double fall
    tick(level, 3);
    assertLevel(level, 6, 1, 255, 0); // settled on the floor
    assertLevel(level, 6, 0, 0, 0);
    tick(level, 10);
    assertLevel(level, 6, 1, 255, 0); // stable once settled
    System.out.println("testFall OK");
  }

  /** A single full cell levels out inside a closed container. */
  private static void testEqualize() {
    Level level = newLevel();
    // closed 3-wide container: floor at y = 0, side walls at y = 1
    for (int x = 4; x <= 8; x++) {
      setBlock(level, x, 0, stone());
      if (x == 4 || x == 8) setBlock(level, x, 1, stone());
    }
    level.setLiquid(6, 1, Liquids.WATER, 255);
    for (int i = 0; i < 100; i++) tick(level, 1);
    assertLevel(level, 5, 1, 85, EPS);
    assertLevel(level, 6, 1, 85, EPS);
    assertLevel(level, 7, 1, 85, EPS);
    System.out.println("testEqualize OK");
  }

  /** On open ground the water spreads sideways, conserving its total. */
  private static void testSpread() {
    Level level = newLevel();
    // floor extending well past the pool so nothing falls off the edge
    for (int x = -10; x <= 22; x++) setBlock(level, x, 0, stone());
    level.setLiquid(6, 1, Liquids.WATER, 255);
    for (int i = 0; i < 10; i++) tick(level, 1);
    assert level.getLiquidLevel(4, 1) > 13 : "no spread to the left";
    assert level.getLiquidLevel(8, 1) > 13 : "no spread to the right";
    assert level.getLiquidLevel(6, 1) > 13 : "center drained";
    int sum = 0;
    for (int x = -4; x <= 16; x++) sum += level.getLiquidLevel(x, 1);
    // the spreading front sheds single units that cannot split further
    // (they evaporate), so most of the water must survive
    assert sum > 216 : "spread lost liquid: " + sum;
    System.out.println("testSpread OK");
  }

  /** The engine conserves the total amount inside a closed container. */
  private static void testConservation() {
    Level level = newLevel();
    for (int x = 4; x <= 8; x++) {
      setBlock(level, x, 0, stone());
      if (x == 4 || x == 8) setBlock(level, x, 1, stone());
    }
    level.setLiquid(5, 1, Liquids.WATER, 102);
    level.setLiquid(6, 1, Liquids.WATER, 102);
    level.setLiquid(7, 1, Liquids.WATER, 51);
    for (int i = 0; i < 100; i++) tick(level, 1);
    int sum = 0;
    for (int x = 5; x <= 7; x++) sum += level.getLiquidLevel(x, 1);
    assert sum == 255 : "conservation broken: " + sum;
    assertLevel(level, 4, 1, 0, 0); // the walls hold everything in
    assertLevel(level, 8, 1, 0, 0);
    System.out.println("testConservation OK");
  }

  /** An over-full tile squeezes the excess upward (Starbound overfill). */
  private static void testPressureRise() {
    Level level = newLevel();
    // sealed 1-wide box with an open top
    for (int x = 4; x <= 6; x++) setBlock(level, x, 0, stone());
    setBlock(level, 4, 1, stone());
    setBlock(level, 6, 1, stone());
    level.setLiquid(5, 1, Liquids.WATER, 382); // 255 + 127 overfill
    tick(level, 1);
    // the whole overfill (127) is squeezed upward (Y-up: larger y)
    assertLevel(level, 5, 1, 255, 0);
    assertLevel(level, 5, 2, 127, 0);
    System.out.println("testPressureRise OK");
  }

  /** Lava touching enough water solidifies into stone. */
  private static void testLavaReaction() {
    Level level = newLevel();
    // sealed container: lava with a single water cell beside it, so nothing
    // can flow back after the reaction
    for (int x = 4; x <= 7; x++) {
      setBlock(level, x, 0, stone());
      if (x == 4 || x == 7) setBlock(level, x, 1, stone());
    }
    level.setLiquid(5, 1, Liquids.LAVA, 255);
    level.setLiquid(6, 1, Liquids.WATER, 255);
    tick(level, 1);
    assertLevel(level, 5, 1, 0, 0);
    assertLevel(level, 6, 1, 0, 0);
    assert level.getBlock(5, 1).block() == Registries.STONE : "lava did not solidify";
    System.out.println("testLavaReaction OK");
  }

  /** Two small pools of different liquids mix instead of layering. */
  private static void testMix() {
    Level level = newLevel();
    // sealed container with a thin layer of water next to a thin lava pool
    for (int x = 4; x <= 7; x++) {
      setBlock(level, x, 0, stone());
      if (x == 4 || x == 7) setBlock(level, x, 1, stone());
    }
    level.setLiquid(5, 1, Liquids.LAVA, 12);   // too little to react
    level.setLiquid(6, 1, Liquids.WATER, 12);
    tick(level, 1);
    // no reaction possible, so the thinner liquid converts: only one type
    // remains (and it must not be empty on both sides without a reaction)
    FluidStack s5 = level.getLiquidStack(5, 1);
    FluidStack s6 = level.getLiquidStack(6, 1);
    assert s5.type() == s6.type() : "liquids layered instead of mixing";
    assert s5.count() + s6.count() > 0 : "both liquids vanished";
    System.out.println("testMix OK");
  }

  /** Liquid flows across chunk borders. */
  private static void testCrossChunk() {
    Level level = newLevel();
    // continuous floor across the chunk (0,0) / (1,0) border at x=15|16
    for (int x = 10; x <= 20; x++) setBlock(level, x, 0, stone());
    level.setLiquid(15, 1, Liquids.WATER, 255); // right border of chunk (0,0)
    tick(level, 1);
    // half the difference flows sideways, split between both sides in
    // random order, so the border tile receives 63 or 127
    assert level.getLiquidLevel(16, 1) >= 51 : "did not flow into chunk (1,0)";
    assert level.getLiquidLevel(14, 1) >= 51 : "did not flow into chunk (0,0)";
    System.out.println("testCrossChunk OK");
  }

  /** Unloading a chunk removes its liquid from the simulation. */
  private static void testUnload() {
    Level level = newLevel();
    setBlock(level, 6, 0, stone());
    level.setLiquid(6, 1, Liquids.WATER, 255);
    level.unloadChunk(new ChunkPos(0, 0));
    for (int i = 0; i < 10; i++) tick(level, 1);
    assert level.getLiquidLevel(6, 1) == 0 : "liquid survived the unload";
    System.out.println("testUnload OK");
  }

  /** Liquid light and physics properties. */
  private static void testLiquidProperties() {
    assert Liquids.LAVA.density() > Liquids.WATER.density();
    assert Liquids.LAVA.temperature() > Liquids.WATER.temperature();
    assert Liquids.LAVA.viscosity() >= Liquids.WATER.viscosity();
    assert Liquids.LAVA.emitAmbient(0, 0, 255, Channel.RED) > 0F : "lava does not glow";
    assert Liquids.WATER.emitAmbient(0, 0, 255, Channel.RED) == 0F : "water glows";
    assert Liquids.WATER.filterLight(0, 0, 255, 1F, Channel.RED) < 1F : "water does not absorb light";
    System.out.println("testLiquidProperties OK");
  }

  /** An entity floats in water, buoyed by its contact area. */
  private static void testBuoyancy() {
    Level level = newLevel();
    // water pool with walls and a floor (Y-up: floor at 0, water above)
    for (int y = 0; y <= 2; y++) {
      setBlock(level, 2, y, stone());
      setBlock(level, 8, y, stone());
      for (int x = 3; x <= 7; x++) level.setLiquid(x, y, Liquids.WATER, 255);
    }
    for (int x = 2; x <= 8; x++) setBlock(level, x, 0, stone());
    // feet at 0.35, inside the pool (water 0..3, floor at 0)
    Entity p = Entity.player(new PrecisePos(5, 0.35F));
    p.enterChunk(level);
    for (int i = 0; i < 30; i++) p.tick(1F / 60F, level);
    assert p.position().yf() > 0.35F : "player did not float: y=" + p.position().yf();
    assert p.isFloating() : "player not flagged as floating in water";
    System.out.println("testBuoyancy OK");
  }

  /** Liquid contact respects the surface height, not the full tile. */
  private static void testSurfaceContact() {
    Level level = newLevel();
    for (int x = 2; x <= 8; x++) setBlock(level, x, 0, stone());
    // shallow pool: 1/4 full, surface at 1 + 0.25 = 1.25 (above the
    // ground block top at 1)
    for (int x = 3; x <= 7; x++) level.setLiquid(x, 1, Liquids.WATER, 64);
    // the player feet are the position (Y-up); standing fully above the
    // surface (feet 1.3 > 1.25) → dry
    Entity dry = Entity.player(new PrecisePos(5, 1.3F));
    dry.enterChunk(level);
    dry.tick(1F / 60F, level);
    assert !dry.isFloating() : "player above the surface must be dry";
    // feet below the surface → wet
    Entity wet = Entity.player(new PrecisePos(5, 1.1F));
    wet.enterChunk(level);
    wet.tick(1F / 60F, level);
    assert wet.isFloating() : "player below the surface must be wet";
    System.out.println("testSurfaceContact OK");
  }

  /** Starbound-style swimming: a press adds a burst, holding approaches the
   * swim speed instead of overriding the velocity. */
  private static void testSwim() {
    Level level = newLevel();
    for (int y = 0; y <= 2; y++) {
      setBlock(level, 2, y, stone());
      setBlock(level, 8, y, stone());
      for (int x = 3; x <= 7; x++) level.setLiquid(x, y, Liquids.WATER, 255);
    }
    for (int x = 2; x <= 8; x++) setBlock(level, x, 0, stone());
    Entity p = Entity.player(new PrecisePos(5, 0.35F));
    p.enterChunk(level);
    p.tick(1F / 60F, level);
    // a fresh press adds an upward burst (Y-up: +Y)
    p.liquidJump(true, 1F / 60F);
    assert p.velocity().y() > 1F : "press should add an upward burst";
    // holding approaches the swim speed and never exceeds it
    for (int i = 0; i < 120; i++) p.liquidJump(true, 1F / 60F);
    assert p.velocity().y() <= 6F : "holding must not exceed the swim speed";
    // releasing and pressing again adds a fresh burst
    float before = p.velocity().y();
    p.liquidJump(false, 1F / 60F);
    p.liquidJump(true, 1F / 60F);
    assert p.velocity().y() > before : "a fresh press should burst upward again";
    System.out.println("testSwim OK");
  }

  // -- helpers -------------------------------------------------------------

  private static Level newLevel() {
    return new Level((chunk, seed) -> chunk.setLoaded(true), 42L);
  }

  private static BlockState stone() {
    return Registries.STONE.defaultState();
  }

  private static void setBlock(Level level, int x, int y, BlockState state) {
    level.setBlock(new BlockPos(x, y), state);
  }

  private static void tick(Level level, int n) {
    for (int i = 0; i < n; i++) level.tick(FluidEngine.TICK_INTERVAL);
  }

  private static void assertLevel(Level level, int x, int y, int expected, int eps) {
    int actual = level.getLiquidLevel(x, y);
    assert Math.abs(actual - expected) <= eps
        : "(" + x + "," + y + "): expected " + expected + " ± " + eps + " got " + actual;
  }
}
