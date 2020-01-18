package steamlocomotive;

import battlecode.common.*;

import java.util.Arrays;

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
        // Lost and without a purpose...
        ROAMING
    }

    /**
     * A transition from one state to another. Marks the target and whether an action has been taken.
     */
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
    private MapLocation ourHQLoc, enemyHQLoc;
    //wall locations
    private Bitconnect.HQSurroundings wallLocations;
    // Pathfinder object for stateful pathfinding.
    private BugPathfinder pathfinder;
    // The number of steps taken with the current pathfinder.
    private int pathfindSteps;
    //wall location index target
    private int wallIdxTarget = 0;
    //how many times a landscaper will tolerate moving away from the goal when going towards a wall
    private int patience = 30;
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
        // Update soup knowledge by scanning surroundings.
        this.scanSurroundings(rc);
        comms.updateForTurn(rc);

        // Swap on current state.
        boolean madeAction;
        do {
            Transition trans;
            switch (this.state) {
                case BUILD_WALL:
                    trans = this.buildWall(rc);
                    break;
                case MOVE_TO_WALL:
                    trans = this.moveToWall(rc);
                    break;
                case BURY_ENEMY:
                    trans = this.buryEnemy(rc);
                    break;
                case UNBURY_ALLY:
                    trans = this.unburyAlly(rc);
                    break;
                case TERRAFORM:
                    trans = this.terraform(rc);
                    break;
                default:
                case ROAMING:
                    trans = this.roaming(rc);
                    break;
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

    public Direction digOppositeSmart(RobotController rc, Direction ops, boolean breakLattice) throws GameActionException {
        //tries to dig dirt in the location opposite a given location, will take from any other location following
        // the lattice otherwise, prioritizing spots that don't have allies in them
        Direction lowPriority = null;
        Direction digFrom = ops.opposite();
        if (rc.canDigDirt(digFrom) && ((rc.getLocation().add(digFrom).x % 2 == 0 && rc.getLocation().add(digFrom).y % 2 == 0) || breakLattice) && !Arrays.asList(wallLocations.adjacentWallSpots).contains(rc.getLocation().add(digFrom))) {
            if (rc.isLocationOccupied(rc.adjacentLocation(digFrom))) {
                lowPriority = digFrom;
            } else {
                return digFrom;
            }
        }
        for (Direction direction : Direction.allDirections()) {
            if (rc.canDigDirt(direction) && ((rc.getLocation().add(direction).x % 2 == 0 && rc.getLocation().add(direction).y % 2 == 0) || breakLattice) && !Arrays.asList(wallLocations.adjacentWallSpots).contains(rc.getLocation().add(direction))) {

                if (rc.isLocationOccupied(rc.adjacentLocation(direction))) {
                    lowPriority = direction;
                } else {
                    return direction;
                }
            }
        }

        if (lowPriority == null) {
            System.out.println("nothing to dig");
        } else {
            System.out.println("taking from ally" + rc.adjacentLocation(lowPriority));
        }
        return lowPriority;
    }

    public Transition buildWall(RobotController rc) throws GameActionException {

        System.out.println("Building a wall");
        if (comms.isWallDone(rc)) {
            equalize = true;
        }
        Direction digFrom = digOppositeSmart(rc, rc.getLocation().directionTo(ourHQLoc), true);
        Direction depositLoc = Direction.CENTER;
        int height = rc.senseElevation(rc.adjacentLocation(depositLoc));
        if (equalize) {
            for (Direction dir : Direction.allDirections()) {
                if (Arrays.asList(wallLocations.adjacentWallSpots).contains(rc.adjacentLocation(dir))) {
                    int tempHeight = rc.senseElevation(rc.adjacentLocation(dir));
                    if (height > tempHeight) {
                        depositLoc = dir;
                        height = tempHeight;
                    }
                }
            }
        }
        if (rc.canDepositDirt(depositLoc)) {
            rc.depositDirt(depositLoc);
        } else {
            if (digFrom != null && rc.canDigDirt(digFrom)) {
                rc.digDirt(digFrom);
            }
        }
        return new Transition(LandscaperState.BUILD_WALL, true);
    }

    public Transition moveToWall(RobotController rc) throws GameActionException {

        //System.out.println("wall location size " + wallLocations.adjacentWallSpots.length);
        //System.out.println("moving towards wall");
        //check if in position
        MapLocation pos = rc.getLocation();
        for (int i = 0; i < wallLocations.adjacentWallSpots.length; i++) {
            if (pos.equals(wallLocations.adjacentWallSpots[i])) {
                return new Transition(LandscaperState.BUILD_WALL, false);
            }
        }

        MapLocation temp = wallLocations.adjacentWallSpots[wallIdxTarget];
        System.out.println(temp);
        while (rc.canSenseLocation(temp) && rc.isLocationOccupied(temp)) {
            wallIdxTarget += 1;
            if (wallIdxTarget >= wallLocations.adjacentWallSpots.length) {
                wallIdxTarget -= 1;
                return new Transition(LandscaperState.ROAMING, false);
            } else {
                temp = wallLocations.adjacentWallSpots[wallIdxTarget];
            }
        }
        if (rc.getLocation().distanceSquaredTo(temp) <= 2) {
            if (rc.canMove(rc.getLocation().directionTo(temp))) {
                rc.move(rc.getLocation().directionTo(temp));
                return new Transition(LandscaperState.MOVE_TO_WALL, true);
            } else {
                System.out.println("elevator!");
                if (rc.canDepositDirt(Direction.CENTER)) {
                    rc.depositDirt(Direction.CENTER);
                    return new Transition(LandscaperState.MOVE_TO_WALL, true);
                } else {
                    Direction digFrom = digOppositeSmart(rc, rc.getLocation().directionTo(temp), true);
                    if (digFrom != null && rc.canDigDirt(digFrom)) {
                        rc.digDirt(digFrom);
                        return new Transition(LandscaperState.MOVE_TO_WALL, true);
                    }
                    System.out.println("wasting turn");
                }
            }

        }

        //move to position
        pathfinder = this.newPathfinder(wallLocations.adjacentWallSpots[wallIdxTarget], false);
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
        if (move != null && move != Direction.CENTER) {
            if (rc.getLocation().distanceSquaredTo(temp) < rc.adjacentLocation(move).distanceSquaredTo(temp)) {
                patience -= 1;
            }
            rc.move(move);
            if (patience <= 0) {
                return new Transition(LandscaperState.ROAMING, true);
            }
            return new Transition(LandscaperState.MOVE_TO_WALL, true);
        }

        return new Transition(LandscaperState.ROAMING, true);
    }

    public Transition buryEnemy(RobotController rc) throws GameActionException {
        return new Transition(LandscaperState.BUILD_WALL, true);
    }

    public Transition unburyAlly(RobotController rc) throws GameActionException {
        RobotInfo[] info = rc.senseNearbyRobots();
        if (info.length == 0) {
            return new Transition(LandscaperState.ROAMING, false);
        }
        for (RobotInfo rob : info) {
            if (rob.team == rc.getTeam() && rob.type == RobotType.HQ && rob.dirtCarrying > 0) {
                if (rc.getLocation().distanceSquaredTo(rob.getLocation()) == 1) {
                    if (rc.canDepositDirt(rc.getLocation().directionTo(rob.getLocation()))) {
                        rc.depositDirt(rc.getLocation().directionTo(rob.getLocation()));
                    } else {
                        Direction digFrom = digOppositeSmart(rc, Direction.NORTH, false);
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
        return new Transition(LandscaperState.UNBURY_ALLY, true);
    }

    public Transition terraform(RobotController rc) throws GameActionException {
        return new Transition(LandscaperState.BUILD_WALL, true);
    }

    public Transition roaming(RobotController rc) throws GameActionException {

        MapLocation pos = rc.getLocation();
        for (int i = 0; i < wallLocations.adjacentWallSpots.length; i++) {
            if (pos.equals(wallLocations.adjacentWallSpots[i])) {
                return new Transition(LandscaperState.BUILD_WALL, false);
            }
        }

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

        return new Transition(LandscaperState.ROAMING, true);
    }

    public boolean isImportantTile(RobotController rc, MapLocation loc, int dist) throws GameActionException {
        int x0 = loc.x;
        int y0 = loc.y;
        int x1 = ourHQLoc.x;
        int x2 = enemyHQLoc.x;
        int y1 = ourHQLoc.y;
        int y2 = enemyHQLoc.y;
        double top = Math.abs((y2 - y1) * x0 - (x2 - x1) * y0 + x2 * y1 - y2 * x1);
        double bottom = Math.sqrt(Math.pow(y2 - y1, 2) + Math.pow(x2 - x1, 2));
        if (top / bottom < dist) {
            return true;
        }
        return false;
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        comms = new Bitconnect(rc, rc.getMapWidth(), rc.getMapHeight());
        wallLocations = comms.getWallLocations(rc);
        if (wallLocations == null) {
            System.out.println("Niels my landscapers aren't getting comms");
        }
        ourHQLoc = wallLocations.hq;
        if (comms.isWallDone(rc)) {
            System.out.println("WALL IS DONE");
            state = LandscaperState.ROAMING;
        }
        //enemyHQLoc = wallLocations.hq;
    }
}