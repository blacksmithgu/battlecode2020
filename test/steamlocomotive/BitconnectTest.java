package steamlocomotive;

import battlecode.common.*;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyInt;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        Mockito.when(rc.getTeam()).thenReturn(Team.A);
        Bitconnect bitconnect = new Bitconnect(rc, 10, 10);

        MapLocation[] adj = new MapLocation[1];
        adj[0] = new MapLocation(2,2);

        Bitconnect.HQSurroundings surroundings = new Bitconnect.HQSurroundings(new MapLocation(1,2), adj, rc.getTeam());

        bitconnect.sendLandscaperLocations(rc, surroundings);
        Block block = bitconnect.blocksToSend.pop();

        int[] expectedBlock = {42,1,2,4,0,0,6123412^42^1^2^4};
        Block expected = Block.extractBlock(expectedBlock, rc.getTeam());

        assertArrayEquals(expected.content, block.content);

        Bitconnect.HQSurroundings result = Bitconnect.HQSurroundings.fromMessage(block, rc.getTeam());

        assertTrue(surroundings.equals(result));
        assertEquals(1, surroundings.adjacentWallSpots.length);
    }
}