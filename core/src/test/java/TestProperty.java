import io.viki.momentum.util.Palette;
import io.viki.momentum.math.Direction2D;
import io.viki.rf.util.property.ImmutablePropertyMap;
import io.viki.rf.util.property.Property;
import io.viki.rf.util.property.PropertyDef;

import java.util.List;

public class TestProperty {
  static void main() {
    Property<Boolean> LIT = Property.of("lit", false, Property.bool());
    Property<Direction2D> FACING = Property.of("facing", Direction2D.NORTH, List.of(Direction2D.ALL));
    Property<Integer> GROWTH = Property.of("growth", 0, Property.rangedInt(0, 4));
    Property<Direction2D> FACING_CARDINAL = Property.of("facing", Direction2D.NORTH, List.of(Direction2D.CARDINAL));

    PropertyDef def1 = new PropertyDef()
        .put(LIT)
        .put(FACING)
        .put(GROWTH);

    PropertyDef def2 = new PropertyDef()
        .put(FACING_CARDINAL);

    PropertyDef def3 = new PropertyDef();

    PropertyDef def4 = new PropertyDef()
        .put(FACING_CARDINAL)
        .put(GROWTH);

    Palette<ImmutablePropertyMap> palette = new Palette<>();
    def1.collectStates(palette);
    def2.collectStates(palette);
    def3.collectStates(palette);
    def4.collectStates(palette);

    ImmutablePropertyMap map = def1.defaultMap();
    map = map.with(LIT, true).with(GROWTH, 3);

    int id = map.identity();
    int id2 = palette.searchIndex(map);

    assert id == id2;

    System.out.println("==== ALL TESTS PASSED ====");
  }
}
