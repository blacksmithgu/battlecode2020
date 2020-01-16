package steamlocomotive;

import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Bitconnect {
    // width of the map
    final int width;
    // height of the map
    final int height;

    public Bitconnect(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * get a bit at an index of an integer
     */
    private boolean getBit(int integer, int index) {
        return (integer >> index) % 2 == 1;
    }

    private int setBit(int integer, int index, boolean value) {
        return value ? integer | (1 << index) : integer & ~(1 << index);
    }

    /**
     * verify that a checksum is valid for a message of 6 ints
     */
    private boolean correctChecksum(int[] message, int checksum) {
        return false;
    }

    /**
     * create a checksum for a given message of 6 ints
     */
    private int makeChecksum(int[] message) {
        return 0;
    }

    /**
     * extract a checksum from a 7 int message
     */
    private int getChecksum(int[] message) {
        return 0;
    }

    public MapLocation[] getLandscaperLocations(RobotController rc) {
        return null;
    }

    /**
     * assumed that map locations are a subset of a 3x3 grid with the center as center
\    */
    public void sendLandscaperLocations(RobotController rc, MapLocation center, MapLocation[] mapLocations) {

    }
}
