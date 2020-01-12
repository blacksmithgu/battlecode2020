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

    /**
     * The 8 possible cardinal directions.
     **/
    public static Direction[] DIRECTIONS = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST
    };

    public static boolean canBuild(RobotController rc, RobotType robotType) {
        for (Direction direction : DIRECTIONS) {
            if (rc.canBuildRobot(robotType, direction)) {
                return true;
            }
        }
        return false;
    }

    public static void buildInAnyDirection(RobotController rc, RobotType robotType) {
        for (Direction direction : DIRECTIONS) {
            if (rc.canBuildRobot(robotType, direction)) {
                try {
                    rc.buildRobot(robotType, direction);
                } catch (GameActionException e) {
                    System.out.println(e.getStackTrace());
                }
            }
        }
    }

    //prefers a specific direction but if the spot is filled then builds in a random direction
    public static void buildInDirection(RobotController rc, RobotType robotType, Direction dir) {
        if (rc.canBuildRobot(robotType, dir)) {
            try {
                rc.buildRobot(robotType, dir);
            } catch (GameActionException e) {
                System.out.println(e.getStackTrace());
            }
        } else {
            for (Direction direction : DIRECTIONS) {
                if (rc.canBuildRobot(robotType, direction)) {
                    try {
                        rc.buildRobot(robotType, direction);
                    } catch (GameActionException e) {
                        System.out.println(e.getStackTrace());
                    }
                }
            }
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