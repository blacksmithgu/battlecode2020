package steamlocomotive;

import battlecode.common.*;

import java.util.Map;

public class Bitconnect {

    // width of the map
    final int width;
    // height of the map
    final int height;
    // our HQ setup
    HQSurroundings ourHQSurroundings;
    // is our wall done
    boolean isWallDone = false;

    private MapLocation enemyBaseLocation = null;

    final CircularStack<Block> blocksToSend;

    private enum MessageType {
        HQ_SETTUP(42),
        WALL_DONE(76),
        ENEMY_BASE(91);

        final int id;

        private MessageType(int i) {
            this.id = i;
        }

        public int getId() {
            return this.id;
        }
    }

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
                if(test.x == location.x && test.y == location.y) {
                    return true;
                }
            }
            return false;
        }

        public Block toMessage() {
            int[] message = new int[6];
            message[0] = MessageType.HQ_SETTUP.getId();
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
            int[] message = block.getBlockMessage();
            if(message[0]!=MessageType.HQ_SETTUP.getId()) {
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


    private Block sendMessage(RobotController rc, Block block) throws GameActionException {
        if (rc.getTeamSoup() > Config.SOUP_FOR_COMS) {
            rc.submitTransaction(block.getBlockMessage(), Config.SOUP_FOR_COMS);
            return block;
        } else if (rc.getTeamSoup() > 0) {
            rc.submitTransaction(block.getBlockMessage(), rc.getTeamSoup());
            return block;
        }
        return null;
    }

    public Bitconnect(RobotController rc, int width, int height) throws GameActionException {
        this.width = width;
        this.height = height;
        this.blocksToSend = new CircularStack<Block>(10);

        for(int turn = 1; turn < 20 && turn < rc.getRoundNum(); turn++) {
            Transaction[] transactions = rc.getBlock(turn);
            for(Transaction transaction: transactions) {
                Block block = Block.extractBlock(transaction.getMessage());
                if(block != null) {
                    HQSurroundings surroundings = HQSurroundings.fromMessage(block);
                    if(surroundings!=null) {
                        System.out.println("Our HQ is at: " + surroundings.hq);
                        this.ourHQSurroundings = surroundings;
                    }
                }
            }
        }

        for(int turn = 1; turn < 50 && turn < rc.getRoundNum(); turn++) {
            Transaction[] transactions = rc.getBlock(rc.getRoundNum() - turn);
            for(Transaction transaction: transactions) {
                Block block = Block.extractBlock(transaction.getMessage());
                if(block != null) {
                    if(block.getMessage()[0]==MessageType.WALL_DONE.getId()) {
                        this.isWallDone = true;
                        continue;
                    }

                    if(block.getMessage()[0]==MessageType.ENEMY_BASE.getId()) {
                        this.enemyBaseLocation = new MapLocation(block.getMessage()[1], block.getMessage()[2]);
                    }
                }
            }
        }
    }


    /**
     * All robots that want to recieve new coms should call this at the start of their turn.
     */
    public void updateForTurn(RobotController rc) throws GameActionException {
        if(rc.getRoundNum() == 1) {
            return;
        }

        Block toSend = blocksToSend.pop();
        if(toSend!=null) {
            Block message = this.sendMessage(rc, toSend);
            if(message == null) {
                blocksToSend.push(message);
            }
        }

        Transaction[] transactions = rc.getBlock(rc.getRoundNum()-1);
        for(Transaction transaction: transactions) {
            Block block = Block.extractBlock(transaction.getMessage());
            if(block != null) {
                HQSurroundings surroundings = HQSurroundings.fromMessage(block);
                if(surroundings!=null) {
                    System.out.println("Our HQ is at: " + surroundings.hq);
                    this.ourHQSurroundings = surroundings;
                    continue;
                }
                if(block.getMessage()[0] == MessageType.WALL_DONE.getId()) {
                    isWallDone = true;
                    continue;
                }
                if(block.getMessage()[0] == MessageType.ENEMY_BASE.getId()) {
                    this.enemyBaseLocation = new MapLocation(block.getMessage()[1], block.getMessage()[2]);
                }
            }
        }
    }

    /**
     * Returns the HQ location and adjacent locations where we want to build walls.
     */
    public HQSurroundings getWallLocations(RobotController rc) {
        return this.ourHQSurroundings;
    }

    /**
     * Sends a map of the HQ and desired wall locations.
     */
    public void sendLandscaperLocations(RobotController rc, HQSurroundings surroundings) throws GameActionException {
        this.blocksToSend.push(surroundings.toMessage());
    }

    /**
     *  Says all the wall spots have been claimed by landscapers
     */
    public void wallClaimed(RobotController rc) {
        int[] message = new int[6];
        message[0] = MessageType.WALL_DONE.getId();
        Block block = Block.createBlock(message);
        this.blocksToSend.push(block);
    }

    /**
     * Returns true if the "wallClaimed" message has been sent in the last 50 turns
     */
    public boolean isWallDone(RobotController rc) {
        return isWallDone;
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

    public MapLocation getEnemyBaseLocation() {
        return this.enemyBaseLocation;
    }

    /**
     * Sends the enemyBaseLocation via the blockchain
     */
    public void setEnemyBaseLocation(MapLocation location) {
        int[] message = new int[6];
        message[0] = MessageType.ENEMY_BASE.getId();
        message[1] = location.x;
        message[2] = location.y;
        this.blocksToSend.push(Block.createBlock(message));
    }
}
