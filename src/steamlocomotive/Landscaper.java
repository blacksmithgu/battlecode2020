package steamlocomotive;

import battlecode.common.*;

public class Landscaper extends Unit {

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
        // Unbury an ally building (specifically the HQ).
        UNBURY_ALLY,
        // Terraform towards the enemy base.
        TERRAFORM,
    }

    // Current landscaper state.
    private LandscaperState state;
    // Communication object.
    private Bitconnect comms;
    // Our and enemy HQ locations.
    private MapLocation hq, enemyHq;
    // wall locations
    private Bitconnect.HQSurroundings wallLocations;
    // Pathfinder object for stateful pathfinding.
    private BugPathfinder pathfinder;
    // The number of steps taken with the current pathfinder.
    private int pathfindSteps;
    //whether the wall should be equalized in elevation
    private boolean equalize = false;

    public Landscaper(int id) {
        super(id);
        this.state = LandscaperState.MOVE_TO_WALL;
        this.pathfinder = null;
        this.pathfindSteps = 0;
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // Check the blockchain for useful information.
        comms.updateForTurn(rc);
        // Update soup knowledge by scanning surroundings.
        this.scanSurroundings(rc);

        // Swap on current state.
        while (rc.isReady()) {
            LandscaperState next;
            switch (this.state) {
                case BUILD_WALL: next = this.buildWall(rc); break;
                case MOVE_TO_WALL: next = this.moveToWall(rc); break;
                case BURY_ENEMY: next = this.buryEnemy(rc); break;
                case UNBURY_ALLY: next = this.unburyAlly(rc); break;
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

    /** Scan surroundings for enemy and allied buildings. */
    public void scanSurroundings(RobotController rc) throws GameActionException {
        // Update the location of our HQ if needed.
        Utils.ClosestRobot hqLoc = Utils.closestRobot(rc, RobotType.HQ, rc.getTeam());
        if (hqLoc.robot != null) this.hq = hqLoc.robot.getLocation();

        // Update the location of the enemy HQ if needed.
        Utils.ClosestRobot enemyHqLoc = Utils.closestRobot(rc, RobotType.HQ, rc.getTeam().opponent());
        if (enemyHqLoc.robot != null) this.enemyHq = enemyHqLoc.robot.getLocation();
    }

    /** The landscaper is building a wall around HQ. */
    public LandscaperState buildWall(RobotController rc) throws GameActionException {
        // Start equalizing if all landscapers have arrived.
        if (comms.isWallDone(rc)) equalize = true;

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

        if (rc.canDepositDirt(depositLoc)) {
            rc.depositDirt(depositLoc);
        } else {
            if (digFrom != null && rc.canDigDirt(digFrom)) rc.digDirt(digFrom);
        }

        return LandscaperState.BUILD_WALL;
    }

    /** Move towards the wall so that we can build it. */
    public LandscaperState moveToWall(RobotController rc) throws GameActionException {
        // If we're on the wall, get digging.
        if (this.isWallTile(rc.getLocation())) return LandscaperState.BUILD_WALL;

        // If the wall is occupied, go do something more useful.
        if (this.comms.isWallDone(rc)) return LandscaperState.TERRAFORM;

        // Clear the existing pathfinder if it's space is occupied.
        if (this.pathfinder != null && rc.canSenseLocation(this.pathfinder.goal()) && rc.isLocationOccupied(this.pathfinder.goal()))
            this.pathfinder = null;

        // Create a pathfinder to the first open wall tile.
        if (this.pathfinder == null) {
            MapLocation target = null;
            int bestDistance = Integer.MAX_VALUE;
            for (int index = 0; index < wallLocations.adjacentWallSpots.length; index++) {
                MapLocation wall = wallLocations.adjacentWallSpots[index];
                if (rc.canSenseLocation(wall) && rc.isLocationOccupied(wall)) continue;

                int dist = rc.getLocation().distanceSquaredTo(wall);
                if (dist < bestDistance) {
                    bestDistance = dist;
                    target = wall;
                }
            }

            if (target == null) return LandscaperState.TERRAFORM;
            this.pathfinder = this.newPathfinder(target, false);
        }

        // If we are immediately adjacent to the wall tile we want to be on, but cannot reach it due to a height
        // difference, go ahead and elevate ourselves via digging.
        // TODO: Only do this if there is not a closer landscaper.
        if (rc.getLocation().distanceSquaredTo(this.pathfinder.goal()) <= 2) {
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
        return LandscaperState.TERRAFORM;
    }

    /** Unbury an ally building which has less than full health. */
    public LandscaperState unburyAlly(RobotController rc) throws GameActionException {
        RobotInfo[] info = rc.senseNearbyRobots();
        if (info.length == 0) return LandscaperState.TERRAFORM;

        for (RobotInfo rob : info) {
            if (rob.team == rc.getTeam() && rob.type == RobotType.HQ && rob.dirtCarrying > 0) {
                if (rc.getLocation().distanceSquaredTo(rob.getLocation()) == 1) {
                    if (rc.canDepositDirt(rc.getLocation().directionTo(rob.getLocation()))) {
                        rc.depositDirt(rc.getLocation().directionTo(rob.getLocation()));
                    } else {
                        Direction digFrom = smartDigDirection(rc);
                        if (digFrom != null && rc.canDigDirt(digFrom)) {
                            rc.digDirt(digFrom);
                        }
                    }
                } else {
                    pathfinder = this.newPathfinder(rob.location, true);
                    Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
                    if (move != null && move != Direction.CENTER) rc.move(move);
                }
            }
        }

        return LandscaperState.UNBURY_ALLY;
    }

    /** Terraform the map into a checkerboard; will roam randomly doing so for now until we improve pathing. */
    public LandscaperState terraform(RobotController rc) throws GameActionException {
        int ourHeight = rc.senseElevation(rc.getLocation());
        int terraHeight = this.terraformHeight();

        // If we are not on the checkerboard, get on the checkerboard.
        if (!onLattice(rc.getLocation())) {
            for (Direction dir : Direction.cardinalDirections()) {
                MapLocation loc = rc.getLocation().add(dir);
                if (!rc.canSenseLocation(loc)) continue;
                if (!rc.senseFlooding(loc) && rc.canMove(dir)) {
                    rc.move(dir);
                    return LandscaperState.TERRAFORM;
                }
            }

            // We can't make any cardinal moves. Just explode for now out of pure laziness.
            // TODO: Terraform our way up.
            rc.disintegrate();
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
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> onLattice(rc.getLocation().add(dir)) && Landscaper.canMoveL(rc, dir));
        this.pathfindSteps++;
        if (move == null || move == Direction.CENTER) return LandscaperState.TERRAFORM;

        // If the movement is to a tile of a different height, then start terraforming.
        // If the target is above, then dig it out and dump it in the lowest elevation tile.
        int moveHeight = rc.senseElevation(rc.getLocation().add(move));
        boolean flooded = rc.senseFlooding(rc.getLocation().add(move));
        if (flooded || moveHeight < terraHeight) {
            Direction digDir = smartDigDirection(rc);
            if (rc.canDepositDirt(move)) {
                rc.depositDirt(move);
                return LandscaperState.TERRAFORM;
            } else {
                rc.digDirt(digDir);
                return LandscaperState.TERRAFORM;
            }
        } else {
            rc.move(move);
        }

        return LandscaperState.TERRAFORM;
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        comms = new Bitconnect(rc, rc.getMapWidth(), rc.getMapHeight());
        wallLocations = comms.getWallLocations(rc);

        // TODO: Actually figure out where the enemy HQ is.
        this.hq = wallLocations.hq;
        this.enemyHq = new MapLocation(rc.getMapWidth()- hq.x-1,rc.getMapHeight()- hq.y-1);
        if (comms.isWallDone(rc)) state = LandscaperState.TERRAFORM;
    }

    /** Tries to dig dirt in the location opposite the given location; if there is a unit there, will try to dig from a non-lattice tile. */
    private Direction smartDigDirection(RobotController rc) throws GameActionException {
        Direction bestDirection = null;
        boolean hasAlly = true;
        boolean onLattice = true;
        for (Direction dir : Direction.allDirections()) {
            if (dir == Direction.CENTER) continue;

            MapLocation loc = rc.getLocation().add(dir);
            if (this.isWallTile(loc)) continue;

            if (!onLattice && onLattice(loc)) continue;

            RobotInfo robot = rc.senseRobotAtLocation(loc);
            if (!hasAlly && robot != null) continue;

            onLattice = onLattice(loc);
            hasAlly = robot != null;
            bestDirection = dir;
        }

        return bestDirection;
    }

    /** Returns true if this tile is on the checkerboard and should thus be filled in. */
    private boolean onLattice(MapLocation loc) {
        return (loc.x % 2 == 0) || (loc.y % 2 == 0);
    }

    /** Returns true if this tile is along the line from our HQ to the enemy HQ. */
    private boolean isImportantTile(RobotController rc, MapLocation loc, int dist) throws GameActionException {
        if (loc.x % 2 == 0 && loc.y % 2 == 0) return false;
        if (rc.getLocation().distanceSquaredTo(hq)<20) return false;

        int x0 = loc.x, y0 = loc.y, x1 = hq.x, x2 = enemyHq.x, y1 = hq.y, y2 = enemyHq.y;
        double top = Math.abs((y2 - y1) * x0 - (x2 - x1) * y0 + x2 * y1 - y2 * x1);
        double bottom = Math.sqrt(Math.pow(y2 - y1, 2) + Math.pow(x2 - x1, 2));
        return top / bottom < dist;
    }

    /** Returns true if the location is one of the HQ wall locations. */
    private boolean isWallTile(MapLocation loc) {
        if (this.wallLocations == null) return false;

        MapLocation[] spots = this.wallLocations.adjacentWallSpots;
        for (int i = 0; i < spots.length; i++) {
            if (loc.equals(spots[i])) return true;
        }

        return false;
    }

    /** Returns the height the lattice should be terraformed to. */
    private int terraformHeight() {
        // Make this dynamic w/ time using the current water level. All landscapers should share this value.
        return 6;
    }

    /**
     *  A 'landscaper move', which allows the pathfinder to take a step in a direction if we can terraform the tile to be close
     */
    private static boolean canMoveL(RobotController rc, Direction dir) throws GameActionException {
        int height = rc.senseElevation(rc.getLocation());

        MapLocation target = rc.getLocation().add(dir);
        if (!rc.canSenseLocation(target)) return false;
        if (rc.isLocationOccupied(target)) return false;

        int targetHeight = rc.senseElevation(target);
        if (targetHeight >= height) return rc.canMove(dir);
        return true;
    }
}