package steamlocomotive;

import battlecode.common.*;

public class Miner extends Unit {
    /** The list of units a miner is allowed to spawn. **/
    public static RobotType[] SPAWNABLE_UNITS = {
            RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN };

    private MapLocation HQ_LOCATION = null;

    public Miner(RobotController rc) {
        for(RobotInfo info: rc.senseNearbyRobots()) {
            if(info.team == rc.getTeam() && info.type == RobotType.HQ) {
                this.HQ_LOCATION = info.location;
                break;
            }
        }
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
      startTurn(rc, turn);
      executeTurn(rc, turn);
      endTurn(rc, turn);
    }

    public void startTurn(RobotController rc, int turn) throws GameActionException {
        for(MapLocation location: Utils.senseableLocations(rc)) {
            if(rc.senseSoup(location) > 0) {
                Pathfinder.addSoupLocation(location);
            }
        }
    }

    public void executeTurn(RobotController rc, int turn) throws GameActionException {
        if(Pathfinder.getSoupLocations().size() > 0) {
            Pathfinder.move(rc, Pathfinder.getSoupLocations());
        } else {
            // Move from HQ location
        }
    }

    public void endTurn(RobotController rc, int turn) {

    }
}