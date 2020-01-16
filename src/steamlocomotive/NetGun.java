package steamlocomotive;

import battlecode.common.*;

public class NetGun extends Unit {

    public NetGun(int id) {
        super(id);
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        NetGun.findAndShoot(rc);
    }

    public static boolean findAndShoot(RobotController rc) throws GameActionException {
        Utils.ClosestRobot closestEnemy = Utils.closestRobot(rc,
                robot -> robot.type == RobotType.DELIVERY_DRONE && rc.canShootUnit(robot.getID()), rc.getTeam().opponent());

        if (closestEnemy.robot != null) {
            rc.shootUnit(closestEnemy.robot.getID());
            return true;
        }

        return false;
    }
}