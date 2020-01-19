package steamlocomotive;

import battlecode.common.GameConstants;
import battlecode.common.RobotType;

/**
 * Stores agent configuration parameters which control heuristic agent actions.
 */
public class Config {

    // GLOBAL CONFIG

    /**
     * The amount of soup used for communications.
     */
    public static final int SOUP_FOR_COMS = 10;

    // HQ CONFIG

    /**
     * Number of rounds until we save for refinery.
     */
    public static final int MIN_REFINERY_ROUND = 30;

    /**
     * The round the HQ decides how to build the wall around itself
     */
    public static final int HQ_WALL_PLANNING_ROUND = 1;

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
    public static final int BUILD_BUILDING_MIN_ROUND = 60;

    /**
     * Minimum amount of soup we need to have on hand before considering building a building.
     */
    public static final int BUILD_BUILDING_MIN_SOUP = RobotType.DESIGN_SCHOOL.cost;

    /**
     * Probability that a miner decides to build a building.
     */
    public static final double BUILD_BUILDING_PROB = 0.20;

    /**
     * Number of tiles we'll consider moving before potentially building a building.
     */
    public static final int BUILD_BUILDING_ROAM_DISTANCE = 3;

    public static final int BUILD_BUILDING_MIN_DIST = 64;

    /**
     * The min squared distance we build buildings away from the HQ.
     */
    public static final int BUILD_BUILDING_MIN_HQ_DIST = 9;
}
