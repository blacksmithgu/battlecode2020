package steamlocomotive;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import org.junit.*;
import static org.junit.Assert.*;

/** Simple tests for utility methods. **/
public class UtilsTests {
    @Test
    public void testInvertDirection() {
        assertEquals(Direction.CENTER, Utils.invertDirection(Direction.CENTER));
        assertEquals(Direction.NORTH, Utils.invertDirection(Direction.SOUTH));
        assertEquals(Direction.EAST, Utils.invertDirection(Direction.WEST));
    }

    @Test
    public void testDirectionTo() {
        assertEquals(Direction.NORTH, Utils.directionTo(new MapLocation(0, 0), new MapLocation(0, 4)));
        assertEquals(Direction.NORTHEAST, Utils.directionTo(new MapLocation(0, 0), new MapLocation(1, 4)));
        assertEquals(Direction.WEST, Utils.directionTo(new MapLocation(2, 2), new MapLocation(0, 2)));
    }
}
