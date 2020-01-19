package steamlocomotive;

import battlecode.common.*;

public class DesignSchool extends Unit {

    public DesignSchool(int id) {
        super(id);
    }

    int numLandscapersBuilt = 0;
    boolean isNearHQ = false;
    Team schoolTeam;
    MapLocation myHQLoc;

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
        if (rc.getRoundNum() < 100 && numLandscapersBuilt < 2) {
            for (Direction adj : Direction.allDirections()) {
                if (adj == Direction.CENTER) continue;
                if (rc.canBuildRobot(RobotType.LANDSCAPER, adj)) {
                    rc.buildRobot(RobotType.LANDSCAPER, adj);
                    numLandscapersBuilt++;
                    return;
                }
            }
        }

        // The design school near the HQ builds 8 landscapers quickly, so that the wall gets up as fast as possible
        // Only does this building every other turn so that the first design school can get out its early drones
        if (isNearHQ && numLandscapersBuilt < 8 && rc.getTeamSoup() >= RobotType.LANDSCAPER.cost + RobotType.REFINERY.cost && currentRound % 2 == myID % 2) {
            buildLandscaperBasic(rc);
            numLandscapersBuilt++;
        }

        // If the HQ has dirt on it and friendly landscapers don't outnumber enemy landscapers, build landscaper
        if (isNearHQ && rc.canSenseLocation(myHQLoc)) {
            if (rc.getTeamSoup() >= RobotType.LANDSCAPER.cost && hqNeedsHelp(rc, myHQLoc, nearbyRobots, schoolTeam)) {
                    buildLandscaperBasic(rc);
                    numLandscapersBuilt++;
                    return;
            }
        }


        // This is the typical landscaper production behavior, same as drone production
        // Ramps up production rate based on the amount of soup. Over 2000 soup, it makes a drone every turn.
        // TODO: Make the round cutoffs and rates into easily-twiddled constants in Config
        if (teamSoup >= RobotType.LANDSCAPER.cost) {
            if (teamSoup > 500 && currentRound % 32 == myID % 32) {
                buildLandscaperBasic(rc);
            } else if (teamSoup > 1000 && currentRound % 16 == myID % 16) {
                buildLandscaperBasic(rc);
            } else if (teamSoup > 1500 && currentRound % 8 == myID % 8) {
                buildLandscaperBasic(rc);
            } else if (teamSoup > 2000 && currentRound % 2 == myID % 2) {
                buildLandscaperBasic(rc);
            } else if (teamSoup >= 3000) {
                buildLandscaperBasic(rc);
            }
        }
        return;
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

    public int[] headcount(RobotController rc, RobotInfo[] nearby, Team myTeam) throws GameActionException {
        //Counts enemy landscapers, enemy miners, friendly not-carrying-something drones, and friendly landscapers
        //Outputs integers counting them in that order

        int numLandscapers = 0;
        int numMiners = 0;
        int numFriendlyDrones = 0;
        int numFriendlyLandscapers = 0;
        for (RobotInfo info : nearby) {
            if (info.team != myTeam) {
                if (info.type == RobotType.LANDSCAPER) {
                    numLandscapers++;
                }
                else if (info.type == RobotType.MINER) {
                    numMiners++;
                }
            }
            else if (info.team == myTeam) {
                if (info.type == RobotType.DELIVERY_DRONE && !info.currentlyHoldingUnit) {
                    numFriendlyDrones++;
                }
                else if (info.type == RobotType.LANDSCAPER) {
                    numFriendlyLandscapers++;
                }
            }
        }
        int output[] = {numLandscapers, numMiners, numFriendlyDrones, numFriendlyLandscapers};
        return output;
    }

    public boolean hqNeedsHelp(RobotController rc, MapLocation hqLoc, RobotInfo[] nearbyRobots, Team myTeam) throws GameActionException {
        if (hqLoc == null) {
            return false;
        }
        else if (rc.senseRobotAtLocation(hqLoc).dirtCarrying > 0) {
            // If the HQ has dirt on it and #enemy landscapers + 2 > #friendly landscapers, then HQ needs more landscapers to help
            // (There also need to be a non-zero number of enemy landscapers
            int[] headcount = headcount(rc, nearbyRobots, myTeam);
            if (headcount[0] != 0 && headcount[0] + 2 > headcount[3]) {
                return true;
            }
        }
        return false;
    }

    public void onCreation(RobotController rc) throws GameActionException {
        Utils.ClosestRobot heq = Utils.closestRobot(rc, RobotType.HQ, rc.getTeam());
        if (heq.robot != null) {
            isNearHQ = true;
            myHQLoc = heq.robot.location;
        }
    }
}
