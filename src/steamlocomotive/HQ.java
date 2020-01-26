package steamlocomotive;

import battlecode.common.*;

/**
 * Responsible for spawning miners and planning high-level strategic things.
 */
public class HQ extends Unit {

    public static final int RETRANSMIT_FREQUENCY = 40;

    // Number of miners which have been spawned.
    private int numMiners = 0;

    private int numBuilders = 0;

    // HQ wall information.
    private Bitconnect.HQSurroundings wall;
    // If true, we've planned out the wall successfully.
    private boolean planningDone = false;

    // Build a special miner to build a design school inside the base to build landscapers to bolster the wall internally
    private boolean builtSpecialMiner = false;

    // comms
    private Bitconnect comms;

    public HQ(int id) {
        super(id);
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // Compute the wall on the appropriate turn.
        if (!planningDone) {
            DynamicArray<MapLocation> wallLocs = this.computeWall(rc);
            comms.notifyHqSurroundings(rc.getLocation(), wallLocs);
            planningDone = true;
        }

        // Consistently send out HQ wall information on a regular basis for newly created landscapers.
        if (turn % RETRANSMIT_FREQUENCY == 0) {
            // If wall is done, notify landscapers.
            if (this.isWallDone(rc)) comms.notifyWallDone(true);

            // If enemy HQ has been found, notify this.
            if (comms.enemyHq() != null) comms.notifyEnemyBase(comms.enemyHq());
        }

        // Read the blockchain for any status updates (and send any queued messages).
        comms.updateForTurn(rc);

        // If we can't take actions this turn, do nothing else.
        if (!rc.isReady()) return;
        // Aggressively shoot down enemy drones if they roam too closely.
        if (NetGun.findAndShoot(rc)) return;

        // Aggressively build MAX_NUM_MINERS in early game
        if (rc.getRoundNum() < 300 && this.numMiners <= Config.MAX_NUM_MINERS) {
            // Wait for cost of miner if before refinery cutoff, otherwise wait for cost of refinery + miner.
            if (rc.getRoundNum() < Config.MIN_REFINERY_ROUND) {
                if (rc.getTeamSoup() < RobotType.MINER.cost) return;
            } else {
                if (rc.getTeamSoup() < RobotType.MINER.cost + RobotType.REFINERY.cost) return;
            }

            this.buildMiner(rc);
            return;
        }

        if (rc.getTeamSoup() > 200 && !comms.isWallDone() && rc.getRoundNum() > 700 && !this.builtSpecialMiner){
            this.buildSpecialMiner(rc);
            this.builtSpecialMiner = true;
        }

        // Soup-income based miner production. Note miners can't be produced once all landscapers have taken position.
        int teamSoup = rc.getTeamSoup();
        int currentRound = rc.getRoundNum();
        int myID = rc.getID();
        if (teamSoup >= RobotType.MINER.cost) {
            if (teamSoup > 1000 && currentRound % 32 == myID % 32) {
                buildMiner(rc);
            } else if (teamSoup > 2000 && currentRound % 64 == myID % 64) {
                buildMiner(rc);
            }
        }
    }
    /**
     * Build a special miner to build a design school internally to bolster HQ walls
     */
    public void buildSpecialMiner(RobotController rc) throws GameActionException {
        Direction best = null;
        int dist = 100;
        for (Direction dir : Direction.allDirections()){
            if (rc.canBuildRobot(RobotType.MINER,dir)){
                if (rc.getLocation().distanceSquaredTo(rc.adjacentLocation(dir))<dist){
                    best = dir;
                    dist = rc.getLocation().distanceSquaredTo(rc.adjacentLocation(dir));
                }
            }
        }

        if (best != null && rc.canBuildRobot(RobotType.MINER, best))
            rc.buildRobot(RobotType.MINER, best);
    }


    /**
     * Build a miner close to soup; build randomly if no soup is visible.
     */
    public void buildMiner(RobotController rc) throws GameActionException {
        // Look at all of the soup locations, and send a miner to a random soup location.
        MapLocation[] soupLocations = rc.senseNearbySoup();

        if (soupLocations.length == 0) {
            soupLocations = new MapLocation[]{new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight()))};
        }

        if((numMiners < 4 && rc.getRoundNum() %4 == 0) || (numBuilders >= 2 && rc.getRoundNum() %4 == 0) || (numMiners >= 4 && numBuilders == 0 && rc.getRoundNum()%4 != 0)) {
            return;
        }

        // Randomly choose a location to send a miner off too to die.
        MapLocation target = soupLocations[this.rng.nextInt(soupLocations.length)];
        Direction desired = rc.getLocation().directionTo(target);

        for (int c = 0; c < 8 && !rc.canBuildRobot(RobotType.MINER, desired); c++) {
            desired = desired.rotateRight();
        }

        if (rc.canBuildRobot(RobotType.MINER, desired) && !comms.isWallDone()) {
            rc.buildRobot(RobotType.MINER, desired);
            if(rc.getRoundNum()%4==0) {
                Utils.print("I made a builder!");
                numBuilders++;
            }
            this.numMiners += 1;
        }
    }

    /**
     * Compute the wall tiles around the HQ.
     */
    public DynamicArray<MapLocation> computeWall(RobotController rc) {
        DynamicArray<MapLocation> wallSpots = new DynamicArray<>(8);
        MapLocation us = rc.getLocation();
        int width = rc.getMapWidth();
        int height = rc.getMapHeight();
        // TODO: implement the remaining off-corner cases
        if(us.x == 2 && us.y == 1) {
            wallSpots.add(new MapLocation(0,2));
            wallSpots.add(new MapLocation(1,2));
            wallSpots.add(new MapLocation(2,2));
            wallSpots.add(new MapLocation(3,2));
            wallSpots.add(new MapLocation(3,1));
            wallSpots.add(new MapLocation(3,0));

        } if(us.x == 1 && us.y == 2) {
            wallSpots.add(new MapLocation(2,0));
            wallSpots.add(new MapLocation(2,1));
            wallSpots.add(new MapLocation(2,2));
            wallSpots.add(new MapLocation(2,3));
            wallSpots.add(new MapLocation(1,3));
            wallSpots.add(new MapLocation(0,3));
        } else if(us.x == 2 && us.y == height - 2) {
            wallSpots.add(new MapLocation(0,height-3));
            wallSpots.add(new MapLocation(1,height-3));
            wallSpots.add(new MapLocation(2,height-3));
            wallSpots.add(new MapLocation(3,height-3));
            wallSpots.add(new MapLocation(3,height-2));
            wallSpots.add(new MapLocation(3,height-1));
        } else if(us.x == width-2 && us.y == 2) {
            wallSpots.add(new MapLocation(width-3,0));
            wallSpots.add(new MapLocation(width-3,1));
            wallSpots.add(new MapLocation(width-3,2));
            wallSpots.add(new MapLocation(width-3,3));
            wallSpots.add(new MapLocation(width-2,3));
            wallSpots.add(new MapLocation(width-1,3));
        } else if (us.x == width-3 && us.y == 1) {
            wallSpots.add(new MapLocation(width-4,0));
            wallSpots.add(new MapLocation(width-4,1));
            wallSpots.add(new MapLocation(width-4,2));
            wallSpots.add(new MapLocation(width-3,2));
            wallSpots.add(new MapLocation(width-2,2));
            wallSpots.add(new MapLocation(width-1,2));
        } else if (us.x == 1 && us.y == height-3) {
            wallSpots.add(new MapLocation(0,height-4));
            wallSpots.add(new MapLocation(1,height-4));
            wallSpots.add(new MapLocation(2,height-4));
            wallSpots.add(new MapLocation(2,height-3));
            wallSpots.add(new MapLocation(2,height-2));
            wallSpots.add(new MapLocation(2,height-1));
        } else if (us.x == width-3 && us.y==height-2)  {
            wallSpots.add(new MapLocation(width-4, height-1));
            wallSpots.add(new MapLocation(width-4, height-2));
            wallSpots.add(new MapLocation(width-4, height-3));
            wallSpots.add(new MapLocation(width-3, height-3));
            wallSpots.add(new MapLocation(width-2, height-3));
            wallSpots.add(new MapLocation(width-1, height-3));
        } else if (us.x == width-2 && us.y==height-3)  {
            wallSpots.add(new MapLocation(width-1, height-4));
            wallSpots.add(new MapLocation(width-2, height-4));
            wallSpots.add(new MapLocation(width-3, height-4));
            wallSpots.add(new MapLocation(width-3, height-3));
            wallSpots.add(new MapLocation(width-3, height-2));
            wallSpots.add(new MapLocation(width-3, height-1));
        } else if (us.x == 2 && us.y == 2) {
            wallSpots.add(new MapLocation(0,3));
            wallSpots.add(new MapLocation(1,3));
            wallSpots.add(new MapLocation(2,3));
            wallSpots.add(new MapLocation(3,3));
            wallSpots.add(new MapLocation(3,2));
            wallSpots.add(new MapLocation(3,1));
            wallSpots.add(new MapLocation(3,0));
        } else if (us.x == 2 && us.y == height-3) {
            wallSpots.add(new MapLocation(0,height-4));
            wallSpots.add(new MapLocation(1,height-4));
            wallSpots.add(new MapLocation(2,height-4));
            wallSpots.add(new MapLocation(3,height-4));
            wallSpots.add(new MapLocation(3,height-3));
            wallSpots.add(new MapLocation(3,height-2));
            wallSpots.add(new MapLocation(3,height-1));
        } else if (us.x == width-3 && us.y == height-3) {
            wallSpots.add(new MapLocation(width-1,height-4));
            wallSpots.add(new MapLocation(width-2,height-4));
            wallSpots.add(new MapLocation(width-3,height-4));
            wallSpots.add(new MapLocation(width-4,height-4));
            wallSpots.add(new MapLocation(width-4,height-3));
            wallSpots.add(new MapLocation(width-4,height-2));
            wallSpots.add(new MapLocation(width-4,height-1));
        } else if (us.x == width-3 && us.y == 2) {
            wallSpots.add(new MapLocation(width-1,3));
            wallSpots.add(new MapLocation(width-2,3));
            wallSpots.add(new MapLocation(width-3,3));
            wallSpots.add(new MapLocation(width-4,3));
            wallSpots.add(new MapLocation(width-4,2));
            wallSpots.add(new MapLocation(width-4,1));
            wallSpots.add(new MapLocation(width-4,0));
        } else {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int yOffset = -1; yOffset <= 1; yOffset++) {
                    if (xOffset == 0 && yOffset == 0) continue;

                    MapLocation loc = new MapLocation(us.x + xOffset, us.y + yOffset);
                    if (!rc.onTheMap(loc)) continue;

                    // Check to see if we're in a corner, or against a wall, to see if the wall is not necessary.
                    // Check for an absolute corner of the map.
                    if (Math.abs(xOffset) + Math.abs(yOffset) == 2) {
                        MapLocation xDir = new MapLocation(us.x + 2 * xOffset, us.y);
                        MapLocation yDir = new MapLocation(us.x, us.y + 2 * yOffset);
                        if (!rc.onTheMap(xDir) && !rc.onTheMap(yDir)) continue;
                    }

                    // Check for an edge of the map that's not a corner.
                    if (Math.abs(xOffset) + Math.abs(yOffset) == 1) {
                        MapLocation cornerDir = new MapLocation(us.x + 2 * xOffset, us.y + 2 * yOffset);
                        if (!rc.onTheMap(cornerDir)) continue;
                    }

                    wallSpots.add(loc);
                }
            }
        }

        return wallSpots;
    }

    /** Checks if landscapers are on all wall tiles. */
    public boolean isWallDone(RobotController rc) throws GameActionException {
        for (MapLocation loc : comms.walls()) {
            RobotInfo rob = rc.senseRobotAtLocation(loc);
            if (rob == null || rob.type != RobotType.LANDSCAPER || rob.team != rc.getTeam())
                return false;
        }

        return true;
    }

    public void onCreation(RobotController rc) throws GameActionException {
        this.comms = Bitconnect.initialize(rc);
    }
}