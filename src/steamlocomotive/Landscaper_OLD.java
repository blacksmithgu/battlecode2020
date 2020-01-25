package steamlocomotive;

import battlecode.common.*;

public class Landscaper_OLD extends Unit {

    /**
     * Possible states the landscaper can be in.
     */
    public enum LandscaperState {
        // WE WILL BUILD A WALL AND MAKE THE BLUE TEAM PAY FOR IT.
        BUILD_WALL,
        // Move towards building the wall. Elect Donald Trump today.
        MOVE_TO_WALL,
        // Bury a detected enemy building.
        BURY_ENEMY,
        // Terraform towards the enemy base.
        TERRAFORM,
    }

    // Current landscaper state.
    private LandscaperState state;
    // Communication object.
    private Bitconnect comms;
    // Our and enemy HQ locations.
    private MapLocation hq, enemyHq;
    // Pathfinder object for stateful pathfinding.
    private BugPathfinder pathfinder;
    // The number of steps taken with the current pathfinder.
    private int pathfindSteps;
    // whether the wall should be equalized in elevation
    private boolean equalize = false;
    // Boolean to determine if we've found the enemy HQ because the HQ location is a temp variable in the mean time
    private boolean foundHQ;
    // Possible locations of the enemy HQ from symmetry
    private MapLocation[] symmetryHq;
    // Index of which symmetric HQ location we are currently using
    private int enemyHqSymmetryIdx;

    // Updated per-round; the closest detected buriable enemy.
    private RobotInfo closestEnemy;

    public Landscaper_OLD(int id) {
        super(id);
        this.state = LandscaperState.MOVE_TO_WALL;
        this.pathfinder = null;
        this.pathfindSteps = 0;
        this.enemyHqSymmetryIdx = 0;
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // Check the blockchain for useful information.
        comms.updateForTurn(rc);

        // Update our local knowledge by scanning surroundings.
        this.scanSurroundings(rc);

        // TODO: Cleanup HQ detection logic.
        if (foundHQ == false) {
            if (comms.getEnemyBaseLocation() != null) {
                this.enemyHq = comms.getEnemyBaseLocation();
                foundHQ = true;
            }
        }

        // Swap on current state.
        while (rc.isReady()) {
            Utils.print(state.toString());

            LandscaperState next;
            switch (this.state) {
                case BUILD_WALL: next = this.buildWall(rc); break;
                case MOVE_TO_WALL: next = this.moveToWall(rc); break;
                case BURY_ENEMY: next = this.buryEnemy(rc); break;
                default:
                case TERRAFORM: next = this.terraform(rc); break;
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
        // Update the location of our HQ if needed.
        Utils.ClosestRobot hqLoc = Utils.closestRobot(rc, RobotType.HQ, rc.getTeam());
        if (hqLoc.robot != null) this.hq = hqLoc.robot.getLocation();

        // Update the location of the enemy HQ if needed.
        Utils.ClosestRobot enemyHqLoc = Utils.closestRobot(rc, RobotType.HQ, rc.getTeam().opponent());
        if (enemyHqLoc.robot != null && foundHQ == false) {
            this.enemyHq = enemyHqLoc.robot.getLocation();
            comms.setEnemyBaseLocation(this.enemyHq);
            foundHQ = true;
        } else if (enemyHqLoc.robot == null && foundHQ == false && rc.canSenseLocation(this.enemyHq)) {
            enemyHqSymmetryIdx += 1;
            enemyHq = symmetryHq[enemyHqSymmetryIdx];
            if (enemyHqSymmetryIdx == 2) {
                comms.setEnemyBaseLocation(this.enemyHq);
                foundHQ = true;
            }
        }

        // Compute the closest enemy; this is updated per-round, and we don't hold onto old state.
        boolean closeToEnemyHQ = this.enemyHq != null && rc.getLocation().distanceSquaredTo(enemyHq) < 18;

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
    }

    /**
     * The landscaper is building a wall around HQ.
     */
    public LandscaperState buildWall(RobotController rc) throws GameActionException {
        // Start equalizing if all landscapers have arrived.
        if (comms.isWallDone(rc) || rc.getRoundNum() > Config.EQUALITY_ROUND) equalize = true;

        // Dig from the HQ if it is being buried, otherwise dig off-lattice.
        Direction digFrom = smartDigDirection(rc);
        if (rc.canSenseLocation(this.hq) && rc.senseRobotAtLocation(this.hq).dirtCarrying > 0)
            digFrom = rc.getLocation().directionTo(this.hq);



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
     * Move towards the wall so that we can build it.
     */
    public LandscaperState moveToWall(RobotController rc) throws GameActionException {
        // If we're on the wall, get digging.
        if (this.isWallTile(rc.getLocation())) return LandscaperState.BUILD_WALL;

        // If we encounter an enemy (building, probably) on our epic journey to get to the wall location, we attack
        if (this.closestEnemy != null) return LandscaperState.BURY_ENEMY;

        // If we're adjacent to HQ and HQ has dirt on it, dig from it. If can't due to being full of dirt already, place dirt beneath self
        if (hq != null && rc.getLocation().isAdjacentTo(hq) && rc.canSenseLocation(hq) && rc.senseRobotAtLocation(hq).dirtCarrying >0) {
            if (rc.canDigDirt(rc.getLocation().directionTo(hq))) {
                rc.digDirt(rc.getLocation().directionTo(hq));
                return LandscaperState.MOVE_TO_WALL;
            }
            else if (rc.canDepositDirt(Direction.CENTER)) {
                rc.depositDirt(Direction.CENTER);
                return LandscaperState.MOVE_TO_WALL;
            }
        }


        // If the wall is occupied, go do something more useful.
        if (this.comms.isWallDone(rc)) return LandscaperState.TERRAFORM;

        // Create a pathfinder to the first open wall tile.
        if (this.pathfinder == null) this.pathfinder = this.newPathfinder(hq, false);

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
        // TODO: Terraform towards the wall to speed things up.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
        if (move != null && move != Direction.CENTER) rc.move(move);
        this.pathfindSteps++;

        return LandscaperState.MOVE_TO_WALL;
    }

    /** Bury an enemy detected building. */
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
        if (this.pathfinder == null || !this.pathfinder.goal().equals(this.closestEnemy.location) || this.pathfinder.finished(rc.getLocation())) {
            this.pathfinder = this.newPathfinder(this.closestEnemy.location, true);
            System.out.println("new pathy");
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> onLattice(rc.getLocation().add(dir)) && Landscaper_OLD.canMoveL(rc, dir));
        this.pathfindSteps++;
        if (move == null || move == Direction.CENTER) return LandscaperState.BURY_ENEMY;

        this.tryTerraformMove(rc, move);
        return LandscaperState.BURY_ENEMY;
    }

    /**
     * Terraform the map into a checkerboard; will roam randomly doing so for now until we improve pathing.
     */
    public LandscaperState terraform(RobotController rc) throws GameActionException {
        // If we sense a nearby enemy building... say hello.
        if (this.closestEnemy != null) return LandscaperState.BURY_ENEMY;

        int ourHeight = rc.senseElevation(rc.getLocation());
        int terraHeight = Config.terraformHeight(rc.getRoundNum());

        // If we are not on the checkerboard, get on the checkerboard.
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

        // If the pathfinder is inactive or finished, pick a new random location to pathfind to.
        if (this.pathfinder == null || this.pathfinder.finished(rc.getLocation()) || this.pathfindSteps > Config.MAX_ROAM_DISTANCE) {
            // TODO: More intelligent target selection. We choose randomly for now.
            MapLocation target;
            if (this.enemyHq != null) target = this.enemyHq;
            else target = new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight()));

            this.pathfinder = this.newPathfinder(target, true);
            this.pathfindSteps = 0;
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> onLattice(rc.getLocation().add(dir)) && Landscaper_OLD.canMoveL(rc, dir));
        this.pathfindSteps++;
        if (move == null || move == Direction.CENTER) return LandscaperState.TERRAFORM;

        this.tryTerraformMove(rc, move);
        return LandscaperState.TERRAFORM;
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        comms = new Bitconnect(rc, rc.getMapWidth(), rc.getMapHeight());

        // TODO: Actually figure out where the enemy HQ is.
        this.hq = comms.ourHQSurroundings.hq;
        this.enemyHq = comms.getEnemyBaseLocation();
        this.symmetryHq = new MapLocation[3];
        this.symmetryHq[0] = new MapLocation(rc.getMapWidth() - hq.x - 1, rc.getMapHeight() - hq.y - 1);
        this.symmetryHq[1] = new MapLocation(hq.x, rc.getMapHeight() - hq.y - 1);
        this.symmetryHq[2] = new MapLocation(rc.getMapWidth() - hq.x - 1, hq.y);

        if (this.enemyHq != null) foundHQ = true;
        else this.enemyHq = this.symmetryHq[0];

        if (comms.isWallDone(rc)) state = LandscaperState.TERRAFORM;
    }

    /**
     * Tries to dig dirt in the location opposite the given location; if there is a unit there, will try to dig from a non-lattice tile.
     */
    private Direction smartDigDirection(RobotController rc) throws GameActionException {
        Direction bestDirection = null;
        boolean onLattice = true, hasAlly = true;
        for (Direction dir : Direction.allDirections()) {
            if (dir == Direction.CENTER) continue;

            MapLocation loc = rc.getLocation().add(dir);
            if (!rc.canSenseLocation(loc)) continue;
            if (this.isWallTile(loc)) continue;

            RobotInfo robot = rc.senseRobotAtLocation(loc);
            if (robot != null && !robot.type.canBePickedUp()) continue;

            boolean better = false;
            if (!onLattice && onLattice(loc)) continue;
            else if (onLattice && !onLattice(loc)) better = true;

            boolean locAlly = robot != null && robot.getTeam().isPlayer();
            if (!hasAlly && locAlly) continue;
            else if (hasAlly && !locAlly) better = true;

            if (better) {
                bestDirection = dir;
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
        return (loc.x % 2 == 0) || (loc.y % 2 == 0);
    }

    /**
     * Returns true if the location is one of the HQ wall locations.
     */
    private boolean isWallTile(MapLocation loc) {
        // TODO: Remove and replace with using surroundings directly.
        return comms.ourHQSurroundings != null && comms.ourHQSurroundings.isWall(loc);
    }

    /** Moves in a given direction (which respects canMoveL), terraforming if necessary. */
    private boolean tryTerraformMove(RobotController rc, Direction move) throws GameActionException {
        // If the movement is to a tile of a different height, then start terraforming.
        // If the target is above, then dig it out and dump it in the lowest elevation tile.
        int moveHeight = rc.senseElevation(rc.getLocation().add(move));
        boolean flooded = rc.senseFlooding(rc.getLocation().add(move));
        if (flooded || moveHeight < Config.terraformHeight(rc.getRoundNum())) {
            Direction digDir = smartDigDirection(rc);
            if (rc.canDepositDirt(move)) {
                rc.depositDirt(move);
                return true;
            } else if (rc.canDigDirt(digDir)) {
                rc.digDirt(digDir);
                return true;
            }
        } else if (rc.canMove(move)) {
            rc.move(move);
            return true;
        }

        return false;
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