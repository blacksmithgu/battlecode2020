package steamlocomotive;

import battlecode.common.*;

public class Miner extends Unit {

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
        /** The miner is considering opening a refinery of it's own, settling down, having a family. */
        DREAMING_ABOUT_REFINERY,
        /** The miner is considering opening a fulfillment center, really try to find joy in life. */
        DREAMING_ABOUT_FULFILLMENT
    }

    /** A transition from one state to another. Marks the target and whether an action has been taken. */
    private static class Transition {
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
    // The number of steps that have been taken while pathfinding.
    private int pathfindSteps;
    // Clusters seen soup locations.
    private Utils.Clusterer soups;
    // The location of the HQ we are dumping resources at.
    private MapLocation refinery;

    public Miner(int id) {
        super(id);
        this.pathfinder = null;
        this.pathfindSteps = 0;
        this.refinery = null;
        this.state = MinerState.ROAMING;
        this.soups = new Utils.Clusterer(Config.TRACKED_SOUP_COUNT, Config.REPRESENTATIVE_THRESHOLD);
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
                case DREAMING_ABOUT_REFINERY: trans = this.refinery(rc); break;
                case DREAMING_ABOUT_FULFILLMENT: trans = this.fulfillment(rc); break;
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

        // Useful for debugging.
        if (this.pathfinder != null) rc.setIndicatorLine(rc.getLocation(), this.pathfinder.goal(), 0, 255, 0);
    }

    /** Update soup cluster and dropoff state. */
    public void scanSurroundings(RobotController rc) throws GameActionException {
        // Update the closest refinery.
        Utils.ClosestRobot closeRefinery = Utils.closestRobot(rc, RobotType.REFINERY, rc.getTeam());
        int refineDistance = this.refinery == null ? Integer.MAX_VALUE : this.refinery.distanceSquaredTo(rc.getLocation());
        if (closeRefinery.distance < refineDistance) this.refinery = closeRefinery.robot.getLocation();

        // Check and clear representatives if they are no longer present.
        this.soups.clearInvalid(rc, loc -> rc.canSenseLocation(loc) && !rc.senseFlooding(loc) && rc.senseSoup(loc) > 0);

        // Soup memory; track recently seen soup clusters.
        MapLocation[] sensedSoup = rc.senseNearbySoup();
        for (int i = 0; i < sensedSoup.length; i++) {
            MapLocation soupLoc = sensedSoup[i];
            if (rc.canSenseLocation(soupLoc) && rc.senseFlooding(soupLoc)) continue;
            this.soups.update(rc, soupLoc, this.rng);
        }
    }

    /** Implements roaming behavior, where the miner roams until it finds soup somewhere. */
    public Transition roaming(RobotController rc) throws GameActionException {
        // TODO: Roaming targets may be unreachable, so choose a new target after X steps.

        // If there is nonzero soup we are aware of, transition to traveling to it.
        if (soups.hasCluster()) return new Transition(MinerState.TRAVEL, false);

        // If the pathfinder is inactive or finished, pick a new random location to pathfind to.
        if (this.pathfinder == null || this.pathfinder.finished(rc.getLocation()) || this.pathfindSteps > Config.MAX_ROAM_DISTANCE) {
            // TODO: More intelligent target selection. We choose randomly for now.
            MapLocation target = new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight()));

            this.pathfinder = this.newPathfinder(target, true);
            this.pathfindSteps = 0;
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
        if (move != null && move != Direction.CENTER) rc.move(move);
        this.pathfindSteps++;

        return new Transition(MinerState.ROAMING, true);
    }

    /** Implements mining behavior, where a miner is actively next to some soup and is harvesting it. */
    public Transition mining(RobotController rc) throws GameActionException {
        // If inventory is full then head back to dropoff.
        if (rc.getSoupCarrying() >= Config.INVENTORY_RETURN_SIZE) return new Transition(MinerState.DREAMING_ABOUT_REFINERY, false);

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

    /** Travel behavior, where a miner travels to a known soup location. */
    public Transition travel(RobotController rc) throws GameActionException {
        // Hacky solution to some bad behavior; if we can mine soup, immediately transition to mining.
        for (Direction dir : Direction.allDirections()) {
            if (rc.canMineSoup(dir)) {
                return new Transition(MinerState.MINE, false);
            }
        }

        // If no pathfinder, create it to the closest soup.
        if (this.pathfinder == null) {
            MapLocation closest = soups.closest(rc.getLocation());

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
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
        if (move != null && move != Direction.CENTER) rc.move(move);
        this.pathfindSteps++;

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
                rc.depositSoup(toRefinery, rc.getSoupCarrying());

                // Build a fulfillment center if necessary.
                // TODO: Change these into constants.
                if (rc.getRoundNum() > 200 && rc.getTeamSoup() > 400) {
                    return new Transition(MinerState.DREAMING_ABOUT_FULFILLMENT, true);
                }

                return new Transition(MinerState.TRAVEL, true);
            } else {
                // No refinery is where we thought it was anymore. Start roaming around looking for a refinery.
                return new Transition(MinerState.ROAMING, false);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
        if (move != null && move != Direction.CENTER) rc.move(move);
        this.pathfindSteps++;

        return new Transition(MinerState.DROPOFF, true);
    }

    public Transition refinery(RobotController rc) throws GameActionException {
        // Ensure we have enough money to build a refinery.
        if (rc.getTeamSoup() < RobotType.REFINERY.cost) return new Transition(MinerState.DROPOFF, false);

        // Ensure we're placing the refinery far enough away!
        // TODO: Consider a more advanced distance metric than as-the-crow-flys.
        if (rc.getLocation().distanceSquaredTo(refinery) < Config.REFINERY_MIN_DISTANCE) return new Transition(MinerState.DROPOFF, false);

        // Alright, it's time to build.
        for (Direction adj : Direction.allDirections()) {
            if (adj == Direction.CENTER) continue;
            if (rc.canBuildRobot(RobotType.REFINERY, adj)) {
                rc.buildRobot(RobotType.REFINERY, adj);
                return new Transition(MinerState.DROPOFF, true);
            }
        }

        // :(
        return new Transition(MinerState.DROPOFF, false);
    }

    public Transition fulfillment(RobotController rc) throws GameActionException {
        // Ensure we have enough money to build a fulfillment center.
        if (rc.getTeamSoup() < RobotType.FULFILLMENT_CENTER.cost) return new Transition(MinerState.DROPOFF, false);

        // Ensure we're placing the fulfillment center far enough away!
        for (RobotInfo info : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (info.getType() == RobotType.FULFILLMENT_CENTER && info.getTeam() == rc.getTeam()) {
                return new Transition(MinerState.TRAVEL, false);
            }
        }

        // Builds a fulfillment center in a direction that it can. If can't, then transitions to roaming.
        for (Direction adj : Direction.allDirections()) {
            if (adj == Direction.CENTER) continue;
            if (rc.canBuildRobot(RobotType.FULFILLMENT_CENTER, adj)) {
                rc.buildRobot(RobotType.FULFILLMENT_CENTER, adj);
                return new Transition(MinerState.TRAVEL, true);
            }
        }

        // :(
        return new Transition(MinerState.DROPOFF, false);
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        // Search for HQ/refinery for our initial dropoff. This may change in the future.
        RobotInfo refine = Utils.closestRobot(rc, robot -> robot.getType() == RobotType.REFINERY || robot.getType() == RobotType.HQ, rc.getTeam()).robot;
        if (refine == null)
            throw new IllegalStateException("This miner has nowhere to drop off materials!");

        this.refinery = refine.getLocation();
    }
}