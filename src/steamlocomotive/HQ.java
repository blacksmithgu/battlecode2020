package steamlocomotive;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for spawning miners and planning high-level strategic things.
 */
public class HQ extends Unit {

    // Number of miners which have been spawned.
    private int numMiners = 0;

    // HQ wall information.
    private Bitconnect.HQSurroundings wall;

    // comms
    private Bitconnect comms;

    //enemy HQ location
    private MapLocation enemyHq;

    public HQ(int id) {
        super(id);
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // Read the blockchain for any status updates (and send any queued messages).
        comms.updateForTurn(rc);

        // Compute the wall on the appropriate turn.
        if (rc.getRoundNum() == Config.HQ_WALL_PLANNING_ROUND) {
            this.wall = this.computeWall(rc);
            comms.sendLandscaperLocations(rc, this.wall);
        }

        // Consistently send out HQ wall information on a regular basis for newly created landscapers.
        if (turn % 40 == 0) {
            boolean allDone = true;
            for (MapLocation loc : this.wall.adjacentWallSpots) {
                if (!rc.isLocationOccupied(loc)) {
                    allDone = false;
                    break;
                }
            }

            if (allDone) comms.wallClaimed(rc);
            if (this.enemyHq!=null) comms.setEnemyBaseLocation(this.enemyHq);
        }

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

    /** Build a miner close to soup; build randomly if no soup is visible. */
    public void buildMiner(RobotController rc) throws GameActionException {
        // Look at all of the soup locations, and send a miner to a random soup location.
        MapLocation[] soupLocations = rc.senseNearbySoup();

        if (soupLocations.length == 0) {
            soupLocations = new MapLocation[] { new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight())) };
        }

        // Randomly choose a location to send a miner off too to die.
        MapLocation target = soupLocations[this.rng.nextInt(soupLocations.length)];
        Direction desired = rc.getLocation().directionTo(target);

        for (int c = 0; c < 8 && !rc.canBuildRobot(RobotType.MINER, desired); c++) {
            desired = desired.rotateRight();
        }

        if (rc.canBuildRobot(RobotType.MINER, desired)) {
            rc.buildRobot(RobotType.MINER, desired);
            this.numMiners += 1;
        }
    }

    /** Compute the wall tiles around the HQ. */
    public Bitconnect.HQSurroundings computeWall(RobotController rc) {
        List<MapLocation> wallSpots = new ArrayList<>(8);
        MapLocation us = rc.getLocation();
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                if (xOffset == 0 && yOffset == 0) continue;

                MapLocation loc = new MapLocation(us.x + xOffset, us.y + yOffset);
                if (!rc.onTheMap(loc)) continue;

                // Check to see if we're in a corner, or against a wall, to see if the wall is not necessary.
                // Check for an absolute corner of the map.
                if (Math.abs(xOffset) + Math.abs(yOffset) == 2) {
                    MapLocation xDir = new MapLocation(us.x + 2*xOffset, us.y);
                    MapLocation yDir = new MapLocation(us.x, us.y + 2*yOffset);
                    if (!rc.onTheMap(xDir) && !rc.onTheMap(yDir)) continue;
                }

                // Check for an edge of the map that's not a corner.
                if (Math.abs(xOffset) + Math.abs(yOffset) == 1) {
                    MapLocation cornerDir = new MapLocation(us.x + 2*xOffset, us.y + 2*yOffset);
                    if (!rc.onTheMap(cornerDir)) continue;
                }

                wallSpots.add(loc);
            }
        }

        return new Bitconnect.HQSurroundings(rc.getLocation(), wallSpots.toArray(new MapLocation[0]), rc.getTeam());
    }

    public void onCreation(RobotController rc) throws GameActionException {
        comms = new Bitconnect(rc, rc.getMapWidth(), rc.getMapHeight());
    }
}