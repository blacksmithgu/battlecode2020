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

    /**
     * The number of soup representatives that we keep track.
     */
    public static int TRACKED_SOUP_COUNT = 3;

    /**
     * The distance a soup can be within the representative to be considered part of it's clusters.
     */
    public static int REPRESENTATIVE_THRESHOLD = 15;

    // Miner state for pathfinding to a location.
    private BugPathfinder pathfinder;
    private boolean roaming;

    // Contains up to TRACKED_SOUP_COUNT valid soup representatives we can visit.
    private MapLocation[] soupReps;

    // The location of the HQ we are dumping resources at.
    private MapLocation hqLocation;

    public Miner(int id) {
        super(id);
        this.pathfinder = null;
        this.roaming = true;
        this.soupReps = new MapLocation[TRACKED_SOUP_COUNT];
        this.hqLocation = null;
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException {
        // Check and clear representatives if they are no longer present.
        for (int i = 0; i < soupReps.length; i++) {
            MapLocation rep = soupReps[i];
            if (rep != null && rc.canSenseLocation(rep) && rc.senseSoup(rep) == 0) {
                soupReps[i] = null;
            }
        }

        // Soup memory; track recently seen soup clusters.
        int orig = rc.getRoundNum() * RobotType.MINER.bytecodeLimit + Clock.getBytecodeNum();
        Utils.traverseSensable(rc, loc -> {
            int soup = rc.senseSoup(loc);

            if (soup == 0) return;

            for (int i = 0; i < soupReps.length; i++) {
                if (soupReps[i] != null && loc.distanceSquaredTo(soupReps[i]) <= REPRESENTATIVE_THRESHOLD) {
                    return;
                }
            }

            // Try to designate as a representative. If there is a slot, fill it.
            for (int i = 0; i < soupReps.length; i++) {
                if(soupReps[i] == null) {
                    soupReps[i] = loc;
                    return;
                }
            }

            // Otherwise replace randomly.
            this.soupReps[this.rng.nextInt(TRACKED_SOUP_COUNT)] = loc;
        });
        int after = rc.getRoundNum() * RobotType.MINER.bytecodeLimit + Clock.getBytecodeNum();
        System.out.printf("%d -> %d (%d)\n", orig, after, after - orig);

        // Prioritize returning with soup.
        // TODO: Make decision call on whether to go to another vein or return to base.
        if (rc.getSoupCarrying() >= INVENTORY_RETURN_SIZE) {
            // Drop off to the HQ if we are adjacent to it.
            if (rc.getLocation().distanceSquaredTo(this.hqLocation) < 4) {
                rc.depositSoup(rc.getLocation().directionTo(this.hqLocation), rc.getSoupCarrying());
                this.pathfinder = null;
                return;
            }

            if (pathfinder == null) pathfinder = BugPathfinder.pathfindTo(this.hqLocation, this.rng.nextBoolean() ? BugPathfinder.FollowingDirection.LEFT : BugPathfinder.FollowingDirection.RIGHT);
            rc.setIndicatorDot(this.hqLocation, 255, 0, 0);
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
        MapLocation best = soupReps[0];
        int bestDist = Integer.MAX_VALUE;
        for (int i = 1; i < TRACKED_SOUP_COUNT; i++) {
            MapLocation sr = soupReps[i];
            if (sr == null) continue;

            int dist = sr.distanceSquaredTo(rc.getLocation());
            if (best == null || dist < bestDist) {
                best = sr;
                bestDist = dist;
            }
        }

        // TODO: This pathfinder state variable is BAAAAAAAAAAAD.
        if (best != null) {
            if (roaming) {
                this.pathfinder = null;
                this.roaming = false;
            }

            if (pathfinder == null) pathfinder = BugPathfinder.pathfindTo(best, this.rng.nextBoolean() ? BugPathfinder.FollowingDirection.LEFT : BugPathfinder.FollowingDirection.RIGHT);
            rc.setIndicatorDot(best, 255, 0, 0);
            Direction dir = pathfinder.findMove(rc.getLocation(), d -> rc.canMove(d));
            if (dir != Direction.CENTER) rc.move(dir);
            return;
        }

        // We don't know where soup is. Do we contemplate suicide?
        this.roaming = true;

        if (pathfinder == null) {
            // TODO: More intelligent target selection.
            MapLocation randomTarget = new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight()));

            pathfinder = BugPathfinder.pathfindTo(randomTarget, this.rng.nextBoolean() ? BugPathfinder.FollowingDirection.LEFT : BugPathfinder.FollowingDirection.RIGHT);
            rc.setIndicatorDot(randomTarget, 255, 0, 0);
        }

        Direction dir = pathfinder.findMove(rc.getLocation(), d -> rc.canMove(d));
        if (dir != Direction.CENTER) rc.move(dir);
    }

    @Override
    public void onCreation(RobotController rc) throws GameActionException {
        for (RobotInfo info : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (info.getType() == RobotType.HQ || info.getType() == RobotType.REFINERY) {
                this.hqLocation = info.getLocation();
                break;
            }
        }

        if (this.hqLocation == null) {
            throw new IllegalStateException("This miner has no home :(");
        }
    }
}