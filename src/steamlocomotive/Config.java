package steamlocomotive;

import battlecode.common.GameConstants;
import battlecode.common.RobotType;

/**
 * Stores agent configuration parameters which control heuristic agent actions.
 */
public class Config {

    // HQ CONFIG

    /** Number of rounds until we save for refinery. */
    public static int MIN_REFINERY_ROUND = 30;

    // MINER CONFIG

    /** Minimum distance we have to be away from a known refinery to build a new one. */
    public static int REFINERY_MIN_DISTANCE = 36;

    /**
     * The amount of soup in inventory before the miner should return.
     */
    public static int INVENTORY_RETURN_SIZE = (RobotType.MINER.soupLimit / GameConstants.SOUP_MINING_RATE) * GameConstants.SOUP_MINING_RATE;

    /**
     * The number of soup representatives that Miners keep track of.
     */
    public static int TRACKED_SOUP_COUNT = 3;

    /**
     * The square distance a soup can be within the representative to be considered part of it's clusters.
     */
    public static int REPRESENTATIVE_THRESHOLD = RobotType.MINER.sensorRadiusSquared;

    /**
     * The total number of miners that HQ builds, and then stops.
     */
    public static int MAX_NUM_MINERS = 8;

    /**
     * The round the HQ decides how to build the wall around itself
     */
    public static int PLAN_WALL = 50;
}
