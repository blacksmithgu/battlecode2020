package steamlocomotive;

import battlecode.common.*;

public class DesignSchool extends Unit {

    public DesignSchool(int id) {
        super(id);
    }

    private int numLandscapersBuilt = 0;
    // Tracks whether is within 48 square distance of HQ
    private boolean isNearHQ = false;
    // The design school's team
    private Team schoolTeam;
    // The location of our HQ
    private MapLocation friendlyHQLoc = null;
    // Comms object
    private Bitconnect comms;
    // locations of where to build the wall, transmitted by HQ (we need this because it contains friendly HQ location)
    private Bitconnect.HQSurroundings wallLocations;

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
        if (rc.getRoundNum() < 150 && numLandscapersBuilt < 2) {
            for (Direction adj : Direction.allDirections()) {
                if (adj == Direction.CENTER) continue;
                if (rc.canBuildRobot(RobotType.LANDSCAPER, adj)) {
                    rc.buildRobot(RobotType.LANDSCAPER, adj);
                    numLandscapersBuilt++;
                    return;
                }
            }
        }

        // TODO:  Ensure that there won't be multiple design schools doing this
        // (Probably by looking out for HQ's "all done" sign)

        // The design school near the HQ builds 8 landscapers quickly, so that the wall gets up as fast as possible
        // Only does this building every other turn so that the first design school can get out its early drones
        // Added +50 so this doesn't kill our early econ and prevent any drone building
        if (isNearHQ && numLandscapersBuilt < 8 && rc.getTeamSoup() >= RobotType.REFINERY.cost + 20) {
            buildLandscaperBasic(rc);
            numLandscapersBuilt++;
            return;
        }

        // If the HQ has dirt on it and friendly landscapers don't outnumber enemy landscapers, build landscaper
        if (isNearHQ && rc.canSenseLocation(friendlyHQLoc)) {
            if (rc.getTeamSoup() >= RobotType.LANDSCAPER.cost && hqNeedsHelp(rc, friendlyHQLoc, nearbyRobots, schoolTeam)) {
                    buildLandscaperBasic(rc);
                    numLandscapersBuilt++;
                    return;
            }
        }


        // This is the typical landscaper production behavior, same as drone production
        // Ramps up production rate based on the amount of soup. Over 2000 soup, it makes a drone every turn.
        // TODO: Make the round cutoffs and rates into easily-twiddled constants in Config
        if (teamSoup >= RobotType.LANDSCAPER.cost && numLandscapersBuilt <= 10) {
            normalProduction(rc, teamSoup, myID, currentRound);
        }
        else if (teamSoup >= RobotType.LANDSCAPER.cost && numLandscapersBuilt > 10) {
            halfProduction(rc, teamSoup, myID, currentRound);
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

    // Used in productionTemplate for checking whether should produce on curretnRound given rate modulus
    public boolean doesModulusWork(int modulus, int myID, int currentRound) {
        if (modulus == 0) {
            return true;
        }
        else if (myID % modulus == currentRound % modulus) {
            return true;
        }
        else {
            return false;
        }
    }

    // Can call to produce at various rates, e.g. standard (rate=1), half, or double
    public void productionTemplate(RobotController rc, int teamSoup, int myID, int currentRound, int rate, boolean faster) throws GameActionException{
        if (teamSoup >= RobotType.LANDSCAPER.cost) {
            if (!faster) {
                if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_ONE) {
                    int modulus = Config.LANDSCAPER_PROD_RATE_ONE * rate;
                    if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
                } else if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_TWO) {
                    int modulus = Config.LANDSCAPER_PROD_RATE_TWO * rate;
                    if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
                } else if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_THREE) {
                    int modulus = Config.LANDSCAPER_PROD_RATE_THREE * rate;
                    if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
                } else if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_FOUR) {
                    int modulus = Config.LANDSCAPER_PROD_RATE_FOUR * rate;
                    if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
                } else if (teamSoup >= Config.LANDSCAPER_PROD_CHANGE_ROUND_FIVE) {
                    buildLandscaperBasic(rc);
                }
            }
            else if (faster) {
                if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_ONE) {
                    int modulus = Config.LANDSCAPER_PROD_RATE_ONE / rate;
                    if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
                } else if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_TWO) {
                    int modulus = Config.LANDSCAPER_PROD_RATE_TWO / rate;
                    if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
                } else if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_THREE) {
                    int modulus = Config.LANDSCAPER_PROD_RATE_THREE / rate;
                    if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
                } else if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_FOUR) {
                    int modulus = Config.LANDSCAPER_PROD_RATE_FOUR / rate;
                    if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
                } else if (teamSoup >= Config.LANDSCAPER_PROD_CHANGE_ROUND_FIVE) {
                    buildLandscaperBasic(rc);
                }
            }
        }
        return;
    }

    // Produces drones at half the standard rate
    public void halfProduction(RobotController rc, int teamSoup, int myID, int currentRound) throws GameActionException {
        productionTemplate(rc, teamSoup, myID, currentRound, 2, false);
        return;
    }

    //Produces drones at double the standard rate
    public void doubleProduction(RobotController rc, int teamSoup, int myID, int currentRound) throws GameActionException {
        productionTemplate(rc, teamSoup, myID, currentRound, 2, true);
        return;
    }

    // Produces drones at normal rate, i.e. feeding rate=1 into productionTemplate
    public void normalProduction(RobotController rc, int teamSoup, int myID, int currentRound) throws GameActionException {
        productionTemplate(rc, teamSoup, myID, currentRound, 1, false);
        return;
    }




    //There are cases where the first DesignSchool is next to a refinery, not HQ, so "build 8 miners for wall" doesn't trigger
    public void onCreation(RobotController rc) throws GameActionException {
        comms = new Bitconnect(rc, rc.getMapWidth(), rc.getMapHeight());
        wallLocations = comms.getWallLocations(rc);
        friendlyHQLoc = wallLocations.hq;
        if (friendlyHQLoc != null) {
            if (rc.getLocation().distanceSquaredTo(friendlyHQLoc) <= 48) {
                isNearHQ = true;
            }
        }
        else if (friendlyHQLoc == null) {
            // If friendlyHQLoc == null, then comms have malfunctioned in some way, so resort to own vision
            Utils.ClosestRobot heq = Utils.closestRobot(rc, RobotType.HQ, rc.getTeam());
            if (heq.robot != null) {
                isNearHQ = true;
                friendlyHQLoc = heq.robot.location;
            }
        }
    }
}
