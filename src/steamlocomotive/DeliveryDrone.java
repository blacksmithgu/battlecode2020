package steamlocomotive;

import battlecode.common.*;

public strictfp class DeliveryDrone extends Unit {
    // TODO implement interactions with cows (drowning them, dropping them on enemies, or both)
    // TODO: make a "safe chasing" state
    //

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
        // The drone swarms the enemy HQ
        SWARMING,
        // The drone searches for the enemy HQ
        FINDING_ENEMY_HQ
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
    // The closest water we've seen for dunking.
    private MapLocation closestWater;
    // The closest enemy landscaper or miner that we've seen, for dunking
    private MapLocation closestEnemyLandUnit;
    // The closest cow that we've seen (that isn't already close to enemy HQ)
    private MapLocation closestCow;
    // The closest soup we've seen that seems inaccessible to friendly miners
    private MapLocation closestHardSoup;
    // The closest friendly miner that we've seen
    private MapLocation closestFriendlyMiner;
    // The closest friendly landscaper that we've seen
    private MapLocation closestFriendlyLandscaper;
    // The elevation of the closest friendly miner
    private int closestFriendlyMinerElevation;
    // Our team's HQ location
    private MapLocation friendlyHQLoc;
    // Enemy team's HQ location
    private MapLocation enemyHQLoc;
    // Comms object
    private Bitconnect comms;
    // If dropping off a landscaper on the wall, this is the wall to target
    private int wallIdxTarget = 0;
    // locations of where to build the wall, transmitted by HQ
    private Bitconnect.HQSurroundings wallLocations;
    // have we found the enemy HQ location
    private boolean foundHQ;
    // Possible locations of the enemy HQ from symmetry
    private MapLocation[] symmetryHq;
    // Index of which symmetric HQ location we are currently using
    private int enemyHqSymmetryIdx;

    public DeliveryDrone(int id) {
        super(id);
        this.pathfinder = null;
        this.pathfindSteps = 0;
        this.closestWater = null;
        this.closestEnemyLandUnit = null;
        this.closestCow = null;
        this.closestHardSoup = null;
        this.closestFriendlyMiner = null;
        this.closestFriendlyMinerElevation = 0;
        this.closestFriendlyLandscaper = null;
        this.friendlyHQLoc = null;
        this.enemyHQLoc = null;
        this.state = DroneState.ROAMING;
    }

    public void run(RobotController rc, int turn) throws GameActionException {
        // Update our comms
        comms.updateForTurn(rc);

        Utils.print(state.toString());

        // Update water knowledge by scanning surroundings;
        if (this.state == DroneState.FINDING_LANDSCAPER || this.state == DroneState.FERRYING_LANDSCAPER) {
            this.scanSurroundingsDumb(rc);
        } else {
            this.scanSurroundings(rc);
        }

        if (foundHQ == false) {
            if (comms.getEnemyBaseLocation() != null) {
                this.enemyHQLoc = comms.getEnemyBaseLocation();
                foundHQ = true;
            }
        }

        // Swap on current state.
        boolean madeAction;
        do {
            Transition trans;
            switch (this.state) {
                case RECKLESS_CHASING: trans = this.recklessChasing(rc); break;
                case SAFE_CHASING: trans = this.safeChasing(rc); break;
                case DUNKING: trans = this.dunking(rc); break;
                case FINDING_MINER: trans = this.findingMiner(rc); break;
                case FERRYING_MINER: trans = this.ferryingMiner(rc); break;
                case FINDING_LANDSCAPER: trans = this.findingLandscaper(rc); break;
                case FERRYING_LANDSCAPER: trans = this.ferryingLandscaper(rc); break;
                case DROPOFF_FRIENDLY: trans = this.dropoffFriendly(rc); break;
                case FINDING_ENEMY: trans = this.findingEnemy(rc); break;
                case FINDING_COW: trans = this.findingCow(rc); break;
                case CHASING_COW: trans = this.chasingCow(rc); break;
                case SWARMING: trans = this.swarming(rc); break;
                case FINDING_ENEMY_HQ: trans = this.findEnemyHQ(rc); break;
                default:
                case ROAMING: trans = this.roaming(rc); break;
            }

            // Reset transient state.
            if (this.state != trans.target) {
                this.pathfinder = null;
            }

            this.state = trans.target;
            madeAction = trans.madeAction;
        } while (!madeAction);

//        if (enemyHQLoc != null) {
//            System.out.println("Enemy HQ at" + enemyHQLoc);
//        }

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

        // If drone doesn't yet know where the HQ is and is in sensor range of the next possible location, it checks
        if (!foundHQ && enemyHQLoc != null && rc.canSenseLocation(this.enemyHQLoc)) {
            if (rc.senseRobotAtLocation(this.enemyHQLoc) == null || rc.senseRobotAtLocation(this.enemyHQLoc).type != RobotType.HQ) {
                enemyHqSymmetryIdx += 1;
                enemyHQLoc = symmetryHq[enemyHqSymmetryIdx];
                if (enemyHqSymmetryIdx == 2) {
                    comms.setEnemyBaseLocation(this.enemyHQLoc);
                    foundHQ = true;
                }
            } else if (enemyHQLoc != null && rc.canSenseLocation(this.enemyHQLoc)) {
                if (rc.senseRobotAtLocation(this.enemyHQLoc).type == RobotType.HQ) {
                    comms.setEnemyBaseLocation(this.enemyHQLoc);
                    foundHQ = true;
                }
            }
        }



        // If you're wondering why the weird array gimmick, it's so we can use this
        // inside the lambda. Unfortunate, yes.
        // TODO: Optimize this away by inlining traverse sensable.
        int[] waterDistance = new int[]{this.closestWater == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestWater)};
        int[] cowDistance = new int[]{this.closestCow == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestCow)};
        int[] enemyLandUnitDistance = new int[]{this.closestEnemyLandUnit == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestEnemyLandUnit)};
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
                    } else if (nearbyRobot.type == RobotType.HQ && this.enemyHQLoc == null) {
                        this.enemyHQLoc = nearbyRobot.location;
                        if (foundHQ == false) {
                            comms.setEnemyBaseLocation(this.enemyHQLoc);
                            foundHQ = true;
                        }
                    }
                } else if (nearbyRobot.type == RobotType.MINER) {
                    if (dist < friendlyMinerDistance[0]) {
                        this.closestFriendlyMiner = loc;
                        friendlyMinerDistance[0] = dist;
                        closestFriendlyMinerElevation = rc.senseElevation(loc);
                    }
                } else if (nearbyRobot.type == RobotType.LANDSCAPER) {
                    boolean onWall = false;
                    for (int i = 0; i < wallLocations.adjacentWallSpots.length; i++) {
                        if (nearbyRobot.location.equals(wallLocations.adjacentWallSpots[i])) {
                            onWall = true;
                            break;
                        }
                    }
                    if (!onWall) {
                        if (dist < friendlyLandscaperDistance[0]) {
                            this.closestFriendlyLandscaper = loc;
                            friendlyLandscaperDistance[0] = dist;
                            //closestFriendlyMinerElevation = rc.senseElevation(loc);
                        }
                    }
                } else if (nearbyRobot.type == RobotType.HQ && this.friendlyHQLoc == null) {
                    this.friendlyHQLoc = nearbyRobot.location;
                }
            } else if (rc.senseSoup(loc) > 0 && seemsInaccessible(rc, loc)) {
                //TODO: Account for soup that is in water, but adjacent to land that's inaccessible to miners
                if (dist < hardSoupDistance[0]) {
                    this.closestHardSoup = loc;
                    hardSoupDistance[0] = dist;
                }
            }
        });
    }

    public void scanSurroundingsDumb(RobotController rc) throws GameActionException {
        if (closestFriendlyLandscaper != null && rc.canSenseLocation(closestFriendlyLandscaper)) {
            RobotInfo shouldBeLandscaper = rc.senseRobotAtLocation(closestFriendlyLandscaper);
            if (shouldBeLandscaper == null) {
                closestFriendlyLandscaper = null;
            } else if (shouldBeLandscaper.type != RobotType.LANDSCAPER || shouldBeLandscaper.team != rc.getTeam()) {
                closestFriendlyLandscaper = null;
            }
        }

        int[] friendlyLandscaperDistance = new int[]{this.closestFriendlyLandscaper == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestFriendlyLandscaper)};
        RobotInfo[] robots = rc.senseNearbyRobots();
        for (RobotInfo rob : robots) {
            if (rob.type == RobotType.LANDSCAPER) {
                boolean onWall = false;
                for (int i = 0; i < wallLocations.adjacentWallSpots.length; i++) {
                    if (rob.location.equals(wallLocations.adjacentWallSpots[i])) {
                        onWall = true;
                        break;
                    }
                }
                if (onWall == false) {
                    System.out.println("landscaper spotted");
                    int dist = rob.location.distanceSquaredTo(rc.getLocation());
                    if (dist < friendlyLandscaperDistance[0]) {
                        this.closestFriendlyLandscaper = rob.location;
                        friendlyLandscaperDistance[0] = dist;
                    }
                }
            }
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
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.FINDING_LANDSCAPER, true);
    }

    /**
     * Deposits a friendly landscaper on the wall
     */
    public Transition ferryingLandscaper(RobotController rc) throws GameActionException {
        // If not carrying anything, transition to roaming.
        if (!rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.ROAMING, false);

        // Find which wall location to transition to; if every location is occupied, then give up and roam.
        MapLocation targetLoc = wallLocations.adjacentWallSpots[wallIdxTarget];
        while (rc.canSenseLocation(targetLoc) && rc.isLocationOccupied(targetLoc)) {
            if (rc.senseRobotAtLocation(targetLoc).team != rc.getTeam() && rc.senseRobotAtLocation(targetLoc).type.canBePickedUp()) {
                return new Transition(DroneState.DROPOFF_FRIENDLY, false);
            }
            wallIdxTarget += 1;
            if (wallIdxTarget >= wallLocations.adjacentWallSpots.length) {
                return new Transition(DroneState.DROPOFF_FRIENDLY, false);
            } else {
                targetLoc = wallLocations.adjacentWallSpots[wallIdxTarget];
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
        if (this.pathfinder == null) this.pathfinder = this.newPathfinder(targetLoc, true);

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.FERRYING_LANDSCAPER, true);
    }

    /** If ferrying miners/landscapers is interrupted, tries to drop them off on any available space. */
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
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, true));
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
        if (rc.getRoundNum() >= 1000 && rc.getRoundNum() < 2000 && enemyHQLoc != null && foundHQ && !rc.isCurrentlyHoldingUnit()) {
            return new Transition(DroneState.SWARMING, false);
        }


        //If there's hard-to-reach soup, and not currently carrying anything, transition to ferrying a miner
        if (closestHardSoup != null && closestFriendlyMiner != null && !rc.isCurrentlyHoldingUnit()) {
            if (closestFriendlyMiner.distanceSquaredTo(closestHardSoup) >= 18) {
                return new Transition(DroneState.FINDING_MINER, false);
            }
        }

        //If it's after a certain round and the wall has not been built, transition to ferrying a landscaper
        if (wallLocations != null && wallIdxTarget != wallLocations.adjacentWallSpots.length && rc.getRoundNum() > 200 && !comms.isWallDone(rc) && closestFriendlyLandscaper != null && !rc.isCurrentlyHoldingUnit()) {
            return new Transition(DroneState.FINDING_LANDSCAPER, false);
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
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, true));
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
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.DUNKING, true);
    }

    public Transition recklessChasing(RobotController rc) throws GameActionException {
        MapLocation droneLoc = rc.getLocation();

        // Check if carrying anything. If so, transition to dunking.
        if (rc.isCurrentlyHoldingUnit() && closestWater != null) return new Transition(DroneState.DUNKING, false);

        // Look for enemy robot. If see one, identify closest unit that can be picked up and move towards it.
        // If can already pick up unit, do so and transition to dunking.
        if (!rc.isCurrentlyHoldingUnit()) {
            Utils.ClosestRobot closest = Utils.closestRobot(rc, robot -> robot.type.canBePickedUp(), rc.getTeam().opponent());

            // If not close, swap back to roaming.
            if (closest.robot == null) return new Transition(DroneState.ROAMING, false);

            // Pick it up if adjacent.
            if (rc.canPickUpUnit(closest.robot.getID())) {
                rc.pickUpUnit(closest.robot.getID());
                return new Transition(DroneState.DUNKING, true);
            }

            // Otherwise move towards it.
            // TODO: Implement better chasing movement.
            MapLocation targetEnemyLocation = closest.robot.location;
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
            if (rc.canPickUpUnit(targetMinerInfo.ID)) {
                rc.pickUpUnit(targetMinerInfo.ID);
                return new Transition(DroneState.FERRYING_MINER, true);
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
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, true));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.FINDING_MINER, true);
    }

    public Transition ferryingMiner(RobotController rc) throws GameActionException {
        // If not carrying anything, transition to roaming.
        if (!rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.ROAMING, false);

        // If something has happened to our destination, drop the miner
        if (closestHardSoup == null) return new Transition(DroneState.DROPOFF_FRIENDLY, true);

        // If we can drop miner on soup location, immediately do so and go back to roaming
        if (rc.getLocation().isAdjacentTo(closestHardSoup)) {
            Direction onSoupDirection = rc.getLocation().directionTo(closestHardSoup);
            if (rc.canDropUnit(rc.getLocation().directionTo(closestHardSoup))) {
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
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, true));
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
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, true));
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
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> DeliveryDrone.canMoveD(rc, dir, enemies, true));
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
        if (closestTarget == null) {
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
        else if (this.pathfinder == null) {
            // If closestTarget is somehow null, roam.
            if (closestTarget == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closestTarget.robot.location, true);
            }
        }


        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER && canMoveD(rc, move, nearbyEnemies, true)) rc.move(move);


        return new Transition(DroneState.DUNKING, true);
    }

    public Transition swarming(RobotController rc) throws GameActionException{
        if (rc.isCurrentlyHoldingUnit()) return new Transition(DroneState.DUNKING,false);

        // If drone doesn't know where HQ is, it looks for the HQ
        if (!foundHQ) return new Transition(DroneState.FINDING_ENEMY_HQ, false);

        // If sufficiently far from enemy base, then will dunk enemies on the way
        if (rc.getLocation().distanceSquaredTo(enemyHQLoc) > 40) {
            RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            for (RobotInfo nearbyEnemy : enemyRobots) {
                if (nearbyEnemy.type.canBePickedUp()) {
                    return new Transition(DroneState.RECKLESS_CHASING, false);
                }
            }
        }

        // If drone near HQ and it's been long enough, swarm. There are four waves.
        if (rc.getLocation().distanceSquaredTo(enemyHQLoc) < 25){
            if (rc.getRoundNum() > 1600 && rc.getRoundNum() < 1610) {
                return new Transition(DroneState.RECKLESS_CHASING, false);
            }
            else if (rc.getRoundNum() > 2100 && rc.getRoundNum() < 2110) {
                return new Transition(DroneState.RECKLESS_CHASING, false);
            }
            else if (rc.getRoundNum() > 2600 && rc.getRoundNum() < 2610) {
                return new Transition(DroneState.RECKLESS_CHASING, false);
            }
            else if (rc.getRoundNum() > 3000) {
                return new Transition(DroneState.RECKLESS_CHASING, false);
            }
        }

        // If drone not yet near HQ, it goes to it
        // If no pathfinder, create it to the HQ.
        if (this.pathfinder == null) {
            // If don't know where an enemy is, cry a little and roam.
            if (enemyHQLoc == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(enemyHQLoc, true);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        // Checks that the destination is a certain distance away from HQ because there's one corner where drone...
        // can't see HQ, but will move into net range
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER && canMoveD(rc, move, enemies, true))
            if (rc.getLocation().add(move).distanceSquaredTo(enemyHQLoc) > GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED) {
                rc.move(move);
            }

        return new Transition(DroneState.SWARMING, true);
    }

    public Transition findEnemyHQ(RobotController rc) throws GameActionException{
        if (foundHQ) {
            return new Transition(DroneState.ROAMING, false);
        }

        // If no pathfinder, create it to the HQ.
        if (this.pathfinder == null) {
            // If enemyHQLoc is somehow null, cry a little and roam.
            if (enemyHQLoc == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(enemyHQLoc, true);
            }
        }

        if (this.pathfinder.goal() != enemyHQLoc) {
            this.pathfinder = this.newPathfinder(enemyHQLoc, true);
        }

        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER && canMoveD(rc, move, nearbyEnemies, true)) rc.move(move);

        return new Transition(DroneState.FINDING_ENEMY_HQ, true);
    }

    // Chasing an enemy while trying not to get shot down by net guns
    public Transition safeChasing(RobotController rc) throws GameActionException {

        // If carrying anything, transition to dunking
        if (rc.isCurrentlyHoldingUnit()) {
            return new Transition(DroneState.DUNKING, false);
        }

        if (foundHQ && enemyHQLoc !=null && rc.getRoundNum() > 1000 && rc.getLocation().distanceSquaredTo(enemyHQLoc) < 70) {
            return new Transition(DroneState.SWARMING, false);
        }

        // Drone identifies its target. If no target, it transitions to roaming.
        Utils.ClosestRobot closestTarget = Utils.closestRobot(rc, robot -> robot.type == RobotType.LANDSCAPER || robot.type == RobotType.MINER, rc.getTeam().opponent());
        if (closestTarget == null) {
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
        else if (this.pathfinder == null) {
            // If closestTarget is somehow null, roam.
            if (closestTarget == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closestTarget.robot.location, true);
            }
        }


        // Obtain a movement from the pathfinder and follow it.
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER && canMoveD(rc, move, nearbyEnemies, true)) rc.move(move);


        return new Transition(DroneState.SAFE_CHASING, true);
    }



    /** A movement check which respects enemy netgun range. */
    private static boolean canMoveD(RobotController rc, Direction dir, RobotInfo[] enemies, boolean respectNetguns) {
        if (!rc.canMove(dir)) return false;
        if (!respectNetguns) return true;

        // We respect netguns and can otherwise move; go ahead and ensure we are not in range of any netguns.
        MapLocation target = rc.getLocation().add(dir);
        for (RobotInfo info : enemies) {
            if (info.location.distanceSquaredTo(target) <= GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED && (info.type == RobotType.NET_GUN || info.type == RobotType.HQ))
                return false;
        }

        return true;
    }

    /** Returns true if the location contains soup and it seems like miners need help getting to it. */
    private boolean seemsInaccessible(RobotController rc, MapLocation loc) throws GameActionException {
        // TODO: Hmm... not a fan of this state here.
        if (closestFriendlyMiner == null) return true;

        return rc.senseElevation(loc) > closestFriendlyMinerElevation + GameConstants.MAX_DIRT_DIFFERENCE
                || closestFriendlyMiner.distanceSquaredTo(loc) >= 10
                || rc.senseElevation(loc) < closestFriendlyMinerElevation - GameConstants.MAX_DIRT_DIFFERENCE;
    }

    /** Takes a target location loc. If can move towards it, then does. If can't, then moves wherever it can. */
    private void recklessAndDumbChasing(RobotController rc, MapLocation loc) throws GameActionException {
        Direction straightToTarget = rc.getLocation().directionTo(loc);
        if (rc.canMove(straightToTarget)) {
            rc.move(straightToTarget);
            return;
        } else if (rc.canMove(straightToTarget.rotateLeft())) {
            rc.move(straightToTarget.rotateLeft());
            return;
        } else if (rc.canMove(straightToTarget.rotateRight())) {
            rc.move(straightToTarget.rotateRight());
            return;
        } else {
            for (Direction adj : Direction.allDirections()) {
                if (adj == Direction.CENTER) continue;
                if (rc.canMove(adj)) {
                    rc.move(adj);
                    return;
                }
            }
        }
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        comms = new Bitconnect(rc, rc.getMapWidth(), rc.getMapHeight());
        wallLocations = comms.getWallLocations(rc);
        friendlyHQLoc = wallLocations.hq;

        this.enemyHQLoc = comms.getEnemyBaseLocation();
        this.symmetryHq = new MapLocation[3];
        this.symmetryHq[0] = new MapLocation(rc.getMapWidth() - friendlyHQLoc.x - 1, rc.getMapHeight() - friendlyHQLoc.y - 1);
        this.symmetryHq[1] = new MapLocation(friendlyHQLoc.x, rc.getMapHeight() - friendlyHQLoc.y - 1);
        this.symmetryHq[2] = new MapLocation(rc.getMapWidth() - friendlyHQLoc.x - 1, friendlyHQLoc.y);
        if (this.enemyHQLoc != null) {
            foundHQ = true;
        } else {
            this.enemyHQLoc = this.symmetryHq[0];
        }
    }

}