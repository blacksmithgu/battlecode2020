package steamlocomotive;

import battlecode.common.*;

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
    // Contains up to TRACKED_WATER_COUNT valid water representatives we can visit.
    private MapLocation[] waterReps;

    public DeliveryDrone(int id) {
        super(id);
        this.pathfinder = null;
        this.waterReps = new MapLocation[Config.TRACKED_SOUP_COUNT];
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
        // Water memory; track recently seen water.
        Utils.traverseSensable(rc, loc -> {
            boolean isFlooding = rc.senseFlooding(loc);


            if (!isFlooding) {
                //If a water representative has become unflooded, get rid of it.
                for (int i = 0; i < waterReps.length; i++) {
                    if(waterReps[i] == loc) {
                        waterReps[i] = null;
                    }
                }

                //Update soup representatives
                //Update cow representatives
                //Update enemy representatives?
                //Update friendly miner representatives
                //Update friendly refinery locations

                //Update friendly HQ location
                //Update enemy HQ location
                return;
            }

           updateWaterReps(rc, loc);
        });
    }

    /** Implements roaming behavior, where the drone roams until it finds an enemy somewhere. */
    public Transition roaming(RobotController rc) throws GameActionException {
        boolean isWaterToGoTo = false;
        for (MapLocation loc : waterReps) {
            if (loc != null) {
                isWaterToGoTo = true;
                break;
            }
        }

        // Check if carrying anything. If so, transition to dunking.
        if (rc.isCurrentlyHoldingUnit() && isWaterToGoTo) {
            return new Transition(DroneState.DUNKING, false);
        }

        //Look for enemy robot. If see one and not currently holding a unit, transition to chasing.
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
        //If not carrying anything, transition to roaming.
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
            MapLocation closest = waterReps[0];
            int bestDistance = waterReps[0] == null ? Integer.MAX_VALUE : closest.distanceSquaredTo(rc.getLocation());
            for (int i = 1; i < waterReps.length; i++) {
                if (waterReps[i] == null) continue;
                int dist = waterReps[i].distanceSquaredTo(rc.getLocation());
                if (dist >= bestDistance) continue;

                closest = waterReps[i];
                bestDistance = dist;
            }

            // If all water has been unflooded, cry a little and roam.
            if (closest == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closest, true);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.DUNKING, true);
    }

    public Transition chasing(RobotController rc) throws GameActionException {
        boolean isWaterToGoTo = false;
        for (MapLocation loc : waterReps) {
            if (loc != null) {
                isWaterToGoTo = true;
                break;
            }
        }
        MapLocation droneLoc = rc.getLocation();

        //Check if carrying anything. If so, transition to dunking.
        if (rc.isCurrentlyHoldingUnit() && isWaterToGoTo) {
            return new Transition(DroneState.DUNKING, false);
        }

        //Look for enemy robot. If see one, identify closest unit that can be picked up and move towards it.
        //If can already pick up unit, do so and transition to dunking.
        //First, identify map location of closest enemy unit that can be picked up
        MapLocation closestEnemyLoc = droneLoc;
        int closestEnemyDist = 500;
        if (!rc.isCurrentlyHoldingUnit()) {
            RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            for (RobotInfo nearbyEnemy : enemyRobots) {
                if (nearbyEnemy.type.canBePickedUp()) {
                    if (rc.canPickUpUnit(nearbyEnemy.ID)) {
                        rc.pickUpUnit(nearbyEnemy.ID);
                        return new Transition(DroneState.DUNKING, true);
                    }
                    int enemyDist = droneLoc.distanceSquaredTo(nearbyEnemy.location);
                    if (enemyDist < closestEnemyDist) {
                        closestEnemyDist = enemyDist;
                        closestEnemyLoc = nearbyEnemy.location;
                    }
                }
            }
        }

        // This if statement shouldn't trigger.
        // If something goes wrong finding a nearby enemy's location, this should trigger and send the drone north.
        if (closestEnemyDist == 500) {
            closestEnemyLoc = droneLoc.add(Direction.NORTH);
        }

        // TODO: Implement better chasing movement
        
        // Drone tries to move directly towards target. If it can't, moves the first direction it can.
        Direction straightToClosest = droneLoc.directionTo(closestEnemyLoc);
        if (rc.canMove(straightToClosest)) {
            rc.move(straightToClosest);
            return new Transition(DroneState.CHASING, true);
        } else if (rc.canMove(straightToClosest.rotateLeft())) {
            rc.move(straightToClosest.rotateLeft());
            return new Transition(DroneState.CHASING, true);
        } else if (rc.canMove(straightToClosest.rotateRight())) {
            rc.move(straightToClosest.rotateRight());
            return new Transition(DroneState.CHASING, true);
        }else {
            for (Direction adj : Direction.allDirections()) {
                if (adj == Direction.CENTER) continue;
                if (rc.canMove(adj)) {
                    rc.move(adj);
                    return new Transition(DroneState.CHASING, true);
                }
            }
        }

        // If we get to here, something is wrong. Go to roaming and hope things work out.
        return new Transition(DroneState.ROAMING,false);
    }

    public void updateWaterReps(RobotController rc, MapLocation loc) {
        //Updates the waterReps list
        //Assumes that the location loc it receives is flooded.
        genericRepLocUpdate(rc, loc, waterReps);
        return;
    }

    public void genericRepLocUpdate(RobotController rc, MapLocation loc, MapLocation[] reps) {
        //Can be used to update any representative list
        //Takes as input a location loc **WHICH THIS FUNCTION ASSUMES HAS THE APPROPRIATE THING IN IT**
        //And the list of representatives reps to be updated
        // Ignore location which is already close to a representative.
        for (int i = 0; i < reps.length; i++) {
            MapLocation rep = reps[i];
            if (rep != null && loc.distanceSquaredTo(rep) <= Config.REPRESENTATIVE_THRESHOLD) {
                return;
            }
        }

        // Try to designate as a representative. If there is a slot, fill it.
        for (int i = 0; i < reps.length; i++) {
            if(reps[i] == null) {
                reps[i] = loc;
                return;
            }
        }

        // Otherwise replace randomly.
        reps[this.rng.nextInt(Config.TRACKED_SOUP_COUNT)] = loc;
        return;
    }
}