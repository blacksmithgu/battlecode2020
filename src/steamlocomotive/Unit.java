package steamlocomotive;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import java.util.Random;

/**
 * Base class which all other unit implementations extend.
 */
public abstract class Unit {
    // A per-unit Random generator seeded by the unit ID.
    protected Random rng;

    // The global unique ID of this unit.
    protected int id;

    public Unit(int id) {
        this.id = id;
        this.rng = new Random(id);
    }

    /**
     * Perform actions on the given turn.
     */
    public abstract void run(RobotController rc, int turn) throws GameActionException;

    /**
     * Perform any initial agent actions when the agent is created.
     */
    public void onCreation(RobotController rc) throws GameActionException { }
}
