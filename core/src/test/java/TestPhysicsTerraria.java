import io.viki.rf.Registries;
import io.viki.rf.world.block.TileShape;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.BlockPos;
import io.viki.rf.world.util.PrecisePos;

/**
 * Hand-run behavior tests for the entity physics (SBPhyObj with
 * VoxelOutline staircase slopes), Y-up world (y grows upward; the ground
 * is at low y; the player position is the feet). Covers slope climbing
 * without bouncing, 1-block platform ledges, platforms, and wall
 * contact (no sinking / tunneling).
 */
public class TestPhysicsTerraria {

  public static void main(String[] args) {
    Registries.bootstrap();
    testSlopeUpNoBounce();
    testStepUpPlatformLedge();
    testStepDownLedge();
    testPlatformOnGround();
    testRestAgainstWall();
    testRideWallEdgeThenWalk();
    testBlockPlacedOnPlayer();
    System.out.println("==== ALL TERRARIA PHYSICS TESTS PASSED ====");
  }

  /** Pressing against a wall (body touching its edge) must not sink. */
  private static void testRestAgainstWall() {
    Level level = newLevel();
    for (int x = 2; x <= 8; x++) setBlock(level, x, 0, stone());
    for (int y = 1; y <= 3; y++) setBlock(level, 2, y, stone()); // wall left
    Entity p = Entity.player(new PrecisePos(3.0F, 1F));
    p.enterChunk(level);
    p.setVelocity(-4F, 0F);
    for (int i = 0; i < 60; i++) p.tick(1F / 60F, level);
    assert p.position().xf() >= 3.0F - 0.01F : "should not pass through the wall, x=" + p.position().xf();
    assert Math.abs(p.position().yf() - 1F) < 0.05F : "should not sink, feet=" + p.position().yf();
    assert p.onGround() : "should stay on the ground";
    System.out.println("testRestAgainstWall OK");
  }

  /** Landing on a wall edge then walking left slides off and lands. */
  private static void testRideWallEdgeThenWalk() {
    Level level = newLevel();
    for (int x = -8; x <= 8; x++) setBlock(level, x, 0, stone());
    for (int y = 1; y <= 3; y++) setBlock(level, 3, y, stone());
    Entity p = Entity.player(new PrecisePos(2.5F, 5F)); // body overlaps the wall column
    p.enterChunk(level);
    for (int i = 0; i < 120; i++) p.tick(1F / 60F, level);
    assert p.position().yf() > 3.9F : "should ride the wall top, feet=" + p.position().yf();
    p.setVelocity(-4F, 0F);
    for (int i = 0; i < 90; i++) p.tick(1F / 60F, level);
    assert Math.abs(p.position().yf() - 1F) < 0.05F : "should land on the ground, feet=" + p.position().yf();
    assert p.position().xf() < 0F : "should have walked left, x=" + p.position().xf();
    assert p.onGround() : "should be on the ground";
    System.out.println("testRideWallEdgeThenWalk OK");
  }

  /** A block placed overlapping the body pushes the body out, no bounce. */
  private static void testBlockPlacedOnPlayer() {
    Level level = newLevel();
    for (int x = 2; x <= 8; x++) setBlock(level, x, 0, stone());
    Entity p = Entity.player(new PrecisePos(3.0F, 1F));
    p.enterChunk(level);
    for (int i = 0; i < 30; i++) p.tick(1F / 60F, level);
    setBlock(level, 3, 1, stone()); // placed into the body [3,4]x[1,3.65]
    float maxVy = 0F;
    for (int i = 0; i < 30; i++) {
      p.tick(1F / 60F, level);
      maxVy = Math.max(maxVy, Math.abs(p.velocity().y()));
    }
    assert p.position().yf() > 1.5F : "should be pushed onto the block, feet=" + p.position().yf();
    assert maxVy < 10F : "pushing out must not launch the body, max vy=" + maxVy;
    System.out.println("testBlockPlacedOnPlayer OK");
  }

  /** Walking up a ↗ slope must never bounce: the vertical velocity stays
   * small (Terraria SlopeCollision zeroes it on the surface). */
  private static void testSlopeUpNoBounce() {
    Level level = newLevel();
    for (int x = 3; x <= 10; x++) setBlock(level, x, 0, stone());
    for (int x = 5; x <= 7; x++) setSlope(level, x, 1, TileShape.SLOPE_LEFT_DOWN);
    for (int x = 8; x <= 10; x++) setBlock(level, x, 1, stone());
    Entity p = Entity.player(new PrecisePos(2, 1F));
    p.enterChunk(level);
    p.setVelocity(4F, 0F);
    float maxVy = 0F;
    for (int i = 0; i < 90; i++) {
      p.tick(1F / 60F, level);
      maxVy = Math.max(maxVy, Math.abs(p.velocity().y()));
    }
    float feet = p.position().yf();
    assert Math.abs(feet - 2F) < 0.1F : "should stand on the high ground, feet " + feet;
    assert p.position().xf() > 5F : "should have crossed the slope, x=" + p.position().xf();
    assert maxVy < 5F : "bounced up the slope: max |vy| " + maxVy;
    System.out.println("testSlopeUpNoBounce OK");
  }

  /** A 1-block platform ledge is stepped up automatically (StepUp). */
  private static void testStepUpPlatformLedge() {
    Level level = newLevel();
    for (int x = 3; x <= 4; x++) setBlock(level, x, 0, stone());
    for (int x = 5; x <= 7; x++) setBlock(level, x, 1, platform());
    Entity p = Entity.player(new PrecisePos(2, 1F));
    p.enterChunk(level);
    p.setVelocity(4F, 0F);
    for (int i = 0; i < 90; i++) p.tick(1F / 60F, level);
    float feet = p.position().yf();
    assert p.position().xf() > 5F : "should have crossed onto the platform, x=" + p.position().xf();
    assert Math.abs(feet - 2F) < 0.05F : "should stand on the platform, feet " + feet;
    assert p.onGround() : "should be on the ground on the platform";
    System.out.println("testStepUpPlatformLedge OK");
  }

  /** Walking off a 1-block ledge lands on the lower ground (StepDown or
   * a plain fall — either way the body ends up on the lower ground). */
  private static void testStepDownLedge() {
    Level level = newLevel();
    for (int x = 3; x <= 5; x++) setBlock(level, x, 1, stone());   // ledge, top 2
    for (int x = 6; x <= 9; x++) setBlock(level, x, 0, stone());   // ground, top 1
    Entity p = Entity.player(new PrecisePos(4.5F, 2F));
    p.enterChunk(level);
    p.setVelocity(4F, 0F);
    for (int i = 0; i < 90; i++) p.tick(1F / 60F, level);
    float feet = p.position().yf();
    assert p.position().xf() > 6F : "should have walked off the ledge, x=" + p.position().xf();
    assert Math.abs(feet - 1F) < 0.05F : "should stand on the lower ground, feet " + feet;
    assert p.onGround() : "should be on the ground below";
    System.out.println("testStepDownLedge OK");
  }

  /** Platforms: land on top, jump up through, and a fast fall lands on
   * them even without holding down (Terraria: Velocity.Y > 1 lands). */
  private static void testPlatformOnGround() {
    Level level = newLevel();
    for (int x = 3; x <= 7; x++) setBlock(level, x, 0, stone());
    for (int x = 3; x <= 7; x++) setBlock(level, x, 2, platform());
    // a fast fall lands on the platform
    Entity p = Entity.player(new PrecisePos(5, 5F));
    p.enterChunk(level);
    p.setVelocity(0F, -10F);
    for (int i = 0; i < 30; i++) p.tick(1F / 60F, level);
    assert Math.abs(p.position().yf() - 3F) < 0.05F : "fast fall should land on the platform, feet " + p.position().yf();
    assert p.onGround() : "should be on the ground on the platform";
    // jump up through it, fall back and land again
    p.setVelocity(0F, 10F);
    for (int i = 0; i < 30; i++) p.tick(1F / 60F, level);
    assert p.position().yf() > 3.5F : "jump should pass through the platform";
    for (int i = 0; i < 60; i++) p.tick(1F / 60F, level);
    assert Math.abs(p.position().yf() - 3F) < 0.05F : "should land back on the platform, feet " + p.position().yf();
    System.out.println("testPlatformOnGround OK");
  }

  // -- helpers -------------------------------------------------------------

  private static Level newLevel() {
    return new Level((chunk, seed) -> chunk.setLoaded(true), 42L);
  }

  private static BlockState stone() { return Registries.STONE.defaultState(); }
  private static BlockState platform() { return Registries.PLATFORM.defaultState(); }

  private static void setSlope(Level level, int x, int y, byte shape) {
    level.setBlock(new BlockPos(x, y), stone());
    level.setBlockShape(x, y, shape);
  }

  private static void setBlock(Level level, int x, int y, BlockState state) {
    level.setBlock(new BlockPos(x, y), state);
  }
}
