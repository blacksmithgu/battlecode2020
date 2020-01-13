package steamlocomotive;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** General utilities for writing agents. */
public class Utils {

    @FunctionalInterface
    public interface GameConsumer<T> {
        void accept(T value) throws GameActionException;
    }

    /** Returns true if the given direction is cardinal. 1 bytecode. Get wrecked. */
    public static boolean isCardinal(Direction dir) {
        switch (dir) {
            case NORTH:
            case SOUTH:
            case EAST:
            case WEST:
                return true;
            default:
                return false;
        }
    }

    public static void traverseSensable(RobotController rc, GameConsumer<MapLocation> func) throws GameActionException {
        int visionRadius = (int) Math.ceil(Math.sqrt(rc.getType().sensorRadiusSquared));
        int ourX = rc.getLocation().x;
        int ourY = rc.getLocation().y;

        for (int x = -visionRadius; x <= visionRadius; x++) {
            for (int y = -visionRadius; y <= visionRadius; y++) {
                MapLocation location = new MapLocation(ourX + x, ourY + y);
                if (rc.canSenseLocation(location)) {
                    func.accept(location);
                }
            }
        }
    }
}