package steamlocomotive;

import battlecode.common.*;

/**
 * Core class which is run for every unit. Checks which actual unit type it is
 * and then dispatches to the appropriate unit implementation.
 */
public strictfp class RobotPlayer {
    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Number of turns this robot has percieved/seen. Note this does not correspond
        // to the number of rounds that have passed!
        int turn = 0;

        // Figure out the unit type that this agent is, for dispatch.
        Unit unit;
        switch (rc.getType()) {
            case HQ: unit = new HQ(rc.getID()); break;
            case MINER: unit = new Miner(rc.getID()); break;
            case REFINERY: unit = new Refinery(rc.getID()); break;
            case VAPORATOR: unit = new Vaporator(rc.getID()); break;
            case DESIGN_SCHOOL: unit = new DesignSchool(rc.getID()); break;
            case FULFILLMENT_CENTER: unit = new FulfillmentCenter(rc.getID()); break;
            case LANDSCAPER: unit = new Landscaper(rc.getID()); break;
            case DELIVERY_DRONE: unit = new DeliveryDrone(rc.getID()); break;
            case NET_GUN: unit = new NetGun(rc.getID()); break;
            default: throw new IllegalStateException("Invalid unit type - should not happen.");
        }

        // Initial on creation call for agent setup.
        try {
            unit.onCreation(rc);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        while (true) {
            turn += 1;

            // Try/catch to avoid crashing the unit if it encounters an exception.
            // Try not to throw exceptions in the first place.
            try {
                // We can consider allowing communication & execution if the robot
                // is not ready via another method; for now do nothing.
                if (rc.isReady()) {
                    int bround = rc.getRoundNum();
                    int bbytes = Clock.getBytecodeNum() + bround * rc.getType().bytecodeLimit;
                    unit.run(rc, turn);
                    int around = rc.getRoundNum();
                    int abytes = Clock.getBytecodeNum() + rc.getRoundNum() * rc.getType().bytecodeLimit;

                    if (bround != around)  {
                        // Check for timeouts so we can warn appropriately.
                        System.out.printf("Robot %s timed out (round %d -> %d, %d bytecodes)%n", rc.getType(), bround, around, abytes - bbytes);
                    }

                    // TODO: Consider adding a 'low utilization' warning.
                }

                // Wait until the start of the next turn.
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
