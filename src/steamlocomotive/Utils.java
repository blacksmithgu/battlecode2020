package steamlocomotive;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Utils {
    /** The 8 possible cardinal directions. **/
    static Direction[] DIRECTIONS = {
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
        for(Direction direction: DIRECTIONS) {
            if(rc.canBuildRobot(robotType, direction)) {
                return true;
            }
        }
        return false;
    }

    public static void buildInAnyDirection(RobotController rc, RobotType robotType) {
        for(Direction direction: DIRECTIONS) {
            if(rc.canBuildRobot(robotType, direction)) {
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
        if(rc.canBuildRobot(robotType, dir)) {
            try {
                rc.buildRobot(robotType, dir);
            } catch (GameActionException e) {
                System.out.println(e.getStackTrace());
            }
        } else {
            for(Direction direction: DIRECTIONS) {
                if(rc.canBuildRobot(robotType, direction)) {
                    try {
                        rc.buildRobot(robotType, direction);
                    } catch (GameActionException e) {
                        System.out.println(e.getStackTrace());
                    }
                }
            }
        }
    }

    public static Set<MapLocation> senseableLocations(RobotController rc) {
        int visionRadius = (int) Math.sqrt(rc.getType().sensorRadiusSquared);
        int ourX = rc.getLocation().x;
        int ourY = rc.getLocation().y;
        Set<MapLocation> toReturn = new HashSet<>();
        for(int x = -visionRadius; x < visionRadius; x++) {
            for(int y = - visionRadius; y < visionRadius; y++) {
                MapLocation location = new MapLocation(ourX + x, ourY+y);
                if(rc.canSenseLocation(new MapLocation(ourX + x, ourY+y))) {
                    toReturn.add(location);
                }
            }
        }
        return toReturn;
    }

    public static Direction directionToPoint(RobotController rc, MapLocation point) {
        int deltaX = rc.getLocation().x - point.x;
        int deltaY = rc.getLocation().y - point.y;

        if(deltaX > 0 && deltaY > 0) {
            return Direction.SOUTHWEST;
        }
        if(deltaX > 0 && deltaY == 0) {
            return Direction.WEST;
        }
        if(deltaX > 0 && deltaY < 0) {
            return Direction.NORTHWEST;
        }
        if(deltaX == 0 && deltaY > 0 ) {
            return Direction.SOUTH;
        }
        if(deltaX == 0 && deltaY == 0) {
            return Direction.CENTER;
        }
        if(deltaX == 0 && deltaY < 0) {
            return Direction.NORTH;
        }
        if(deltaX < 0 && deltaY > 0) {
            return Direction.SOUTHEAST;
        }
        if(deltaX < 0 && deltaY == 0) {
            return Direction.EAST;
        }
        return Direction.NORTHEAST;
    }

    public static Direction directionFromPoint(RobotController rc, MapLocation point) {
        return invertDirection(directionToPoint(rc, point));
    }

    private static Direction invertDirection(Direction direction) {
        switch (direction) {
            case NORTH:
                return Direction.SOUTH;
            case NORTHEAST:
                return Direction.SOUTHWEST;
            case EAST:
                return Direction.WEST;
            case SOUTHEAST:
                return Direction.NORTHWEST;
            case SOUTH:
                return Direction.NORTH;
            case SOUTHWEST:
                return Direction.NORTHEAST;
            case WEST:
                return Direction.EAST;
            case NORTHWEST:
                return Direction.SOUTHEAST;
            default:
                return Direction.CENTER;
        }
    }
}