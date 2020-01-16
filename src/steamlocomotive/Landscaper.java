package steamlocomotive;

import battlecode.common.*;

import java.util.Map;

public class Landscaper extends Unit {

    //communication object
    private Bitconnect comms;

    //in position to build wall
    private boolean inPosition = false;

    //our HQ location
    private MapLocation ourHQLoc;

    //enemy HQ location
    private MapLocation enemyHQLoc;

    //wall locations
    private Bitconnect.HQSurroundings wallLocations;



    public Landscaper(int id) {
        super(id);
    }

    @Override
    public void run(RobotController rc, int turn) throws GameActionException{
        //if wall builder, move towards one of the desired locations

        if (inPosition == false){
            //check if in position
            MapLocation pos = rc.getLocation();
            for (int i = 1; i <= wallLocations.adjacentWallSpots.length; i++){
                if (pos.equals(wallLocations.adjacentWallSpots[i])){
                    inPosition = true;
                }
            }
        if (inPosition = true){
            Direction digFrom = rc.getLocation().directionTo(ourHQLoc).opposite();
            if (!rc.canDigDirt(digFrom)){
                for (Direction direction : Direction.allDirections()){
                    if (!direction.equals(Direction.CENTER) && rc.canDigDirt(direction)){
                        digFrom = direction;
                        break;
                    }
                }
            }
            if (turn%2==0){
                rc.digDirt(digFrom);
            } else {
                if (rc.canDepositDirt(Direction.CENTER)){
                    rc.depositDirt(Direction.CENTER);
                }
            }
        }
    }

    public void onCreation(RobotController rc){
        comms = new Bitconnect(rc.getMapWidth(),rc.getMapHeight());
        wallLocations = comms.getWallLocations(rc);
    }
}