package steamlocomotive;

import battlecode.common.*;

import java.util.Random;

/** General utilities for writing agents. */
public class Utils {

    @FunctionalInterface
    public interface GameConsumer<T> {
        void accept(T value) throws GameActionException;
    }

    @FunctionalInterface
    public interface GamePredicate<T> {
        boolean check(T value) throws GameActionException;
    }

    /** Returns true if the given direction is cardinal. 1 bytecode. Get wrecked. */
    public static boolean isCardinal(Direction dir) {
        switch (dir) {
            case NORTH:
            case SOUTH:
            case EAST:
            case WEST:
                return true;
            default:
                return false;
        }
    }

    /** Run the consumer function on every sensable tile. */
    public static void traverseSensable(RobotController rc, GameConsumer<MapLocation> func) throws GameActionException {
        int visionRadius = (int) Math.ceil(Math.sqrt(rc.getType().sensorRadiusSquared));
        int ourX = rc.getLocation().x;
        int ourY = rc.getLocation().y;

        for (int x = -visionRadius; x <= visionRadius; x++) {
            for (int y = -visionRadius; y <= visionRadius; y++) {
                MapLocation location = new MapLocation(ourX + x, ourY + y);
                if (rc.canSenseLocation(location)) {
                    func.accept(location);
                }
            }
        }
    }

    /** Find the closest unit of the given type. The location will be null if there is no robot in sensor range. */
    public static ClosestRobot closestRobot(RobotController rc, RobotType type, Team team) {
        RobotInfo best = null;
        int bestDistance = Integer.MAX_VALUE;

        MapLocation us = rc.getLocation();
        for (RobotInfo robot : rc.senseNearbyRobots(-1, team)) {
            if (robot.type != type) continue;

            int dist = us.distanceSquaredTo(robot.getLocation());
            if (dist < bestDistance) {
                bestDistance = dist;
                best = robot;
            }
        }

        return new ClosestRobot(best, bestDistance);
    }

    /** Find the closest robot which obeys the given predicate. */
    public static ClosestRobot closestRobot(RobotController rc, GamePredicate<RobotInfo> pred, Team team)
        throws GameActionException {
        RobotInfo best = null;
        int bestDistance = Integer.MAX_VALUE;

        MapLocation us = rc.getLocation();
        for (RobotInfo robot : rc.senseNearbyRobots(-1, team)) {
            if (!pred.check(robot)) continue;

            int dist = us.distanceSquaredTo(robot.getLocation());
            if (dist < bestDistance) {
                bestDistance = dist;
                best = robot;
            }
        }

        return new ClosestRobot(best, bestDistance);
    }

    public static class ClosestRobot {
        public RobotInfo robot;
        public int distance;

        public ClosestRobot(RobotInfo robot, int distance) {
            this.robot = robot;
            this.distance = distance;
        }
    }

    /** Clusters groups of resources (water, soup) for memory/time efficient tracking. */
    public static class Clusterer {
        // The cluster representatives.
        private MapLocation[] clusters;
        // The min squared distance between clusters.
        private int radius;

        public Clusterer(int count, int radius) {
            this.clusters = new MapLocation[count];
            this.radius = radius;
        }

        /** Find the closest cluster representative to the given location. */
        public MapLocation closest(MapLocation loc) {
            int bestDistance = Integer.MAX_VALUE;
            MapLocation best = null;
            for (int i = 0; i < clusters.length; i++) {
                MapLocation clust = clusters[i];
                if (clust == null) continue;

                int dist = clust.distanceSquaredTo(loc);
                if (dist < bestDistance) {
                    bestDistance = dist;
                    best = clust;
                }
            }

            return best;
        }

        /** Given a map location which should be included in a cluster, update the cluster state. */
        public void update(RobotController rc, MapLocation loc, Random rng) {
            int locToUs = rc.getLocation().distanceSquaredTo(loc);
            int closest = -1;
            for (int i = 0; i < clusters.length; i++) {
                if (clusters[i] == null) continue;
                int clusterToLoc = clusters[i].distanceSquaredTo(loc);
                int clusterToUs = clusters[i].distanceSquaredTo(rc.getLocation());

                // If this is part of a cluster, but is closer than the rep, swap.
                if (clusterToLoc < this.radius) {
                    closest = i;
                    if (locToUs < clusterToUs) clusters[i] = loc;
                    break;
                }
            }

            // Not part of any cluster, swap out at random.
            if (closest == -1) clusters[rng.nextInt(clusters.length)] = loc;
        }

        /** Return true if this clusterer is tracking any clusters. */
        public boolean hasCluster() {
            for (int i = 0; i < this.clusters.length; i++) {
                if (clusters[i] != null) return true;
            }

            return false;
        }

        /** Clear now-invalid tiles according to the predicate. */
        public void clearInvalid(RobotController rc, GamePredicate<MapLocation> pred) throws GameActionException {
            for (int i = 0; i < clusters.length; i++) {
                if (clusters[i] == null) continue;
                if (!pred.check(clusters[i])) clusters[i] = null;
            }
        }
    }
}
