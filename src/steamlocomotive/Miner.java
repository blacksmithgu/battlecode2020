package steamlocomotive;

import battlecode.common.*;

public class Miner extends Unit {
    /** The list of units a miner is allowed to spawn. **/
    public static RobotType[] SPAWNABLE_UNITS = {
            RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN };

    @Override
    public void run(RobotController rc, int turn) {

    }
}