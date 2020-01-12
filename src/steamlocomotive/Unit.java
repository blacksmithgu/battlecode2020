package steamlocomotive;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

/**
 * Base class which all other unit implementations extend.
 */
public abstract class Unit {
    protected static MapLocation HQ_LOCATION;

    /**
     * Perform actions on the given turn.
     */
    public abstract void run(RobotController rc, int turn) throws GameActionException;
}
