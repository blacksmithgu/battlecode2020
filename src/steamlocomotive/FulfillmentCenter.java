package steamlocomotive;

import battlecode.common.*;

public class FulfillmentCenter extends Unit {

    public FulfillmentCenter(int id) {
        super(id);
    }

    int numEarlyDronesBuilt = 0;

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        //Fulfillment center builds drones early, but not a lot
        //Also builds lots of drones in late game
        if (rc.getRoundNum() < 100 && numEarlyDronesBuilt <= 2) {
            for (Direction adj : Direction.allDirections()) {
                if (adj == Direction.CENTER) continue;
                if (rc.canBuildRobot(RobotType.DELIVERY_DRONE, adj)) {
                    rc.buildRobot(RobotType.DELIVERY_DRONE, adj);
                    numEarlyDronesBuilt++;
                }
            }
        }
        else if (rc.getRoundNum() > 200) {
            for (Direction adj : Direction.allDirections()) {
                if (adj == Direction.CENTER) continue;
                if (rc.canBuildRobot(RobotType.DELIVERY_DRONE, adj)) {
                    rc.buildRobot(RobotType.DELIVERY_DRONE, adj);
                }
            }
        }
    }
}