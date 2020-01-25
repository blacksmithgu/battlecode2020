package steamlocomotive;

import battlecode.common.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Landscaper extends Unit {

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
    // Our and enemy HQ locations.
    private MapLocation hq;
    // Pathfinder object for stateful pathfinding.
    private BugPathfinder pathfinder;
    // The number of steps taken with the current pathfinder.
    private int pathfindSteps;
    // whether the wall should be equalized in elevation
    private boolean equalize = false;
    // Wall spots
    private Bitconnect.HQSurroundings wallLocations;
    // Spawn location used to move far away on the wall
    private MapLocation spawnLoc;
    // Target wall location
    private MapLocation targetWallLoc;
    // Wall Bolster Locations
    private ArrayList<MapLocation> bolsterLocations;


    // Updated per-round; the closest detected buriable enemy.
    private RobotInfo closestEnemy;

    public Landscaper(int id) {
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
    public void scanSurroundings(RobotController rc) throws GameActionException {
        RobotInfo[] robots = rc.senseNearbyRobots();
        for (RobotInfo rob : robots) {
            if (rob.type == RobotType.HQ && rob.team == rc.getTeam()) {
                if (this.hq == null) {
                    this.hq = rob.location;
                    wallLocations = computeWall(rc);
                }
            }
        }
    }

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
        for (MapLocation loc : wallLocations.adjacentWallSpots) {
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

        if (!rc.canSenseLocation(this.hq)) {
            this.pathfinder = this.newPathfinder(this.hq, true);
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
                this.pathfinder = this.newPathfinder(this.hq, true);
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

        if (rc.getLocation().distanceSquaredTo(this.hq) <= 2 && rc.senseRobotAtLocation(this.hq).dirtCarrying > 0)
            return rc.getLocation().directionTo(this.hq);
        Direction digFrom = Direction.NORTH;
        for (int i = 0; i < 4; i++) {
            MapLocation possibleLocation = this.hq.add(digFrom).add(digFrom);
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
        for (MapLocation loc : wallLocations.adjacentWallSpots) {
            if (myLoc.distanceSquaredTo(loc) == 0) {
                return true;
            }
        }
        return false;
    }

    public boolean isWallBolsterLocation(RobotController rc, MapLocation myLoc) {
        if (myLoc.distanceSquaredTo(this.hq) >= 4 && myLoc.distanceSquaredTo(this.hq) < 9) {
            return true;
        }
        return false;
    }

    public boolean isIdealWallDigLocation(RobotController rc, MapLocation myLoc) {
        Direction digFrom = Direction.NORTH;
        for (int i = 0; i < 4; i++) {
            MapLocation possibleLocation = this.hq.add(digFrom).add(digFrom);
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
        for (MapLocation loc : wallLocations.adjacentWallSpots) {
            Direction start = loc.directionTo(this.hq).opposite().rotateLeft();
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
        for (MapLocation loc : wallLocations.adjacentWallSpots) {
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
        comms = new Bitconnect(rc, rc.getMapWidth(), rc.getMapHeight());

        this.scanSurroundings(rc);

        this.hq = comms.ourHQSurroundings.hq;
        if (this.hq != null) {
            wallLocations = computeWall(rc);
            bolsterLocations = computeBolster(rc);
            targetWallLoc = this.hq;
        }

        spawnLoc = rc.getLocation();

        //if (comms.isWallDone(rc)) state = LandscaperState.TERRAFORM;
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

    //TODO: keep this in sync with the HQ version of the function that Niels will be updating
    public Bitconnect.HQSurroundings computeWall(RobotController rc) {
        List<MapLocation> wallSpots = new ArrayList<>(8);
        MapLocation us = this.hq;
        int width = rc.getMapWidth();
        int height = rc.getMapHeight();
        // TODO: implement the remaining off-corner cases
        if (us.x == 2 && us.y == 1) {
            wallSpots.add(new MapLocation(0, 2));
            wallSpots.add(new MapLocation(1, 2));
            wallSpots.add(new MapLocation(2, 2));
            wallSpots.add(new MapLocation(3, 2));
            wallSpots.add(new MapLocation(3, 1));
            wallSpots.add(new MapLocation(3, 0));

        } else if (us.x == 2 && us.y == height - 2) {
            wallSpots.add(new MapLocation(0, height - 3));
            wallSpots.add(new MapLocation(1, height - 3));
            wallSpots.add(new MapLocation(2, height - 3));
            wallSpots.add(new MapLocation(3, height - 3));
            wallSpots.add(new MapLocation(3, height - 2));
            wallSpots.add(new MapLocation(3, height - 1));
        } else {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int yOffset = -1; yOffset <= 1; yOffset++) {
                    if (xOffset == 0 && yOffset == 0) continue;

                    MapLocation loc = new MapLocation(us.x + xOffset, us.y + yOffset);
                    if (!rc.onTheMap(loc)) continue;

                    // Check to see if we're in a corner, or against a wall, to see if the wall is not necessary.
                    // Check for an absolute corner of the map.
                    if (Math.abs(xOffset) + Math.abs(yOffset) == 2) {
                        MapLocation xDir = new MapLocation(us.x + 2 * xOffset, us.y);
                        MapLocation yDir = new MapLocation(us.x, us.y + 2 * yOffset);
                        if (!rc.onTheMap(xDir) && !rc.onTheMap(yDir)) continue;
                    }

                    // Check for an edge of the map that's not a corner.
                    if (Math.abs(xOffset) + Math.abs(yOffset) == 1) {
                        MapLocation cornerDir = new MapLocation(us.x + 2 * xOffset, us.y + 2 * yOffset);
                        if (!rc.onTheMap(cornerDir)) continue;
                    }

                    wallSpots.add(loc);
                }
            }
        }

        return new Bitconnect.HQSurroundings(rc.getLocation(), wallSpots.toArray(new MapLocation[0]), rc.getTeam());
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

}