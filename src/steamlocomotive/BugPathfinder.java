package steamlocomotive;

import battlecode.common.Direction;
import battlecode.common.MapLocation;

import java.util.function.Function;

/**
 * Implements bug pathfinding vaguely according to https://www.cs.cmu.edu/~motionplanning/lecture/Chap2-Bug-Alg_howie.pdf
 * and Niels' wisdom.
 *
 * @author niels
 * @author msbrenan
 */
public class BugPathfinder {

    /** The direction that the pathfinder is following an obstacle. */
    public enum FollowingDirection {
        LEFT, RIGHT;

        public Direction alongWall(Direction dir) {
            switch (this) {
                case LEFT: return dir.rotateRight();
                case RIGHT: return dir.rotateLeft();
                default: return dir;
            }
        }

        public Direction againstWall(Direction dir) {
            switch (this) {
                case LEFT: return dir.rotateLeft();
                case RIGHT: return dir.rotateRight();
                default: return dir;
            }
        }
    }

    /** Create a bug pathfinder pathfinding to the given location. */
    public static BugPathfinder pathfindTo(MapLocation goal, FollowingDirection preferred, boolean allowAdjacent) {
        return new BugPathfinder(goal, preferred, allowAdjacent);
    }

    // The goal location we want to get close to.
    private final MapLocation goal;

    // The direction we are following obstacles.
    private FollowingDirection followDirection;
    // If we are currently following an obstacle.
    private boolean following;
    // The heading we are following the obstacle along.
    private Direction heading;
    // The distance we collided the obstacle at.
    private int obstacleDistance;
    /// If true, then we can just pathfind to an adjacent tile to the goal.
    private boolean allowAdjacent;

    private BugPathfinder(MapLocation goal, FollowingDirection preferredDirection, boolean allowAdjacent) {
        this.goal = goal;
        this.followDirection = preferredDirection;
        this.following = false;
        this.obstacleDistance = -1;
        this.allowAdjacent = allowAdjacent;
    }

    /** Return the goal we are pathfinding towards. */
    public MapLocation goal() { return goal; }

    public boolean finished(MapLocation loc) {
        return loc.equals(this.goal) || (allowAdjacent && loc.isAdjacentTo(this.goal));
    }

    /**
     * Find the move towards the goal state given the current location and a function for determining if a tile is walkable.
     * Returns Direction.CENTER if no further actions are necessary, or null if no actions are currently available.
     */
    public Direction findMove(MapLocation loc, Function<Direction, Boolean> walkable) {
        // Already found the goal dummy.
        if (this.finished(loc)) return Direction.CENTER;

        // See if we can just directly move in the direction of the goal.
        Direction direct = loc.directionTo(this.goal);
        if (!this.following && walkable.apply(direct)) {
            return direct;
        }

        // Otherwise, we can't directly go; if we aren't following, then we hit an obstacle, so start following.
        if (!this.following) {
            this.following = true;
            this.obstacleDistance = loc.distanceSquaredTo(this.goal);
            this.heading = direct;
        }

        // Need to follow the wall.
        // If we can directly move towards goal and we are closer to goal, then do that and reset obstacle.
        if (loc.distanceSquaredTo(this.goal) <= this.obstacleDistance && walkable.apply(direct)) {
            this.following = false;
            return direct;
        }

        // Check if we can follow the obstacle corner closer to the goal.
        Direction desired = this.followDirection.alongWall(this.heading);
        if (walkable.apply(desired)) {
            this.heading = desired;
            return desired;
        }

        // We can't, rotate against the wall until we can go.
        Direction starting = this.heading;
        while (!walkable.apply(this.heading)) {
            this.heading = this.followDirection.againstWall(this.heading);
            // We're surrounded, give up for now and try not to die.
            if (this.heading == starting) return null;
        }

        return this.heading;
    }
}
