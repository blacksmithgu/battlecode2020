package steamlocomotive;

import battlecode.common.RobotController;

/** Base class which all other unit implementations extend. */
public abstract class Unit {
    /** Perform actions on the given turn. */
    public abstract void run(RobotController rc, int turn);
}
