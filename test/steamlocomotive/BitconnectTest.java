package steamlocomotive;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Transaction;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyInt;

public class BitconnectTest {

    @Test
    public void testBitsetting() {
        assertTrue(Bitconnect.getBit(1,0));
        assertTrue(Bitconnect.getBit(Bitconnect.setBit(0, 10, true), 10));
        assertFalse(Bitconnect.getBit(Bitconnect.setBit(1, 0, false), 0));
    }

    @Test
    public void TestHQWallCode() throws GameActionException {
        RobotController rc = Mockito.mock(RobotController.class);
        Mockito.when(rc.getBlock(anyInt())).thenReturn(new Transaction[0]);
        Mockito.when(rc.getTeamSoup()).thenReturn(Integer.MAX_VALUE);
        Bitconnect bitconnect = new Bitconnect(rc, 10, 10);
        Bitconnect.HQSurroundings surroundings = new Bitconnect.HQSurroundings(new MapLocation(1,2), new MapLocation[0]);
        Bitconnect.Block block = bitconnect.sendLandscaperLocations(rc, surroundings);
        Bitconnect.HQSurroundings result = Bitconnect.HQSurroundings.fromMessage(block);
        System.out.println(result);
        assertTrue(surroundings.equals(result));
    }
}