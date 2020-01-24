package steamlocomotive;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

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
        CLOCKWISE, COUNTERCLOCKWISE;

        public Direction alongWall(Direction dir) {
            switch (this) {
                case CLOCKWISE: return dir.rotateRight();
                case COUNTERCLOCKWISE: return dir.rotateLeft();
                default: return dir;
            }
        }

        public Direction againstWall(Direction dir) {
            switch (this) {
                case CLOCKWISE: return dir.rotateLeft();
                case COUNTERCLOCKWISE: return dir.rotateRight();
                default: return dir;
            }
        }
    }

    /** Create a bug pathfinder pathfinding to the given location. */
    public static BugPathfinder pathfindTo(MapLocation goal, FollowingDirection preferred, boolean allowAdjacent) {
        return new BugPathfinder(goal, preferred, allowAdjacent);
    }

    /** Can move wrapper which also checks for flooding. */
    public static boolean canMoveF(RobotController rc, Direction dir) {
        MapLocation target = rc.getLocation().add(dir);
        boolean flooded = false;
        // TODO: If the soup is adjacent to land, we can still mine it.
        try {
            flooded = rc.senseFlooding(target);
        } catch (GameActionException ex) { }

        return rc.canMove(dir) && !flooded;
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
    // If true, then we can just pathfind to an adjacent tile to the goal.
    private boolean allowAdjacent;
    // List of locations that we have visited along a wall.
    private DynamicArray<LocationAndDirection> wallPerimeter;

    private class LocationAndDirection {
        MapLocation location;
        Direction heading;

        public LocationAndDirection(MapLocation loc, Direction heading) {
            this.location = loc;
            this.heading = heading;
        }

        @Override
        public int hashCode() {
            return location.hashCode() ^ heading.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if(other == null) {
                return false;
            }
            if(other instanceof LocationAndDirection) {
                LocationAndDirection loc = (LocationAndDirection) other;
                return this.heading == loc.heading && this.location.x == loc.location.x && this.location.y == loc.location.y;
            }
            return false;
        }
    }

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
    public Direction findMove(MapLocation loc, Utils.GameFunction<Direction, Boolean> walkable) throws GameActionException {
        // Already found the goal dummy.
        if (this.finished(loc)) return Direction.CENTER;

        // Check if the wall is still around - could have been a unit!
        // The wall is orthogonal to the heading based on our follow direction.
        if (this.following) {
            if(wallPerimeter.contains(new LocationAndDirection(loc, heading))) {
                this.following = false;
            } else {
                wallPerimeter.add(new LocationAndDirection(loc, heading));
                Direction toWall = this.followDirection.alongWall(this.followDirection.alongWall(this.heading));
                if (walkable.apply(toWall)) {
                    this.following = false;
                }
            }
        }

        // See if we can just directly move in the direction of the goal.
        Direction direct = loc.directionTo(this.goal);
        if (!this.following && walkable.apply(direct)) {
            return direct;
        }

        // Otherwise, we can't directly go; if we aren't following, then we hit an obstacle, so start following.
        if (!this.following) {
            this.following = true;
            this.wallPerimeter = new DynamicArray<>(30);
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
            this.heading = this.followDirection.alongWall(desired);
            return desired;
        }

        // We can't, rotate against the wall until we can go.
        Direction cuttingMove = this.heading;
        while (!walkable.apply(cuttingMove)) {
            cuttingMove = this.followDirection.againstWall(cuttingMove);
            // We're surrounded, give up for now and try not to die.
            if (cuttingMove == this.heading) return null;
        }

        if (Utils.isCardinal(cuttingMove)) {
            this.heading = cuttingMove;
        } else {
            this.heading = this.followDirection.alongWall(cuttingMove);
        }

        return cuttingMove;
    }
}
