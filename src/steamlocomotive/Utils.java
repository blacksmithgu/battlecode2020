package steamlocomotive;

import battlecode.common.*;

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
}