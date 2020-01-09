package steamlocomotive;

import battlecode.common.*;

public class HQ extends Unit {
    @Override
    public void run(RobotController rc, int turn) {
        if(Utils.canBuild(rc, RobotType.MINER)) {
            Utils.buildInAnyDirection(rc, RobotType.MINER);
        }
    }
}