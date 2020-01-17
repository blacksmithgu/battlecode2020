package steamlocomotive;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Transaction;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

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
        MapLocation[] adj = new MapLocation[1];
        adj[0] = new MapLocation(2,2);
        Bitconnect.HQSurroundings surroundings = new Bitconnect.HQSurroundings(new MapLocation(1,2), adj);
        Bitconnect.Block block = bitconnect.sendLandscaperLocations(rc, surroundings);
        int[] expectedBlock = {42,1,2,4,0,0,0};
        Bitconnect.Block expected = Bitconnect.Block.extractBlock(expectedBlock);
        assertArrayEquals(expected.content, block.content);
        Bitconnect.HQSurroundings result = Bitconnect.HQSurroundings.fromMessage(block);
        assertTrue(surroundings.equals(result));
        assertEquals(1, surroundings.adjacentWallSpots.length);
    }
}