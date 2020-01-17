package steamlocomotive;

import battlecode.common.*;

public class FulfillmentCenter extends Unit {

    public FulfillmentCenter(int id) {
        super(id);
    }

    int numEarlyDronesBuilt = 0;
    boolean isNearHQ = false;
    Team centerTeam;

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();

        //If fulfillment center is in range of enemy netgun or HQ, it doesn't build drone.
        if (checkForNets(rc, nearbyRobots, centerTeam)) {
            return;
        }

        //TODO: When helping HQ, build drone as close to enemy landscaper as possible.
        //The fulfillment center checks if HQ needs help. If so, makes a drone.
        if (isOutnumbered(rc, nearbyRobots, centerTeam) && isNearHQ) {
            buildDroneBasic(rc);
            return;
        }

        //Fulfillment center builds drones early, but not a lot
        //Also builds lots of drones in late game
        if (rc.getRoundNum() < 100 && numEarlyDronesBuilt <= 2) {
            buildDroneBasic(rc);
            return;
        }
        else if (rc.getRoundNum() > 200) {
            buildDroneBasic(rc);
            return;
        }
        return;
    }

    public boolean checkForNets(RobotController rc, RobotInfo[] nearby, Team myTeam) throws GameActionException {
        /* Returns true iff the fulfillment center can see enemy netgun or HQ.
        Takes as input the RobotConteroller, an array of nearby robot info (i.e. from rc.senseNearbyRobots())...
         and the team of the robot using the function */
        for (RobotInfo info : nearby) {
            if (info.getTeam() != myTeam) {
                RobotType enemyRobotType = info.getType();
                if (enemyRobotType == RobotType.HQ || enemyRobotType == RobotType.NET_GUN) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isOutnumbered(RobotController rc, RobotInfo[] nearby, Team myTeam) throws GameActionException {
        /*
         Returns true iff (#nearby enemy landscapers >= #nearby friendly drones that aren't carrying things)
         */
        int numFriendlyDrones = 0;
        int numEnemyLandscapers = 0;
        for (RobotInfo info : nearby) {
            if (info.getTeam() != myTeam && info.getType() == RobotType.LANDSCAPER) {
                numEnemyLandscapers++;
            }
            else if (info.getTeam() == myTeam) {
                //TODO: Add check that friendly drone ISN'T already carrying something
                if (info.getType() == RobotType.DELIVERY_DRONE) {
                    numFriendlyDrones++;
                }
            }
        }
        if (numEnemyLandscapers >= numFriendlyDrones) {
            return true;
        }
        else {
            return false;
        }
    }

    public void buildDroneBasic(RobotController rc) throws GameActionException {
        /*
        Basic drone building behavior. Cycles through all the directions, builds drone in the first direction it can.
         */
        for (Direction adj : Direction.allDirections()) {
            if (adj == Direction.CENTER) continue;
            if (rc.canBuildRobot(RobotType.DELIVERY_DRONE, adj)) {
                rc.buildRobot(RobotType.DELIVERY_DRONE, adj);
                return;
            }
        }
        return;
    }

    public void onCreation(RobotController rc) throws GameActionException {
        /*
        Notes its own team. Notes whether it's near our HQ.
         */
        centerTeam = rc.getTeam();
        for (RobotInfo info : rc.senseNearbyRobots()) {
            if (info.getTeam() == centerTeam && info.getType()==RobotType.HQ) {
                isNearHQ = true;
            }
        }
    }

}