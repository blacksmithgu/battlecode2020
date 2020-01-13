package steamlocomotive;

import battlecode.common.*;

import java.util.Arrays;

public class Miner extends Unit {
    /**
     * The amount of soup in inventory before the miner should return.
     */
    public static int INVENTORY_RETURN_SIZE = (RobotType.MINER.soupLimit / GameConstants.SOUP_MINING_RATE) * GameConstants.SOUP_MINING_RATE;

    /**
     * The number of soup representatives that we keep track.
     */
    public static int TRACKED_SOUP_COUNT = 3;

    /**
     * The square distance a soup can be within the representative to be considered part of it's clusters.
     */
    public static int REPRESENTATIVE_THRESHOLD = RobotType.MINER.sensorRadiusSquared;

    /**
     * How long to travel towards random points when roaming; after this many steps, a new random target is chosen.
     */
    public static int ROAM_SEGMENT_LENGTHS = 15;

    /** The possible miner states the miner can be in. */
    public enum MinerState {
        /** The miner is dropping off resources at HQ. */
        DROPOFF,
        /** The miner is traveling to a mine. */
        TRAVEL,
        /** The miner is actively mining a vein. */
        MINE,
        /** The miner is roaming looking for soup. */
        ROAMING,
        // TODO: Add states for refinery building, vaporator building, and so on.
    }

    /** A transition from one state to another. Marks the target and whether an action has been taken. */
    private class Transition {
        public MinerState target;
        public boolean madeAction;

        public Transition(MinerState target, boolean madeAction) {
            this.target = target;
            this.madeAction = madeAction;
        }
    }

    // The mode that the miner is currently in.
    private MinerState state;
    // Pathfinder for going to a location;
    private BugPathfinder pathfinder;
    // Contains up to TRACKED_SOUP_COUNT valid soup representatives we can visit.
    private MapLocation[] soupReps;
    // The location of the HQ we are dumping resources at.
    private MapLocation refinery;

    public Miner(int id) {
        super(id);
        this.pathfinder = null;
        this.soupReps = new MapLocation[TRACKED_SOUP_COUNT];
        this.refinery = null;
        this.state = MinerState.ROAMING;
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
                case DROPOFF: trans = this.dropoff(rc); break;
                case TRAVEL: trans = this.travel(rc); break;
                case MINE: trans = this.mining(rc); break;
                default:
                case ROAMING: trans = this.roaming(rc); break;
            }

            System.out.println(this.state + " -> " + trans.target);

            // Reset transient miner state.
            if (this.state != trans.target) {
                this.pathfinder = null;
            }

            this.state = trans.target;
            madeAction = trans.madeAction;
        } while (!madeAction);

        // Useful for debugging.
        if (this.pathfinder != null) rc.setIndicatorDot(this.pathfinder.goal(), 255, 0, 0);
    }

    /** Update soup cluster and dropoff state. */
    public void scanSurroundings(RobotController rc) throws GameActionException {
        // Check and clear representatives if they are no longer present.
        for (int i = 0; i < soupReps.length; i++) {
            MapLocation rep = soupReps[i];
            if (rep != null && rc.canSenseLocation(rep) && rc.senseSoup(rep) <= 0) {
                soupReps[i] = null;
            }
        }

        // Soup memory; track recently seen soup clusters.
        Utils.traverseSensable(rc, loc -> {
            int soup = rc.senseSoup(loc);
            if (soup == 0) return;

            // Ignore soup which is already close to a representative.
            for (int i = 0; i < soupReps.length; i++) {
                if (soupReps[i] != null && loc.distanceSquaredTo(soupReps[i]) <= REPRESENTATIVE_THRESHOLD) {
                    // TODO: Swap out for closer soup for more optimal pathfinding.
                    return;
                }
            }

            // Try to designate as a representative. If there is a slot, fill it.
            for (int i = 0; i < soupReps.length; i++) {
                if(soupReps[i] == null) {
                    soupReps[i] = loc;
                    return;
                }
            }

            // Otherwise replace randomly.
            this.soupReps[this.rng.nextInt(TRACKED_SOUP_COUNT)] = loc;
        });
    }

    /** Implements roaming behavior, where the miner roams until it finds soup somewhere. */
    public Transition roaming(RobotController rc) throws GameActionException {
        // TODO: Roaming targets may be unreachable, so choose a new target after X steps.

        // If there is nonzero soup we are aware of, transition to traveling to it.
        for (int i = 0; i < soupReps.length; i++) {
            if (soupReps[i] != null) return new Transition(MinerState.TRAVEL, false);
        }

        // If the pathfinder is inactive or finished, pick a new random location to pathfind to.
        if (this.pathfinder == null || this.pathfinder.finished(rc.getLocation())) {
            // TODO: More intelligent target selection. We choose randomly for now.
            MapLocation target = new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight()));

            this.pathfinder = this.newPathfinder(target, true);
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(MinerState.ROAMING, true);
    }

    /** Implements mining behavior, where a miner is actively next to some soup and is harvesting it. */
    public Transition mining(RobotController rc) throws GameActionException {
        // If inventory is full then head back to dropoff.
        if (rc.getSoupCarrying() >= INVENTORY_RETURN_SIZE) return new Transition(MinerState.DROPOFF, false);

        // Try mining in every direction. If we can't, swap to traveling mode to go to some more soup.
        for (Direction dir : Direction.allDirections()) {
            if (rc.canMineSoup(dir)) {
                rc.mineSoup(dir);
                return new Transition(MinerState.MINE, true);
            }
        }

        // Then swap to traveling to a new vein.
        return new Transition(MinerState.TRAVEL, false);
    }

    int repeats = 0;

    /** Travel behavior, where a miner travels to a known soup location. */
    public Transition travel(RobotController rc) throws GameActionException {
        // If no pathfinder, create it to the closest soup.
        if (this.pathfinder == null) {
            repeats = 0;
            MapLocation closest = soupReps[0];
            int bestDistance = soupReps[0] == null ? Integer.MAX_VALUE : closest.distanceSquaredTo(rc.getLocation());
            for (int i = 1; i < soupReps.length; i++) {
                if (soupReps[i] == null) continue;
                int dist = soupReps[i].distanceSquaredTo(rc.getLocation());
                if (dist >= bestDistance) continue;

                closest = soupReps[i];
                bestDistance = dist;
            }

            // If there is no soup, cry a little and roam.
            if (closest == null) {
                return new Transition(MinerState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closest, true);
            }
        }

        // If pathfinder finished, transition to mining.
        if (this.pathfinder.finished(rc.getLocation())) {
            return new Transition(MinerState.MINE, false);
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER) rc.move(move);
        repeats++;

        return new Transition(MinerState.TRAVEL, true);
    }

    /** Dropoff behavior, where a miner travels to the refinery for dropoff. */
    public Transition dropoff(RobotController rc) throws GameActionException {
        // TODO: Handle destroyed refinery during roaming.

        // Set up the pathfinder if it's currently null.
        if (this.pathfinder == null) {
            this.pathfinder = this.newPathfinder(this.refinery, true);
        }

        // If the pathfinder is finished, drop off and transition.
        if (this.pathfinder.finished(rc.getLocation())) {
            Direction toRefinery = rc.getLocation().directionTo(this.refinery);
            // If we can drop off, do it.
            if (rc.canDepositSoup(toRefinery)) {
                // TODO: This is an action - do we need to mark transitions as turn terminating?
                rc.depositSoup(toRefinery, rc.getSoupCarrying());
                return new Transition(MinerState.TRAVEL, true);
            } else {
                // No refinery is where we thought it was anymore. Start roaming around looking for a refinery.
                return new Transition(MinerState.ROAMING, false);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(MinerState.DROPOFF, true);
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        // Search for HQ/refinery for our initial dropoff. This may change in the future.
        for (RobotInfo info : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (info.getType() == RobotType.HQ || info.getType() == RobotType.REFINERY) {
                this.refinery = info.getLocation();
                break;
            }
        }

        if (this.refinery == null) {
            throw new IllegalStateException("This miner has nowhere to drop off materials!");
        }
    }
}