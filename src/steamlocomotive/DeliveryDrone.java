package steamlocomotive;

import battlecode.common.*;

import java.awt.*;

public strictfp class DeliveryDrone extends Unit {
    //TODO implement behavior where drones carry miners to unreachable soup (e.g. TwoForOneAndTwoForAll)
    //TODO implement interactions with cows (drowning them, dropping them on enemies, or both)
    //TODO make drones stay out of range of enemy net shooters and HQ
    //TODO: after dunking enemy unit, go to closestEnemyLandUnit to find another

    public enum DroneState {
        // The drone looks for hapless victims.
        ROAMING,
        // The drone hunts its prey after sighting it.
        CHASING,
        // The drone gives its new friend a bath.
        DUNKING,
        // The drone finds a friendly miner to put on soup
        FINDING_MINER,
        // The drone puts a friendly miner on top of hard-to-reach soup
        FERRYING_MINER,
        // The drone looks for an enemy that it saw earlier
        FINDING_ENEMY
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

        System.out.println("Started turn in state " + this.state);
        // Swap on current state.
        boolean madeAction;
        do {
            Transition trans;
            switch (this.state) {
                case CHASING: trans = this.chasing(rc); break;
                case DUNKING: trans = this.dunking(rc); break;
                case FINDING_MINER: trans = this.findingMiner(rc); break;
                case FERRYING_MINER: trans = this.ferryingMiner(rc); break;
                case FINDING_ENEMY: trans = this.findingEnemy(rc); break;
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

        //Reset closestFriendlyMiner to null if it's not there anymore
        if (closestFriendlyMiner != null && rc.canSenseLocation(closestFriendlyMiner)) {
            RobotInfo shouldBeMiner = rc.senseRobotAtLocation(closestFriendlyMiner);
            if (shouldBeMiner == null) {
                closestFriendlyMiner = null;
            }
            else if (shouldBeMiner.type != RobotType.MINER || shouldBeMiner.team != rc.getTeam()) {
                closestFriendlyMiner = null;
            }
        }

        //Reset closestEnemyLandUnit to null if outdated
        if (closestEnemyLandUnit != null && rc.canSenseLocation(closestEnemyLandUnit)) {
            RobotInfo shouldBeEnemy = rc.senseRobotAtLocation(closestEnemyLandUnit);
            if (shouldBeEnemy == null) {
                closestEnemyLandUnit = null;
            }
            else if (shouldBeEnemy.team == rc.getTeam()) {
                closestEnemyLandUnit = null;
            }
            else if (shouldBeEnemy.type != RobotType.MINER || shouldBeEnemy.type != RobotType.LANDSCAPER) {
                closestEnemyLandUnit = null;
            }
        }

        //Reset closestCow to null if outdated
        if (closestCow != null && rc.canSenseLocation(closestCow)) {
            RobotInfo shouldBeCow = rc.senseRobotAtLocation(closestCow);
            if (shouldBeCow == null) {
                closestCow = null;
            }
            else if (shouldBeCow.type != RobotType.COW) {
                closestCow = null;
            }
        }

        //Reset closest soup to null if there's no longer soup
        if (closestHardSoup != null && rc.canSenseLocation(closestHardSoup)) {
            if (rc.senseSoup(closestHardSoup) == 0) {
                closestHardSoup = null;
            }
        }

        //TODO:  Also reset soup when appropriate

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

        //If there's hard-to-reach soup, and not currently carrying anything, transition to ferrying a miner
        if (closestHardSoup != null && closestFriendlyMiner != null && !rc.isCurrentlyHoldingUnit()) {
            if (closestFriendlyMiner.distanceSquaredTo(closestHardSoup) >= 18) {
                return new Transition(DroneState.FINDING_MINER, false);
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
                if (closestEnemyLandUnit != null) {
                    return new Transition(DroneState.FINDING_ENEMY, true);
                }
                else {
                    return new Transition(DroneState.ROAMING, true);
                }
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

            // If not close, swap back to roaming.
            if (closest.robot == null) return new Transition(DroneState.ROAMING, false);

            // Pick it up if adjacent.
            if (rc.canPickUpUnit(closest.robot.getID())) {
                rc.pickUpUnit(closest.robot.getID());
                return new Transition(DroneState.DUNKING, true);
            }

            // Otherwise move towards it.
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

    public Transition findingMiner(RobotController rc) throws GameActionException {
        // If somehow holding a unit, dunk it
        // TODO: Be very very sure we won't dunk our own units
        if (rc.isCurrentlyHoldingUnit()) {
            return new Transition(DroneState.DUNKING, false);
        }

        //It's possible that the miner we're looking for has disappeared. In that case, go back to roaming.
        if (closestFriendlyMiner == null) {
            return new Transition(DroneState.ROAMING,false);
        }

        //TODO:  If miner is already very close (adjacent) to soup, then don't pick it up

        // If adjacent to a friendly miner, pick it up
        if (rc.getLocation().isAdjacentTo(closestFriendlyMiner)) {
            RobotInfo targetMinerInfo = rc.senseRobotAtLocation(closestFriendlyMiner);
            if (targetMinerInfo == null) {
                return new Transition(DroneState.ROAMING, false);
            }
            Direction onMinerDirection = rc.getLocation().directionTo(closestFriendlyMiner);
            if (rc.canPickUpUnit(targetMinerInfo.ID)) {
                rc.pickUpUnit(targetMinerInfo.ID);
                return new Transition(DroneState.FERRYING_MINER, true);
            }
        }

        // If no pathfinder, create it to the closest friendly miner.
        if (this.pathfinder == null) {
            // If we haven't seen any friendly miners, cry a little and roam.
            if (closestFriendlyMiner == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closestFriendlyMiner, true);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER) rc.move(move);


        return new Transition(DroneState.FINDING_MINER, true);
    }

    public Transition ferryingMiner(RobotController rc) throws GameActionException {
        // If not carrying anything, transition to roaming.
        if (!rc.isCurrentlyHoldingUnit()) {
            return new Transition(DroneState.ROAMING, false);
        }

        // If something has happened to our destination, drop the miner
        if (closestHardSoup == null) {
            for (Direction adj : Direction.allDirections()) {
                if (adj == Direction.CENTER) continue;
                if (!rc.senseFlooding(rc.getLocation().add(adj)) && rc.canDropUnit(adj)) {
                    rc.dropUnit(adj);
                    return new Transition(DroneState.ROAMING, true);
                }
            }
        }

        // If we can drop miner on soup location, immediately do so and go back to roaming
        if (rc.getLocation().isAdjacentTo(closestHardSoup)) {
            Direction onSoupDirection = rc.getLocation().directionTo(closestHardSoup);
            if (rc.canDropUnit(rc.getLocation().directionTo(closestHardSoup))) {
                rc.dropUnit(onSoupDirection);
                return new Transition(DroneState.ROAMING, true);
            }
            //If can't drop it directly on the tile, drop miner on any non-flooded tile
            for (Direction adj : Direction.allDirections()) {
                if (adj == Direction.CENTER) continue;
                if (!rc.senseFlooding(rc.getLocation().add(adj)) && rc.canDropUnit(adj)) {
                    rc.dropUnit(adj);
                    return new Transition(DroneState.ROAMING, true);
                }
            }
        }

        // If no pathfinder, create it to the closest hard-to-reach soup.
        if (this.pathfinder == null) {
            // If all hard soup is gone, cry a little and roam.
            if (closestHardSoup == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closestHardSoup, true);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.FERRYING_MINER, true);
    }

    public Transition findingEnemy(RobotController rc) throws GameActionException {
        // If carrying anything, transition to dunking.
        // This shouldn't happen, but it's here just in case
        if (rc.isCurrentlyHoldingUnit()) {
            return new Transition(DroneState.DUNKING, false);
        }

        // If there's no longer an enemy where we thought there was, give up the hunt and start roaming.
        if (closestEnemyLandUnit == null) {
            return new Transition(DroneState.ROAMING, false);
        }

        // If we see an enemy, transition to chasing it
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo nearbyEnemy : enemyRobots) {
            if (nearbyEnemy.type.canBePickedUp()) {
                return new Transition(DroneState.CHASING, false);
            }
        }

        // If no pathfinder, create it to the closest enemy.
        if (this.pathfinder == null) {
            // If don't know where an enemy is, cry a little and roam.
            if (closestEnemyLandUnit == null) {
                return new Transition(DroneState.ROAMING, false);
            } else {
                this.pathfinder = this.newPathfinder(closestEnemyLandUnit, true);
            }
        }

        // Obtain a movement from the pathfinder and follow it.
        Direction move = this.pathfinder.findMove(rc.getLocation(), dir -> rc.canMove(dir));
        if (move != null && move != Direction.CENTER) rc.move(move);

        return new Transition(DroneState.FINDING_ENEMY, true);
    }



    public boolean seemsInaccessible(RobotController rc, MapLocation loc) throws GameActionException {
        //Returns true iff loc contains soup and it seems like miners may need help getting to it
        //Right now, drones will err on the side of helping miners
        if (closestFriendlyMiner == null) {
            return true;
        }
        if (rc.senseElevation(loc) > closestFriendlyMinerElevation + 3 || closestFriendlyMiner.distanceSquaredTo(loc) >=10) {
            return true;
        }
        return false;
    }

}