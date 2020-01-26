package steamlocomotive;

import battlecode.common.*;

public class Miner extends Unit {

    /** The possible miner states the miner can be in. */
    public enum MinerState {
        /** The miner is dropping off resources at a refinery. */
        DROPOFF,
        /** The miner is traveling to a mine. */
        TRAVEL,
        /** The miner is actively mining a vein. */
        MINE,
        /** The miner is roaming looking for soup. */
        ROAMING,
        /** The miner isn't considering opening a refinery - it has no choice but to. The State has decided it's fate. */
        FORCE_REFINERY,
        /** The miner is considering opening a refinery of it's own, settling down, having a family. */
        DREAMING_ABOUT_REFINERY,
        /** The miner is considering opening a fulfillment center or design school, to really start building the community. */
        DREAMING_ABOUT_BUILDINGS,
        /** The miner decides that it can best contribute by making a ton of vaporators near HQ */
        BASE_BUILDING
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
    private MapLocation refinery, fulfillment, design, netGun, vaporator;
    // Communication Object
    private Bitconnect comms;
    // Tracks whether the miner is a base builder
    private boolean isBaseBuilder;

    public Miner(int id) {
        super(id);
        this.pathfinder = null;
        this.pathfindSteps = 0;
        this.refinery = this.fulfillment = this.design = this.netGun = this.vaporator = null;
        this.state = MinerState.ROAMING;
        this.soups = new Utils.Clusterer(Config.NUM_SOUP_CLUSTERS, Config.MAX_CLUSTER_DISTANCE);
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // Update soup and unit knowledge by scanning surroundings.
        this.scanSurroundings(rc);

        // Update comms so we are aware of important global state.
        comms.updateForTurn(rc);

        // Self destruct if blocking wall after round 500
        if (rc.getRoundNum() > 500 && rc.getRoundNum() < 700 && rc.getLocation().distanceSquaredTo(comms.hq())<=2) {
            rc.disintegrate();
            return;
        }

        if (rc.getRoundNum() > 700 && rc.getLocation().distanceSquaredTo(comms.hq())<=2){
            if (rc.getTeamSoup() > 200){
                Direction best = null;
                int dist = 0;
                for (Direction dir : Direction.allDirections()){
                    if (rc.canBuildRobot(RobotType.DESIGN_SCHOOL, dir)){
                        int tempDist = rc.adjacentLocation(dir).distanceSquaredTo(comms.hq());
                        if (tempDist>dist){
                            best = dir;
                            dist = tempDist;
                        }
                    }
                }
                if (rc.canBuildRobot(RobotType.DESIGN_SCHOOL, best)){
                    rc.buildRobot(RobotType.DESIGN_SCHOOL, best);
                    rc.disintegrate();
                }
            }
        }

        // Sorry miner, you were in the way :(
        if (wallStarted(rc, rc.getLocation()) && comms.walls() != null && comms.walls().indexOf(rc.getLocation()) != -1 && rc.senseElevation(rc.getLocation()) >= 20) {
            rc.disintegrate();
            return;
        }

        // Emergency self-defense checks (like netguns).
        // Have ready check because miner may have built net gun or run away from drone
        this.checkForSelfDefense(rc);

        int numTransitions = 0;
        while (rc.isReady()) {
            Utils.print(state.toString());
            MinerState next;
            switch (this.state) {
                case DROPOFF: next = this.dropoff(rc); break;
                case TRAVEL: next = this.travel(rc); break;
                case MINE: next = this.mining(rc); break;
                case FORCE_REFINERY: next = this.forceRefinery(rc); break;
                case DREAMING_ABOUT_REFINERY: next = this.refinery(rc); break;
                case DREAMING_ABOUT_BUILDINGS: next = this.buildings(rc); break;
                case BASE_BUILDING: next = this.baseBuilding(rc); break;
                default:
                case ROAMING: next = this.roaming(rc); break;
            }

            // Reset transient miner state.
            if (this.state != next) {
                this.pathfinder = null;
                this.pathfindSteps = 0;
                numTransitions++;
            } else {
                // If the state transitioned to the same state, don't execute it again - there's no new information!
                break;
            }

            if (numTransitions >= 100) break;

            this.state = next;
        }

        if (numTransitions >= 100)
            System.out.println("Went through more than 100 transitions in one state machine execution...");

        // Useful for debugging.
        if (this.pathfinder != null) rc.setIndicatorLine(rc.getLocation(), this.pathfinder.goal(), 255, 0, 0);
    }

    /** Update soup cluster and dropoff state. */
    public void scanSurroundings(RobotController rc) throws GameActionException {
        // Update the closest refinery.
        // TODO: Reduce number of scan calls to 1 instead of 5.
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

        Utils.ClosestRobot closeNetGun = Utils.closestRobot(rc, RobotType.NET_GUN, rc.getTeam());
        int netGunDistance = this.netGun == null ? Integer.MAX_VALUE : this.netGun.distanceSquaredTo(rc.getLocation());
        if (closeNetGun.distance < netGunDistance) this.netGun = closeNetGun.robot.location;

        Utils.ClosestRobot closeVaporator = Utils.closestRobot(rc, RobotType.VAPORATOR, rc.getTeam());
        int vaporatorDistance = this.vaporator == null ? Integer.MAX_VALUE : this.vaporator.distanceSquaredTo(rc.getLocation());
        if (closeVaporator.distance < vaporatorDistance) this.vaporator = closeVaporator.robot.location;

        // Update the location of the enemy HQ if needed.
        if (comms.enemyHq() == null) {
            Utils.ClosestRobot enemyHqLoc = Utils.closestRobot(rc, RobotType.HQ, rc.getTeam().opponent());
            if (enemyHqLoc.robot != null) comms.notifyEnemyBase(enemyHqLoc.robot.getLocation());

            // Eliminate potential enemy locations.
            if (comms.potentialEnemyLocations() != null) {
                for (MapLocation loc : comms.potentialEnemyLocations()) {
                    if (!rc.canSenseLocation(loc)) continue;

                    RobotInfo robot = rc.senseRobotAtLocation(loc);
                    if (robot == null || robot.type != RobotType.HQ) comms.notifyNoEnemyBase(loc);
                }
            }
        }

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

    /** Scan for dangerous enemies and potentially reactively build defenses. */
    public void checkForSelfDefense(RobotController rc) throws GameActionException {
        // Can't take any actions.
        if (!rc.isReady()) return;

        Utils.ClosestRobot closestDrone = Utils.closestRobot(rc, RobotType.DELIVERY_DRONE, rc.getTeam().opponent());
        if(closestDrone.robot == null) return;

        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(rc.getLocation(), GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED, rc.getTeam());
        boolean foundNetgun = false;
        for(RobotInfo info: nearbyRobots) {
            if(info.type == RobotType.NET_GUN) {
                foundNetgun = true;
                break;
            }
        }

        // Check for enemy drones and try to build a net gun
        if (rc.getTeamSoup() >= RobotType.NET_GUN.cost) {
            if (!foundNetgun && closestDrone.distance <= GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED) {
                if (rc.canBuildRobot(RobotType.NET_GUN, rc.getLocation().directionTo(closestDrone.robot.location))) {
                    rc.buildRobot(RobotType.NET_GUN, rc.getLocation().directionTo(closestDrone.robot.location));
                    return;
                }
                else {
                    for (Direction dir : Direction.allDirections()) {
                        if (rc.canBuildRobot(RobotType.NET_GUN, dir)) {
                            rc.buildRobot(RobotType.NET_GUN, dir);
                            return;
                        }
                    }
                }
            }
        }

        //If can't build a net gun and drone is too close try to move directly away from the enemy drone
        if (closestDrone.distance <= 8) {
            Direction runAwayDirection = closestDrone.robot.location.directionTo(rc.getLocation());
            if (rc.canMove(runAwayDirection) && !rc.senseFlooding(rc.getLocation().add(runAwayDirection))) {
                rc.move(runAwayDirection);
                return;
            } else if (rc.canMove(runAwayDirection.rotateRight()) && !rc.senseFlooding(rc.getLocation().add(runAwayDirection.rotateRight()))) {
                rc.move(runAwayDirection.rotateRight());
                return;
            } else if (rc.canMove(runAwayDirection.rotateLeft()) && !rc.senseFlooding(rc.getLocation().add(runAwayDirection.rotateLeft()))) {
                rc.move(runAwayDirection.rotateLeft());
                return;
            }
        }
    }

    /** Implements roaming behavior, where the miner roams until it finds soup somewhere. */
    public MinerState roaming(RobotController rc) throws GameActionException {
        // If miner is a base builder, get to base building
        if (isBaseBuilder) return MinerState.BASE_BUILDING;

        // If our inventory is full, drop it off.
        if (rc.getSoupCarrying() >= Config.INVENTORY_RETURN_SIZE) return MinerState.DROPOFF;

        // If there is nonzero soup we are aware of, transition to traveling to it.
        if (rc.getSoupCarrying() < Config.INVENTORY_RETURN_SIZE && soups.hasCluster()) return MinerState.TRAVEL;

        // If we have a lot of soup, consider building a building
        if (rc.getTeamSoup() > 1000) return MinerState.DREAMING_ABOUT_BUILDINGS;

        // If it's sufficiently late in the game, transition to being a base builder
        if (rc.getRoundNum() > Config.BUILD_TRANSITION_ROUND) {
            isBaseBuilder = true;
            return MinerState.BASE_BUILDING;
        }

        // Otherwise, roam around looking for soup and other objects of interest.
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
        // TODO: Hacky :/
        if (isBaseBuilder) return MinerState.BASE_BUILDING;

        // Hacky solution to some bad behavior; if we can mine soup, immediately transition to mining.
        for (Direction dir : Direction.allDirections()) {
            if (rc.canMineSoup(dir)) return MinerState.MINE;
        }

        // If we've been travelling for too long, set all soup locations to null and roam
        if (this.pathfindSteps > 200) {
            soups.clearInvalid(rc, loc -> true);
            this.pathfinder = null;
            return MinerState.ROAMING;
        }

        // If no pathfinder, create it to the closest soup.
        MapLocation closest = soups.closest(rc.getLocation());
        if (closest == null) return MinerState.ROAMING;

        if (this.pathfinder == null || !this.pathfinder.goal().equals(closest))
            this.pathfinder = this.newPathfinder(closest, true);

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
        // If construction has started on the wall, drop efforts to drop at the HQ.
        if (comms.hq().equals(this.refinery) && rc.canSenseLocation(comms.hq()) && wallStarted(rc, comms.hq()))
            this.refinery = null;

        // No refinery to drop off to or refinery seems unreachable; this should not happen often, so we'll force building a refinery here.
        if (this.refinery == null || pathfindSteps > 200) return MinerState.FORCE_REFINERY;

        // Set up the pathfinder if it's currently null.
        if (this.pathfinder == null || !this.pathfinder.goal().equals(this.refinery))
            this.pathfinder = this.newPathfinder(this.refinery, true);

        // If the pathfinder is finished, drop off and transition.
        if (this.pathfinder.finished(rc.getLocation())) {
            Direction toRefinery = rc.getLocation().directionTo(this.refinery);
            // If we can drop off, do it.
            if (rc.canDepositSoup(toRefinery)) {
                rc.depositSoup(toRefinery, rc.getSoupCarrying());

                // Build a fulfillment center/design school if necessary.
                if (rc.getRoundNum() > Config.BUILD_BUILDING_MIN_ROUND && rc.getTeamSoup() > Config.BUILD_BUILDING_MIN_SOUP) {
                   return MinerState.DREAMING_ABOUT_BUILDINGS;
                }

                return MinerState.TRAVEL;
            } else {
                // No refinery is where we thought it was anymore; that's not good. Build a new one.
                // TODO: Roaming around in this case is acceptable.
                return MinerState.FORCE_REFINERY;
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

    /** Forces the building of an accessible refinery. This supercedes distance checks. */
    public MinerState forceRefinery(RobotController rc) throws GameActionException {
        // Looks like there is a refinery; go use it. Prevents every miner from building their own refinery.
        if (this.refinery != null) return MinerState.DROPOFF;

        // Scan the nearby surroundings for a good place within a few steps of us to build our building.
        if (this.pathfinder == null) {
            MapLocation best = this.findGoodBuildingLocation(rc, Config.BUILD_BUILDING_ROAM_DISTANCE, RobotType.REFINERY);

            // No good locations, give up.
            if (best == null) return MinerState.TRAVEL;

            this.pathfinder = this.newPathfinder(best, true);
        }

        // If done, attempt to build.
        if (this.pathfinder.finished(rc.getLocation())) {
            Direction towards = rc.getLocation().directionTo(this.pathfinder.goal());
            if (rc.canBuildRobot(RobotType.REFINERY, towards)) {
                rc.buildRobot(RobotType.REFINERY, towards);
                return MinerState.DROPOFF;
            } else {
                return MinerState.FORCE_REFINERY;
            }
        }

        // Otherwise, take a step with the pathfinder.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
        if (move != null && move != Direction.CENTER) rc.move(move);
        this.pathfindSteps++;

        // You better build a refinery - there's no escaping now.
        return MinerState.FORCE_REFINERY;
    }

    /** Try to build a refinery in a reasonable place (not adjacent to any existing building!). */
    public MinerState refinery(RobotController rc) throws GameActionException {
        // Ensure we have enough money to build a refinery.
        if (rc.getTeamSoup() < RobotType.REFINERY.cost) return MinerState.DROPOFF;

        // Ensure we're placing the refinery far enough away!
        // If the refinery is the HQ, we ignore this check.
        if (this.refinery != null && !comms.hq().equals(this.refinery) && rc.getLocation().distanceSquaredTo(this.refinery) < Config.REFINERY_MIN_DISTANCE)
            return MinerState.DROPOFF;

        // Scan the nearby surroundings for a good place within a few steps of us to build our building.
        if (this.pathfinder == null) {
            MapLocation best = this.findGoodBuildingLocation(rc, Config.BUILD_BUILDING_ROAM_DISTANCE, RobotType.REFINERY);

            // No good locations, give up.
            if (best == null) return MinerState.DROPOFF;

            this.pathfinder = this.newPathfinder(best, true);
        }

        // If done, attempt to build.
        if (this.pathfinder.finished(rc.getLocation())) {
            Direction towards = rc.getLocation().directionTo(this.pathfinder.goal());
            if (rc.canBuildRobot(RobotType.REFINERY, towards)) {
                rc.buildRobot(RobotType.REFINERY, towards);
            }

            return MinerState.DROPOFF;
        }

        // Quit if we've wasted too much time on this.
        if (this.pathfindSteps >= 2 * Config.BUILD_BUILDING_ROAM_DISTANCE) return MinerState.DROPOFF;

        // Otherwise, take a step with the pathfinder.
        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
        if (move != null && move != Direction.CENTER) rc.move(move);
        this.pathfindSteps++;

        // :(
        return MinerState.DROPOFF;
    }

    /** Try to build a fulfillment center or design school in a reasonable place. */
    public MinerState buildings(RobotController rc) throws GameActionException {
        // Ensure we have enough money to build a fulfillment center or design school.
        if (rc.getTeamSoup() < RobotType.FULFILLMENT_CENTER.cost || rc.getTeamSoup() < RobotType.DESIGN_SCHOOL.cost) return MinerState.TRAVEL;

        // Determine which buildings we should consider building based on how far away we are from existing buildings.
        boolean buildFulfillment = (this.fulfillment == null || this.fulfillment.distanceSquaredTo(rc.getLocation()) >= Config.BUILD_BUILDING_MIN_DIST)
                && comms.fulfillmentCenters().size() == 0;
        boolean buildDesign = (this.design == null || this.design.distanceSquaredTo(rc.getLocation()) >= Config.BUILD_BUILDING_MIN_DIST)
                && comms.designSchools().size() == 0;
        boolean buildNetGun = (this.netGun == null || this.netGun.distanceSquaredTo(rc.getLocation()) >= Config.BUILD_NET_GUN_MIN_DIST);
        boolean buildVaporator = (this.vaporator == null || this.vaporator.distanceSquaredTo(rc.getLocation()) >= Config.BUILD_VAP_MIN_DIST);

        if (!buildDesign && !buildFulfillment && !buildNetGun && !buildVaporator)
            return MinerState.TRAVEL;

        // If things go wrong somehow, the miner defaults to wanting to build a vaporator
        RobotType typeToBuild;
        if (buildDesign && rc.getLocation().distanceSquaredTo(comms.hq()) < 80) typeToBuild = RobotType.DESIGN_SCHOOL;
        else if (buildFulfillment && rc.getLocation().distanceSquaredTo(comms.hq()) < 60) typeToBuild = RobotType.FULFILLMENT_CENTER;
        else if (buildVaporator) typeToBuild = RobotType.VAPORATOR;
        else if (buildFulfillment) typeToBuild = RobotType.FULFILLMENT_CENTER;
        else if (buildDesign) typeToBuild = RobotType.DESIGN_SCHOOL;
        else if (buildNetGun) typeToBuild = RobotType.NET_GUN;
        else typeToBuild = (this.rng.nextDouble() < Config.FULFILLMENT_CENTER_PROB) ? RobotType.FULFILLMENT_CENTER : RobotType.DESIGN_SCHOOL;

        MapLocation best = this.findGoodBuildingLocation(rc, Config.BUILD_BUILDING_ROAM_DISTANCE, typeToBuild);

        // No good locations, give up.
        if (best == null) return MinerState.TRAVEL;

        // Scan the nearby surroundings for a good place within a few steps of us to build our building.
        if (this.pathfinder == null || !this.pathfinder.goal().equals(best))
            this.pathfinder = this.newPathfinder(best, true);

        // If done, attempt to build.
        if (this.pathfinder.finished(rc.getLocation())) {
            Direction towards = rc.getLocation().directionTo(this.pathfinder.goal());

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

        return MinerState.DREAMING_ABOUT_BUILDINGS;
    }

    /** The miner roams near our HQ, building many vaporators and some net guns. */
    public MinerState baseBuilding(RobotController rc) throws GameActionException {
        // If we have enough soup, build things
        if (rc.getTeamSoup() > RobotType.VAPORATOR.cost && rc.getRoundNum() % 4 == rc.getID() % 4)
            return MinerState.DREAMING_ABOUT_BUILDINGS;

        // If sufficiently far from HQ, head to the other side of it
        if (rc.getLocation().distanceSquaredTo(comms.hq()) >= 45) {
            Direction towardsHQ = rc.getLocation().directionTo(comms.hq());
            MapLocation farSideOfHQ = comms.hq().add(towardsHQ).add(towardsHQ).add(towardsHQ).add(towardsHQ);
            if (this.pathfinder == null || this.pathfinder.finished(rc.getLocation()) || this.pathfindSteps > Config.MAX_ROAM_DISTANCE || this.pathfinder.goal() != farSideOfHQ) {
                // TODO: More intelligent target selection. We choose randomly for now.
                MapLocation target = farSideOfHQ;

                this.pathfinder = this.newPathfinder(target, true);
                this.pathfindSteps = 0;
            }

            // Obtain a movement from the pathfinder and follow it.
            Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
            if (move != null && move != Direction.CENTER) rc.move(move);
            this.pathfindSteps++;
        } else {
            // Else, roam randomly
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
        }

        return MinerState.BASE_BUILDING;
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        comms = Bitconnect.initialize(rc);
        comms.scanRecent(rc, 50);

        // Search for HQ/refinery for our initial dropoff. This may change in the future.
        RobotInfo refine = Utils.closestRobot(rc, robot -> robot.getType() == RobotType.REFINERY || robot.getType() == RobotType.HQ, rc.getTeam()).robot;
        if (refine == null) throw new IllegalStateException("This miner has nowhere to drop off materials!");

        this.refinery = refine.getLocation();

        if (rc.getRoundNum() > Config.BUILD_ON_CREATION_ROUND_NUMBER || (rc.getRoundNum() % 4 == 2 && rc.getRoundNum() >= 150)) isBaseBuilder = true;
        else isBaseBuilder = false;
    }

    /** Determines if a building location is a good one according to a few heuristics. */
    private boolean goodBuildingLocation(RobotController rc, MapLocation loc, RobotInfo[] sensed, RobotType desiredBuilding) throws GameActionException {
        if (desiredBuilding == RobotType.HQ || !desiredBuilding.isBuilding()) return false;
        if (!rc.canSenseLocation(loc)) return false;

        // The building must be at least a minimum distance from our HQ.
        if (loc.distanceSquaredTo(comms.hq()) < Config.BUILD_BUILDING_MIN_HQ_DIST) return false;

        // The building must be at least a minimum distance from any other observed buildings.
        for (RobotInfo robot : sensed) {
            if (robot.type.canBePickedUp()) {
                if (robot.location.equals(loc)) return false;
                continue;
            }

            if (desiredBuilding != RobotType.VAPORATOR && loc.isAdjacentTo(robot.location)) return false;
        }

        if (desiredBuilding == RobotType.VAPORATOR) {
            if (rc.getRoundNum() < 200 && rc.senseElevation(loc) < 3) {
                return false;
            }
            else if (rc.getRoundNum() < 500 && rc.senseElevation(loc) < 10) {
                return false;
            }
            else if (rc.getRoundNum() < 1000 && rc.senseElevation(loc) < 20) {
                return false;
            }
        }

        // We'd like the building to not block our pathfinding if possible; at least three of the four cardinal directions
        // should not be flooded.
        int valid = 0;
        for (Direction dir : Direction.cardinalDirections()) {
            MapLocation adj = loc.add(dir);
            if (rc.canSenseLocation(adj) && !rc.senseFlooding(adj)) valid++;
        }

        return valid >= 3;
    }

    /** Find a good building location within the given radius. */
    private MapLocation findGoodBuildingLocation(RobotController rc, int radius, RobotType desiredBuilding) throws GameActionException {
        if (desiredBuilding == RobotType.HQ || !desiredBuilding.isBuilding()) return null;

        MapLocation us = rc.getLocation();
        MapLocation best = null;
        int bestDistance = Integer.MAX_VALUE;
        RobotInfo[] sensed = rc.senseNearbyRobots(radius * radius, rc.getTeam());

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx == 0 && dy == 0) continue;

                MapLocation loc = new MapLocation(us.x + dx, us.y + dy);
                int dist = loc.distanceSquaredTo(rc.getLocation());
                if (goodBuildingLocation(rc, loc, sensed, desiredBuilding)){
                    if (desiredBuilding == RobotType.DESIGN_SCHOOL && loc.distanceSquaredTo(comms.hq()) <= 13 ) {
                        best = loc;
                        return best;
                    } else if (dist < bestDistance) {
                        bestDistance = dist;
                        best = loc;
                    }
                }
            }
        }

        return best;
    }

    /** If true, it looks like the wall has been started. */
    private boolean wallStarted(RobotController rc, MapLocation hq) throws GameActionException {
        int numDiggers = 0;
        for (Direction d : Direction.allDirections()) {
            if (d == Direction.CENTER) continue;

            MapLocation loc = hq.add(d);
            if (!rc.canSenseLocation(loc)) continue;

            RobotInfo robot = rc.senseRobotAtLocation(loc);
            if (robot != null && robot.type == RobotType.LANDSCAPER) numDiggers++;
        }

        return numDiggers >= 5;
    }
}