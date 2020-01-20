package steamlocomotive;

import battlecode.common.*;

import static steamlocomotive.Block.*;

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

    final CircularStack<Block.Message> messagesToSend;

    public static final int MESSAGE_ID_BITS = 4;

    /**
     * Each MessageType id must be smaller than 2^MESSAGE_ID_BITS
     */
    private enum MessageType {
        HQ_SETTUP(0),
        WALL_DONE(1),
        ENEMY_BASE(2);

        final int id;

        private MessageType(int i) {
            this.id = i;
        }

        public int getId() {
            return this.id;
        }
    }

    public static int[] compressInts(int[] content, int[] numBits) {
        int totalBits = 0;
        for (int bits : numBits) {
            totalBits++;
        }
        int[] toReturn = new int[(int) Math.ceil(((double) totalBits) / 32)];

        int currentBit = 0;
        for (int index = 0; index < content.length; index++) {
            setBits(toReturn, currentBit, numBits[index] % 32, content[index]);
            currentBit += numBits[index] % 32;
        }

        return toReturn;
    }

    public static int[] decompressBits(int[] content, int[] numBits) {
        int[] result = new int[numBits.length];
        int currentBit = 0;
        for (int index = 0; index < numBits.length; index++) {
            result[index] = getBits(content, currentBit, numBits[index]);
            currentBit += numBits[index] % 32;
        }
        return result;
    }

    public static class HQSurroundings {
        MapLocation hq;
        MapLocation[] adjacentWallSpots;
        private static final int[] numBits = {MESSAGE_ID_BITS, 6, 6, 8};

        /**
         * adjacent wall spots should be next to the HQ.
         */
        public HQSurroundings(MapLocation hq, MapLocation[] adjacentWallSpots) {
            this.hq = hq;
            this.adjacentWallSpots = adjacentWallSpots;
        }

        private boolean listContainsLocation(MapLocation[] locs, MapLocation location) {
            for (MapLocation test : locs) {
                if (test.x == location.x && test.y == location.y) {
                    return true;
                }
            }
            return false;
        }

        public Block.Message toMessage() {
            int[] message = new int[4];
            message[0] = MessageType.HQ_SETTUP.getId();
            message[1] = hq.x;
            message[2] = hq.y;
            message[3] = 0;

            int index = 0;

            for (Direction direction : Direction.allDirections()) {
                if (direction.equals(Direction.CENTER)) {
                    continue;
                }

                if (listContainsLocation(this.adjacentWallSpots, hq.add(direction))) {
                    message[3] = Block.setBit(message[3], index, true);
                } else {
                    message[3] = Block.setBit(message[3], index, false);
                }
                index++;
            }
            return new Block.Message(compressInts(message, numBits), numBits[0] + 20);
        }

        public static HQSurroundings fromMessage(Block.Message compressed) {

            int[] message = decompressBits(compressed.message, numBits);
            if (message[0] != MessageType.HQ_SETTUP.getId()) {
                return null;
            }
            MapLocation hq = new MapLocation(message[1], message[2]);
            int count = 0;

            int adjContent = message[3];
            for (int index = 0; index < 8; index++) {
                if (getBit(adjContent, index)) {
                    count++;
                }
            }

            MapLocation[] locations = new MapLocation[count];
            int locationsIndex = 0;
            int adjIndex = 0;
            for (Direction direction : Direction.allDirections()) {
                if (direction == Direction.CENTER) {
                    continue;
                }
                if (getBit(adjContent, adjIndex)) {
                    locations[locationsIndex] = hq.add(direction);
                    locationsIndex++;
                }
                adjIndex++;
            }
            return new HQSurroundings(hq, locations);
        }

        public boolean equals(HQSurroundings other) {
            if (other == null) {
                return false;
            }
            if (!this.hq.equals(other.hq)) {
                return false;
            }
            for (MapLocation location : this.adjacentWallSpots) {
                if (!this.listContainsLocation(other.adjacentWallSpots, location)) {
                    return false;
                }
            }
            for (MapLocation location : other.adjacentWallSpots) {
                if (!this.listContainsLocation(this.adjacentWallSpots, location)) {
                    return false;
                }
            }
            return true;
        }
    }


    private Block sendMessage(RobotController rc, Block block) throws GameActionException {
        if (rc.getTeamSoup() > Config.SOUP_FOR_COMS) {
            rc.submitTransaction(block.getBlockchainMessage(), Config.SOUP_FOR_COMS);
            return block;
        } else if (rc.getTeamSoup() > 0) {
            rc.submitTransaction(block.getBlockchainMessage(), rc.getTeamSoup());
            return block;
        }
        return null;
    }

    public Bitconnect(RobotController rc, int width, int height) throws GameActionException {
        this.width = width;
        this.height = height;
        this.messagesToSend = new CircularStack<Block.Message>(10);

        for (int turn = 1; turn < 20 && turn < rc.getRoundNum(); turn++) {
            Transaction[] transactions = rc.getBlock(turn);
            for (Transaction transaction : transactions) {
                Block block = Block.extractBlock(transaction.getMessage());
                for (Block.Message message : block.getMessages()) {
                    HQSurroundings surroundings = HQSurroundings.fromMessage(message);
                    if (surroundings != null) {
                        System.out.println("Our HQ is at: " + surroundings.hq);
                        this.ourHQSurroundings = surroundings;
                    }
                }
            }
        }

        for (int turn = 1; turn < 50 && turn < rc.getRoundNum(); turn++) {
            Transaction[] transactions = rc.getBlock(rc.getRoundNum() - turn);
            for (Transaction transaction : transactions) {
                Block block = Block.extractBlock(transaction.getMessage());
                for (Block.Message message : block.getMessages()) {
                    if (message.message[0] == MessageType.WALL_DONE.getId()) {
                        this.isWallDone = true;
                        continue;
                    }

                    if (message.message[0] == MessageType.ENEMY_BASE.getId()) {
                        this.enemyBaseLocation = new MapLocation(message.message[1], message.message[2]);
                    }
                }
            }
        }
    }


    /**
     * All robots that want to recieve new coms should call this at the start of their turn.
     */
    public void updateForTurn(RobotController rc) throws GameActionException {
        if (rc.getRoundNum() == 1) {
            return;
        }

        // Collect all the messages we want to send for the turn and send a blocks worth of them.
        sendTransactions(rc);

        // Scan for new messages
        Transaction[] transactions = rc.getBlock(rc.getRoundNum() - 1);
        for (Transaction transaction : transactions) {
            Block block = Block.extractBlock(transaction.getMessage());
            for (Block.Message message : block.getMessages()) {
                HQSurroundings surroundings = HQSurroundings.fromMessage(message);
                if (surroundings != null) {
                    System.out.println("Our HQ is at: " + surroundings.hq);
                    this.ourHQSurroundings = surroundings;
                    continue;
                }
                if (message.message[0] == MessageType.WALL_DONE.getId()) {
                    isWallDone = true;
                    continue;
                }
                if (message.message[0] == MessageType.ENEMY_BASE.getId()) {
                    this.enemyBaseLocation = new MapLocation(message.message[1], message.message[2]);
                }
            }
        }
    }

    private void sendTransactions(RobotController rc) throws GameActionException {
        Block block = new Block(rc.getID(), rc.getLocation().x, rc.getLocation().y);
        Block.Message messageToAdd = messagesToSend.pop();
        while (messageToAdd != null && block.addMessage(messageToAdd)) {
            messageToAdd = messagesToSend.pop();
        }
        if (messageToAdd != null) {
            messagesToSend.push(messageToAdd);
        }
        sendMessage(rc, block);
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
        this.messagesToSend.push(surroundings.toMessage());
    }

    /**
     * Says all the wall spots have been claimed by landscapers
     */
    public void wallClaimed(RobotController rc) {
        int[] message = new int[1];
        message[0] = MessageType.WALL_DONE.getId();
        this.messagesToSend.push(new Block.Message(message, MESSAGE_ID_BITS));
    }

    /**
     * Returns true if the "wallClaimed" message has been sent in the last 50 turns
     */
    public boolean isWallDone(RobotController rc) {
        return isWallDone;
    }

    public MapLocation getEnemyBaseLocation() {
        return this.enemyBaseLocation;
    }

    /**
     * Sends the enemyBaseLocation via the blockchain
     */
    public void setEnemyBaseLocation(MapLocation location) {
        int[] message = new int[3];
        message[0] = MessageType.ENEMY_BASE.getId();
        message[1] = location.x;
        message[2] = location.y;

        int[] numBits = {MESSAGE_ID_BITS, 6, 6};
        this.messagesToSend.push(new Block.Message(compressInts(message, numBits), MESSAGE_ID_BITS + 12));
    }
}
