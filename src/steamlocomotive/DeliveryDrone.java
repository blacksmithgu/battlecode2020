package steamlocomotive;

import battlecode.common.*;

public strictfp class DeliveryDrone extends Unit {
    public enum DroneState {
        // The drone looks for hapless victims.
        ROAMING,
        // The drone throws caution to the wind and attacks without regard for nets
        RECKLESS_CHASING,
        // The drone chases enemies while trying not to kill itself
        SAFE_CHASING,
        // The drone gives its new friend a bath.
        DUNKING,
        // The drone looks for an enemy that it saw earlier
        FINDING_ENEMY,
        // The drone goes to where it last saw a cow
        FINDING_COW,
        // The drone chases down and picks up a cow
        CHASING_COW,
        // The drone finds a friendly miner to put on soup
        FINDING_MINER,
        // The drone puts a friendly miner on top of hard-to-reach soup
        FERRYING_MINER,
        // Drone looks for a friendly landscaper to put it on top of the wall to build
        FINDING_LANDSCAPER,
        // Drone puts the friendly landscaper on top of the wall
        FERRYING_LANDSCAPER,
        // The drone ferrying was interrupted due to new information, and it is dropping off it's carried unit.
        DROPOFF_FRIENDLY,
        // Make a defensive wall around HQ
        DRONE_WALL,
        // The drone swarms the enemy HQ
        SWARMING,
        // The drone searches for the enemy HQ
        FINDING_ENEMY_HQ,
        // Moving a miner to the lattice
        LATTICE_PLACING,
    }

    private static class Transition {
        public DeliveryDrone.DroneState target;
        public boolean madeAction;

        public Transition(DeliveryDrone.DroneState target, boolean madeAction) {
            this.target = target;
            this.madeAction = madeAction;
        }
    }

    // The mode that the drone is currently in.
    private DeliveryDrone.DroneState state;
    // Pathfinder for going to a location;
    private BugPathfinder pathfinder;
    // The number of steps the pathfinder has taken.
    private int pathfindSteps;
    // Comms object
    private Bitconnect comms;

    // The closest water we've seen for dunking.
    private MapLocation closestWater;
    // The closest enemy landscaper or miner that we've seen, for dunking
    private MapLocation closestEnemyLandUnit;
    // The closest enemy net gun that the drone remembers seeing
    private MapLocation closestEnemyNetGun;
    // The closest cow that we've seen (that isn't already close to enemy HQ)
    private MapLocation closestCow;
    // The closest soup we've seen that seems inaccessible to friendly miners
    private MapLocation closestHardSoup;
    // The closest friendly miner that we've seen
    private MapLocation closestFriendlyMiner;
    // Tracks whether closestFriendlyMiner is near soup
    private boolean closestMinerNearSoup;
    // The closest friendly landscaper that we've seen
    private MapLocation closestFriendlyLandscaper;
    // The elevation of the closest friendly miner
    private int closestFriendlyMinerElevation;
    // If dropping off a landscaper on the wall, this is the wall to target
    private int wallIdxTarget = 0;
    // Possible locations of the enemy HQ from symmetry
    private MapLocation[] symmetryHq;
    // Index of which symmetric HQ location we are currently using
    private int enemyHqSymmetryIdx;

    DynamicArray<Integer> allyDrones;
    DynamicArray<Integer> enemyDrones;

    public DeliveryDrone(int id) {
        super(id);
        this.state = DroneState.ROAMING;

        this.pathfinder = null;
        this.pathfindSteps = 0;
        this.closestWater = null;
        this.closestEnemyLandUnit = null;
        this.closestEnemyNetGun = null;
        this.closestCow = null;
        this.closestHardSoup = null;
        this.closestFriendlyMiner = null;
        this.closestMinerNearSoup = false;
        this.closestFriendlyMinerElevation = 0;
        this.closestFriendlyLandscaper = null;
    }

    public void run(RobotController rc, int turn) throws GameActionException {
        // Uncomment to turn off drone functionality for other team, useful for testing if they slow down your econ
        // especially since we don't have the miners building net guns around base yet
        //if (rc.getTeam()== Team.B)
        //    return;

        // Update our comms for global shared state.
        comms.updateForTurn(rc);

        // Update water knowledge by scanning surroundings;
        this.scanSurroundings(rc);

        // Can't do anything if we aren't ready.
        if (!rc.isReady()) return;

        // Swap on current state.
        boolean madeAction;
        do {
            Transition trans;
            Utils.print(state.toString());
            switch (this.state) {
                case RECKLESS_CHASING:
                    trans = this.recklessChasing(rc);
                    break;
                case SAFE_CHASING:
                    trans = this.safeChasing(rc);
                    break;
                case DUNKING:
                    trans = this.dunking(rc);
                    break;
                case FINDING_MINER:
                    trans = this.findingMiner(rc);
                    break;
                case FERRYING_MINER:
                    trans = this.ferryingMiner(rc);
                    break;
                case FINDING_LANDSCAPER:
                    trans = this.findingLandscaper(rc);
                    break;
                case FERRYING_LANDSCAPER:
                    trans = this.ferryingLandscaper(rc);
                    break;
                case DROPOFF_FRIENDLY:
                    trans = this.dropoffFriendly(rc);
                    break;
                case FINDING_ENEMY:
                    trans = this.findingEnemy(rc);
                    break;
                case FINDING_COW:
                    trans = this.findingCow(rc);
                    break;
                case CHASING_COW:
                    trans = this.chasingCow(rc);
                    break;
                case SWARMING:
                    trans = this.swarming(rc);
                    break;
                case FINDING_ENEMY_HQ:
                    trans = this.findEnemyHQ(rc);
                    break;
                case DRONE_WALL:
                    trans = this.droneWall(rc);
                    break;
                case LATTICE_PLACING:
                    trans = this.latticePlacing(rc);
                    break;
                default:
                case ROAMING:
                    trans = this.roaming(rc);
                    break;
            }

            // Reset transient state.
            if (this.state != trans.target) {
                this.pathfinder = null;
            }

            this.state = trans.target;
            madeAction = trans.madeAction;
        } while (!madeAction);

        // Useful for debugging.
        if (this.pathfinder != null) rc.setIndicatorLine(rc.getLocation(), this.pathfinder.goal(), 0, 255, 0);
    }

    public void scanSurroundings(RobotController rc) throws GameActionException {
        // Reset closest water if it's... unflooded.
        if (closestWater != null && rc.canSenseLocation(closestWater) && !rc.senseFlooding(closestWater)) {
            closestWater = null;
        }

        //Reset closestFriendlyMiner to null if it's not there anymore
        if (closestFriendlyMiner != null && rc.canSenseLocation(closestFriendlyMiner)) {
            RobotInfo shouldBeMiner = rc.senseRobotAtLocation(closestFriendlyMiner);
            if (shouldBeMiner == null) {
                closestFriendlyMiner = null;
            } else if (shouldBeMiner.type != RobotType.MINER || shouldBeMiner.team != rc.getTeam()) {
                closestFriendlyMiner = null;
            }
        }

        if (closestFriendlyLandscaper != null && rc.canSenseLocation(closestFriendlyLandscaper)) {
            RobotInfo shouldBeLandscaper = rc.senseRobotAtLocation(closestFriendlyLandscaper);
            if (shouldBeLandscaper == null) {
                closestFriendlyLandscaper = null;
            } else if (shouldBeLandscaper.type != RobotType.LANDSCAPER || shouldBeLandscaper.team != rc.getTeam()) {
                closestFriendlyLandscaper = null;
            }
        }

        //Reset closestEnemyLandUnit to null if outdated
        if (closestEnemyLandUnit != null && rc.canSenseLocation(closestEnemyLandUnit)) {
            RobotInfo shouldBeEnemy = rc.senseRobotAtLocation(closestEnemyLandUnit);
            if (shouldBeEnemy == null) {
                closestEnemyLandUnit = null;
            } else if (shouldBeEnemy.team == rc.getTeam()) {
                closestEnemyLandUnit = null;
            } else if (shouldBeEnemy.type != RobotType.MINER && shouldBeEnemy.type != RobotType.LANDSCAPER) {
                closestEnemyLandUnit = null;
            }
        }

        //Reset closestEnemyNetGun to null if outdated
        if (closestEnemyNetGun != null && rc.canSenseLocation(closestEnemyNetGun)) {
            RobotInfo shouldBeEnemy = rc.senseRobotAtLocation(closestEnemyNetGun);
            if (shouldBeEnemy == null) {
                closestEnemyNetGun = null;
            } else if (shouldBeEnemy.team == rc.getTeam()) {
                closestEnemyNetGun = null;
            } else if (shouldBeEnemy.type != RobotType.NET_GUN) {
                closestEnemyNetGun = null;
            }
        }

        // Reset closestCow to null if outdated
        if (closestCow != null && rc.canSenseLocation(closestCow)) {
            RobotInfo shouldBeCow = rc.senseRobotAtLocation(closestCow);
            if (shouldBeCow == null) {
                closestCow = null;
            } else if (shouldBeCow.type != RobotType.COW) {
                closestCow = null;
            }
        }

        // Reset closest soup to null if there's no longer soup or it's now flooded.
        if (closestHardSoup != null && rc.canSenseLocation(closestHardSoup)) {
            if (rc.senseSoup(closestHardSoup) == 0 || rc.senseFlooding(closestHardSoup)) closestHardSoup = null;
        }

        // If drone doesn't yet know where the HQ is and is in sensor range of a potential enemy HQ location, check if it is the enemy HQ.
        if (comms.enemyHq() == null && comms.potentialEnemyLocations() != null) {
            for (MapLocation loc : comms.potentialEnemyLocations()) {
                if (!rc.canSenseLocation(loc)) continue;

                RobotInfo robot = rc.senseRobotAtLocation(loc);
                if (robot == null || (robot.type != RobotType.HQ)) comms.notifyNoEnemyBase(loc);
            }
        }

        // If you're wondering why the weird array gimmick, it's so we can use this
        // inside the lambda. Unfortunate, yes.
        // TODO: Optimize this away by inlining traverse sensable.
        int[] waterDistance = new int[]{this.closestWater == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestWater)};
        int[] cowDistance = new int[]{this.closestCow == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestCow)};
        int[] enemyLandUnitDistance = new int[]{this.closestEnemyLandUnit == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestEnemyLandUnit)};
        int[] enemyNetGunDistance = new int[]{this.closestEnemyNetGun == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestEnemyNetGun)};
        int[] friendlyMinerDistance = new int[]{this.closestFriendlyMiner == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestFriendlyMiner)};
        int[] friendlyLandscaperDistance = new int[]{this.closestFriendlyLandscaper == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestFriendlyLandscaper)};
        int[] hardSoupDistance = new int[]{this.closestHardSoup == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestHardSoup)};

        // Scan the sensable area for water for some dunking/fun in the sun action.
        Utils.traverseSensable(rc, loc -> {
            int dist = loc.distanceSquaredTo(rc.getLocation());

            // Update closest water tile.
            if (rc.senseFlooding(loc)) {
                if (dist < waterDistance[0]) {
                    this.closestWater = loc;
                    waterDistance[0] = dist;
                }

                return;
            }

            // Update locations of robots and cows
            RobotInfo nearbyRobot = rc.senseRobotAtLocation(loc);
            if (nearbyRobot != null) {
                if (nearbyRobot.type == RobotType.COW) {
                    if (dist < cowDistance[0]) {
                        this.closestCow = loc;
                        cowDistance[0] = dist;
                    }
                } else if (nearbyRobot.team != rc.getTeam()) {
                    if (nearbyRobot.type == RobotType.MINER || nearbyRobot.type == RobotType.LANDSCAPER) {
                        if (dist < enemyLandUnitDistance[0]) {
                            this.closestEnemyLandUnit = loc;
                            enemyLandUnitDistance[0] = dist;
                        }
                    } else if (nearbyRobot.type == RobotType.HQ && comms.enemyHq() == null) {
                        comms.notifyEnemyBase(loc);
                    } else if (nearbyRobot.type == RobotType.NET_GUN) {
                        if (dist < enemyNetGunDistance[0]) {
                            this.closestEnemyNetGun = loc;
                            enemyNetGunDistance[0] = dist;
                        }
                    }
                } else if (nearbyRobot.type == RobotType.MINER) {
                    if (dist < friendlyMinerDistance[0]) {
                        this.closestFriendlyMiner = loc;
                        friendlyMinerDistance[0] = dist;
                        closestFriendlyMinerElevation = rc.senseElevation(loc);
                    }
                } else if (nearbyRobot.type == RobotType.LANDSCAPER && (comms.walls() == null || comms.walls().indexOf(loc) == -1)) {
                    if (dist < friendlyLandscaperDistance[0]) {
                        this.closestFriendlyLandscaper = loc;
                        friendlyLandscaperDistance[0] = dist;
                    }
                }
            } else if (rc.senseSoup(loc) > 0 && seemsInaccessible(rc, loc)) {
                //TODO: Account for soup that is in water, but adjacent to land that's inaccessible to miners
                if (dist < hardSoupDistance[0]) {
                    this.closestHardSoup = loc;
                    hardSoupDistance[0] = dist;
                }
            }
        });

        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        for(RobotInfo info: nearbyRobots) {
            if (info.type == RobotType.DELIVERY_DRONE) {
                if (info.team == rc.getTeam()) {
                    if (!allyDrones.contains(info.getID())) {
                        allyDrones.add(info.getID());
                    }
                } else {
                    if (!enemyDrones.contains(info.getID())) {
                        enemyDrones.add(info.getID());
                    }
                }
            }
        }
        if (closestEnemyNetGun != null && rc.getLocation().distanceSquaredTo(closestEnemyNetGun) < GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED) {
            moveAway(rc, closestEnemyNetGun);
        }
    }

    /**
     * Searches for a friendly landscaper and picks it up so it then can drop it on the wall
     */
    public Transition findingLandscaper(RobotController rc) throws GameActionException {
        // If somehow holding a unit, dunk it
        // TODO: Be very very sure we won't dunk our own units
        if (rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.DUNKING, false);

        //It's possible that the landscaper we're looking for has disappeared. In that case, go back to roaming.
        if (closestFriendlyLandscaper == null) return new Transition(DroneState.ROAMING, false);

        // If adjacent to a friendly landscaper, pick it up
        if (rc.getLocation().isAdjacentTo(closestFriendlyLandscaper)) {
            RobotInfo targetLandscaperInfo = rc.senseRobotAtLocation(closestFriendlyLandscaper);
            if (targetLandscaperInfo == null) return new Transition(DroneState.ROAMING, false);

            if (rc.canPickUpUnit(targetLandscaperInfo.ID)) {
                rc.pickUpUnit(targetLandscaperInfo.ID);
                return new Transition(DroneState.FERRYING_LANDSCAPER, true);
            }
        }

        // If no pathfinder, create it to the closest friendly miner.
        if (this.pathfinder == null) this.pathfinder = this.newPathfinder(closestFriendlyLandscaper, true);

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, comms.enemyHq(), closestEnemyNetGun, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.FINDING_LANDSCAPER, true);
    }

    /**
     * Deposits a friendly landscaper on the wall
     */
    public Transition ferryingLandscaper(RobotController rc) throws GameActionException {
        // If not carrying anything, transition to roaming.
        if (!rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.ROAMING, false);
        if (comms.isWallDone()) return new Transition(DroneState.DROPOFF_FRIENDLY, false);

        // Find which wall location to transition to; if every location is occupied, then give up and roam.
        MapLocation targetLoc = comms.walls().get(wallIdxTarget);
        while (rc.canSenseLocation(targetLoc) && rc.isLocationOccupied(targetLoc)) {
            if (rc.senseRobotAtLocation(targetLoc).team != rc.getTeam() && rc.senseRobotAtLocation(targetLoc).type.canBePickedUp()) {
                return new Transition(DroneState.DROPOFF_FRIENDLY, false);
            }
            wallIdxTarget += 1;
            if (wallIdxTarget >= comms.walls().size()) {
                return new Transition(DroneState.DROPOFF_FRIENDLY, false);
            } else {
                targetLoc = comms.walls().get(wallIdxTarget);
            }
        }

        // If we can drop landscaper on wall location, immediately do so and go back to roaming
        if (rc.getLocation().isAdjacentTo(targetLoc)) {
            Direction onWallDirection = rc.getLocation().directionTo(targetLoc);
            if (rc.canDropUnit(rc.getLocation().directionTo(targetLoc))) {
                rc.dropUnit(onWallDirection);
                return new Transition(DroneState.ROAMING, true);
            }
        }

        // If no pathfinder, create it to the closest hard-to-reach soup.
        if (this.pathfinder == null || this.pathfinder.goal() != targetLoc)
            this.pathfinder = this.newPathfinder(targetLoc, true);

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, comms.enemyHq(), closestEnemyNetGun, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.FERRYING_LANDSCAPER, true);
    }


    /**
     * Places a miner on the lattice
     */
    private Transition latticePlacing(RobotController rc) throws GameActionException {
        if (!rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.ROAMING, false);
        if (rc.getRoundNum() > 1000) return new Transition(DroneState.DROPOFF_FRIENDLY, false);

        MapLocation hq = comms.hq();
        if (rc.getLocation().distanceSquaredTo(hq) <= 20 && this.pathfinder != null && this.pathfinder.goal() == hq) {
            Direction directionToHQ = rc.getLocation().directionTo(hq);
            this.pathfinder = this.newPathfinder(hq.add(directionToHQ).add(directionToHQ).add(directionToHQ).add(directionToHQ).add(directionToHQ), true);
        }
        else if(this.pathfinder == null) {
            this.pathfinder = this.newPathfinder(hq, true);
        }

        if(rc.getLocation().distanceSquaredTo(hq) < 60) {
            for(Direction dir: Direction.allDirections()) {
                if(dir == Direction.CENTER) {
                    continue;
                }
                if(rc.senseElevation(rc.getLocation().add(dir)) >= 10 && rc.canDropUnit(dir) && !rc.senseFlooding(rc.getLocation().add(dir)) && rc.getLocation().add(dir).distanceSquaredTo(hq) > 5) {
                    rc.dropUnit(dir);
                    return new Transition(DroneState.ROAMING, true);
                }
            }
        }
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, comms.enemyHq(), closestEnemyNetGun, true));
        if(move != null && move!= Direction.CENTER) {
            rc.move(move);
        }
        return new Transition(DroneState.LATTICE_PLACING, true);
    }

    /**
     * If ferrying miners/landscapers is interrupted, tries to drop them off on any available space.
     */
    public Transition dropoffFriendly(RobotController rc) throws GameActionException {
        // If we aren't holding something suddenly, transition immediately.
        if (!rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.ROAMING, false);

        // If we can drop the unit off on any tile, do so.
        for (Direction dir : Direction.allDirections()) {
            if (dir == Direction.CENTER) continue;

            if (rc.canDropUnit(dir)) {
                rc.dropUnit(dir);
                return new Transition(DroneState.ROAMING, true);
            }
        }

        // Otherwise, roam around looking for a space to drop off.
        // If the pathfinder is inactive or finished, pick a new random location to pathfind to.
        if (this.pathfinder == null || this.pathfinder.finished(rc.getLocation())) {
            // TODO: More intelligent target selection. We choose randomly for now.
            // Suggestion: Drone remembers where it last picked enemy up and goes back. If nobody there, roam randomly.
            MapLocation target = new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight()));

            this.pathfinder = this.newPathfinder(target, true);
        }

        // We've wasted too much time on this, just give up on the unit.
        if (this.pathfindSteps >= 2 * Config.MAX_ROAM_DISTANCE) return new Transition(DroneState.ROAMING, false);

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, comms.enemyHq(), closestEnemyNetGun,true));
        if (move != null && move != Direction.CENTER) rc.move(move);
        this.pathfindSteps++;

        return new Transition(DroneState.DROPOFF_FRIENDLY, true);
    }

    /**
     * Implements roaming behavior, where the drone roams until it finds an enemy somewhere.
     */
    public Transition roaming(RobotController rc) throws GameActionException {
        // Check if carrying anything. If so, transition to dunking.
        if (rc.isCurrentlyHoldingUnit() && closestWater != null)
            return new Transition(DroneState.DUNKING, false);

        // Look for enemy robot. If see one and not currently holding a unit, transition to chasing.
        if (!rc.isCurrentlyHoldingUnit()) {
            RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            for (RobotInfo nearbyEnemy : enemyRobots) {
                if (nearbyEnemy.type.canBePickedUp()) {
                    return new Transition(DroneState.SAFE_CHASING, false);
                }
            }
        }


        // If it's past round 1000, swarm the enemy base
        if (rc.getRoundNum() >= 1000 && comms.enemyHq() != null && !rc.isCurrentlyHoldingUnit() && allyDrones.size() > enemyDrones.size()) {
            return new Transition(DroneState.SWARMING, false);
        }

        if (rc.getRoundNum() >= 1000 && !rc.isCurrentlyHoldingUnit() && (allyDrones.size() <= enemyDrones.size() || comms.enemyHq() == null )) {
            return new Transition(DroneState.DRONE_WALL, false);
        }



        //If it's after a certain round and the wall has not been built, transition to ferrying a landscaper
        if (comms.walls() != null && wallIdxTarget != comms.walls().size() && rc.getRoundNum() > 100 && !comms.isWallDone()
                && closestFriendlyLandscaper != null && !rc.isCurrentlyHoldingUnit()) {
            return new Transition(DroneState.FINDING_LANDSCAPER, false);
        }


        //TODO: Move this to the sensing logic
        if (closestFriendlyMiner != null && rc.canSenseLocation(closestFriendlyMiner)) {
            closestMinerNearSoup = false;
            MapLocation[] nearbySoup = rc.senseNearbySoup(closestFriendlyMiner,2);
            if (nearbySoup != null) {
                for (MapLocation loc : nearbySoup) {
                    if (loc != null && loc.isAdjacentTo(closestFriendlyMiner)) {
                        closestMinerNearSoup = true;
                        break;
                    }
                }
            }
        }

        //If there's hard-to-reach soup, and not currently carrying anything, transition to ferrying a miner
        if (!closestMinerNearSoup && closestHardSoup != null && closestFriendlyMiner != null && !rc.isCurrentlyHoldingUnit()) {
            if (closestFriendlyMiner.distanceSquaredTo(closestHardSoup) > 2) {
                return new Transition(DroneState.FINDING_MINER, false);
            }
        }

        //If it is the round to get builders and there are non-mining miners, make them builders
        if (!closestMinerNearSoup && closestFriendlyMiner != null && !rc.isCurrentlyHoldingUnit() && rc.canSenseLocation(closestFriendlyMiner) && rc.getRoundNum() > Config.BUILD_TRANSITION_ROUND && rc.senseElevation(closestFriendlyMiner) < 10 && rc.getRoundNum() < 800) {
            return new Transition(DroneState.FINDING_MINER, false);
        }

        // If drone knows where a cow is, it chases after it.
        if (closestCow != null && !rc.isCurrentlyHoldingUnit())
            return new Transition(DroneState.FINDING_COW, false);

        // If the pathfinder is inactive or finished, pick a new random location to pathfind to.
        if (this.pathfinder == null || this.pathfinder.finished(rc.getLocation())) {
            // TODO: More intelligent target selection. We choose randomly for now.
            // Suggestion: Drone remembers where it last picked enemy up and goes back. If nobody there, roam randomly.
            MapLocation target = new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight()));

            this.pathfinder = this.newPathfinder(target, true);
        }

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, comms.enemyHq(), closestEnemyNetGun, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.ROAMING, true);
    }

    /**
     * Travel behavior, where a drone travels to a known water location to dunk.
     */
    public Transition dunking(RobotController rc) throws GameActionException {
        // If not carrying anything, transition to roaming.
        if (!rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.ROAMING, false);

        // If we can dunk an enemy, immediately do so and go back to roaming to find more victims.
        for (Direction dir : Direction.allDirections()) {
            if (rc.canDropUnit(dir) && rc.senseFlooding(rc.getLocation().add(dir))) {
                rc.dropUnit(dir);
                if (closestEnemyLandUnit != null) {
                    return new Transition(DroneState.FINDING_ENEMY, true);
                } else {
                    return new Transition(DroneState.ROAMING, true);
                }
            }
        }

        // If on top of the closest water, move off of it
        if (rc.getLocation() == closestWater) {
            for (Direction dir: Direction.allDirections()) {
                if (dir != Direction.CENTER && rc.canMove(dir)) {
                    rc.move(dir);
                    return new Transition(DroneState.DUNKING, true);
                }
            }
        }

        // If no pathfinder, create it to the closest water.
        if (this.pathfinder == null) {
            // If all water has been unflooded, cry a little and roam.
            if (closestWater == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closestWater, true);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, comms.enemyHq(), closestEnemyNetGun, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.DUNKING, true);
    }

    public Transition recklessChasing(RobotController rc) throws GameActionException {
        // Check if carrying anything. If so, transition to dunking.
        if (rc.isCurrentlyHoldingUnit() && closestWater != null) return new Transition(DroneState.DUNKING, false);

        // Look for enemy robot. If see one, identify closest unit that can be picked up and move towards it.
        // If can already pick up unit, do so and transition to dunking.
        if (!rc.isCurrentlyHoldingUnit()) {
            Utils.ClosestRobot closest = Utils.closestRobot(rc, robot -> robot.type.canBePickedUp(), rc.getTeam().opponent());

            // If not close, swap back to roaming.
            if (closest.robot == null && (comms.enemyHq() == null || rc.getLocation().distanceSquaredTo(comms.enemyHq()) > 25))
                return new Transition(DroneState.ROAMING, false);

            // Pick it up if adjacent.
            if (closest.robot != null && rc.canPickUpUnit(closest.robot.getID())) {
                rc.pickUpUnit(closest.robot.getID());
                return new Transition(DroneState.DUNKING, true);
            }

            // Otherwise move towards it.
            // TODO: Implement better chasing movement.
            MapLocation targetEnemyLocation = closest.robot == null ? comms.enemyHq() : closest.robot.location;
            recklessAndDumbChasing(rc, targetEnemyLocation);
            return new Transition(DroneState.RECKLESS_CHASING, true);
        }

        // No unit to chase; go to roaming and hope things work out.
        return new Transition(DroneState.ROAMING, false);
    }

    public Transition findingMiner(RobotController rc) throws GameActionException {
        // If somehow holding a unit, dunk it
        // TODO: Be very very sure we won't dunk our own units
        if (rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.DUNKING, false);

        //It's possible that the miner we're looking for has disappeared. In that case, go back to roaming.
        if (closestFriendlyMiner == null) return new Transition(DroneState.ROAMING, false);

        //TODO:  If miner is already very close (adjacent) to soup, then don't pick it up

        // If adjacent to a friendly miner, pick it up
        if (rc.getLocation().isAdjacentTo(closestFriendlyMiner)) {
            RobotInfo targetMinerInfo = rc.senseRobotAtLocation(closestFriendlyMiner);
            if (targetMinerInfo == null) {
                return new Transition(DroneState.ROAMING, false);
            }
            if (rc.canPickUpUnit(targetMinerInfo.ID) && closestHardSoup!= null) {
                rc.pickUpUnit(targetMinerInfo.ID);
                return new Transition(DroneState.FERRYING_MINER, true);
            } else if (rc.canPickUpUnit(targetMinerInfo.ID)) {
                rc.pickUpUnit(targetMinerInfo.ID);
                return new Transition(DroneState.LATTICE_PLACING, true);
            }
        }

        // If no pathfinder, create it to the closest friendly miner.
        if (this.pathfinder == null) {
            // If we haven't seen any friendly miners, cry a little and roam.
            if (closestFriendlyMiner == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closestFriendlyMiner, true);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, comms.enemyHq(), closestEnemyNetGun, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.FINDING_MINER, true);
    }

    public Transition ferryingMiner(RobotController rc) throws GameActionException {
        // If not carrying anything, transition to roaming.
        if (!rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.ROAMING, false);

        // If something has happened to our destination, drop the miner
        if (closestHardSoup == null) return new Transition(DroneState.DROPOFF_FRIENDLY, true);

        // If we can drop miner by soup location, immediately do so and go back to roaming
        if (rc.getLocation().isAdjacentTo(closestHardSoup)) {
            Direction onSoupDirection = rc.getLocation().directionTo(closestHardSoup);
            if (rc.canDropUnit(onSoupDirection.rotateLeft()) && !rc.senseFlooding(rc.getLocation().add(onSoupDirection.rotateLeft()))) {
                rc.dropUnit(onSoupDirection.rotateLeft());
                return new Transition(DroneState.ROAMING, true);
            } else if (rc.canDropUnit(onSoupDirection.rotateRight()) && !rc.senseFlooding(rc.getLocation().add(onSoupDirection.rotateRight()))) {
                rc.dropUnit(onSoupDirection.rotateRight());
                return new Transition(DroneState.ROAMING, true);
            } else if (rc.canDropUnit(rc.getLocation().directionTo(closestHardSoup))) {
                rc.dropUnit(onSoupDirection);
                return new Transition(DroneState.ROAMING, true);
            }

            // If can't drop it directly on the tile, drop miner on any non-flooded tile
            for (Direction adj : Direction.allDirections()) {
                if (adj == Direction.CENTER) continue;
                if (!rc.senseFlooding(rc.getLocation().add(adj)) && rc.canDropUnit(adj)) {
                    rc.dropUnit(adj);
                    return new Transition(DroneState.ROAMING, true);
                }
            }
        }

        // If no pathfinder, create it to the closest hard-to-reach soup.
        if (this.pathfinder == null) {
            // If all hard soup is gone, cry a little and roam.
            if (closestHardSoup == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closestHardSoup, true);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, comms.enemyHq(), closestEnemyNetGun, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.FERRYING_MINER, true);
    }

    public Transition findingEnemy(RobotController rc) throws GameActionException {
        // If carrying anything, transition to dunking.
        // This shouldn't happen, but it's here just in case
        if (rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.DUNKING, false);

        // If there's no longer an enemy where we thought there was, give up the hunt and start roaming.
        if (closestEnemyLandUnit == null) return new Transition(DroneState.ROAMING, false);

        // If we see an enemy, transition to chasing it
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo nearbyEnemy : enemyRobots) {
            if (nearbyEnemy.type.canBePickedUp()) {
                return new Transition(DroneState.SAFE_CHASING, false);
            }
        }

        // If no pathfinder, create it to the closest enemy.
        if (this.pathfinder == null) {
            // If don't know where an enemy is, cry a little and roam.
            if (closestEnemyLandUnit == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closestEnemyLandUnit, true);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, comms.enemyHq(), closestEnemyNetGun, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.FINDING_ENEMY, true);
    }

    public Transition findingCow(RobotController rc) throws GameActionException {
        // If carrying anything, transition to dunking.
        // This shouldn't happen, but it's here just in case
        if (rc.isCurrentlyHoldingUnit()) {
            return new Transition(DroneState.DUNKING, false);
        }

        // If there's no longer a cow where we thought there was, give up the hunt and start roaming.
        if (closestCow == null) {
            return new Transition(DroneState.ROAMING, false);
        }

        // If we see an enemy, transition to chasing it
        RobotInfo[] enemyRobots = rc.senseNearbyRobots();
        for (RobotInfo nearbyEnemy : enemyRobots) {
            if (nearbyEnemy.type.canBePickedUp() && nearbyEnemy.team == rc.getTeam().opponent()) {
                return new Transition(DroneState.SAFE_CHASING, false);
            }
        }

        // If we see a cow, transition to chasing it
        for (RobotInfo nearbyEnemy : enemyRobots) {
            if (nearbyEnemy.type == RobotType.COW) {
                return new Transition(DroneState.CHASING_COW, false);
            }
        }

        // If no pathfinder, create it to the closest cow.
        if (this.pathfinder == null) {
            // If don't know where an enemy is, cry a little and roam.
            if (closestCow == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closestCow, true);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, comms.enemyHq(), closestEnemyNetGun, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.FINDING_COW, true);
    }

    public Transition chasingCow(RobotController rc) throws GameActionException {
        // If carrying anything, transition to dunking
        if (rc.isCurrentlyHoldingUnit()) {
            return new Transition(DroneState.DUNKING, false);
        }

        // Drone identifies its target. If no target, it transitions to roaming.
        Utils.ClosestRobot closestTarget = Utils.closestRobot(rc, robot -> robot.type == RobotType.COW, Team.NEUTRAL);
        if (closestTarget.robot == null) {
            return new Transition(DroneState.ROAMING, false);
        }

        //If adjacent to target, pick them up and transition to dunking
        if (rc.getLocation().isAdjacentTo(closestTarget.robot.location)) {
            if (rc.canPickUpUnit(closestTarget.robot.ID)) {
                rc.pickUpUnit(closestTarget.robot.ID);
                return new Transition(DroneState.DUNKING, true);
            }
        }

        // If pathfinder is not currently targeting the closest enemy, reset it
        if (this.pathfinder != null) {
            if (this.pathfinder.goal() != closestTarget.robot.location) {
                this.pathfinder = null;
            }
        } // If no pathfinder, create it to the closest enemy
        if (this.pathfinder == null) {
            // If closestTarget is somehow null, roam.
            if (closestTarget.robot == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closestTarget.robot.location, true);
            }
        }


        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER && canMoveD(rc, move, nearbyEnemies, comms.enemyHq(), closestEnemyNetGun, true)) rc.move(move);


        return new Transition(DroneState.DUNKING, true);
    }

    public Transition swarming(RobotController rc) throws GameActionException {
        if (rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.DUNKING, false);

        // If drone doesn't know where HQ is, it looks for the HQ
        if (comms.enemyHq() == null) return new Transition(DroneState.FINDING_ENEMY_HQ, false);

        // If sufficiently far from enemy base, then will dunk enemies on the way
        if (rc.getLocation().distanceSquaredTo(comms.enemyHq()) > 40) {
            RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            for (RobotInfo nearbyEnemy : enemyRobots) {
                if (nearbyEnemy.type.canBePickedUp()) {
                    return new Transition(DroneState.SAFE_CHASING, false);
                }
            }
        }

        // If drone near HQ and it's been long enough, swarm. There are four waves.
        // If drone is near HQ and it's not yet time, it sits still
        if (rc.getLocation().distanceSquaredTo(comms.enemyHq()) <= 25) {
            if (rc.getRoundNum() > 1300 && rc.getRoundNum() < 1325) {
                return new Transition(DroneState.RECKLESS_CHASING, false);
            } else if (rc.getRoundNum() > 1700 && rc.getRoundNum() < 1725) {
                return new Transition(DroneState.RECKLESS_CHASING, false);
            } else if (rc.getRoundNum() > 2100 && rc.getRoundNum() < 2125) {
                return new Transition(DroneState.RECKLESS_CHASING, false);
            } else if (rc.getRoundNum() > 3000) {
                return new Transition(DroneState.RECKLESS_CHASING, false);
            } else {
                return new Transition(DroneState.SWARMING, true);
            }
        }

        // If drone not yet near HQ, it goes to it
        // If no pathfinder, create it to the HQ.
        if (this.pathfinder == null) {
            // If don't know where an enemy is, cry a little and roam.
            if (comms.enemyHq() == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(comms.enemyHq(), true);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        // Checks that the destination is a certain distance away from HQ because there's one corner where drone...
        // can't see HQ, but will move into net range
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER && canMoveD(rc, move, enemies, comms.enemyHq(), closestEnemyNetGun, true)) {
            if (rc.getLocation().add(move).distanceSquaredTo(comms.enemyHq()) > GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED) {
                rc.move(move);
            }
        }

        return new Transition(DroneState.SWARMING, true);
    }

    public Transition droneWall(RobotController rc) throws GameActionException {
        if (rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.DUNKING, false);

        // If sufficiently far from enemy base, then will dunk enemies on the way
        if (rc.getLocation().distanceSquaredTo(comms.hq()) > 40) {
            RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            for (RobotInfo nearbyEnemy : enemyRobots) {
                if (nearbyEnemy.type.canBePickedUp()) {
                    return new Transition(DroneState.SAFE_CHASING, false);
                }
            }
        }

        // If close to HQ and a landscaper is between drone and HQ, drone is still.
        // This may interfere with late-to-the-party bolsterers,  but a stationary wall is good
        if (rc.getLocation().distanceSquaredTo(comms.hq()) <=  18) {
            RobotInfo robotBetweenDroneAndHQ = rc.senseRobotAtLocation(rc.getLocation().add(rc.getLocation().directionTo(comms.hq())));
            if (robotBetweenDroneAndHQ != null && robotBetweenDroneAndHQ.team == rc.getTeam() && robotBetweenDroneAndHQ.type == RobotType.LANDSCAPER) {
                return new Transition(DroneState.DRONE_WALL, true);
            }
        }


        // If drone not yet near HQ, it goes to it
        // If no pathfinder, create it to the HQ.
        if (this.pathfinder == null) {
            this.pathfinder = this.newPathfinder(comms.hq(), true);
        }

        // Obtain a movement from the pathfinder and follow it.
        // Checks that the destination is a certain distance away from HQ because there's one corner where drone...
        // can't see HQ, but will move into net range
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER && canMoveD(rc, move, enemies, comms.enemyHq(), closestEnemyNetGun, true)) {
            if (rc.getLocation().add(move).distanceSquaredTo(comms.enemyHq()) > GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED) {
                rc.move(move);
            }
        }

        return new Transition(DroneState.DRONE_WALL, true);
    }

    public Transition findEnemyHQ(RobotController rc) throws GameActionException {
        if (comms.enemyHq() != null) return new Transition(DroneState.ROAMING, false);

        // If no pathfinder, create it to the HQ.
        MapLocation latestOption = comms.potentialEnemyLocations() == null ? null : comms.potentialEnemyLocations().get(0);
        if (latestOption == null) return new Transition(DroneState.ROAMING, false);

        if (this.pathfinder == null || !this.pathfinder.goal().equals(latestOption))
            this.pathfinder = this.newPathfinder(latestOption, true);

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> canMoveD(rc, dir, nearbyEnemies, comms.enemyHq(), closestEnemyNetGun, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.FINDING_ENEMY_HQ, true);
    }

    // Chasing an enemy while trying not to get shot down by net guns
    public Transition safeChasing(RobotController rc) throws GameActionException {
        // If carrying anything, transition to dunking
        if (rc.isCurrentlyHoldingUnit())
            return new Transition(DroneState.DUNKING, false);

        //if (comms.enemyHq() != null && rc.getRoundNum() > 1000 && rc.getLocation().distanceSquaredTo(comms.enemyHq()) < 70)
        //    return new Transition(DroneState.SWARMING, false);

        if (comms.enemyHq() != null && rc.getRoundNum() > 1000 && rc.getLocation().distanceSquaredTo(comms.enemyHq()) < 70)
            return new Transition(DroneState.DRONE_WALL, false);

        // Drone identifies its target. If no target, it transitions to roaming.
        Utils.ClosestRobot closestTarget = Utils.closestRobot(rc, robot -> robot.type == RobotType.LANDSCAPER || robot.type == RobotType.MINER, rc.getTeam().opponent());
        if (closestTarget.robot == null) {
            return new Transition(DroneState.ROAMING, false);
        }

        //If adjacent to target, pick them up and transition to dunking
        if (rc.getLocation().isAdjacentTo(closestTarget.robot.location)) {
            if (rc.canPickUpUnit(closestTarget.robot.ID)) {
                rc.pickUpUnit(closestTarget.robot.ID);
                return new Transition(DroneState.DUNKING, true);
            }
        }

        if (this.pathfinder == null || !this.pathfinder.goal().equals(closestTarget.robot.location))
            this.pathfinder = this.newPathfinder(closestTarget.robot.location, true);

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> canMoveD(rc, dir, nearbyEnemies, comms.enemyHq(), closestEnemyNetGun, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.SAFE_CHASING, true);
    }

    /**
     * Returns true if the location contains soup and it seems like miners need help getting to it.
     */
    private boolean seemsInaccessible(RobotController rc, MapLocation loc) throws GameActionException {
        // TODO: Hmm... not a fan of this state here.
        if (closestFriendlyMiner == null) return true;

        return rc.senseElevation(loc) > closestFriendlyMinerElevation + GameConstants.MAX_DIRT_DIFFERENCE
                || closestFriendlyMiner.distanceSquaredTo(loc) >= 10
                || rc.senseElevation(loc) < closestFriendlyMinerElevation - GameConstants.MAX_DIRT_DIFFERENCE;
    }

    /**
     * Takes a target location loc. If can move towards it, then does. If can't, then moves wherever it can.
     */
    private boolean recklessAndDumbChasing(RobotController rc, MapLocation loc) throws GameActionException {
        Direction straightToTarget = rc.getLocation().directionTo(loc);
        if (rc.canMove(straightToTarget)) {
            rc.move(straightToTarget);
            return true;
        } else if (rc.canMove(straightToTarget.rotateLeft())) {
            rc.move(straightToTarget.rotateLeft());
            return true;
        } else if (rc.canMove(straightToTarget.rotateRight())) {
            rc.move(straightToTarget.rotateRight());
            return true;
        } else {
            for (Direction adj : Direction.allDirections()) {
                if (adj == Direction.CENTER) continue;
                if (rc.canMove(adj)) {
                    rc.move(adj);
                    return true;
                }
            }

            return false;
        }
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        this.comms = Bitconnect.initialize(rc);
        this.allyDrones = new DynamicArray<>(50);
        this.enemyDrones = new DynamicArray<>(50);
        comms.scanRecent(rc, 50);
    }

    /**
     * A movement check which respects enemy netgun range.
     */
    private static boolean canMoveD(RobotController rc, Direction dir, RobotInfo[] enemies, MapLocation enemyHQLoc, MapLocation closestEnemyNetGun, boolean respectNetguns) {
        if (!rc.canMove(dir)) return false;
        if (!respectNetguns) return true;

        // We respect netguns and can otherwise move; go ahead and ensure we are not in range of any netguns.
        MapLocation target = rc.getLocation().add(dir);
        for (RobotInfo info : enemies) {
            if (info.location.distanceSquaredTo(target) <= GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED && (info.type == RobotType.NET_GUN || info.type == RobotType.HQ))
                return false;
        }
        if (closestEnemyNetGun != null && closestEnemyNetGun.distanceSquaredTo(target) <= GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED) return false;
        if (enemyHQLoc != null && enemyHQLoc.distanceSquaredTo(target) <= GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED) return false;

        return true;
    }

    private void moveAway(RobotController rc, MapLocation loc) throws GameActionException{
        Direction directlyAway = loc.directionTo(rc.getLocation());
        if (rc.canMove(directlyAway)) rc.move(directlyAway);
        else if (rc.canMove(directlyAway.rotateRight())) rc.move(directlyAway.rotateRight());
        else if (rc.canMove(directlyAway.rotateLeft())) rc.move(directlyAway.rotateLeft());
    }
}