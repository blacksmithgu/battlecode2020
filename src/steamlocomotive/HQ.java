package steamlocomotive;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for spawning miners and planning high-level strategic things.
 */
public class HQ extends Unit {

    // Number of miners which have been spawned.
    private int numMiners = 0;

    // Spots for the wall
    private List<MapLocation> wallSpots = new ArrayList<>();

    public HQ(int id) {
        super(id);
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // TODO: Integrate drone shooting into other sensing if needed for efficiency
        //Look for enemy robots. Identify closest drone. Shoot it down.

        MapLocation hqLoc = rc.getLocation();
        RobotInfo closestDrone = rc.senseRobot(rc.getID());
        int closestEnemyDist = 500;
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        int enemyDist;
        for (RobotInfo nearbyEnemy : enemyRobots) {
            if (nearbyEnemy.type == RobotType.DELIVERY_DRONE) {
                enemyDist = hqLoc.distanceSquaredTo(nearbyEnemy.location);
                if (enemyDist < closestEnemyDist) {
                    closestEnemyDist = enemyDist;
                    closestDrone = nearbyEnemy;
                }
            }
        }
        if (rc.canShootUnit(closestDrone.ID) && closestDrone.type == RobotType.DELIVERY_DRONE) {
            rc.shootUnit(closestDrone.ID);
            //System.out.println("I have entered the drone shooting if statement.");
            return;
        }






        // Wait for cost of miner if before refinery cutoff, otherwise wait for cost of refinery + miner.
        //Stop building miners if we've reached the number of miners we want
        if (rc.getRoundNum() < Config.MIN_REFINERY_ROUND) {
            if (rc.getTeamSoup() < RobotType.MINER.cost) return;
        }
        else if (numMiners >= Config.MAX_NUM_MINERS){
            return;
        }
        else {
            if (rc.getTeamSoup() < RobotType.MINER.cost + RobotType.REFINERY.cost) return;
        }

        // Look at all of the soup locations, create a list, and send a miner to a random soup location.
        // TODO: Cache and update every X rounds in the future.
        List<MapLocation> soupLocations = new ArrayList<>();

        // TODO: May consider soups from the whole map.
        Utils.traverseSensable(rc, loc -> {
            int soup = rc.senseSoup(loc);
            if (soup > 0) soupLocations.add(loc);
        });

        if (soupLocations.size() == 0) {
            soupLocations.add(new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight())));
        }

        // Randomly choose a location to send a miner off too to die.
        MapLocation target = soupLocations.get(this.rng.nextInt(soupLocations.size()));
        Direction desired = rc.getLocation().directionTo(target);

        for (int c = 0; c < 8 && !rc.canBuildRobot(RobotType.MINER, desired); c++) {
            desired = desired.rotateRight();
        }

        if (rc.canBuildRobot(RobotType.MINER, desired)) {
            rc.buildRobot(RobotType.MINER, desired);
            this.numMiners += 1;
        }
        //plan out where the wall should go
        if (rc.getRoundNum() == Config.PLAN_WALL){
            for (int xOffset = -1; xOffset <=1; xOffset++){
                for (int yOffset = -1; yOffset <=1; yOffset++){
                    if (xOffset!=0 || yOffset!=0){
                        MapLocation loc = rc.getLocation();
                        loc = new MapLocation(loc.x+xOffset,loc.y+yOffset);
                        if (rc.onTheMap(loc)){
                            wallSpots.add(loc);
                        }
                    }
                }
            }

            //send out communication message with the locations for the walls of the base
        }

    }
}