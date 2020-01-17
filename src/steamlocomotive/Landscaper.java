package steamlocomotive;

import battlecode.common.*;

import java.util.Map;

public class Landscaper extends Unit {

    /** Possible states the landscaper can be in. */
    public enum LandscaperState {
        // WE WILL BUILD A WALL AND MAKE THE BLUE TEAM PAY FOR IT.
        BUILD_WALL,
        // Move towards building the wall. Elect Donald Trump today.
        MOVE_TO_WALL,
        // Bury a detected enemy building.
        BURY_ENEMY,
        // Unbury an ally building (specifically the HQ).
        UNBURY_ALLY,
        // Terraform towards the enemy base.
        TERRAFORM,
        // Lost and without a purpose...
        ROAMING
    }

    /** A transition from one state to another. Marks the target and whether an action has been taken. */
    private static class Transition {
        public LandscaperState target;
        public boolean madeAction;

        public Transition(LandscaperState target, boolean madeAction) {
            this.target = target;
            this.madeAction = madeAction;
        }
    }

    // Current landscaper state.
    private LandscaperState state;
    // Communication object.
    private Bitconnect comms;
    // Our and enemy HQ locations.
    private MapLocation ourHQLoc, enemyHqLoc;
    //wall locations
    private Bitconnect.HQSurroundings wallLocations;
    // Pathfinder object for stateful pathfinding.
    private BugPathfinder pathfinder;
    // The number of steps taken with the current pathfinder.
    private int pathfindSteps;

    public Landscaper(int id) {
        super(id);
        this.state = LandscaperState.ROAMING;
        this.pathfinder = null;
        this.pathfindSteps = 0;
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // Update soup knowledge by scanning surroundings.
        this.scanSurroundings(rc);

        // Swap on current state.
        boolean madeAction;
        do {
            Transition trans;
            switch (this.state) {
                case BUILD_WALL: trans = this.buildWall(rc); break;
                case MOVE_TO_WALL: trans = this.moveToWall(rc); break;
                case BURY_ENEMY: trans = this.buryEnemy(rc); break;
                case UNBURY_ALLY: trans = this.unburyAlly(rc); break;
                case TERRAFORM: trans = this.terraform(rc); break;
                default:
                case ROAMING: trans = this.roaming(rc); break;
            }

            // Reset transient miner state.
            if (this.state != trans.target) {
                this.pathfinder = null;
                this.pathfindSteps = 0;
            }

            this.state = trans.target;
            madeAction = trans.madeAction;
        } while (!madeAction);

        //if wall builder, move towards one of the desired locations
        // Useful for debugging.
        if (this.pathfinder != null) rc.setIndicatorLine(rc.getLocation(), this.pathfinder.goal(), 0, 0, 255);
    }

    public void scanSurroundings(RobotController rc) throws GameActionException {
        // TODO: Do something?
    }

    public Transition buildWall(RobotController rc) throws GameActionException {
        return new Transition(LandscaperState.BUILD_WALL, true);
    }

    public Transition moveToWall(RobotController rc) throws GameActionException {
        return new Transition(LandscaperState.BUILD_WALL, true);
    }

    public Transition buryEnemy(RobotController rc) throws GameActionException {
        return new Transition(LandscaperState.BUILD_WALL, true);
    }

    public Transition unburyAlly(RobotController rc) throws GameActionException {
        return new Transition(LandscaperState.BUILD_WALL, true);
    }

    public Transition terraform(RobotController rc) throws GameActionException {
        return new Transition(LandscaperState.BUILD_WALL, true);
    }

    public Transition roaming(RobotController rc) throws GameActionException {
        return new Transition(LandscaperState.BUILD_WALL, true);
    }

    @Override
    public void onCreation(RobotController rc) {
        comms = new Bitconnect(rc.getMapWidth(), rc.getMapHeight());
        wallLocations = comms.getWallLocations(rc);
    }
}