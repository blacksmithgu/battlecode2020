package steamlocomotive;

import battlecode.common.*;

public class DesignSchool extends Unit {

    public DesignSchool(int id) {
        super(id);
    }

    int numEarlyLandscapersBuilt = 0;

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // The center scans nearby robots at the start of each turn, then passes the result into many of its checks
        // Soup amount is used in many places, so just call rc.getTeamSoup() once here
        // Similarly for myID and currentRound
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        int teamSoup = rc.getTeamSoup();
        int myID = rc.getID();
        int currentRound = rc.getRoundNum();


        // Design school builds landscapers early, but not a lot
        // Similarly to drones, this should be insurance against rush.
        // (As long as first design center gets built near HQ quickly and landscapers know to unbury HQ)
        if (rc.getRoundNum() < 100 && numEarlyLandscapersBuilt <= 2) {
            for (Direction adj : Direction.allDirections()) {
                if (adj == Direction.CENTER) continue;
                if (rc.canBuildRobot(RobotType.LANDSCAPER, adj)) {
                    rc.buildRobot(RobotType.LANDSCAPER, adj);
                    numEarlyLandscapersBuilt++;
                }
            }
        }

        //TODO: Add defensive behavior where school pumps out landscapers if HQ is getting buried




        // This is the typical landscaper production behavior, same as drone production
        // Ramps up production rate based on the amount of soup. Over 2000 soup, it makes a drone every turn.
        // TODO: Make the round cutoffs and rates into easily-twiddled constants in Config
        if (teamSoup >= RobotType.LANDSCAPER.cost) {
            if (teamSoup > 500 && currentRound % 32 == myID % 32) {
                buildLandscaperBasic(rc);
            }
            else if (teamSoup > 1000 && currentRound % 16 == myID % 16) {
                buildLandscaperBasic(rc);
            }
            else if (teamSoup > 1500 && currentRound % 8 == myID % 8) {
                buildLandscaperBasic(rc);
            }
            else if (teamSoup > 2000 && currentRound % 2 == myID % 2) {
                buildLandscaperBasic(rc);
            }
            else if (teamSoup >= 3000){
                buildLandscaperBasic(rc);
            }
        }

    }

    public void buildLandscaperBasic(RobotController rc) throws GameActionException {
        /*
        Basic drone building behavior. Cycles through all the directions, builds landscaper in the first direction it can.
         */
        for (Direction adj : Direction.allDirections()) {
            if (adj == Direction.CENTER) continue;
            if (rc.canBuildRobot(RobotType.LANDSCAPER, adj)) {
                rc.buildRobot(RobotType.LANDSCAPER, adj);
                return;
            }
        }
        return;
    }
}
