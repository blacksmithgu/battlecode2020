package steamlocomotive;

import battlecode.common.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stateful map representation which describes this units understanding of the map, including HQ location,
 * current pathfinding objective (if any), soup and elevation information.
 */
public class MapState {

    /**
     * Stores information about a specific tile on the map.
     */
    public static class MapTile {
        public int soup;
        public int lastSeen;
        public int elevation;
        public boolean flooded;

        public MapTile() {
            this.soup = this.lastSeen = this.elevation = -1;
            this.flooded = false;
        }
    }

    // Location of the HQ. This is always right, if it is set.
    // TODO: This can be set with an early blockchain message.
    private MapLocation hqLocation;

    // Width and height of the map.
    private int width, height;

    // Known locations and amounts of soup.
    private final Map<MapLocation, Integer> soup;

    // Per-tile information. Stored as a linear array indexed by width*y + x.
    private final MapTile[] tiles;

    // TODO: Add building locations, these won't change often.

    // TODO: Track last seen robot locations.

    /** Obtain the location of the HQ. */
    public MapLocation hq() { return hqLocation; }

    public Map<MapLocation, Integer> soup() { return soup; }

    public MapTile tile(MapLocation loc) {
        if (loc.x < 0 || loc.x >= width || loc.y < 0 || loc.y >= this.height) return null;

        return this.tiles[loc.y * width + loc.x];
    }

    // TODO: Consider adding back width/height.
    public MapState(int width, int height) {
        this.hqLocation = null;
        this.soup = new HashMap<>();
        this.tiles = new MapTile[width * height];
    }

    /**
     * Update the pathfinder state by actively sensing the area around the robot.
     */
    public void update(RobotController rc) throws GameActionException {
        // Look for the HQ if we don't know where it is :(
        if (hqLocation == null) {
            for (RobotInfo info : rc.senseNearbyRobots()) {
                if (info.getTeam().isPlayer() && info.getType() == RobotType.HQ) {
                    hqLocation = info.getLocation();
                    break;
                }
            }
        }

        // Sense updated soup, elevation, and flood data.
        final int roundNum = rc.getRoundNum();
        Utils.traverseSensable(rc, loc -> {
            int index = this.width * loc.y + loc.x;
            MapTile tile = tiles[index];
            if (tile == null) {
                tile = tiles[index] = new MapTile();
            }

            tile.lastSeen = roundNum;
            tile.flooded = rc.senseFlooding(loc);
            tile.soup = rc.senseSoup(loc);
            tile.elevation = rc.senseElevation(loc);

            if (soup.containsKey(loc) && tile.soup == 0) {
                soup.remove(loc);
            } else if (tile.soup > 0) {
                soup.put(loc, tile.soup);
            }
        });
    }
}
