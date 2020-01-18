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

    // Spots for the wall
    private List<MapLocation> wallSpots = new ArrayList<>();

    // comms
    private Bitconnect comms;

    public HQ(int id) {
        super(id);
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // Read the blockchain for any status updates (and send any queued messages).
        comms.updateForTurn(rc);

        // Aggressively shoot down enemy drones if they roam too closely.
        NetGun.findAndShoot(rc);

        //plan out where the wall should go
        if (rc.getRoundNum() == Config.HQ_WALL_PLANNING_ROUND) {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int yOffset = -1; yOffset <= 1; yOffset++) {
                    if (xOffset != 0 || yOffset != 0) {
                        MapLocation loc = rc.getLocation();
                        loc = new MapLocation(loc.x + xOffset, loc.y + yOffset);
                        if (rc.onTheMap(loc)) {
                            boolean ignore = false;

                            //do a check to see if we're in a corner or against a wall and see if the wall is not necessary

                            //check for an absolute corner of the map
                            if (Math.abs(xOffset)+Math.abs(yOffset)==2){
                                if (!rc.onTheMap(new MapLocation(loc.x + 2*xOffset, loc.y + 0*yOffset)) && !rc.onTheMap(new MapLocation(loc.x + 0*xOffset, loc.y + 2*yOffset))){
                                    ignore = true;
                                }
                            }
                            //check for an edge of the map that's not a corner
                            if (Math.abs(xOffset)+Math.abs(yOffset)==1){
                                if (!rc.onTheMap(new MapLocation(loc.x + 2*xOffset, loc.y + 2*yOffset))){
                                    ignore = true;
                                }
                            }
                            if (!ignore){
                                wallSpots.add(loc);
                            }

                        }
                    }
                }
            }

            //send out communication message with the locations for the walls of the base
            MapLocation[] adj_spots = new MapLocation[wallSpots.size()];
            int idx = 0;
            for (MapLocation loc : wallSpots) {
                adj_spots[idx] = loc;
                idx += 1;
            }
            Bitconnect.HQSurroundings data = new Bitconnect.HQSurroundings(rc.getLocation(), adj_spots);

            comms.sendLandscaperLocations(rc, data);
        }

        if (turn % 41 == 0) {
            boolean allDone = true;
            for (MapLocation loc : wallSpots) {
                if (!rc.isLocationOccupied(loc)) {
                    allDone = false;
                    break;
                }
            }

            if (allDone) comms.wallClaimed(rc);
        }

        // Aggressively build MAX_NUM_MINERS in early game
        if (rc.getRoundNum() < 150) {
            if (this.numMiners >= Config.MAX_NUM_MINERS) return;

            // Wait for cost of miner if before refinery cutoff, otherwise wait for cost of refinery + miner.
            if (rc.getRoundNum() < Config.MIN_REFINERY_ROUND) {
                if (rc.getTeamSoup() < RobotType.MINER.cost) return;
            } else {
                if (rc.getTeamSoup() < RobotType.MINER.cost + RobotType.REFINERY.cost) return;
            }

            this.buildMiner(rc);
            return;
        }

        int teamSoup = rc.getTeamSoup();
        int currentRound = rc.getRoundNum();
        int myID = rc.getID();
        if (teamSoup >= RobotType.MINER.cost) {
            if (teamSoup > 1000 && currentRound % 32 == myID % 32) {
                buildMiner(rc);
                numMiners++;
                return;
            }
            else if (teamSoup > 2000 && currentRound % 64 == myID % 64) {
                buildMiner(rc);
                numMiners++;
                return;
            }
        }
    }

    public void buildMiner(RobotController rc) throws GameActionException {
        // Look at all of the soup locations, and send a miner to a random soup location.
        MapLocation[] soupLocations = rc.senseNearbySoup();

        if (soupLocations.length == 0) {
            soupLocations = new MapLocation[]{new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight()))};
        }

        // Randomly choose a location to send a miner off too to die.
        MapLocation target = soupLocations[this.rng.nextInt(soupLocations.length)];
        Direction desired = rc.getLocation().directionTo(target);

        for (int c = 0; c < 8 && !rc.canBuildRobot(RobotType.MINER, desired); c++) {
            desired = desired.rotateRight();
        }

        if (rc.canBuildRobot(RobotType.MINER, desired)) {
            rc.buildRobot(RobotType.MINER, desired);
            numMiners += 1;
            return;
        }
    }

    public void onCreation(RobotController rc) throws GameActionException {
        System.out.println("setting up coms");
        comms = new Bitconnect(rc, rc.getMapWidth(), rc.getMapHeight());
        System.out.println("done setting up coms");
    }
}