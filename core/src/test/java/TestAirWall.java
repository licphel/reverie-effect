import io.viki.rf.Registries;
import io.viki.rf.world.block.TileShape;
import io.viki.rf.world.block.BlockState;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.BlockPos;
import io.viki.rf.world.util.PrecisePos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Repro: walking up a slope under a low ceiling stops at an "air wall". */
public class TestAirWall {

  @BeforeAll
  static void bootstrap() {
    Registries.bootstrap();
  }

  /** Slope (5..7,1) + ceiling block (6,4). */
  @Test
  void slopeUnderCeiling() {
    Level level = newLevel();
    for (int x = 3; x <= 10; x++) setBlock(level, x, 0, stone());
    for (int x = 5; x <= 7; x++) setSlope(level, x, 1, TileShape.SLOPE_LEFT_DOWN);
    setBlock(level, 6, 4, stone()); // ceiling over the slope
    Entity p = Entity.player(new PrecisePos(2, 1F));
    p.enterChunk(level);
    p.setVelocity(4F, 0F);
    for (int i = 0; i < 90; i++) {
      p.tick(1F / 60F, level);
      if (i % 5 == 0 || i > 85) {
        System.out.printf("  t=%3d feet=(%.3f, %.3f) top=%.3f vel=(%.2f, %.2f) ground=%s%n",
            i, p.position().xf(), p.position().yf(), p.bounds().maxY(),
            p.velocity().x(), p.velocity().y(), p.onGround());
      }
    }
    System.out.println("END slopeUnderCeiling: feet=" + p.position().yf() + " top=" + p.bounds().maxY());
  }

  /** 1-block step (6,1) + ceiling (6,3): rise 1.0, head room blocked. */
  @Test
  void stepUnderCeiling() {
    Level level = newLevel();
    for (int x = 3; x <= 8; x++) setBlock(level, x, 0, stone());
    setBlock(level, 6, 1, stone());
    setBlock(level, 6, 3, stone()); // ceiling 1 above the step top
    Entity p = Entity.player(new PrecisePos(2, 1F));
    p.enterChunk(level);
    p.setVelocity(4F, 0F);
    for (int i = 0; i < 90; i++) {
      p.tick(1F / 60F, level);
      if (i % 5 == 0 || i > 85) {
        System.out.printf("  t=%3d feet=(%.3f, %.3f) top=%.3f vel=(%.2f, %.2f) ground=%s%n",
            i, p.position().xf(), p.position().yf(), p.bounds().maxY(),
            p.velocity().x(), p.velocity().y(), p.onGround());
      }
    }
    System.out.println("END stepUnderCeiling: feet=" + p.position().yf() + " top=" + p.bounds().maxY());
  }

  // -- helpers -------------------------------------------------------------

  private static Level newLevel() {
    return new Level((chunk, seed) -> chunk.setLoaded(true), 42L);
  }

  private static BlockState stone() { return Registries.STONE.defaultState(); }

  private static void setSlope(Level level, int x, int y, byte shape) {
    level.setBlock(new BlockPos(x, y), stone());
    level.setBlockShape(x, y, shape);
  }

  private static void setBlock(Level level, int x, int y, BlockState state) {
    level.setBlock(new BlockPos(x, y), state);
  }
}
