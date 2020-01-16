package steamlocomotive;

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
        return (integer>>index) %2 == 1;
    }

    /**
     * verify that a checksum is valid for a message of 6 ints
     */
    private boolean correctChecksum(int[] message, int checksum) {
        return false;
    }

    /**
     * create a checksum for a given message
     */
    private int makeChecksum(int[] message) {
        return 0;
    }
}
