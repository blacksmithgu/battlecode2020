package steamlocomotive;

import battlecode.common.*;

import java.awt.*;

public strictfp class DeliveryDrone extends Unit {
    //TODO implement behavior where drones carry miners to unreachable soup (e.g. TwoForOneAndTwoForAll)
    //TODO implement interactions with cows (drowning them, dropping them on enemies, or both)
    //TODO make drones stay out of range of enemy net shooters and HQ

    public enum DroneState {
        // The drone looks for hapless victims.
        ROAMING,
        // The drone hunts its prey after sighting it.
        CHASING,
        // The drone gives its new friend a bath.
        DUNKING
    }

    private static class Transition {
        public DeliveryDrone.DroneState target;
        public boolean madeAction;

        public Transition(DeliveryDrone.DroneState target, boolean madeAction) {
            this.target = target;
            this.madeAction = madeAction;
        }
    }

    // The mode that the drone is currently in.
    private DeliveryDrone.DroneState state;
    // Pathfinder for going to a location;
    private BugPathfinder pathfinder;
    // The closest water we've seen for dunking.
    private MapLocation closestWater;
    // The closest enemy landscaper or miner that we've seen, for dunking
    private MapLocation closestEnemyLandUnit;
    // The closest cow that we've seen (that isn't already close to enemy HQ)
    private MapLocation closestCow;
    // The closest soup we've seen that seems inaccessible to friendly miners
    private MapLocation closestHardSoup;
    // The closest friendly miner that we've seen
    private MapLocation closestFriendlyMiner;
    // The elevation of the closest friendly miner
    private int closestFriendlyMinerElevation;
    // Our team's HQ location
    private MapLocation friendlyHQLoc;
    // Enemy team's HQ location
    private MapLocation enemyHQLoc;


    public DeliveryDrone(int id) {
        super(id);
        this.pathfinder = null;
        this.closestWater = null;
        this.closestEnemyLandUnit = null;
        this.closestCow = null;
        this.closestHardSoup = null;
        this.closestFriendlyMiner = null;
        this.closestFriendlyMinerElevation = 0;
        this.friendlyHQLoc = null;
        this.enemyHQLoc = null;
        this.state = DroneState.ROAMING;
    }

    public void run(RobotController rc, int turn) throws GameActionException {
        // Update water knowledge by scanning surroundings.
        this.scanSurroundings(rc);

        // Swap on current state.
        boolean madeAction;
        do {
            Transition trans;
            switch (this.state) {
                case CHASING: trans = this.chasing(rc); break;
                case DUNKING: trans = this.dunking(rc); break;
                default:
                case ROAMING: trans = this.roaming(rc); break;
            }

            // Reset transient state.
            if (this.state != trans.target) {
                this.pathfinder = null;
            }

            this.state = trans.target;
            madeAction = trans.madeAction;
        } while (!madeAction);

        // Useful for debugging.
        if (this.pathfinder != null) rc.setIndicatorLine(rc.getLocation(), this.pathfinder.goal(), 0, 255, 0);
    }

    public void scanSurroundings(RobotController rc) throws GameActionException {
        // Reset closest water if it's... unflooded.
        if (closestWater != null && rc.canSenseLocation(closestWater) && !rc.senseFlooding(closestWater)) {
            closestWater = null;
        }

        // If you're wondering why the weird array gimmick, it's so we can use this
        // inside the lambda. Unfortunate, yes.
        // TODO: Optimize this away by inlining traverse sensable.
        int[] waterDistance = new int[] { this.closestWater == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestWater) };
        int[] cowDistance = new int[] { this.closestCow == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestCow) };
        int[] enemyLandUnitDistance = new int[] { this.closestEnemyLandUnit == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestEnemyLandUnit) };
        int[] friendlyMinerDistance = new int[] { this.closestFriendlyMiner == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestFriendlyMiner) };
        int[] hardSoupDistance = new int[] { this.closestHardSoup == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(this.closestHardSoup) };

        // Scan the sensable area for water for some dunking/fun in the sun action.
        Utils.traverseSensable(rc, loc -> {
            // Update the closest water tile
            if (rc.senseFlooding(loc)) {
                int dist = loc.distanceSquaredTo(rc.getLocation());
                if (dist < waterDistance[0]) {
                    this.closestWater = loc;
                    waterDistance[0] = dist;
                }
            }
            else {
                //Update locations of robots and cows
                RobotInfo nearbyRobot = rc.senseRobotAtLocation(loc);
                if (nearbyRobot != null) {
                    if (nearbyRobot.type == RobotType.COW) {
                        int dist = loc.distanceSquaredTo(rc.getLocation());
                        if (dist < cowDistance[0]) {
                            this.closestCow = loc;
                            cowDistance[0] = dist;
                        }
                    }
                    else if (nearbyRobot.team != rc.getTeam()) {
                        if (nearbyRobot.type == RobotType.MINER || nearbyRobot.type == RobotType.LANDSCAPER) {
                            int dist = loc.distanceSquaredTo(rc.getLocation());
                            if (dist < enemyLandUnitDistance[0]) {
                                this.closestEnemyLandUnit = loc;
                                enemyLandUnitDistance[0] = dist;
                            }
                        }
                        else if (nearbyRobot.type == RobotType.HQ && this.enemyHQLoc == null) {
                            this.enemyHQLoc = nearbyRobot.location;
                        }
                    }
                    else if (nearbyRobot.type == RobotType.MINER) {
                        int dist = loc.distanceSquaredTo(rc.getLocation());
                        if (dist < friendlyMinerDistance[0]) {
                            this.closestFriendlyMiner = loc;
                            friendlyMinerDistance[0] = dist;
                            closestFriendlyMinerElevation = rc.senseElevation(loc);
                        }
                    }
                    else if (nearbyRobot.type == RobotType.HQ && this.friendlyHQLoc == null) {
                        this.friendlyHQLoc = nearbyRobot.location;
                    }
                }
                else if (rc.senseSoup(loc) > 0 && seemsInaccessible(rc, loc)) {
                    //TODO: Account for soup that is in water, but adjacent to land that's inaccessible to miners
                    int dist = loc.distanceSquaredTo(rc.getLocation());
                    if (dist < hardSoupDistance[0]) {
                        this.closestHardSoup = loc;
                        hardSoupDistance[0] = dist;
                    }
                }
            }

            //Update soup representatives


        });

//        if (closestHardSoup != null) {
//            System.out.println("Hard soup at " + closestHardSoup);
//        }

        //System.out.println("Closest friendly miner elevation is " + closestFriendlyMinerElevation);

        System.out.println(Clock.getBytecodesLeft() + "bytecodes left after scanning.");
        // TODO: Scan for soup with no nearby miners.
    }

    /** Implements roaming behavior, where the drone roams until it finds an enemy somewhere. */
    public Transition roaming(RobotController rc) throws GameActionException {
        // Check if carrying anything. If so, transition to dunking.
        if (rc.isCurrentlyHoldingUnit() && closestWater != null) {
            return new Transition(DroneState.DUNKING, false);
        }

        // Look for enemy robot. If see one and not currently holding a unit, transition to chasing.
        if (!rc.isCurrentlyHoldingUnit()) {
            RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            for (RobotInfo nearbyEnemy : enemyRobots) {
                if (nearbyEnemy.type.canBePickedUp()) {
                    return new Transition(DroneState.CHASING, false);
                }
            }
        }

        // If the pathfinder is inactive or finished, pick a new random location to pathfind to.
        if (this.pathfinder == null || this.pathfinder.finished(rc.getLocation())) {
            // TODO: More intelligent target selection. We choose randomly for now.
            // Suggestion: Drone remembers where it last picked enemy up and goes back. If nobody there, roam randomly.
            MapLocation target = new MapLocation(this.rng.nextInt(rc.getMapWidth()), this.rng.nextInt(rc.getMapHeight()));

            this.pathfinder = this.newPathfinder(target, true);
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.ROAMING, true);
    }

    /** Travel behavior, where a drone travels to a known water location to dunk. */
    public Transition dunking(RobotController rc) throws GameActionException {
        // If not carrying anything, transition to roaming.
        if (!rc.isCurrentlyHoldingUnit()) {
            return new Transition(DroneState.ROAMING, false);
        }

        // If we can dunk an enemy, immediately do so and go back to roaming to find more victims.
        for (Direction dir : Direction.allDirections()) {
            if (rc.canDropUnit(dir) && rc.senseFlooding(rc.getLocation().add(dir))) {
                rc.dropUnit(dir);
                return new Transition(DroneState.ROAMING, true);
            }
        }

        // If no pathfinder, create it to the closest water.
        if (this.pathfinder == null) {
            // If all water has been unflooded, cry a little and roam.
            if (closestWater == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closestWater, true);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.DUNKING, true);
    }

    public Transition chasing(RobotController rc) throws GameActionException {
        MapLocation droneLoc = rc.getLocation();

        // Check if carrying anything. If so, transition to dunking.
        if (rc.isCurrentlyHoldingUnit() && closestWater != null) {
            return new Transition(DroneState.DUNKING, false);
        }

        // Look for enemy robot. If see one, identify closest unit that can be picked up and move towards it.
        // If can already pick up unit, do so and transition to dunking.
        if (!rc.isCurrentlyHoldingUnit()) {
            Utils.ClosestRobot closest = Utils.closestRobot(rc, robot -> robot.type.canBePickedUp(), rc.getTeam().opponent());

            // If no close, swap back to roaming.
            if (closest.robot == null) return new Transition(DroneState.ROAMING, false);

            // Pick it up if adjacent.
            if (rc.canPickUpUnit(closest.robot.getID())) {
                rc.pickUpUnit(closest.robot.getID());
                return new Transition(DroneState.DUNKING, true);
            }

            // Otherwise path towards it.
            // TODO: Implement better chasing movement.
            Direction straightToClosest = droneLoc.directionTo(closest.robot.getLocation());
            if (rc.canMove(straightToClosest)) {
                rc.move(straightToClosest);
                return new Transition(DroneState.CHASING, true);
            } else if (rc.canMove(straightToClosest.rotateLeft())) {
                rc.move(straightToClosest.rotateLeft());
                return new Transition(DroneState.CHASING, true);
            } else if (rc.canMove(straightToClosest.rotateRight())) {
                rc.move(straightToClosest.rotateRight());
                return new Transition(DroneState.CHASING, true);
            } else {
                for (Direction adj : Direction.allDirections()) {
                    if (adj == Direction.CENTER) continue;
                    if (rc.canMove(adj)) {
                        rc.move(adj);
                        return new Transition(DroneState.CHASING, true);
                    }
                }
            }
        }

        // No unit to chase; go to roaming and hope things work out.
        return new Transition(DroneState.ROAMING,false);
    }

    public boolean seemsInaccessible(RobotController rc, MapLocation loc) throws GameActionException {
        //Returns true iff loc contains soup and it seems like miners may need help getting to it
        //TODO:  Write this function
        return true;
    }

}