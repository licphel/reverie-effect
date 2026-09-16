import io.viki.rf.Registries;
import io.viki.rf.world.entity.Entity;
import io.viki.rf.world.level.Level;
import io.viki.rf.world.util.BlockPos;
import io.viki.rf.world.util.PrecisePos;

public class TestPhysicsDebug {

  public static void main(String[] args) {
    Registries.bootstrap();
    Level level = new Level((chunk, seed) -> chunk.setLoaded(true), 42L);
    for (int y = -2; y <= 2; y++) level.setBlock(new BlockPos(6, y), Registries.STONE.defaultState());
    for (int x = 3; x <= 5; x++) level.setBlock(new BlockPos(x, -3), Registries.STONE.defaultState());
    Entity p = Entity.player(new PrecisePos(3, -2F));
    p.enterChunk(level);
    p.setVelocity(10F, 0F);
    for (int i = 1; i <= 25; i++) {
      p.tick(1F / 60F, level);
      if (i >= 12) {
        System.out.printf("tick %2d: pos=(%.3f, %.3f) vel=(%.2f, %.2f) onGround=%s%n",
            i, p.position().xf(), p.position().yf(), p.velocity().x(), p.velocity().y(), p.onGround());
      }
    }
  }
}
