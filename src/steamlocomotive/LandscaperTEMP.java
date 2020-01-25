package steamlocomotive;

import battlecode.common.*;

import java.util.ArrayList;

public class LandscaperTEMP extends Unit {

    /**
     * Possible states the landscaper can be in.
     */
    public enum LandscaperState {
        // WE WILL BUILD A WALL AND MAKE THE BLUE TEAM PAY FOR IT.
        BUILD_WALL,
        // Terraform around the base up to some radius
        TERRAFORM,
        // Counter enemy rush
        COUNTER_RUSH,
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
    // Spawn location used to move far away on the wall
    private MapLocation spawnLoc;
    // Target wall location
    private MapLocation targetWallLoc;
    // Wall Bolster Locations
    private ArrayList<MapLocation> bolsterLocations;


    // Updated per-round; the closest detected buriable enemy.
    private RobotInfo closestEnemy;

    public LandscaperTEMP(int id) {
        super(id);
        this.state = LandscaperState.BUILD_WALL;
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
                case COUNTER_RUSH:
                    next = this.counterRush(rc);
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
    public void scanSurroundings(RobotController rc) throws GameActionException { }

    /**
     * The landscaper is defending the HQ against an enemy rush
     */
    public LandscaperState counterRush(RobotController rc) throws GameActionException {
        return LandscaperState.COUNTER_RUSH;
    }

    /**
     * Landscaper should terraform a lattice around the HQ
     */
    public LandscaperState terraform(RobotController rc) throws GameActionException {
        return LandscaperState.TERRAFORM;
    }

    /**
     * Returns where the landscaper should be trying to go to get into position on the wall
     */
    public MapLocation getWallTarget(RobotController rc) throws GameActionException {
        MapLocation furthestSpot = null;
        float dist = 0;
        for (MapLocation loc : comms.walls()) {
            if (rc.canSenseLocation(loc) && (!rc.isLocationOccupied(loc)||rc.getLocation().equals(loc)) && Math.abs(rc.senseElevation(loc) - rc.senseElevation(rc.getLocation())) <= 3) {
                int tempDist = spawnLoc.distanceSquaredTo(loc) + loc.x / 1000;
                if (tempDist > dist) {
                    dist = tempDist;
                    furthestSpot = loc;
                }
            }
        }
        return furthestSpot;
    }

    /**
     * Returns where the landscaper should be trying to go for bolstering
     */
    public MapLocation getBolsterTarget(RobotController rc) throws GameActionException {
        MapLocation furthestSpot = null;
        float dist = 0;
        for (MapLocation loc : bolsterLocations) {
            if (rc.canSenseLocation(loc) && (!rc.isLocationOccupied(loc)||rc.getLocation().equals(loc)) && Math.abs(rc.senseElevation(loc) - rc.senseElevation(rc.getLocation())) <= 100) {
                int tempDist = spawnLoc.distanceSquaredTo(loc) + loc.x / 1000;
                if (tempDist > dist) {
                    dist = tempDist;
                    furthestSpot = loc;
                }
            }
        }
        return furthestSpot;
    }

    /**
     * The landscaper is building a wall around HQ.
     */
    public LandscaperState buildWall(RobotController rc) throws GameActionException {
        //If wall is done move on the outside to one of the corners to help reinforce the wall, move to furthest corner

        //If wall is done and the corners are filled, switch to terraform around HQ

        //Move to a wall spot furthest from the spawn location of this landscaper

        //If cannot move further but sees a spot further, equalize elevation to that spot to eventually move there

        if (!rc.canSenseLocation(comms.hq())) {
            this.pathfinder = this.newPathfinder(comms.hq(), true);
            Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
            if (move != null && move != Direction.CENTER) {
                rc.move(move);
                return LandscaperState.BUILD_WALL;
            }
        }
        if (coreWallDone(rc) && !isWallLocation(rc, rc.getLocation())) {
            if (wallCornersFull(rc) && !isWallBolsterLocation(rc, rc.getLocation())) {
                return LandscaperState.TERRAFORM;
            } else {
                MapLocation bolsterTarget = getBolsterTarget(rc);
                rc.setIndicatorLine(rc.getLocation(),bolsterTarget,0,255,0);
                if (rc.getLocation().distanceSquaredTo(bolsterTarget) == 0) {
                    //dig
                    if (rc.senseElevation(rc.getLocation()) < 20) {
                        //need to raise current elevation
                        if (rc.canDepositDirt(Direction.CENTER)) {
                            rc.depositDirt(Direction.CENTER);
                            return LandscaperState.BUILD_WALL;
                        } else {
                            Direction digFrom = wallDigFrom(rc);
                            if (digFrom != null && rc.canDigDirt(digFrom)) {
                                rc.digDirt(digFrom);
                                return LandscaperState.BUILD_WALL;
                            }
                        }
                    } else {
                        //build on main wall
                        Direction lowestWall = Direction.CENTER;
                        int lowestHeight = rc.senseElevation(rc.getLocation());
                        for (Direction dir : Direction.allDirections()) {
                            if (dir == Direction.CENTER) continue;
                            MapLocation loc = rc.getLocation().add(dir);
                            if (!rc.canSenseLocation(loc)) continue;
                            if (isWallLocation(rc, loc) && rc.senseElevation(loc) < lowestHeight) {
                                lowestHeight = rc.senseElevation(loc);
                                lowestWall = dir;
                            }
                        }

                        if (rc.canDepositDirt(lowestWall)) {
                            rc.depositDirt(lowestWall);
                            return LandscaperState.BUILD_WALL;
                        } else {
                            Direction digFrom = wallDigFrom(rc);
                            if (digFrom != null && rc.canDigDirt(digFrom)) {
                                rc.digDirt(digFrom);
                                return LandscaperState.BUILD_WALL;
                            }
                        }
                    }
                } else {
                    if (rc.getLocation().distanceSquaredTo(bolsterTarget) == 2 && Math.abs(rc.senseElevation(bolsterTarget) - rc.senseElevation(rc.getLocation())) > 3) {
                        if (rc.canDepositDirt(rc.getLocation().directionTo(bolsterTarget))) {
                            rc.depositDirt(rc.getLocation().directionTo(bolsterTarget));
                            return LandscaperState.BUILD_WALL;
                        } else {
                            Direction digFrom = wallDigFrom(rc);
                            if (digFrom != null && rc.canDigDirt(digFrom)) {
                                rc.digDirt(digFrom);
                                return LandscaperState.BUILD_WALL;
                            }
                        }
                    }
                    this.pathfinder = this.newPathfinder(bolsterTarget, false);
                    Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
                    if (move != null && move != Direction.CENTER) {
                        rc.move(move);
                        return LandscaperState.BUILD_WALL;
                    }
                }
            }
        } else {
            //if on wall, move to target
            if (isWallLocation(rc, rc.getLocation())){
                MapLocation wallTarget = getWallTarget(rc);
                if (rc.getLocation().distanceSquaredTo(wallTarget) == 0) {
                    //dig
                    Direction lowestWall = Direction.CENTER;
                    if (coreWallDone(rc) || rc.getRoundNum()>500){
                        int lowestHeight = 10000;
                        for (Direction dir : Direction.allDirections()) {
                            if (dir == Direction.CENTER) continue;
                            MapLocation loc = rc.getLocation().add(dir);
                            if (!rc.canSenseLocation(loc)) continue;
                            if (isWallLocation(rc, loc) && rc.senseElevation(loc) < lowestHeight) {
                                lowestHeight = rc.senseElevation(loc);
                                lowestWall = dir;
                            }
                        }
                    }

                    if (rc.canDepositDirt(lowestWall)) {
                        rc.depositDirt(lowestWall);
                        return LandscaperState.BUILD_WALL;
                    } else {
                        Direction digFrom = wallDigFrom(rc);
                        if (digFrom != null && rc.canDigDirt(digFrom)) {
                            rc.digDirt(digFrom);
                            return LandscaperState.BUILD_WALL;
                        }
                    }
                } else {
                    this.pathfinder = this.newPathfinder(wallTarget, false);
                    Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
                    if (move != null && move != Direction.CENTER) {
                        rc.move(move);
                        return LandscaperState.BUILD_WALL;
                    }

                }

            } else {
                this.pathfinder = this.newPathfinder(comms.hq(), true);
                Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
                if (move != null && move != Direction.CENTER) {
                    rc.move(move);
                    return LandscaperState.BUILD_WALL;
                }
            }


        }
        //stuck on wall and can't get further back on wall so build up current wall location
        if (isWallLocation(rc,rc.getLocation())){
            Direction lowestWall = Direction.CENTER;
            if (coreWallDone(rc) || rc.getRoundNum()>500){
                int lowestHeight = rc.senseElevation(rc.getLocation());
                for (Direction dir : Direction.allDirections()) {
                    if (dir == Direction.CENTER) continue;
                    MapLocation loc = rc.getLocation().add(dir);
                    if (!rc.canSenseLocation(loc)) continue;
                    if (isWallLocation(rc, loc) && rc.senseElevation(loc) < lowestHeight) {
                        lowestHeight = rc.senseElevation(loc);
                        lowestWall = dir;
                    }
                }
            }
            if (rc.canDepositDirt(lowestWall)) {
                rc.depositDirt(lowestWall);
                return LandscaperState.BUILD_WALL;
            } else {
                Direction digFrom = wallDigFrom(rc);
                if (digFrom != null && rc.canDigDirt(digFrom)) {
                    rc.digDirt(digFrom);
                    return LandscaperState.BUILD_WALL;
                }
            }
        }
        return LandscaperState.BUILD_WALL;
    }

    public Direction terraformMove(RobotController rc) throws GameActionException {
        return Direction.NORTH;
    }

    public Direction wallDigFrom(RobotController rc) throws GameActionException {
        // first unbury HQ
        // second dig from a predefined wall digging spot
        // third dig from a spot that's not the wall and that's not occupied
        // fourth dig from a spot that's not the wall

        if (rc.getLocation().distanceSquaredTo(comms.hq()) <= 2 && rc.senseRobotAtLocation(comms.hq()).dirtCarrying > 0)
            return rc.getLocation().directionTo(comms.hq());
        Direction digFrom = Direction.NORTH;
        for (int i = 0; i < 4; i++) {
            MapLocation possibleLocation = comms.hq().add(digFrom).add(digFrom);
            if (rc.onTheMap(possibleLocation) && rc.getLocation().distanceSquaredTo(possibleLocation) <= 2 && rc.canDigDirt(rc.getLocation().directionTo(possibleLocation))) {
                System.out.println("digging from " + possibleLocation);
                return rc.getLocation().directionTo(possibleLocation);
            }
            digFrom = digFrom.rotateRight();
            digFrom = digFrom.rotateRight();
        }
        for (Direction dir : Direction.allDirections()) {
            if (!isWallBolsterLocation(rc, rc.adjacentLocation(dir)) && !isWallLocation(rc, rc.adjacentLocation(dir)) && rc.canDigDirt(dir) && !rc.isLocationOccupied(rc.adjacentLocation(dir))) {
                System.out.println("digging from " + dir);
                return dir;
            }
        }
        //TODO maybe add logic to not unbury enemy buildings? very unlikely this would ever happen though
        for (Direction dir : Direction.allDirections()) {
            if (!isWallLocation(rc, rc.adjacentLocation(dir)) && rc.canDigDirt(dir)) {
                System.out.println("digging from last choice " + dir);
                return dir;
            }
        }

        return null;
    }

    public boolean isWallLocation(RobotController rc, MapLocation myLoc) {
        for (MapLocation loc : comms.walls()) {
            if (myLoc.distanceSquaredTo(loc) == 0) {
                return true;
            }
        }
        return false;
    }

    public boolean isWallBolsterLocation(RobotController rc, MapLocation myLoc) {
        if (myLoc.distanceSquaredTo(comms.hq()) >= 4 && myLoc.distanceSquaredTo(comms.hq()) < 9) {
            return true;
        }
        return false;
    }

    public boolean isIdealWallDigLocation(RobotController rc, MapLocation myLoc) {
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

    public ArrayList<MapLocation> computeBolster(RobotController rc) {
        ArrayList<MapLocation> bolsterLoc = new ArrayList<MapLocation>();
        for (MapLocation loc : comms.walls()) {
            Direction start = loc.directionTo(comms.hq()).opposite().rotateLeft();
            for (int i = 0; i < 3; i++) {
                if (rc.onTheMap(loc.add(start)) && !isIdealWallDigLocation(rc, loc.add(start))) {
                    if (!bolsterLoc.contains(loc.add(start))) {
                        bolsterLoc.add(loc.add(start));
                        rc.setIndicatorDot(loc.add(start), 255, 0, 0);
                    }
                }
                start = start.rotateRight();
            }
        }
        System.out.println(bolsterLoc);
        return bolsterLoc;
    }


    public boolean coreWallDone(RobotController rc) throws GameActionException {
        for (MapLocation loc : comms.walls()) {
            if (rc.canSenseLocation(loc) && !rc.isLocationOccupied(loc)) {
                return false;
            }
        }
        return true;
    }

    public boolean wallCornersFull(RobotController rc) throws GameActionException {
        for (MapLocation loc : bolsterLocations) {
            if (rc.canSenseLocation(loc) && !rc.isLocationOccupied(loc)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        comms = Bitconnect.initialize(rc);

        this.scanSurroundings(rc);

        if (comms.hq() != null) {
            bolsterLocations = computeBolster(rc);
            targetWallLoc = comms.hq();
        }

        spawnLoc = rc.getLocation();
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
        return comms.walls() != null && comms.walls().indexOf(loc) != -1;
    }
}