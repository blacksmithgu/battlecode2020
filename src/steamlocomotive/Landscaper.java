package steamlocomotive;

import battlecode.common.*;

import java.awt.*;
import java.util.Map;

public class Landscaper extends Unit {

    //communication object
    private Bitconnect comms;

    //in position to build wall
    private boolean inPosition = false;

    //our HQ location
    private MapLocation ourHQLoc;

    //enemy HQ location
    private MapLocation enemyHQLoc;

    //wall locations
    private Bitconnect.HQSurroundings wallLocations;

    //wall location index target
    private int wallIdxTarget = 0;

    //bug pathfinder
    private BugPathfinder pathfinder;

    private boolean isWallBuilder = false;

    private boolean needsNewLocation = true;




    public Landscaper(int id) {
        super(id);
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        if (isWallBuilder) {
            buildWall(rc, turn);
        } else {
            terraform(rc, turn);
        }


    }

    public void buildWall(RobotController rc, int turn) throws GameActionException {
        //if wall builder, move towards one of the desired locations
        if (inPosition == false && wallLocations != null) {
            //check if in position
            MapLocation pos = rc.getLocation();
            for (int i = 1; i < wallLocations.adjacentWallSpots.length; i++) {
                if (pos.equals(wallLocations.adjacentWallSpots[i])) {
                    inPosition = true;
                }
            }

            MapLocation temp = wallLocations.adjacentWallSpots[wallIdxTarget];
            if (rc.canSenseLocation(temp) && rc.isLocationOccupied(temp)) {
                wallIdxTarget += 1;
                if (wallIdxTarget >= wallLocations.adjacentWallSpots.length) {
                    wallIdxTarget -= 1;
                }
            }

            //move to position
            pathfinder = this.newPathfinder(wallLocations.adjacentWallSpots[wallIdxTarget], false);
            Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
            if (move != null && move != Direction.CENTER) rc.move(move);
        }


        if (inPosition == true) {
            System.out.println("reaching this method ************");
            Direction digFrom = rc.getLocation().directionTo(ourHQLoc).opposite();
            if (!rc.canDigDirt(digFrom)) {
                for (Direction direction : Direction.allDirections()) {
                    if (!direction.equals(Direction.CENTER) && rc.canDigDirt(direction)) {
                        digFrom = direction;
                        break;
                    }
                }
            }
            if (turn % 2 == 0) {
                rc.digDirt(digFrom);
            } else {
                if (rc.canDepositDirt(Direction.CENTER)) {
                    rc.depositDirt(Direction.CENTER);
                }
            }
        }
    }

    public void terraform(RobotController rc, int turn) throws GameActionException {
        if (needsNewLocation){
            MapLocation target = new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight()));

            this.pathfinder = this.newPathfinder(target, true);
        }

        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
        if (move != null && move != Direction.CENTER) rc.move(move);
    }

//    public Miner.Transition roaming(RobotController rc) throws GameActionException {
//        // TODO: Roaming targets may be unreachable, so choose a new target after X steps.
//
//        // If there is nonzero soup we are aware of, transition to traveling to it.
//        if (soups.hasCluster()) return new Miner.Transition(Miner.MinerState.TRAVEL, false);
//
//        // If the pathfinder is inactive or finished, pick a new random location to pathfind to.
//        if (this.pathfinder == null || this.pathfinder.finished(rc.getLocation()) || this.pathfindSteps > Config.MAX_ROAM_DISTANCE) {
//            // TODO: More intelligent target selection. We choose randomly for now.
//            MapLocation target = new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight()));
//
//            this.pathfinder = this.newPathfinder(target, true);
//        }
//
//        // Obtain a movement from the pathfinder and follow it.
//        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> BugPathfinder.canMoveF(rc, dir));
//        if (move != null && move != Direction.CENTER) rc.move(move);
//        this.pathfindSteps++;
//
//        return new Miner.Transition(Miner.MinerState.ROAMING, true);
//    }

    public boolean isImportantTile(RobotController rc, MapLocation loc, int dist) throws GameActionException {
        int x0 = loc.x;
        int y0 = loc.y;
        int x1 = ourHQLoc.x;
        int x2 = enemyHQLoc.x;
        int y1 = ourHQLoc.y;
        int y2 = enemyHQLoc.y;
        double top = Math.abs((y2-y1)*x0-(x2-x1)*y0+x2*y1-y2*x1);
        double bottom = Math.sqrt(Math.pow(y2-y1,2)+Math.pow(x2-x1,2));
        if (top/bottom<dist){
            return true;
        }
        return false;
    }

    public void onCreation(RobotController rc) {
        comms = new Bitconnect(rc.getMapWidth(), rc.getMapHeight());
        wallLocations = comms.getWallLocations(rc);
        enemyHQLoc = wallLocations.hq;
    }
}