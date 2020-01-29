package steamlocomotive;

import battlecode.common.GameConstants;
import battlecode.common.RobotType;

/**
 * Stores agent configuration parameters which control heuristic agent actions.
 */
public class Config {

    // GLOBAL CONFIG

    /**
     * The amount of soup used for communications (for now).
     */
    public static final int COMMS_COST = 3;

    // HQ CONFIG

    /**
     * Number of rounds until we save for refinery.
     */
    public static final int MIN_REFINERY_ROUND = 30;

    // MINER CONFIG

    /**
     * Minimum distance we have to be away from a known refinery to build a new one.
     */
    public static final int REFINERY_MIN_DISTANCE = 64;

    /**
     * The amount of soup in inventory before the miner should return.
     */
    public static final int INVENTORY_RETURN_SIZE = (RobotType.MINER.soupLimit / GameConstants.SOUP_MINING_RATE) * GameConstants.SOUP_MINING_RATE;

    /**
     * The number of soup representatives that Miners keep track of.
     */
    public static final int NUM_SOUP_CLUSTERS = 3;

    /**
     * The square distance a soup can be within the representative to be considered part of it's clusters.
     */
    public static final int MAX_CLUSTER_DISTANCE = RobotType.MINER.sensorRadiusSquared;

    /**
     * The total number of miners that HQ builds, and then stops.
     */
    public static final int MAX_NUM_MINERS = 8;

    /**
     * The maximum distance a unit will roam before picking a new roam target.
     */
    public static final int MAX_ROAM_DISTANCE = 32;

    /**
     * Probability that a miner builds a fulfillment center (as opposed to a design school).
     */
    public static final double FULFILLMENT_CENTER_PROB = 0.25;

    /**
     * Minimum round before a miner decides to build a building.
     */
    public static final int BUILD_BUILDING_MIN_ROUND = 0;

    /**
     * Minimum amount of soup we need to have on hand before considering building a building.
     */
    public static final int BUILD_BUILDING_MIN_SOUP = RobotType.DESIGN_SCHOOL.cost;

    /**
     * Number of tiles we'll consider moving before potentially building a building.
     */
    public static final int BUILD_BUILDING_ROAM_DISTANCE = 3;

    /**
     * The minimum distance buildings (except for vaporators and netguns) of the same type should be from each other.
     */
    public static final int BUILD_BUILDING_MIN_DIST = 64;

    /**
     * The minimum distance netguns should be from each other
     */
    public static final int BUILD_NET_GUN_MIN_DIST = 50;

    /**
     * The minimum distance vaporators should be from each other
     */
    public static final int BUILD_VAP_MIN_DIST = 7;

    /**
     * The min squared distance we build buildings away from the HQ.
     */
    public static final int BUILD_BUILDING_MIN_HQ_DIST = 9;
    public static final int EQUALITY_ROUND = 240;

    public static final int BUILD_TRANSITION_ROUND = 600;
    public static final int BUILD_ON_CREATION_ROUND_NUMBER = 220;

    // LANDSCAPER CONFIG

    /**
     * Returns the height the lattice should be terraformed to.
     */
    public static int terraformHeight(int round) {
        // Make this dynamic w/ time using the current water level. All landscapers should share this value.
        if (round < 400) return 5;
        else if (round < 800) return 10;
        else if (round < 1400) return 15;
        else if (round < 1800) return 20;
        else return 30;
    }

    /**
     * After this round, if wall has not been built, landscapers go to build wall. Before this round, they don't build wall.
     */
    public static final int WALL_BUILD_ROUND_NUM = 0;

    /** Distance the terraformer scans for good tiles to terraform. */
    public static int TERRAFORM_SCAN_DIST = 2;

    /**
     * Before nth cutoff round, factory basic production for unit has nth rate
     * After last cutoff round, will always produce if possible
     */
    public static final int LANDSCAPER_PROD_CHANGE_ROUND_ONE = 500;
    public static final int LANDSCAPER_PROD_CHANGE_ROUND_TWO = 1000;
    public static final int LANDSCAPER_PROD_CHANGE_ROUND_THREE = 1500;
    public static final int LANDSCAPER_PROD_CHANGE_ROUND_FOUR = 2500;


    public static final int LANDSCAPER_PROD_RATE_ONE = 32;
    public static final int LANDSCAPER_PROD_RATE_TWO = 16;
    public static final int LANDSCAPER_PROD_RATE_THREE = 8;
    public static final int LANDSCAPER_PROD_RATE_FOUR = 2;

    public static final int DRONE_PROD_CHANGE_ROUND_ONE = 500;
    public static final int DRONE_PROD_CHANGE_ROUND_TWO = 1000;
    public static final int DRONE_PROD_CHANGE_ROUND_THREE = 1500;
    public static final int DRONE_PROD_CHANGE_ROUND_FOUR = 2500;


    public static final int DRONE_PROD_RATE_ONE = 32;
    public static final int DRONE_PROD_RATE_TWO = 16;
    public static final int DRONE_PROD_RATE_THREE = 8;
    public static final int DRONE_PROD_RATE_FOUR = 2;

    public static final int MIN_SOUP_NET_GUN = 250;

    public static final boolean DEBUG = true;
}
