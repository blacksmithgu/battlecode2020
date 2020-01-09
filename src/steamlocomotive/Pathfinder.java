package steamlocomotive;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import java.util.Set;

/**
 * Stateful pathfinder which stores previously seen map state and then does a
 * Djikstra's/A* search towards a set of goal states.
 */
public class Pathfinder {

    /** add a soup location to the map */
    public static void addSoupLocation(MapLocation location) {

    }

    /** set the location of the HQ */
    public static void setHQLocation(MapLocation location) {

    }

    /** get the location of the HQ */
    public static MapLocation getHQLocation(){
        return null;
    }

    /** get all known soup locations */
    public static Set<MapLocation> getSoupLocations() {
        return null;
    }


    /** Update the pathfinder state by actively sensing the area around the robot. */
    public static void update(RobotController rc) {
    }
    /**
     * Given a list of goal locations, find the movement direction which moves
     * towards the closest goal location.
     */
    public static Direction findMove(Set<MapLocation> targets) {
        // TODO: There may be no move which works due to obstacles, return center in that
        // case for now but consider using Optional.
        return null;
    }

    /**
     * A convienence function which wraps {@link #findMove(Set)}. Finds the optimal
     * move and then has the robot make that move.
     */
    public static void move(RobotController rc, Set<MapLocation> targets) throws GameActionException {
        Direction dir = findMove(targets);
        rc.move(dir);
    }
}
