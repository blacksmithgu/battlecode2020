package steamlocomotive;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.List;

public class Miner extends Unit {
    /**
     * The list of units a miner is allowed to spawn.
     **/
    public static RobotType[] SPAWNABLE_UNITS = {
            RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    /**
     * The amount of soup in inventory before the miner should return.
     */
    public static int INVENTORY_RETURN_SIZE = (RobotType.MINER.soupLimit / GameConstants.SOUP_MINING_RATE) * GameConstants.SOUP_MINING_RATE;

    private MapState map;
    private BugPathfinder pathfinder;
    private boolean roaming;

    public Miner(int id) {
        super(id);
        this.pathfinder = null;
        this.roaming = true;
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        map.update(rc);

        // Prioritize returning with soup.
        // TODO: Make decision call on whether to go to another vein or return to base.
        if (rc.getSoupCarrying() >= INVENTORY_RETURN_SIZE) {
            // Drop off to the HQ if we are adjacent to it.
            if (rc.getLocation().distanceSquaredTo(map.hq()) < 4) {
                rc.depositSoup(rc.getLocation().directionTo(map.hq()), rc.getSoupCarrying());
                this.pathfinder = null;
                return;
            }

            if (pathfinder == null) pathfinder = BugPathfinder.pathfindTo(map.hq(), this.rng.nextBoolean() ? BugPathfinder.FollowingDirection.LEFT : BugPathfinder.FollowingDirection.RIGHT);
            Direction dir = pathfinder.findMove(rc.getLocation(), d -> rc.canMove(d));
            if (dir != Direction.CENTER) rc.move(dir);
            return;
        }

        // If we can mine, then MINE.
        List<Direction> soupDirs = new ArrayList<>();
        for (Direction dir : Direction.allDirections()) {
            if (rc.canMineSoup(dir)) soupDirs.add(dir);
        }

        if (soupDirs.size() > 0) {
            rc.mineSoup(soupDirs.get(this.rng.nextInt(soupDirs.size())));
            this.pathfinder = null;
            return;
        }

        // No mining, no returning to base. Do we know where soup is?
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapLocation loc : map.soup().keySet()) {
            if (best == null || loc.distanceSquaredTo(rc.getLocation()) < bestDist) {
                best = loc;
                bestDist = loc.distanceSquaredTo(rc.getLocation());
            }
        }

        // TODO: This pathfinder state variable is BAAAAAAAAAAAD.
        if (best != null) {
            if (roaming) {
                this.pathfinder = null;
                this.roaming = false;
            }

            if (pathfinder == null) pathfinder = BugPathfinder.pathfindTo(best, this.rng.nextBoolean() ? BugPathfinder.FollowingDirection.LEFT : BugPathfinder.FollowingDirection.RIGHT);
            Direction dir = pathfinder.findMove(rc.getLocation(), d -> rc.canMove(d));
            if (dir != Direction.CENTER) rc.move(dir);
            return;
        }

        // We don't know where soup is. Do we contemplate suicide?
        this.roaming = true;

        if (pathfinder == null) {
            MapLocation randomTarget = null;
            for (int t = 0; t < 10; t++) {
                randomTarget = new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight()));
                if (map.tile(randomTarget) != null) continue;
            }

            pathfinder = BugPathfinder.pathfindTo(randomTarget, this.rng.nextBoolean() ? BugPathfinder.FollowingDirection.LEFT : BugPathfinder.FollowingDirection.RIGHT);
        }

        Direction dir = pathfinder.findMove(rc.getLocation(), d -> rc.canMove(d));
        if (dir != Direction.CENTER) rc.move(dir);
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        this.map = new MapState(rc.getMapWidth(), rc.getMapHeight());
    }
}