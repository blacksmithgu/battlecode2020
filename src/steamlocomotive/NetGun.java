package steamlocomotive;

import battlecode.common.*;

public class NetGun extends Unit {

    public NetGun(int id) {
        super(id);
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        //Look for enemy robots. Identify closest drone. Shoot it down.
        MapLocation netgunLoc = rc.getLocation();
        RobotInfo closestDrone = rc.senseRobot(rc.getID());
        int closestEnemyDist = 500;
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        int enemyDist;
        for (RobotInfo nearbyEnemy : enemyRobots) {
            if (nearbyEnemy.type == RobotType.DELIVERY_DRONE) {
                enemyDist = netgunLoc.distanceSquaredTo(nearbyEnemy.location);
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

        return;
    }
}