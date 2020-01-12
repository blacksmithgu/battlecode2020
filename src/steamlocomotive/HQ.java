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

    // The HQ map state knowledge.
    private MapState map;

    public HQ(int id) {
        super(id);
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        map.update(rc);

        // The HQ does nothing if we are too poor :(
        if (rc.getTeamSoup() < RobotType.MINER.cost) return;

        // Look at all of the soup locations, create a list, and send a miner to a random soup location.
        // TODO: Cache and update every X rounds in the future.
        List<MapLocation> soupLocations = new ArrayList<>();

        // TODO: May consider soups from the whole map.
        Utils.traverseSensable(rc, loc -> {
            if (!map.soup().containsKey(loc)) return;
            if (map.soup().get(loc) > 0) soupLocations.add(loc);
        });

        // Randomly choose a location to send a miner off too to die.
        MapLocation target = soupLocations.get(this.rng.nextInt(soupLocations.size()));
        Direction desired = rc.getLocation().directionTo(target);

        for (int c = 0; c < 8 && !rc.canBuildRobot(RobotType.MINER, desired); c++) {
            desired = desired.rotateRight();
        }

        if (rc.canBuildRobot(RobotType.MINER, desired)) {
            rc.buildRobot(RobotType.MINER, desired);
        }
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        this.map = new MapState(rc.getMapWidth(), rc.getMapHeight());
    }
}