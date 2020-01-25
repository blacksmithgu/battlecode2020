package steamlocomotive;

import battlecode.common.*;

public class FulfillmentCenter extends Unit {

    public FulfillmentCenter(int id) {
        super(id);
    }

    int numDronesBuilt = 0;
    boolean isNearHQ = false;
    Team centerTeam;

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        if (!rc.isReady()) return;

        // The center scans nearby robots at the start of each turn, then passes the result into many of its checks
        // Soup amount is used in many places, so just call rc.getTeamSoup() once here
        // Similarly for myID and currentRound
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        int teamSoup = rc.getTeamSoup();
        int myID = rc.getID();
        int currentRound = rc.getRoundNum();

        //If fulfillment center is in range of enemy netgun or HQ, it doesn't build a drone.
        if (checkForNets(rc, nearbyRobots, centerTeam)) {
            return;
        }

        // The fulfillment center checks if HQ needs help. If so, makes a drone.
        if(isNearHQ) {
            if (isOutnumbered(rc, nearbyRobots, centerTeam) && teamSoup >= RobotType.DELIVERY_DRONE.cost) {
                buildTowardsEnemy(rc, nearbyRobots);
                return;
            }
        }

        // These initial two drones are meant to preempt a rush
        // The isOutnumbered stuff above can react to rushes, but due to the 10 turn lag on creation to activity...
        // I think it's good to be a little proactive
        if (currentRound < 100 && numDronesBuilt <= 2 && teamSoup >= RobotType.REFINERY.cost + 10) {
            buildDroneBasic(rc);
            numDronesBuilt++;
            return;
        }

        // The +30 is in here so it doesn't interfere with the first 8 landscapers being built
        if (numDronesBuilt <= 1 && teamSoup >= RobotType.REFINERY.cost + 30) {
            buildDroneBasic(rc);
            numDronesBuilt++;
            return;
        }

        // This is the typical drone production behavior
        // Ramps up production rate based on the amount of soup. Over 2000 soup, it makes a drone every turn.
        // TODO: Make the round cutoffs and rates into easily-twiddled constants in Config
        if (teamSoup >= RobotType.VAPORATOR.cost && numDronesBuilt <= 5) {
            normalProduction(rc, teamSoup, myID, currentRound);
            numDronesBuilt++;
        }
        else if (teamSoup >= RobotType.VAPORATOR.cost && numDronesBuilt > 5) {
            halfProduction(rc, teamSoup, myID, currentRound);
            numDronesBuilt++;
        }
        return;
    }

    public boolean checkForNets(RobotController rc, RobotInfo[] nearby, Team myTeam) throws GameActionException {
        /* Returns true iff the fulfillment center can see enemy netgun or HQ.
        Takes as input the RobotConteroller, an array of nearby robot info (i.e. from rc.senseNearbyRobots())...
         and the team of the robot using the function */
        for (RobotInfo info : nearby) {
            if (info.team != myTeam) {
                if (info.type == RobotType.HQ || info.type == RobotType.NET_GUN) {
                    return true;
                }
            }
        }
        return false;
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

    public boolean isOutnumbered(RobotController rc, RobotInfo[] nearby, Team myTeam) throws GameActionException {
        /*
         Returns true if (#nearby enemy landscapers&miners +2 >= #nearby friendly drones that aren't carrying things)
          and there are enemies
         */
        int[] robotCounts = headcount(rc, nearby, myTeam);
        if (robotCounts[0] + robotCounts[1] + 2 >= robotCounts[2] && robotCounts[0] + robotCounts[1] != 0) {
            return true;
        }
        else {
            return false;
        }
    }

    public void buildDroneBasic(RobotController rc) throws GameActionException {
        /*
        Basic drone building behavior. Cycles through all the directions, builds drone in the first direction it can.
         */
        for (Direction adj : Direction.allDirections()) {
            if (adj == Direction.CENTER) continue;
            if (rc.canBuildRobot(RobotType.DELIVERY_DRONE, adj)) {
                rc.buildRobot(RobotType.DELIVERY_DRONE, adj);
                return;
            }
        }
        return;
    }

    public void buildTowardsEnemy(RobotController rc, RobotInfo[] nearby) throws GameActionException {
        //Builds a drone in the direction of the closest enemy
        //First, identifies the closest enemy landscaper or miner and the direction towards is
        RobotInfo best = null;
        int bestDistance = Integer.MAX_VALUE;

        MapLocation us = rc.getLocation();
        for (RobotInfo robot : nearby) {
            if (robot.team == centerTeam) continue;

            if (robot.type == RobotType.LANDSCAPER || robot.type == RobotType.MINER) {
                int dist = us.distanceSquaredTo(robot.getLocation());
                if (dist < bestDistance) {
                    bestDistance = dist;
                    best = robot;
                }
            }
        }
        if (best == null) {
            return;
        }
        Direction bestDirection = us.directionTo(best.location);

        //Second, build drone directly towards that enemy or one angle off
        if (rc.canBuildRobot(RobotType.DELIVERY_DRONE, bestDirection)) {
            rc.buildRobot(RobotType.DELIVERY_DRONE, bestDirection);
            return;
        } else if (rc.canBuildRobot(RobotType.DELIVERY_DRONE, bestDirection.rotateLeft())) {
            rc.buildRobot(RobotType.DELIVERY_DRONE, bestDirection.rotateLeft());
            return;
        } else if (rc.canBuildRobot(RobotType.DELIVERY_DRONE, bestDirection.rotateRight())) {
            rc.buildRobot(RobotType.DELIVERY_DRONE, bestDirection.rotateRight());
            return;
        } else {
            //If those directions don't work, build drone wherever possible
            buildDroneBasic(rc);
            return;
        }
    }


    // Used in productionTemplate for checking whether should produce on curretnRound given rate modulus
    public boolean doesModulusWork(int modulus, int myID, int currentRound) {
        if (modulus == 0) return true;
        if (myID % modulus == currentRound % modulus) return true;
        else return false;
    }

    // Can call to produce at various rates, e.g. standard (rate=1), half, or double
    public void productionTemplate(RobotController rc, int teamSoup, int myID, int currentRound, int rate, boolean faster) throws GameActionException{
        if (teamSoup < RobotType.DELIVERY_DRONE.cost) return;

        if (!faster) {
            if (teamSoup > Config.DRONE_PROD_CHANGE_ROUND_FOUR) {
                int modulus = Config.DRONE_PROD_RATE_FOUR * rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildDroneBasic(rc);
            } else if (teamSoup > Config.DRONE_PROD_CHANGE_ROUND_THREE) {
                int modulus = Config.DRONE_PROD_RATE_THREE * rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildDroneBasic(rc);
            } else if (teamSoup > Config.DRONE_PROD_CHANGE_ROUND_TWO) {
                int modulus = Config.DRONE_PROD_RATE_TWO * rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildDroneBasic(rc);
            } else if (teamSoup >= Config.DRONE_PROD_CHANGE_ROUND_ONE) {
                int modulus = Config.DRONE_PROD_RATE_ONE * rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildDroneBasic(rc);
            }
        } else {
            if (teamSoup > Config.DRONE_PROD_CHANGE_ROUND_FOUR) {
                int modulus = Config.DRONE_PROD_RATE_FOUR / rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildDroneBasic(rc);
            } else if (teamSoup > Config.DRONE_PROD_CHANGE_ROUND_THREE) {
                int modulus = Config.DRONE_PROD_RATE_THREE / rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildDroneBasic(rc);
            } else if (teamSoup > Config.DRONE_PROD_CHANGE_ROUND_TWO) {
                int modulus = Config.DRONE_PROD_RATE_TWO / rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildDroneBasic(rc);
            } else if (teamSoup >= Config.DRONE_PROD_CHANGE_ROUND_ONE) {
                int modulus = Config.DRONE_PROD_RATE_ONE / rate;
                if (doesModulusWork(modulus, myID, currentRound)) buildDroneBasic(rc);
            }
        }
    }

    // Produces drones at half the standard rate
    public void halfProduction(RobotController rc, int teamSoup, int myID, int currentRound) throws GameActionException {
        productionTemplate(rc, teamSoup, myID, currentRound, 2, false);
    }

    //Produces drones at double the standard rate
    public void doubleProduction(RobotController rc, int teamSoup, int myID, int currentRound) throws GameActionException {
        productionTemplate(rc, teamSoup, myID, currentRound, 2, true);
    }

    // Produces drones at normal rate, i.e. feeding rate=1 into productionTemplate
    public void normalProduction(RobotController rc, int teamSoup, int myID, int currentRound) throws GameActionException {
        productionTemplate(rc, teamSoup, myID, currentRound, 1, false);
    }

    /** Notes its own team. Notes whether it's near our HQ. */
    public void onCreation(RobotController rc) throws GameActionException {
        centerTeam = rc.getTeam();
        for (RobotInfo info : rc.senseNearbyRobots()) {
            if (info.team == centerTeam && info.type ==RobotType.HQ) {
                isNearHQ = true;
                break;
            }
        }
    }
}