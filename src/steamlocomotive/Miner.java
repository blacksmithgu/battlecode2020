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
        /** The miner is considering opening a fulfillment center or design school, to really start building the community. */
        DREAMING_ABOUT_BUILDINGS
    }

    // The mode that the miner is currently in.
    private MinerState state;
    // Pathfinder for going to a location;
    private BugPathfinder pathfinder;
    // The number of steps that have been taken while pathfinding.
    private int pathfindSteps;
    // Clusters seen soup locations.
    private Utils.Clusterer soups;
    // The location of the refinery/HQ we are dumping resources at, as well as the last seen fulfillment and design centers.
    private MapLocation refinery, fulfillment, design;
    // The location of the HQ. Given that we spawn here, we should always know where it is!
    private MapLocation hq;

    public Miner(int id) {
        super(id);
        this.pathfinder = null;
        this.pathfindSteps = 0;
        this.refinery = null;
        this.state = MinerState.ROAMING;
        this.soups = new Utils.Clusterer(Config.NUM_SOUP_CLUSTERS, Config.MAX_CLUSTER_DISTANCE);
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // Update soup and unit knowledge by scanning surroundings.
        this.scanSurroundings(rc);

        while (rc.isReady()) {
            MinerState next;
            switch (this.state) {
                case DROPOFF: next = this.dropoff(rc); break;
                case TRAVEL: next = this.travel(rc); break;
                case MINE: next = this.mining(rc); break;
                case DREAMING_ABOUT_REFINERY: next = this.refinery(rc); break;
                case DREAMING_ABOUT_BUILDINGS: next = this.buildings(rc); break;
                default:
                case ROAMING: next = this.roaming(rc); break;
            }

            // Reset transient miner state.
            if (this.state != next) {
                this.pathfinder = null;
                this.pathfindSteps = 0;
            } else {
                // If the state transitioned to the same state, don't execute it again - there's no new information!
                break;
            }

            this.state = next;
        }

        // Useful for debugging.
        if (this.pathfinder != null) rc.setIndicatorLine(rc.getLocation(), this.pathfinder.goal(), 255, 0, 0);
    }

    /** Update soup cluster and dropoff state. */
    public void scanSurroundings(RobotController rc) throws GameActionException {
        // Update the closest refinery.
        Utils.ClosestRobot closeRefinery = Utils.closestRobot(rc, RobotType.REFINERY, rc.getTeam());
        int refineDistance = this.refinery == null ? Integer.MAX_VALUE : this.refinery.distanceSquaredTo(rc.getLocation());
        if (closeRefinery.distance < refineDistance) this.refinery = closeRefinery.robot.getLocation();

        // Update closest of the other buildings: fulfillment and designs.
        Utils.ClosestRobot closeFulfillment = Utils.closestRobot(rc, RobotType.FULFILLMENT_CENTER, rc.getTeam());
        int fulfillDistance = this.fulfillment == null ? Integer.MAX_VALUE : this.fulfillment.distanceSquaredTo(rc.getLocation());
        if  (closeFulfillment.distance < fulfillDistance) this.fulfillment = closeFulfillment.robot.getLocation();

        Utils.ClosestRobot closeDesign = Utils.closestRobot(rc, RobotType.DESIGN_SCHOOL, rc.getTeam());
        int designDistance = this.design == null ? Integer.MAX_VALUE : this.design.distanceSquaredTo(rc.getLocation());
        if (closeDesign.distance < designDistance) this.design = closeDesign.robot.location;

        // Check and clear representatives if they are no longer present.
        this.soups.clearInvalid(rc, loc -> rc.canSenseLocation(loc) && (rc.senseFlooding(loc) || rc.senseSoup(loc) == 0));

        // Soup memory; track recently seen soup clusters.
        MapLocation[] sensedSoup = rc.senseNearbySoup();
        for (int i = 0; i < sensedSoup.length; i++) {
            MapLocation soupLoc = sensedSoup[i];
            if (rc.canSenseLocation(soupLoc)) {
                if (rc.senseFlooding(soupLoc)) {
                    boolean hasSolidAdj = false;
                    for (Direction dir : Direction.allDirections()) {
                        MapLocation adj = soupLoc.add(dir);
                        if (dir == Direction.CENTER) continue;
                        if (rc.canSenseLocation(adj) && !rc.senseFlooding(soupLoc.add(dir))) hasSolidAdj = true;
                    }

                    if (!hasSolidAdj) continue;
                }
            }
            this.soups.update(rc, soupLoc, this.rng);
        }
    }

    /** Implements roaming behavior, where the miner roams until it finds soup somewhere. */
    public MinerState roaming(RobotController rc) throws GameActionException {
        // If our inventory is full and there is a refinery that is reachable, head there.
        if (rc.getSoupCarrying() >= Config.INVENTORY_RETURN_SIZE && this.refinery != null) return MinerState.DROPOFF;

        // If there is nonzero soup we are aware of, transition to traveling to it.
        if (soups.hasCluster()) return MinerState.TRAVEL;

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

        return MinerState.ROAMING;
    }

    /** Implements mining behavior, where a miner is actively next to some soup and is harvesting it. */
    public MinerState mining(RobotController rc) throws GameActionException {
        // If inventory is full then dropoff (potentially by building a refinery to drop off to).
        if (rc.getSoupCarrying() >= Config.INVENTORY_RETURN_SIZE) return MinerState.DREAMING_ABOUT_REFINERY;

        // Try mining in every direction. If we can't, swap to traveling mode to go to some more soup.
        for (Direction dir : Direction.allDirections()) {
            if (rc.canMineSoup(dir)) {
                rc.mineSoup(dir);
                return MinerState.MINE;
            }
        }

        // Then swap to traveling to a new vein.
        return MinerState.TRAVEL;
    }

    /** Travel behavior, where a miner travels to a known soup location. */
    public MinerState travel(RobotController rc) throws GameActionException {
        // Hacky solution to some bad behavior; if we can mine soup, immediately transition to mining.
        for (Direction dir : Direction.allDirections()) {
            if (rc.canMineSoup(dir)) return MinerState.MINE;
        }

        // If no pathfinder, create it to the closest soup.
        if (this.pathfinder == null) {
            MapLocation closest = soups.closest(rc.getLocation());

            // If there is no soup, cry a little and roam.
            if (closest == null) {
                return MinerState.ROAMING;
            } else {
                this.pathfinder = this.newPathfinder(closest, true);
            }
        }

        // If pathfinder finished, transition to mining.
        if (this.pathfinder.finished(rc.getLocation())) return MinerState.MINE;

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
        if (move != null && move != Direction.CENTER) rc.move(move);
        this.pathfindSteps++;

        return MinerState.TRAVEL;
    }

    /** Dropoff behavior, where a miner travels to the refinery for dropoff. */
    public MinerState dropoff(RobotController rc) throws GameActionException {
        // No refinery to drop off to, roam around looking for one.
        if (this.refinery == null) return MinerState.ROAMING;

        // Set up the pathfinder if it's currently null.
        if (this.pathfinder == null) this.pathfinder = this.newPathfinder(this.refinery, true);

        // If the pathfinder is finished, drop off and transition.
        if (this.pathfinder.finished(rc.getLocation())) {
            Direction toRefinery = rc.getLocation().directionTo(this.refinery);
            // If we can drop off, do it.
            if (rc.canDepositSoup(toRefinery)) {
                rc.depositSoup(toRefinery, rc.getSoupCarrying());

                // Build a fulfillment center/design school if necessary.
                if (rc.getRoundNum() > Config.BUILD_BUILDING_MIN_ROUND && rc.getTeamSoup() > Config.BUILD_BUILDING_MIN_SOUP) {
                    if (this.hq.distanceSquaredTo(rc.getLocation()) < rc.getCurrentSensorRadiusSquared() || this.rng.nextDouble() < Config.BUILD_BUILDING_PROB) return MinerState.DREAMING_ABOUT_BUILDINGS;
                }

                return MinerState.TRAVEL;
            } else {
                // No refinery is where we thought it was anymore. Start roaming around looking for a refinery.
                return MinerState.ROAMING;
            }
        }

        // If we've taken too many steps to drop off, then consider building a refinery right now.
        if (this.pathfindSteps >= Config.MAX_ROAM_DISTANCE) return MinerState.DREAMING_ABOUT_REFINERY;

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
        if (move != null && move != Direction.CENTER) rc.move(move);
        this.pathfindSteps++;

        return MinerState.DROPOFF;
    }

    /** Try to build a refinery in a reasonable place (not adjacent to any existing building!). */
    public MinerState refinery(RobotController rc) throws GameActionException {
        // Ensure we have enough money to build a refinery.
        if (rc.getTeamSoup() < RobotType.REFINERY.cost) return MinerState.DROPOFF;

        // Ensure we're placing the refinery far enough away!
        if (rc.getLocation().distanceSquaredTo(refinery) < Config.REFINERY_MIN_DISTANCE) return MinerState.DROPOFF;

        // Alright, it's time to build.
        for (Direction adj : Direction.allDirections()) {
            if (adj == Direction.CENTER) continue;
            if (rc.canBuildRobot(RobotType.REFINERY, adj)) {
                rc.buildRobot(RobotType.REFINERY, adj);
                return MinerState.DROPOFF;
            }
        }

        // :(
        return MinerState.DROPOFF;
    }

    /** Try to build a fulfillment center or design school in a reasonable place. */
    public MinerState buildings(RobotController rc) throws GameActionException {
        // Ensure we have enough money to build a fulfillment center or design school.
        if (rc.getTeamSoup() < RobotType.FULFILLMENT_CENTER.cost || rc.getTeamSoup() < RobotType.DESIGN_SCHOOL.cost) return MinerState.TRAVEL;

        // Determine which buildings we should consider building based on how far away we are from existing buildings.
        boolean buildFulfillment = (this.fulfillment == null || this.fulfillment.distanceSquaredTo(rc.getLocation()) >= Config.BUILD_BUILDING_MIN_DIST);
        boolean buildDesign = (this.design == null || this.design.distanceSquaredTo(rc.getLocation()) >= Config.BUILD_BUILDING_MIN_DIST);

        if (!buildDesign && !buildFulfillment) return MinerState.TRAVEL;

        // Scan the nearby surroundings for a good place within a few steps of us to build our building.
        if (this.pathfinder == null) {
            MapLocation best = this.findGoodBuildingLocation(rc, Config.BUILD_BUILDING_ROAM_DISTANCE);

            // No good locations, give up.
            if (best == null) return MinerState.TRAVEL;

            this.pathfinder = this.newPathfinder(best, true);
        }

        // If done, attempt to build.
        if (this.pathfinder.finished(rc.getLocation())) {
            Direction towards = rc.getLocation().directionTo(this.pathfinder.goal());

            RobotType typeToBuild;
            if (buildDesign && !buildFulfillment) typeToBuild = RobotType.DESIGN_SCHOOL;
            else if (!buildDesign && buildFulfillment) typeToBuild = RobotType.FULFILLMENT_CENTER;
            else typeToBuild = (this.rng.nextDouble() < Config.FULFILLMENT_CENTER_PROB) ? RobotType.FULFILLMENT_CENTER : RobotType.DESIGN_SCHOOL;

            if (rc.canBuildRobot(typeToBuild, towards))
                rc.buildRobot(typeToBuild, towards);

            return MinerState.TRAVEL;
        }

        // Quit if we've wasted too much time on this.
        if (this.pathfindSteps >= 2 * Config.BUILD_BUILDING_ROAM_DISTANCE) return MinerState.TRAVEL;

        // Otherwise, take a step with the pathfinder.
        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
        if (move != null && move != Direction.CENTER) rc.move(move);
        this.pathfindSteps++;

        // :(
        return MinerState.DREAMING_ABOUT_BUILDINGS;
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        // Search for HQ/refinery for our initial dropoff. This may change in the future.
        RobotInfo refine = Utils.closestRobot(rc, robot -> robot.getType() == RobotType.REFINERY || robot.getType() == RobotType.HQ, rc.getTeam()).robot;
        if (refine == null) throw new IllegalStateException("This miner has nowhere to drop off materials!");

        // Building placement depends on knowledge of our actual HQ location.
        RobotInfo h = Utils.closestRobot(rc, RobotType.HQ, rc.getTeam()).robot;
        if (h == null) throw new IllegalStateException("This miner can't see the HQ!");

        this.refinery = refine.getLocation();
        this.hq = h.getLocation();
    }

    /** Determines if a building location is a good one according to a few heuristics. */
    private boolean goodBuildingLocation(RobotController rc, MapLocation loc, RobotInfo[] sensed) throws GameActionException {
        if (!rc.canSenseLocation(loc)) return false;

        // The building must be at least a minimum distance from our HQ.
        if (loc.distanceSquaredTo(this.hq) < Config.BUILD_BUILDING_MIN_HQ_DIST) return false;

        // The building must be at least a minimum distance from any other observed buildings.
        for (RobotInfo robot : sensed) {
            if (robot.type.canBePickedUp()) continue;
            if (loc.isAdjacentTo(robot.location)) return false;
        }

        // We'd like the building to not block our pathfinding if possible; at least three of the four cardinal directions
        // should not be flooded.
        int valid = 0;
        for (Direction dir : Direction.cardinalDirections()) {
            MapLocation adj = loc.add(dir);
            if (rc.canSenseLocation(adj) && !rc.senseFlooding(adj)) valid++;
        }

        if (valid < 3) return false;

        return true;
    }

    /** Find a good building location within the given radius. */
    private MapLocation findGoodBuildingLocation(RobotController rc, int radius) throws GameActionException {
        MapLocation us = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        RobotInfo[] sensed = rc.senseNearbyRobots(radius * radius, rc.getTeam());

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                MapLocation loc = new MapLocation(us.x + dx, us.y + dy);
                int dist = loc.distanceSquaredTo(rc.getLocation());
                if (goodBuildingLocation(rc, loc, sensed) && dist < bestDistance) {
                    bestDistance = dist;
                    best = loc;
                }
            }
        }

        return best;
    }
}