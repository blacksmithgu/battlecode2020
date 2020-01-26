package steamlocomotive;

import battlecode.common.*;

/**
 * Intermediary for global shared state; reads messages every turn to update state and can also queue messages
 * to inform other units of state changes.
 */
public class Bitconnect {

    /** Number of bits for encoding a message type. */
    public static final int MESSAGE_TYPE_BITS;
    static {
        int result = 1;
        while ((1 << result) < MessageType.values().length) result++;

        MESSAGE_TYPE_BITS = result;
    }

    /** Timeout for heartbeat messages. */
    public static final int HEARTBEAT_TIMEOUT = 60;

    /** The number of rounds between heartbeats. */
    public static final int HEARTBEAT_CADENCE = 40;

    /**
     * The possible different types of messages.
     */
    private enum MessageType {
        HQ_SURROUNDINGS(0, true),
        NO_ENEMY_BASE(1, false),
        ENEMY_BASE(2, true),
        WALL_DONE(3, true),
        HEARTBEAT(4, true),
        UNKNOWN(999999, false);

        private final int id;
        private final boolean highPriority;

        public static MessageType fromId(int id) {
            for (MessageType type : MessageType.values()) {
                if (type.id == id) return type;
            }

            return MessageType.UNKNOWN;
        }

        MessageType(int i, boolean highPriority) {
            this.id = i;
            this.highPriority = highPriority;
        }

        public int id() {
            return this.id;
        }

        public boolean isHighPriority() {
            return highPriority;
        }
    }

    /**
     * Utility class which allows for appending bits to a block dynamically.
     */
    public static class BlockBuilder {
        private int[] data;
        private int index;

        public BlockBuilder() {
            this.data = new int[7];
            this.index = 0;
        }

        public void append(boolean bit) {
            int wordIndex = this.index / 32, bitIndex = this.index % 32;
            // Data is 0 initialized, so only overwrite if bit is 1.
            if (bit) data[wordIndex] |= (1 << bitIndex);

            this.index += 1;
        }

        public void append(int value, int numBits) {
            int wordIndex = this.index / 32, bitIndex = this.index % 32;
            int wordAvailable = 32 - bitIndex;
            if (wordAvailable < numBits) {
                // Have to deal with writing across two integers.
                int firstMask = (1 << wordAvailable) - 1;
                int secondMask = (1 << (numBits - wordAvailable)) - 1;
                // Write as many bits as possible to the current word.
                data[wordIndex] |= (value & firstMask) << bitIndex;
                // Write remaining to second word.
                data[wordIndex + 1] |= (value >>> wordAvailable) & secondMask;
            } else {
                // Write just to the current integer.
                int mask = (1 << numBits) - 1;
                data[wordIndex] |= (value & mask) << bitIndex;
            }

            this.index += numBits;
        }

        public int[] finish() {
            return this.data;
        }
    }

    /**
     * Reads data from a block iteratively.
     */
    public static class BlockReader {
        private final int[] data;
        private int index;

        public BlockReader(int[] data) {
            this.data = data;
            this.index = 0;
        }

        public boolean readBoolean() {
            int wordIndex = this.index / 32, bitIndex = this.index % 32;
            boolean value = (data[wordIndex] & (1 << bitIndex)) != 0;

            this.index += 1;
            return value;
        }

        public int readInteger(int numBits) {
            int wordIndex = this.index / 32, bitIndex = this.index % 32;
            int wordAvailable = 32 - bitIndex;
            int result = 0;

            if (wordAvailable < numBits) {
                // Need to handle reading across two seperate words.
                result = (data[wordIndex] >>> bitIndex) & ((1 << wordAvailable) - 1);
                result |= (data[wordIndex + 1] & ((1 << (numBits - wordAvailable)) - 1)) << wordAvailable;
            } else {
                result = (data[wordIndex] >>> bitIndex) & ((1 << numBits) - 1);
            }

            this.index += numBits;
            return result;
        }
    }

    /**
     * A message implementation; allows for converting the message to a block. Check it's type to downcast.
     */
    private interface Message {
        /**
         * The type of this message (see MessageType).
         */
        MessageType type();

        /**
         * The size of the contents of this message in bits.
         */
        int bitSize();

        /**
         * Write this message to the given block builder.
         */
        void write(BlockBuilder builder);
    }

    /**
     * Wall locations and current status of the walls.
     */
    public static class HQSurroundings implements Message {
        // Core wall locations (between 3 and 8) to build the wall.
        private final DynamicArray<MapLocation> walls;
        // The HQ location.
        private final MapLocation hq;

        public HQSurroundings(DynamicArray<MapLocation> walls, MapLocation hq) {
            this.walls = walls;
            this.hq = hq;
        }

        public DynamicArray<MapLocation> walls() {
            return this.walls;
        }

        public MapLocation hq() {
            return this.hq;
        }

        public MessageType type() {
            return MessageType.HQ_SURROUNDINGS;
        }

        public int bitSize() {
            return 4 + 12 * (walls.size() + 1);
        }

        public void write(BlockBuilder builder) {
            // Write the number of walls (3 bits), followed by wall offsets (2x3 bits each), followed by wall finish.
            builder.append(walls.size(), 4);
            for (MapLocation loc : walls) {
                builder.append(loc.x, 6);
                builder.append(loc.y, 6);
            }
            builder.append(hq.x, 6);
            builder.append(hq.y, 6);
        }

        public static HQSurroundings read(BlockReader reader) {
            int numWalls = reader.readInteger(4);
            DynamicArray<MapLocation> walls = new DynamicArray<>(numWalls);
            for (int i = 0; i < numWalls; i++) {
                int x = reader.readInteger(6);
                int y = reader.readInteger(6);
                walls.add(new MapLocation(x, y));
            }

            int x = reader.readInteger(6);
            int y = reader.readInteger(6);
            return new HQSurroundings(walls, new MapLocation(x, y));
        }
    }

    public static class LocationMessage implements Message {
        private MapLocation location;
        private MessageType type;

        public LocationMessage(MapLocation location, MessageType type) {
            this.location = location;
            this.type = type;
        }

        public MapLocation location() {
            return location;
        }

        @Override
        public MessageType type() {
            return type;
        }

        @Override
        public int bitSize() {
            return 12;
        }

        @Override
        public void write(BlockBuilder builder) {
            builder.append(location.x, 6);
            builder.append(location.y, 6);
        }

        public static LocationMessage read(BlockReader reader, MessageType type) {
            MapLocation loc = new MapLocation(reader.readInteger(6), reader.readInteger(6));
            return new LocationMessage(loc, type);
        }
    }

    public static class BooleanMessage implements Message {
        private MessageType type;
        private boolean value;

        public BooleanMessage(boolean value, MessageType type) {
            this.type = type;
            this.value = value;
        }

        public boolean value() {
            return value;
        }

        @Override
        public MessageType type() {
            return type;
        }

        @Override
        public int bitSize() {
            return 1;
        }

        @Override
        public void write(BlockBuilder builder) {
            builder.append(this.value);
        }

        public static BooleanMessage read(BlockReader reader, MessageType type) {
            return new BooleanMessage(reader.readBoolean(), type);
        }
    }

    public static class HeartbeatMessage implements Message {
        private int id;
        private MapLocation location;
        private RobotType type;
        private int round;

        public HeartbeatMessage(int id, MapLocation location, RobotType type, int round) {
            this.id = id;
            this.location = location;
            this.type = type;
            this.round = round;
        }

        public int id() { return id; }
        public MapLocation location() { return location; }
        public int round() { return round; }
        public RobotType robotType() { return type; }

        @Override
        public MessageType type() { return MessageType.HEARTBEAT; }

        @Override
        public int bitSize() { return 16 + 12 + 16 + 4; }

        @Override
        public void write(BlockBuilder builder) {
            builder.append(this.id, 16);
            builder.append(this.location.x, 6);
            builder.append(this.location.y, 6);
            builder.append(this.type.ordinal(), 4);
            builder.append(this.round, 16);
        }

        public static HeartbeatMessage read(BlockReader reader) {
            int id = reader.readInteger(16);
            int locX = reader.readInteger(6);
            int locY = reader.readInteger(6);
            RobotType type = RobotType.values()[reader.readInteger(4)];
            int round = reader.readInteger(16);

            return new HeartbeatMessage(id, new MapLocation(locX, locY), type, round);
        }
    }

    // Map width and height.
    private final int width, height;

    // Our HQ location, and the enemy HQ location (if known; otherwise null).
    private MapLocation hq, enemyHq;

    // Before we know the exact location of the enemy HQ, this is the list of possible enemy HQ locations based on map symmetry.
    private DynamicArray<MapLocation> possibleEnemyHqs;

    // Wall locations surrounding the HQ.
    private DynamicArray<MapLocation> walls;

    // If true, landscapers have reached all wall locations.
    private boolean wallDone;

    // Queue of messages to attempt to send.
    private final DynamicArray<Message> sendQueue;

    // Design schools that have broadcasted existence in the past 30 turns
    private DynamicArray<HeartbeatMessage> designSchools;

    // Fulfillment centers that have broadcasted existence in the past 30 turns.
    private DynamicArray<HeartbeatMessage> fulfillmentCenters;

    /**
     * Initialize a new communications handler from the given robot controller. This initialization
     * can be potentially expensive, since it scans early blocks for HQ location and wall state.
     */
    public static Bitconnect initialize(RobotController rc) throws GameActionException {
        Bitconnect conn = new Bitconnect(rc.getMapWidth(), rc.getMapHeight());
        for (int round = 2; round <= Math.min(20, rc.getRoundNum() - 1); round++) {
            Transaction[] trans = rc.getBlock(round);
            for (Transaction tr : trans) conn.handleTransaction(rc, tr);
        }

        return conn;
    }

    /**
     * Compute potential enemy HQ locations based on our HQ location.
     */
    public static DynamicArray<MapLocation> computeEnemyLocations(MapLocation hq, int width, int height) {
        DynamicArray<MapLocation> possibleEnemyHqs = new DynamicArray<>(3);
        MapLocation curr = hq;
        for (int i = 0; i < 3; i++) {
            curr = new MapLocation(width - curr.y - 1, curr.x);
            possibleEnemyHqs.add(curr);
        }

        return possibleEnemyHqs;
    }

    /**
     * Checksum the indices of the given integer array; use different algorithms depending on the team.
     */
    public static int checksum(int[] data, int start, int end, Team team) {
        int val = (team == Team.A) ? 6123412 : 32742361;
        for (int index = start; index < end; index++) val ^= data[index];
        return val;
    }

    private Bitconnect(int width, int height) {
        this.sendQueue = new DynamicArray<>(20);
        this.width = width;
        this.height = height;
        this.fulfillmentCenters = new DynamicArray<>(4);
        this.designSchools = new DynamicArray<>(4);
    }

    private void handleTransaction(RobotController rc, Transaction trans) throws GameActionException {
        // Verify there's actually a transaction here.
        if (trans == null || trans.getMessage() == null) return;
        // Verify the checksum on this transaction, ignore it if invalid.
        if (Bitconnect.checksum(trans.getMessage(), 0, 6, rc.getTeam()) != trans.getMessage()[6]) return;

        // Read the chunks within this transaction.
        BlockReader reader = new BlockReader(trans.getMessage());
        int numMessages = reader.readInteger(3);

        for (int index = 0; index < numMessages; index++) {
            int messageId = reader.readInteger(MESSAGE_TYPE_BITS);
            switch (MessageType.fromId(messageId)) {
                case ENEMY_BASE:
                    this.enemyHq = LocationMessage.read(reader, MessageType.ENEMY_BASE).location;
                    this.possibleEnemyHqs = new DynamicArray<>(1);
                    this.possibleEnemyHqs.add(enemyHq);
                    break;
                case HQ_SURROUNDINGS:
                    HQSurroundings surr = HQSurroundings.read(reader);
                    MapLocation oldHq = this.hq;
                    this.hq = surr.hq;
                    this.walls = surr.walls;

                    if (this.hq != null && oldHq == null) this.handlePotentialEnemyLocs(this.hq);
                    break;
                case WALL_DONE:
                    this.wallDone = BooleanMessage.read(reader, MessageType.WALL_DONE).value;
                    break;
                case HEARTBEAT:
                    HeartbeatMessage heartbeat = HeartbeatMessage.read(reader);
                    if (heartbeat.type == RobotType.DESIGN_SCHOOL) {
                        int hindex;
                        for (hindex = 0; hindex < designSchools.size(); hindex++) {
                            if (designSchools.get(hindex).id() == heartbeat.id()) break;
                        }

                        if (hindex < designSchools.size()) designSchools.set(hindex, heartbeat);
                        else designSchools.add(heartbeat);
                    } else if (heartbeat.type == RobotType.FULFILLMENT_CENTER) {
                        int hindex;
                        for (hindex = 0; hindex < fulfillmentCenters.size(); hindex++) {
                            if (fulfillmentCenters.get(hindex).id() == heartbeat.id()) break;
                        }

                        if (hindex < fulfillmentCenters.size()) fulfillmentCenters.set(hindex, heartbeat);
                        else fulfillmentCenters.add(heartbeat);
                    }
                    break;
                case NO_ENEMY_BASE:
                    MapLocation enemyLoc = LocationMessage.read(reader, MessageType.NO_ENEMY_BASE).location;
                    if (this.possibleEnemyHqs != null) {
                        int enemyIdx = this.possibleEnemyHqs.indexOf(enemyLoc);
                        if (enemyIdx != -1) this.possibleEnemyHqs.removeQuick(enemyIdx);
                        if (this.possibleEnemyHqs.size() == 1) this.enemyHq = this.possibleEnemyHqs.get(0);
                    }
                    break;
                default:
                    throw new IllegalStateException("Unrecognized message type during transaction parsing");
            }
        }
    }

    /**
     * Send messages optimally by packing multiple messages into a single transaction.
     */
    private boolean clusteredSend(RobotController rc) throws GameActionException {
        if (this.sendQueue.size() == 0) return false;

        BlockBuilder builder = new BlockBuilder();
        DynamicArray<Message> fitMessages = new DynamicArray<>(8);

        int availableBits = 32 * 6 - 3;
        while (fitMessages.size() < 8 && this.sendQueue.size() > 0 && availableBits >= this.sendQueue.get(0).bitSize() + MESSAGE_TYPE_BITS) {
            Message msg = this.sendQueue.get(0);
            this.sendQueue.removeQuick(0);
            fitMessages.add(msg);
            availableBits -= msg.bitSize() + MESSAGE_TYPE_BITS;
        }

        if (fitMessages.size() == 0) return false;

        builder.append(fitMessages.size(), 3);
        for (Message msg : fitMessages) {
            builder.append(msg.type().id(), MESSAGE_TYPE_BITS);
            msg.write(builder);
        }

        int[] result = builder.finish();
        result[result.length - 1] = Bitconnect.checksum(result, 0, 6, rc.getTeam());
        if (rc.canSubmitTransaction(result, Config.COMMS_COST)) {
            System.out.println(rc.getType() + " spent money!");
            rc.submitTransaction(result, Config.COMMS_COST);
            return true;
        } else {
            // Add messages back to send queue so we can try again later.
            for (Message msg : fitMessages) this.sendQueue.add(msg);

            return false;
        }
    }

    // Compute possible enemy HQ locations based on our HQ location.
    private void handlePotentialEnemyLocs(MapLocation hq) {
        if (this.enemyHq != null) return;
        this.possibleEnemyHqs = Bitconnect.computeEnemyLocations(hq, this.width, this.height);
    }

    /** Scan recent blocks that have been advertised. */
    public void scanRecent(RobotController rc, int pastRounds) throws GameActionException {
        for (int round = Math.max(2, rc.getRoundNum() - pastRounds); round < rc.getRoundNum(); round++) {
            Transaction[] trans = rc.getBlock(round);
            for (Transaction tr : trans) this.handleTransaction(rc, tr);
        }
    }

    /**
     * All robots that want to recieve new comms should call this at the start of their turn.
     */
    public void updateForTurn(RobotController rc) throws GameActionException {
        // No blocks are posted on round 1, so skip it.
        if (rc.getRoundNum() == 1) return;

        // Send operations; repeatedly send until the clustered send fails.
        while (this.clusteredSend(rc)) ;

        // Recieve operations.
        for (Transaction transaction : rc.getBlock(rc.getRoundNum() - 1))
            this.handleTransaction(rc, transaction);

        // Timeout heartbeats.
        for (int i = 0; i < designSchools.size(); i++) {
            if (designSchools.get(i).round < rc.getRoundNum() - HEARTBEAT_TIMEOUT) {
                designSchools.removeQuick(i);
                i--;
            }
        }

        for (int i = 0; i < fulfillmentCenters.size(); i++) {
            if (fulfillmentCenters.get(i).round < rc.getRoundNum() - HEARTBEAT_TIMEOUT) {
                fulfillmentCenters.removeQuick(i);
                i--;
            }
        }
    }

    /**
     * Obtain our HQ location, if known (else null).
     */
    public MapLocation hq() {
        return this.hq;
    }

    /**
     * Obtain the enemy HQ location, if known (else null).
     */
    public MapLocation enemyHq() {
        return this.enemyHq;
    }

    /**
     * Obtain the list of wall locations.
     */
    public DynamicArray<MapLocation> walls() {
        return this.walls;
    }

    /**
     * Returns a list of design schools that have broadcasted their existence recently.
     */
    public DynamicArray<HeartbeatMessage> designSchools() { return designSchools; }

    /**
     * Returns a list of fulfillment centers that have broadcasted their existence recently.
     */
    public DynamicArray<HeartbeatMessage> fulfillmentCenters() { return fulfillmentCenters; }

    /**
     * The list of possible enemy locations; null if we don't know our own HQ locations.
     */
    public DynamicArray<MapLocation> potentialEnemyLocations() {
        return this.possibleEnemyHqs;
    }

    public boolean isWallDone() {
        return this.wallDone;
    }

    public void notifyHeartbeat(int id, MapLocation location, RobotType type, int round) {
        this.sendQueue.add(new HeartbeatMessage(id, location, type, round));
    }

    public void notifyHqSurroundings(MapLocation hq, DynamicArray<MapLocation> walls) {
        if (this.hq == null) this.handlePotentialEnemyLocs(hq);

        this.hq = hq;
        this.walls = walls;
        this.sendQueue.add(new HQSurroundings(walls, hq));
    }

    public void notifyNoEnemyBase(MapLocation noBase) {
        if (this.possibleEnemyHqs != null) {
            int enemyIdx = this.possibleEnemyHqs.indexOf(noBase);
            if (enemyIdx != -1) this.possibleEnemyHqs.removeQuick(enemyIdx);
            if (this.possibleEnemyHqs.size() == 1) this.notifyEnemyBase(this.possibleEnemyHqs.get(0));
        }

        this.sendQueue.add(new LocationMessage(noBase, MessageType.NO_ENEMY_BASE));
    }

    public void notifyEnemyBase(MapLocation enemyHq) {
        this.enemyHq = enemyHq;
        this.possibleEnemyHqs = new DynamicArray<>(1);
        this.possibleEnemyHqs.add(enemyHq);
        this.sendQueue.add(new LocationMessage(enemyHq, MessageType.ENEMY_BASE));
    }

    public void notifyWallDone(boolean wallDone) {
        this.wallDone = wallDone;
        this.sendQueue.add(new BooleanMessage(wallDone, MessageType.WALL_DONE));
    }
}
