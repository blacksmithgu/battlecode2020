package steamlocomotive;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Responsible for spawning miners and planning high-level strategic things.
 */
public class HQ extends Unit {

    // Number of miners which have been spawned.
    private int miners = 0;

    public HQ(int id) {
        super(id);
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // TODO: Add net gun behavior so the HQ shoots down nearby drones.
        System.out.println("Joseph has successfully downloaded Git!");
        // Wait for cost of miner if before refinery cutoff, otherwise wait for cost of refinery + miner.
        if (rc.getRoundNum() < Config.MIN_REFINERY_ROUND) {
            if (rc.getTeamSoup() < RobotType.MINER.cost) return;
        } else {
            if (rc.getTeamSoup() < RobotType.MINER.cost + RobotType.REFINERY.cost) return;
        }

        // Look at all of the soup locations, create a list, and send a miner to a random soup location.
        // TODO: Cache and update every X rounds in the future.
        List<MapLocation> soupLocations = new ArrayList<>();

        // TODO: May consider soups from the whole map.
        Utils.traverseSensable(rc, loc -> {
            int soup = rc.senseSoup(loc);
            if (soup > 0) soupLocations.add(loc);
        });

        if (soupLocations.size() == 0) {
            soupLocations.add(new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight())));
        }

        // Randomly choose a location to send a miner off too to die.
        MapLocation target = soupLocations.get(this.rng.nextInt(soupLocations.size()));
        Direction desired = rc.getLocation().directionTo(target);

        for (int c = 0; c < 8 && !rc.canBuildRobot(RobotType.MINER, desired); c++) {
            desired = desired.rotateRight();
        }

        if (rc.canBuildRobot(RobotType.MINER, desired)) {
            rc.buildRobot(RobotType.MINER, desired);
            this.miners += 1;
        }
    }
}