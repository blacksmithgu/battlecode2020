package steamlocomotive;

import battlecode.common.*;

public class Bitconnect {

    final static int HQ_SETTUP_ID = 42;
    // width of the map
    final int width;
    // height of the map
    final int height;
    // our HQ setup
    HQSurroundings ourHQSurroundings;

    public static class HQSurroundings {
        MapLocation hq;
        MapLocation[] adjacentWallSpots;

        /**
         * adjacent wall spots should be next to the HQ.
         */
        public HQSurroundings(MapLocation hq, MapLocation[] adjacentWallSpots) {
            this.hq = hq;
            this.adjacentWallSpots = adjacentWallSpots;
        }

        private boolean listContainsLocation(MapLocation[] locs, MapLocation location) {
            for(MapLocation test: locs) {
                if(locs.equals(location)) {
                    return true;
                }
            }
            return false;
        }

        public Block toMessage() {
            int[] message = new int[6];
            message[0] = HQ_SETTUP_ID;
            message[1] = hq.x;
            message[2] = hq.y;
            message[3] = 0;

            int index = 0;

            for(Direction direction: Direction.allDirections()){
                if(direction.equals(Direction.CENTER)) {
                    continue;
                }

                if(listContainsLocation(this.adjacentWallSpots, hq.add(direction))){
                    message[3] = setBit(message[3], index, true);
                } else {
                    message[3] = setBit(message[3], index, false);
                }
                index++;
            }

            return Block.createBlock(message);
        }

        public static HQSurroundings fromMessage(Block block) {
            int[] message = block.getMessage();
            if(message[0]!=HQ_SETTUP_ID) {
                return null;
            }
            MapLocation hq = new MapLocation(message[1], message[2]);
            int count = 0;

            int adjContent = message[3];
            for(int index = 0; index < 8; index++) {
                if(getBit(adjContent, index)) {
                    count++;
                }
            }

            MapLocation[] locations = new MapLocation[count];
            int locationsIndex = 0;
            int adjIndex = 0;
            for(Direction direction: Direction.allDirections()) {
                if(direction == Direction.CENTER) {
                    continue;
                }
                if(getBit(adjContent, adjIndex)) {
                    locations[locationsIndex] = hq.add(direction);
                    locationsIndex++;
                }
                adjIndex++;
            }
            return new HQSurroundings(hq, locations);
        }

        public boolean equals(HQSurroundings other) {
            if(other == null) {
                return false;
            }
            if(!this.hq.equals(other.hq)) {
                return false;
            }
            for(MapLocation location: this.adjacentWallSpots) {
                if(!this.listContainsLocation(other.adjacentWallSpots, location)) {
                    return false;
                }
            }
            for(MapLocation location: other.adjacentWallSpots) {
                if(!this.listContainsLocation(this.adjacentWallSpots, location)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class Block {
        int[] content;

        /**
         * Create a block from 6 ints
         */
        private Block(int[] content) {
            this.content = content;
        }

        /**
         * Create a block from a 7 int array or return null if the message is not ours
         */
        public static Block extractBlock(int[] block) {
            if (block.length != 7) {
                return null;
            }

            int checksum = getChecksum(block);
            int[] content = getContent(block);

            if (!correctChecksum(content, checksum)) {
                return null;
            }
            return new Block(content);
        }

        private static int[] getContent(int[] block) {
            int[] content = new int[6];

            for (int index = 0; index < content.length; index++) {
                content[index] = block[index];
            }
            return content;
        }

        private static int getChecksum(int[] content) {
            return 0;
        }

        /**
         * Verify that a checksum is valid for a message of 6 ints
         */
        private static boolean correctChecksum(int[] message, int checksum) {
            return false;
        }

        /**
         * Creates a block from a message of 6 ints or returns null if input is wrong size
         */
        public static Block createBlock(int[] message) {
            if(message.length != 6) {
                return null;
            }

            return new Block(message);
        }

        public int[] getMessage() {
            int[] message = new int[7];
            for (int index = 0; index < 6; index++) {
                message[index] = content[index];
            }
            message[6] = getChecksum(content);
            return message;
        }
    }

    private Block sendMessage(RobotController rc, Block block) throws GameActionException {
        if (rc.getTeamSoup() > Config.SOUP_FOR_COMS) {
            rc.submitTransaction(block.getMessage(), Config.SOUP_FOR_COMS);
            return block;
        } else if (rc.getTeamSoup() > 0) {
            rc.submitTransaction(block.getMessage(), rc.getTeamSoup());
            return block;
        }
        return null;
    }

    public Bitconnect(RobotController rc, int width, int height) throws GameActionException {
        this.width = width;
        this.height = height;
        for(int turn = 1; turn < 20 && turn < rc.getRoundNum(); turn++) {
            Transaction[] transactions = rc.getBlock(turn);
            for(Transaction transaction: transactions) {
                Block block = Block.extractBlock(transaction.getMessage());
                if(block != null) {
                    HQSurroundings surroundings = HQSurroundings.fromMessage(block);
                    if(surroundings!=null) {
                        this.ourHQSurroundings = surroundings;
                    }
                }
            }
        }
    }

    public void updateForTurn(RobotController rc) throws GameActionException {
        if(rc.getRoundNum() == 1) {
            return;
        }
        Transaction[] transactions = rc.getBlock(rc.getRoundNum()-1);
        for(Transaction transaction: transactions) {
            Block block = Block.extractBlock(transaction.getMessage());
            if(block != null) {
                HQSurroundings surroundings = HQSurroundings.fromMessage(block);
                if(surroundings!=null) {
                    this.ourHQSurroundings = surroundings;
                }
            }
        }
    }

    /**
     * returns the HQ location and adjacent locations where we want to build walls.
     */
    public HQSurroundings getWallLocations(RobotController rc) {
        return this.ourHQSurroundings;
    }

    /**
     * sends a map of the HQ and desired wall locations.
     */
    public Block sendLandscaperLocations(RobotController rc, HQSurroundings surroundings) throws GameActionException {
        return this.sendMessage(rc, surroundings.toMessage());
    }

    /**
     * Get a bit at an index of an integer.
     */
    public static boolean getBit(int integer, int index) {
        return (integer >> index) % 2 == 1;
    }

    /**
     * Set the bit at an index of an integer, returning the modified integer.
     */
    public static int setBit(int integer, int index, boolean value) {
        return value ? integer | (1 << index) : integer & ~(1 << index);
    }
}
