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
            case HQ: unit = new HQ(); break;
            case MINER: unit = new Miner(rc); break;
            case REFINERY: unit = new Refinery(); break;
            case VAPORATOR: unit = new Vaporator(); break;
            case DESIGN_SCHOOL: unit = new DesignSchool(); break;
            case FULFILLMENT_CENTER: unit = new FulfillmentCenter(); break;
            case LANDSCAPER: unit = new Landscaper(); break;
            case DELIVERY_DRONE: unit = new DeliveryDrone(); break;
            case NET_GUN: unit = new NetGun(); break;
            default: throw new IllegalStateException("Invalid unit type - should not happen.");
        }

        while (true) {
            turn += 1;

            // Try/catch to avoid crashing the unit if it encounters an exception.
            // Try not to through exceptions in the first place.
            try {
                unit.run(rc, turn);

                // Wait until the start of the next turn.
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
