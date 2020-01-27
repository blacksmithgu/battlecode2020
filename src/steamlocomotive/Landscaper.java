package steamlocomotive;

import battlecode.common.*;

public class Landscaper extends Unit {

    /**
     * Possible states the landscaper can be in.
     */
    public enum LandscaperState {
        // WE WILL BUILD A WALL AND MAKE THE BLUE TEAM PAY FOR IT.
        BUILD_WALL,
        // Bolster the core wall being built.
        BOLSTER_WALL,
        // Move towards building the wall. Elect Donald Trump today.
        MOVE_TO_WALL,
        // Move to a bolstering location around the wall.
        MOVE_TO_BOLSTER,
        // Bury a detected enemy building.
        BURY_ENEMY,
        // Terraform towards the enemy base.
        TERRAFORM,
        // Inner wall bolster
        INNER_BOLSTER,
    }

    // Current landscaper state.
    private LandscaperState state;
    // Communication object.
    private Bitconnect comms;
    // Pathfinder object for stateful pathfinding.
    private BugPathfinder pathfinder;
    // The number of steps taken with the current pathfinder.
    private int pathfindSteps;
    // whether the wall should be equalized in elevation
    private boolean equalize = false;

    // Updated per-round; the closest detected buriable enemy.
    private RobotInfo closestEnemy;
    // Wall Bolster Locations
    private DynamicArray<MapLocation> bolsterLocations, innerBolsterLocations;
    // Spawn Location
    private MapLocation spawnLocation;

    public Landscaper(int id) {
        super(id);
        this.state = LandscaperState.TERRAFORM;
        this.pathfinder = null;
        this.pathfindSteps = 0;
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // Check the blockchain for useful information.
        comms.updateForTurn(rc);

        // Update our local knowledge by scanning surroundings.
        this.scanSurroundings(rc);

        // Swap on current state.
        while (rc.isReady()) {
            Utils.print(state.toString());

            LandscaperState next;
            switch (this.state) {
                case BUILD_WALL:
                    next = this.buildWall(rc);
                    break;
                case MOVE_TO_WALL:
                    next = this.moveToWall(rc);
                    break;
                case BURY_ENEMY:
                    next = this.buryEnemy(rc);
                    break;
                case MOVE_TO_BOLSTER:
                    next = this.moveToBolster(rc);
                    break;
                case BOLSTER_WALL:
                    next = this.bolsterWall(rc);
                    break;
                case INNER_BOLSTER:
                    next = this.innerBolster(rc);
                    break;
                default:
                case TERRAFORM:
                    next = this.terraform(rc);
                    break;
            }

            // Reset transient miner state.
            if (this.state != next) {
                this.pathfinder = null;
                this.pathfindSteps = 0;
            } else {
                // If we head back to same state, don't bother running again - there's no new information!
                break;
            }

            this.state = next;
        }

        // Useful for debugging.
        if (this.pathfinder != null) rc.setIndicatorLine(rc.getLocation(), this.pathfinder.goal(), 0, 0, 255);
    }

    /**
     * Scan surroundings for enemy and allied buildings.
     */
    public void scanSurroundings(RobotController rc) throws GameActionException {
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

        // Compute the closest enemy; this is updated per-round, and we don't hold onto old state.
        boolean closeToEnemyHQ = comms.enemyHq() != null && rc.getLocation().distanceSquaredTo(comms.enemyHq()) < 18;

        // Scan for a nearby enemy to bury (either a building or a landscaper building a wall); if there are none, head back to regular terraforming.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        this.closestEnemy = null;
        int closestDistance = Integer.MAX_VALUE;
        for (RobotInfo robot : enemies) {
            // Ignore mobile units unless we are close to HQ.
            boolean isTarget = (!robot.type.canMove() || (closeToEnemyHQ && robot.type == RobotType.LANDSCAPER && rc.senseElevation(robot.location) > 10));
            if (!isTarget) continue;

            int dist = rc.getLocation().distanceSquaredTo(robot.location);
            if (dist < closestDistance || dist == closestDistance && robot.type == RobotType.HQ) {
                this.closestEnemy = robot;
                closestDistance = dist;
            }
        }

        // Change to bolstering if close enough to a bolster location that's the correct elevation
        if (comms.isWallDone()) {
            for (MapLocation loc : bolsterLocations) {
                if (rc.getLocation().distanceSquaredTo(loc) <= 2 && rc.senseElevation(loc) >= Config.terraformHeight(rc.getRoundNum()) && rc.getRoundNum() > 500 && rc.senseElevation(loc) == rc.senseElevation(rc.getLocation()) && !rc.isLocationOccupied(loc)) {
                    this.state = LandscaperState.MOVE_TO_BOLSTER;
                }
            }
        }
        if (comms.isWallDone() && rc.senseElevation(rc.getLocation()) >= Config.terraformHeight(rc.getRoundNum()) && rc.getRoundNum() > 500 && isBolsterTile(rc.getLocation())) {
            this.state = LandscaperState.BOLSTER_WALL;
        }
//        if (rc.getLocation().distanceSquaredTo(comms.hq()) <= 2 && this.state.equals(LandscaperState.TERRAFORM)) {
//            this.state = LandscaperState.BUILD_WALL;
//        }
    }

    /**
     * The landscaper is building a wall around HQ.
     */
    public LandscaperState buildWall(RobotController rc) throws GameActionException {
        // Start equalizing if all landscapers have arrived or if we need this wall up ASAP.
        if (comms.isWallDone() || rc.getRoundNum() > Config.EQUALITY_ROUND) equalize = true;

        // Dig from the HQ if it is being buried, otherwise dig off-lattice.
        Direction digFrom = smartDigDirection(rc);
        if (rc.canSenseLocation(comms.hq()) && rc.senseRobotAtLocation(comms.hq()).dirtCarrying > 0)
            digFrom = rc.getLocation().directionTo(comms.hq());

        Direction depositLoc = Direction.CENTER;
        int height = rc.senseElevation(rc.getLocation());

        // If equalizing, find the lowest adjacent wall tile and build there.
        if (equalize) {
            for (Direction dir : Direction.allDirections()) {
                if (dir == Direction.CENTER) continue;
                MapLocation loc = rc.getLocation().add(dir);
                if (!rc.canSenseLocation(loc)) continue;

                int adjHeight = rc.senseElevation(loc);
                if (this.isWallTile(loc) && adjHeight < height) {
                    depositLoc = dir;
                    height = adjHeight;
                }
            }
        }

        // If adjacent to an enemy building, bury it
        // This should help when defending against rush
        if (this.closestEnemy != null && this.closestEnemy.location.isAdjacentTo(rc.getLocation()) && this.closestEnemy.type != RobotType.LANDSCAPER) {
            if (rc.canDepositDirt(rc.getLocation().directionTo(closestEnemy.location))) {
                depositLoc = rc.getLocation().directionTo(closestEnemy.location);
            }
        }

        // Put dirt on target if we can. If not, dig more.
        if (rc.canDepositDirt(depositLoc)) {
            rc.depositDirt(depositLoc);
        } else {
            if (digFrom != null && rc.canDigDirt(digFrom)) rc.digDirt(digFrom);
        }

        return LandscaperState.BUILD_WALL;
    }

    /**
     * Move to bolster the wall from the inside
     */
    public LandscaperState innerBolster(RobotController rc) throws GameActionException {
        if (isInnerBolsterTile(rc.getLocation()))
            return LandscaperState.BOLSTER_WALL;
        for (Direction dir : Direction.allDirections()) {
            MapLocation target = rc.getLocation().add(dir);
            if (isInnerBolsterTile(target)) {
                if (rc.canMove(dir)) {
                    rc.move(dir);
                    return LandscaperState.BOLSTER_WALL;
                }
            }
        }
        return LandscaperState.TERRAFORM;
    }

    /**
     * The landscaper is moving to bolster the wall, terraforming along the way.
     */
    public LandscaperState moveToBolster(RobotController rc) throws GameActionException {
        for (MapLocation loc : bolsterLocations) {
            if (rc.getLocation().distanceSquaredTo(loc) <= 2 && rc.senseElevation(loc) == rc.senseElevation(rc.getLocation())) {
                Direction direct = rc.getLocation().directionTo(loc);
                if (rc.canMove(direct)) {
                    rc.move(direct);
                    return LandscaperState.BOLSTER_WALL;
                }
            }
        }

        return LandscaperState.MOVE_TO_BOLSTER;
    }

    /**
     * The landscaper is bolstering the wall from an adjacent tile.
     */
    public LandscaperState bolsterWall(RobotController rc) throws GameActionException {
        // Dig from the HQ if it is being buried, otherwise dig off-lattice.
        Direction digFrom = smartDigDirection(rc);

        Direction depositLoc = Direction.CENTER;
        int height = 10000;

        // Wall bolstering always attempts to equalize.
        for (Direction dir : Direction.allDirections()) {
            if (dir == Direction.CENTER) continue;
            MapLocation loc = rc.getLocation().add(dir);
            if (!rc.canSenseLocation(loc)) continue;

            int adjHeight = rc.senseElevation(loc);
            if (this.isWallTile(loc) && adjHeight < height) {
                depositLoc = dir;
                height = adjHeight;
            }
        }

        if (rc.senseElevation(rc.getLocation()) < Config.terraformHeight(rc.getRoundNum()))
            depositLoc = Direction.CENTER;

        // If adjacent to an enemy building, bury it
        // This should help when defending against rush
        if (this.closestEnemy != null && this.closestEnemy.location.isAdjacentTo(rc.getLocation()) && this.closestEnemy.type != RobotType.LANDSCAPER) {
            if (rc.canDepositDirt(rc.getLocation().directionTo(closestEnemy.location))) {
                depositLoc = rc.getLocation().directionTo(closestEnemy.location);
            }
        }

        // Put dirt on target if we can. If not, dig more.
        if (rc.canDepositDirt(depositLoc)) {
            rc.depositDirt(depositLoc);
        } else {
            if (digFrom != null && rc.canDigDirt(digFrom)) rc.digDirt(digFrom);
        }

        return LandscaperState.BOLSTER_WALL;
    }

    /**
     * Returns where the landscaper should be trying to go to get into position on the wall
     */
    public MapLocation getWallTarget(RobotController rc) throws GameActionException {
        MapLocation furthestSpot = comms.hq();
        float dist = 0;
        for (MapLocation loc : comms.walls()) {
            if (rc.canSenseLocation(loc) && (!rc.isLocationOccupied(loc) || rc.getLocation().equals(loc)) && Math.abs(rc.senseElevation(loc) - rc.senseElevation(rc.getLocation())) <= 3) {
                int tempDist = spawnLocation.distanceSquaredTo(loc) + loc.x / 1000;
                if (tempDist > dist) {
                    dist = tempDist;
                    furthestSpot = loc;
                }
            }
        }
        return furthestSpot;
    }

    /**
     * Move towards the wall so that we can build it.
     */
    public LandscaperState moveToWall(RobotController rc) throws GameActionException {
        // If we're on the wall, get digging.
        //if (this.isWallTile(rc.getLocation())) return LandscaperState.BUILD_WALL;

        MapLocation target = getWallTarget(rc);

        if (target.equals(rc.getLocation())) {
            System.out.println("in position!!!!");
            return LandscaperState.BUILD_WALL;
        }

        // If we encounter an enemy (building, probably) on our epic journey to get to the wall location, we attack
        if (this.closestEnemy != null) return LandscaperState.BURY_ENEMY;

        // If we're adjacent to HQ and HQ has dirt on it, dig from it. If can't due to being full of dirt already, place dirt beneath self
        if (comms.hq() != null && rc.getLocation().isAdjacentTo(comms.hq()) && rc.canSenseLocation(comms.hq()) && rc.senseRobotAtLocation(comms.hq()).dirtCarrying > 0) {
            if (rc.canDigDirt(rc.getLocation().directionTo(comms.hq()))) {
                rc.digDirt(rc.getLocation().directionTo(comms.hq()));
                return LandscaperState.MOVE_TO_WALL;
            } else if (rc.canDepositDirt(Direction.CENTER)) {
                rc.depositDirt(Direction.CENTER);
                return LandscaperState.MOVE_TO_WALL;
            }
        }

        // If the wall is occupied, go do something more useful.
        if (this.comms.isWallDone()) return LandscaperState.TERRAFORM;

        // Create a pathfinder to the first open wall tile and if on the wall then the target location
        if (this.isWallTile(rc.getLocation())) {
            if (this.pathfinder == null || !this.pathfinder.goal().equals(target))
                this.pathfinder = this.newPathfinder(target, false);
        } else {
            if (this.pathfinder == null || !this.pathfinder.goal().equals(target))
                this.pathfinder = this.newPathfinder(comms.hq(), true);
        }


        // If we are immediately adjacent to the wall tile we want to be on, but cannot reach it due to a height
        // difference, go ahead and elevate ourselves via digging.
        //TODO: dig if we are farther away than this?
        Direction toGoal = rc.getLocation().directionTo(this.pathfinder.goal());
        RobotInfo robotToGoal = rc.senseRobotAtLocation(rc.getLocation().add(toGoal));
        if (rc.getLocation().distanceSquaredTo(this.pathfinder.goal()) <= 8 &&
                rc.senseElevation(rc.getLocation()) + GameConstants.MAX_DIRT_DIFFERENCE < rc.senseElevation(rc.getLocation().add(rc.getLocation().directionTo(this.pathfinder.goal()))) &&
                (robotToGoal == null || robotToGoal.type != RobotType.LANDSCAPER)) {
            // Move onto the wall if we are high enough.
            Direction direct = rc.getLocation().directionTo(this.pathfinder.goal());
            if (rc.canMove(direct)) {
                rc.move(direct);
                return LandscaperState.BUILD_WALL;
            }

            // Otherwise, dig from another tile and elevate ourselves.
            if (rc.canDepositDirt(Direction.CENTER)) {
                rc.depositDirt(Direction.CENTER);
                return LandscaperState.MOVE_TO_WALL;
            } else {
                Direction digFrom = smartDigDirection(rc);
                if (digFrom != null && rc.canDigDirt(digFrom)) {
                    rc.digDirt(digFrom);
                    return LandscaperState.MOVE_TO_WALL;
                }
            }
        }

        // Move towards the wall.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
        if (move != null && move != Direction.CENTER) rc.move(move);
        this.pathfindSteps++;

        return LandscaperState.MOVE_TO_WALL;
    }

    /**
     * Bury an enemy detected building.
     */
    public LandscaperState buryEnemy(RobotController rc) throws GameActionException {
        if (this.closestEnemy == null) return LandscaperState.TERRAFORM;

        // If we are adjacent to something we can bury, bury it.
        if (rc.getLocation().isAdjacentTo(this.closestEnemy.location)) {
            if (!this.closestEnemy.type.canMove()) {
                // Destroy normal enemy buildings.
                if (rc.getDirtCarrying() > 0) {
                    rc.depositDirt(rc.getLocation().directionTo(this.closestEnemy.location));
                } else {
                    rc.digDirt(smartDigDirection(rc));
                }
            } else {
                // Destroy walls that landscapers are building.
                if (rc.getDirtCarrying() > 0) {
                    rc.depositDirt(Direction.CENTER);
                } else {
                    rc.digDirt(rc.getLocation().directionTo(this.closestEnemy.location));
                }
            }

            return LandscaperState.BURY_ENEMY;
        }

        // We're not adjacent to anything; pathfind towards enemy building using terraforming logic.
        if (this.pathfinder == null || !this.pathfinder.goal().equals(this.closestEnemy.location) || this.pathfinder.finished(rc.getLocation()))
            this.pathfinder = this.newPathfinder(this.closestEnemy.location, true);

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> onLattice(rc.getLocation().add(dir)) && Landscaper.canMoveL(rc, dir));
        this.pathfindSteps++;
        if (move == null || move == Direction.CENTER) return LandscaperState.BURY_ENEMY;

        this.tryTerraformMove(rc, move);
        return LandscaperState.BURY_ENEMY;
    }

    /**
     * Terraform the map into a lattice; deterministically tries to choose points
     * closer to HQ which are below the terraform height.
     */
    public LandscaperState terraform(RobotController rc) throws GameActionException {
        // If we sense a nearby enemy building... say hello.
        if (this.closestEnemy != null) return LandscaperState.BURY_ENEMY;

        if (rc.getRoundNum() > Config.WALL_BUILD_ROUND_NUM && !comms.isWallDone()) {
            return LandscaperState.MOVE_TO_WALL;
        }

        int ourHeight = rc.senseElevation(rc.getLocation());
        int terraHeight = Config.terraformHeight(rc.getRoundNum());

        // If we are not on the lattice, get on the lattice.
        if (!onLattice(rc.getLocation())) {
            double averageAdjacentElevation = 0;

            for (Direction dir : Direction.cardinalDirections()) {
                MapLocation loc = rc.getLocation().add(dir);
                if (!rc.canSenseLocation(loc)) continue;
                if (!rc.senseFlooding(loc) && rc.canMove(dir)) {
                    rc.move(dir);
                    return LandscaperState.TERRAFORM;
                }
                averageAdjacentElevation += rc.senseElevation(loc);
            }

            averageAdjacentElevation /= 4;

            boolean tooHigh = averageAdjacentElevation < rc.senseElevation(rc.getLocation());
            if (rc.getDirtCarrying() > 0 && !tooHigh) {
                rc.depositDirt(Direction.CENTER);
            } else if (rc.getDirtCarrying() == 0 && !tooHigh) {
                for (Direction dir : Direction.cardinalDirections()) {
                    if (rc.canDigDirt(dir.rotateRight())) {
                        rc.digDirt(dir.rotateRight());
                        break;
                    }
                }
            } else if (rc.getDirtCarrying() > 0 && tooHigh) {
                Direction minDirt = Direction.CENTER;
                for (Direction direction : Direction.cardinalDirections()) {
                    if (rc.canDigDirt(direction) && rc.senseElevation(rc.getLocation().add(direction)) <= rc.senseElevation(rc.getLocation().add(minDirt))) {
                        minDirt = direction;
                    }
                }
                rc.depositDirt(minDirt);
            } else {
                rc.digDirt(Direction.CENTER);
            }
            return LandscaperState.TERRAFORM;
        }

        // If the checkerboard is below the terraform height, then build it up to the terraform height.
        if (ourHeight < terraHeight) {
            Direction digDir = smartDigDirection(rc);
            if (rc.canDepositDirt(Direction.CENTER)) {
                rc.depositDirt(Direction.CENTER);
                return LandscaperState.TERRAFORM;
            } else {
                rc.digDirt(digDir);
                return LandscaperState.TERRAFORM;
            }
        }

        // If the pathfinder is inactive or finished, pick a new target location to terraform to.
        if (this.pathfinder == null || this.pathfinder.finished(rc.getLocation()) || this.pathfindSteps > Config.MAX_ROAM_DISTANCE) {
            MapLocation target = this.getTerraformTarget(rc);
            if (target == null) target = this.getRoamingTarget(rc, 6);
            this.pathfinder = this.newPathfinder(target, false);
            this.pathfindSteps = 0;
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> onLattice(rc.getLocation().add(dir)) && Landscaper.canMoveL(rc, dir));
        if (move == null || move == Direction.CENTER) {
            this.pathfindSteps++;
            return LandscaperState.TERRAFORM;
        }

        if (this.tryTerraformMove(rc, move)) this.pathfindSteps++;
        return LandscaperState.TERRAFORM;
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        comms = Bitconnect.initialize(rc);
        comms.scanRecent(rc, 50);
        spawnLocation = rc.getLocation();
        if (comms.isWallDone()) state = LandscaperState.TERRAFORM;
        bolsterLocations = computeBolster(rc);
        innerBolsterLocations = computeInnerBolster(rc);

        for (Direction dir : Direction.allDirections()) {
            MapLocation target = rc.getLocation().add(dir);
            if (isInnerBolsterTile(target)) {
                this.state = LandscaperState.INNER_BOLSTER;
            }
        }
    }

    private DynamicArray<MapLocation> computeBolster(RobotController rc) {
        DynamicArray<MapLocation> bolsterLoc = new DynamicArray<>(12);
        for (MapLocation loc : comms.walls()) {
            Direction start = loc.directionTo(comms.hq()).opposite().rotateLeft();
            for (int i = 0; i < 3; i++) {
                if (rc.onTheMap(loc.add(start)) && !isIdealWallDigLocation(loc.add(start)) && !isWallTile(loc.add(start))) {
                    if (!bolsterLoc.contains(loc.add(start))) {
                        bolsterLoc.add(loc.add(start));
                        rc.setIndicatorDot(loc.add(start), 255, 0, 0);
                    }
                }
                start = start.rotateRight();
            }
        }
        return bolsterLoc;
    }

    public DynamicArray<MapLocation> computeInnerBolster(RobotController rc) {
        DynamicArray<MapLocation> bolsterLoc = new DynamicArray<>(10);
        DynamicArray<MapLocation> interestingLoc = new DynamicArray<>(10);
        DynamicArray<MapLocation> interestingLoc2 = new DynamicArray<>(10);

        //if spot next to HQ is not a wall tile add it to a list
        for (Direction dir : Direction.allDirections()) {
            if (dir.equals(Direction.CENTER)) continue;
            MapLocation testLoc = comms.hq().add(dir);
            if (!isWallTile(testLoc) && rc.onTheMap(testLoc) && !testLoc.equals(comms.hq())) {
                interestingLoc.add(testLoc);
            }
        }
        //expand these spots by one more iteration to get spots connected that are not wall and are two away
        for (MapLocation loc : interestingLoc) {
            for (Direction dir : Direction.allDirections()) {
                if (dir.equals(Direction.CENTER)) continue;
                MapLocation testLoc = loc.add(dir);
                if (!isWallTile(testLoc) && interestingLoc.indexOf(testLoc) == -1 && rc.onTheMap(testLoc) && !testLoc.equals(comms.hq())) {
                    interestingLoc2.add(testLoc);
                }
            }
        }
        // check if these are next to a wall and if so they're bolster spots
        for (MapLocation loc : interestingLoc) {
            boolean nextToWall = false;
            for (Direction dir : Direction.allDirections()) {
                if (dir.equals(Direction.CENTER)) continue;
                MapLocation testLoc = loc.add(dir);
                if (isWallTile(testLoc)) {
                    nextToWall = true;
                    break;
                }
            }
            if (nextToWall && rc.onTheMap(loc)) {
                bolsterLoc.add(loc);
                rc.setIndicatorDot(loc, 0, 0, 255);
            }
        }
        // pretty redundant code but too don't feel like fixing right now to make cleaner because it gets the job done
        for (MapLocation loc : interestingLoc2) {
            boolean nextToWall = false;
            for (Direction dir : Direction.allDirections()) {
                MapLocation testLoc = loc.add(dir);
                if (isWallTile(testLoc)) {
                    nextToWall = true;
                    break;
                }
            }
            if (nextToWall && rc.onTheMap(loc)) {
                bolsterLoc.add(loc);
                rc.setIndicatorDot(loc, 0, 0, 255);
            }
        }
        return bolsterLoc;
    }

    public boolean isIdealWallDigLocation(MapLocation myLoc) {
        Direction digFrom = Direction.NORTH;
        for (int i = 0; i < 4; i++) {
            MapLocation possibleLocation = comms.hq().add(digFrom).add(digFrom);
            if (myLoc.distanceSquaredTo(possibleLocation) == 0) {
                return true;
            }
            digFrom = digFrom.rotateRight();
            digFrom = digFrom.rotateRight();
        }
        return false;
    }

    /**
     * Tries to dig dirt in the location opposite the given location; if there is a unit there, will try to dig from a non-lattice tile.
     */
    private Direction smartDigDirection(RobotController rc) throws GameActionException {
        Direction bestDirection = null;
        boolean isBolster = true, onLattice = true, hasAlly = true;
        for (Direction dir : Direction.allDirections()) {
            if (dir == Direction.CENTER) continue;

            MapLocation loc = rc.getLocation().add(dir);
            if (!rc.canSenseLocation(loc)) continue;
            if (this.isWallTile(loc)) continue;

            RobotInfo robot = rc.senseRobotAtLocation(loc);

            // TODO: No matter how I try, this is ugly without using an actual comparator :(
            boolean better = false;
            if (!onLattice && onLattice(loc)) continue;
            else if (onLattice && !onLattice(loc)) better = true;

            if (!better && !isBolster && (isBolsterTile(loc) || isInnerBolsterTile(loc))) continue;
            else if (isBolster && !isBolsterTile(loc)) better = true;

            boolean locAlly = robot != null && robot.getTeam().isPlayer() && robot.type.canBePickedUp();
            if (!better && !hasAlly && locAlly) continue;
            else if (hasAlly && !locAlly) better = true;

            if (better) {
                bestDirection = dir;
                isBolster = isBolsterTile(loc);
                onLattice = onLattice(loc);
                hasAlly = locAlly;
            }
        }

        return bestDirection;
    }

    /**
     * Returns true if this tile is on the checkerboard and should thus be filled in.
     */
    private boolean onLattice(MapLocation loc) {
        if (isWallTile(loc)) return true;
        if (isBolsterTile(loc)) return true;
        if (isInnerBolsterTile(loc)) return true;
        if (isIdealWallDigLocation(loc)) return false;
        return ((loc.x - comms.hq().x) % 2 == 0) || ((loc.y - comms.hq().y) % 2 == 0);
    }

    /**
     * Returns true if the location is one of the HQ wall locations.
     */
    private boolean isWallTile(MapLocation loc) {
        return comms.walls() != null && comms.walls().indexOf(loc) != -1;
    }

    /**
     * Returns true if the location is a bolster location (i.e., landscapers can stand here to bolster the wall).
     */
    private boolean isBolsterTile(MapLocation loc) {
        return bolsterLocations != null && bolsterLocations.indexOf(loc) != -1;
    }

    private boolean isInnerBolsterTile(MapLocation loc) {
        return innerBolsterLocations != null && innerBolsterLocations.indexOf(loc) != -1;
    }

    /**
     * Moves in a given direction (which respects canMoveL), terraforming if necessary.
     */
    private boolean tryTerraformMove(RobotController rc, Direction move) throws GameActionException {
        // If the movement is to a tile of a different height, then start terraforming.
        // If the target is above, then dig it out and dump it in the lowest elevation tile.
        int moveHeight = rc.senseElevation(rc.getLocation().add(move));
        boolean flooded = rc.senseFlooding(rc.getLocation().add(move));
        if (flooded || moveHeight < Config.terraformHeight(rc.getRoundNum())) {
            Direction digDir = smartDigDirection(rc);
            if (rc.canDepositDirt(move)) {
                rc.depositDirt(move);
                return false;
            } else if (rc.canDigDirt(digDir)) {
                rc.digDirt(digDir);
                return false;
            }
        } else if (rc.canMove(move)) {
            rc.move(move);
            return true;
        }

        return false;
    }

    /**
     * Choose a target near the HQ when roaming.
     */
    private MapLocation getRoamingTarget(RobotController rc, int dist) throws GameActionException {
        MapLocation target = new MapLocation(comms.hq().x + rng.nextInt(dist * 2 + 1) - dist, comms.hq().y + rng.nextInt(dist * 2 + 1) - dist);
        for (int timeout = 0; timeout < 100 && !rc.onTheMap(target); timeout++) {
            target = new MapLocation(comms.hq().x + rng.nextInt(dist * 2 + 1) - dist, comms.hq().y + rng.nextInt(dist * 2 + 1) - dist);
        }

        return target;
    }

    private MapLocation getTerraformTarget(RobotController rc) throws GameActionException {
        MapLocation us = rc.getLocation();
        MapLocation best = null;
        int terraHeight = Config.terraformHeight(rc.getRoundNum());
        int bestDistance = Integer.MAX_VALUE;

        for (int dx = -Config.TERRAFORM_SCAN_DIST; dx <= Config.TERRAFORM_SCAN_DIST; dx++) {
            for (int dy = -Config.TERRAFORM_SCAN_DIST; dy <= Config.TERRAFORM_SCAN_DIST; dy++) {
                if (dx == 0 && dy == 0) continue;

                MapLocation loc = new MapLocation(us.x + dx, us.y + dy);
                if (!rc.onTheMap(loc) || !rc.canSenseLocation(loc) || !onLattice(loc)
                    || rc.isLocationOccupied(loc)) continue;
                if (rc.senseElevation(loc) >= terraHeight) continue;

                int dist = loc.distanceSquaredTo(comms.hq());
                if (dist < bestDistance) {
                    bestDistance = dist;
                    best = loc;
                }
            }
        }

        return best;
    }

    /**
     * A 'landscaper move', which allows the pathfinder to take a step in a direction if we can terraform the tile to be close
     */
    private static boolean canMoveL(RobotController rc, Direction dir) throws GameActionException {
        int height = rc.senseElevation(rc.getLocation());

        MapLocation target = rc.getLocation().add(dir);
        if (!rc.canSenseLocation(target)) return false;
        if (rc.isLocationOccupied(target)) return false;

        int targetHeight = rc.senseElevation(target);
        if (targetHeight >= height) return rc.canMove(dir);
        if (targetHeight <= -100) return false;
        return true;
    }
}