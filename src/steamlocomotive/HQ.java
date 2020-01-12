package steamlocomotive;

import battlecode.common.*;

public class HQ extends Unit {

    // Number of miners which have been spawned.
    private int numMiners = 0;

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        if (turn == 0) {
            onCreation(rc);
        }

        if (Utils.canBuild(rc, RobotType.MINER)) {
            Direction bestDir = Pathfinder.findMove(Pathfinder.getSoupLocations());
            Utils.buildInDirection(rc, RobotType.MINER, bestDir);
            numMiners += 1;
        }
    }

    public void onCreation(RobotController rc) throws GameActionException {
        for (MapLocation location : Utils.senseableLocations(rc)) {
            if (rc.senseSoup(location) > 0) {
                Pathfinder.addSoupLocation(location);
            }
        }
    }
}