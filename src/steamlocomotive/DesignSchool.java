package steamlocomotive;

import battlecode.common.*;

public class DesignSchool extends Unit {

    public DesignSchool(int id) {
        super(id);
    }

    // Number of landscapers this school has built.
    private int numLandscapersBuilt = 0;

    // Tracks whether is within 48 square distance of HQ
    private boolean isNearHQ = false;

    // Comms object
    private Bitconnect comms;

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // Update comms information in case we need it.
        comms.updateForTurn(rc);

        if (!rc.isReady()) return;

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
            buildLandscaperBasic(rc);
        }

        // TODO:  Ensure that there won't be multiple design schools doing this
        // (Probably by looking out for HQ's "all done" sign)

        // The design school near the HQ builds 8 landscapers quickly, so that the wall gets up as fast as possible
        // Only does this building every other turn so that the first design school can get out its early drones
        // Added +50 so this doesn't kill our early econ and prevent any drone building
        if (isNearHQ && numLandscapersBuilt < comms.walls().size() && rc.getTeamSoup() >= RobotType.REFINERY.cost + 20) {
            buildLandscaperBasic(rc);
            return;
        }

        // If the HQ has dirt on it and friendly landscapers don't outnumber enemy landscapers, build landscaper
        // The plus 10 is so that emergency drones are more likely to be built than emergency landscapers
        if (isNearHQ && rc.canSenseLocation(comms.hq()) && rc.getTeamSoup() >= RobotType.LANDSCAPER.cost + 10 && hqNeedsHelp(rc, comms.hq(), nearbyRobots, rc.getTeam())) {
            buildLandscaperBasic(rc);
            return;
        }

        // This is the typical landscaper production behavior, same as drone production
        // Ramps up production rate based on the amount of soup. Over 2000 soup, it makes a drone every turn.
        // TODO: Make the round cutoffs and rates into easily-twiddled constants in Config
        if (teamSoup >= RobotType.VAPORATOR.cost && numLandscapersBuilt <= 10) {
            normalProduction(rc, teamSoup, myID, currentRound);
        } else if (teamSoup >= RobotType.VAPORATOR.cost) {
            halfProduction(rc, teamSoup, myID, currentRound);
        }
    }

    /** Basic drone building behavior. Cycles through all the directions, builds landscaper in the first direction it can. */
    public void buildLandscaperBasic(RobotController rc) throws GameActionException {
        for (Direction adj : Direction.allDirections()) {
            if (adj == Direction.CENTER) continue;
            if (rc.canBuildRobot(RobotType.LANDSCAPER, adj)) {
                rc.buildRobot(RobotType.LANDSCAPER, adj);
                this.numLandscapersBuilt++;
                return;
            }
        }
    }

    //Counts enemy landscapers, enemy miners, friendly not-carrying-something drones, and friendly landscapers
    //Outputs integers counting them in that order
    public int[] headcount(RobotController rc, RobotInfo[] nearby, Team myTeam) throws GameActionException {
        int numLandscapers = 0;
        int numMiners = 0;
        int numFriendlyDrones = 0;
        int numFriendlyLandscapers = 0;
        for (RobotInfo info : nearby) {
            if (info.team != myTeam) {
                if (info.type == RobotType.LANDSCAPER) numLandscapers++;
                else if (info.type == RobotType.MINER) numMiners++;
            } else {
                if (info.type == RobotType.DELIVERY_DRONE && !info.currentlyHoldingUnit) numFriendlyDrones++;
                else if (info.type == RobotType.LANDSCAPER) numFriendlyLandscapers++;
            }
        }

        return new int[] {numLandscapers, numMiners, numFriendlyDrones, numFriendlyLandscapers};
    }

    public boolean hqNeedsHelp(RobotController rc, MapLocation hqLoc, RobotInfo[] nearbyRobots, Team myTeam) throws GameActionException {
        if (hqLoc == null) return false;
        else if (rc.senseRobotAtLocation(hqLoc).dirtCarrying > 0) {
            // If the HQ has dirt on it and #enemy landscapers + 2 > #friendly landscapers, then HQ needs more landscapers to help
            // (There also need to be a non-zero number of enemy landscapers
            int[] headcount = headcount(rc, nearbyRobots, myTeam);
            if (headcount[0] != 0 && headcount[0] + 2 > headcount[3]) return true;
        }

        return false;
    }

    // Used in productionTemplate for checking whether should produce on curretnRound given rate modulus
    public boolean doesModulusWork(int modulus, int myID, int currentRound) {
        if (modulus == 0) return true;
        else if (myID % modulus == currentRound % modulus) return true;
        else return false;
    }

    // Can call to produce at various rates, e.g. standard (rate=1), half, or double
    public void productionTemplate(RobotController rc, int teamSoup, int myID, int currentRound, int rate, boolean faster) throws GameActionException{
        if (teamSoup < RobotType.LANDSCAPER.cost) return;

        if (!faster) {
            if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_FOUR) {
                int modulus = Config.LANDSCAPER_PROD_RATE_FOUR * rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
            } else if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_THREE) {
                int modulus = Config.LANDSCAPER_PROD_RATE_THREE * rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
            } else if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_TWO) {
                int modulus = Config.LANDSCAPER_PROD_RATE_TWO * rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
            } else if (teamSoup >= Config.LANDSCAPER_PROD_CHANGE_ROUND_ONE) {
                int modulus = Config.LANDSCAPER_PROD_RATE_ONE * rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
            }
        } else {
            if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_FOUR) {
                int modulus = Config.LANDSCAPER_PROD_RATE_FOUR / rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
            } else if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_THREE) {
                int modulus = Config.LANDSCAPER_PROD_RATE_THREE / rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
            } else if (teamSoup > Config.LANDSCAPER_PROD_CHANGE_ROUND_TWO) {
                int modulus = Config.LANDSCAPER_PROD_RATE_TWO / rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
            } else if (teamSoup >= Config.LANDSCAPER_PROD_CHANGE_ROUND_ONE) {
                int modulus = Config.LANDSCAPER_PROD_RATE_ONE / rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildLandscaperBasic(rc);
            }
        }
    }

    /** Produces drones at half the standard rate */
    public void halfProduction(RobotController rc, int teamSoup, int myID, int currentRound) throws GameActionException {
        productionTemplate(rc, teamSoup, myID, currentRound, 2, false);
    }

    /** Produces drones at double the standard rate */
    public void doubleProduction(RobotController rc, int teamSoup, int myID, int currentRound) throws GameActionException {
        productionTemplate(rc, teamSoup, myID, currentRound, 2, true);
    }

    /** Produces drones at normal rate, i.e. feeding rate=1 into productionTemplate */
    public void normalProduction(RobotController rc, int teamSoup, int myID, int currentRound) throws GameActionException {
        productionTemplate(rc, teamSoup, myID, currentRound, 1, false);
    }

    //There are cases where the first DesignSchool is next to a refinery, not HQ, so "build 8 miners for wall" doesn't trigger
    public void onCreation(RobotController rc) throws GameActionException {
        comms = Bitconnect.initialize(rc);
        isNearHQ = (comms.hq() != null && rc.getLocation().distanceSquaredTo(comms.hq()) <= 48);
    }
}
