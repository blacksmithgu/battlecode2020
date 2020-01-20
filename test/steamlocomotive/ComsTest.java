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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ComsTest {

    @Test
    public void testBitsetting() {
        assertTrue(Block.getBit(1,0));
        assertTrue(Block.getBit(Block.setBit(0, 10, true), 10));
        assertFalse(Block.getBit(Block.setBit(1, 0, false), 0));
    }

    @Test
    public void testMultiBitSetting() {
        int[] test = new int[10];
        Block.setBits(test, 30, 4, 9);
        System.out.println(Arrays.toString(test));
        assertEquals(9, Block.getBits(test, 30, 4));
    }

    @Test
    public void testCompressInts() {
        int[] values = {10, 13, 99, 7};
        int[] sizes = {4, 4, 7, 3};
        assertEquals(Arrays.toString(values), Arrays.toString(Bitconnect.decompressBits(Bitconnect.compressInts(values, sizes), sizes)));
    }

    @Test
    public void TestHQWallCode() throws GameActionException {
        /*
        RobotController rc = Mockito.mock(RobotController.class);
        Mockito.when(rc.getBlock(anyInt())).thenReturn(new Transaction[0]);
        Mockito.when(rc.getTeamSoup()).thenReturn(Integer.MAX_VALUE);
        Bitconnect bitconnect = new Bitconnect(rc, 10, 10);

        MapLocation[] adj = new MapLocation[1];
        adj[0] = new MapLocation(2,2);

        Bitconnect.HQSurroundings surroundings = new Bitconnect.HQSurroundings(new MapLocation(1,2), adj);

        bitconnect.sendLandscaperLocations(rc, surroundings);
        Block block = bitconnect.blocksToSend.pop();

        int[] expectedBlock = {42,1,2,4,0,0,0};
        Block expected = Block.extractBlock(expectedBlock);

        assertArrayEquals(expected.content, block.content);

        Bitconnect.HQSurroundings result = Bitconnect.HQSurroundings.fromMessage(block);

        assertTrue(surroundings.equals(result));
        assertEquals(1, surroundings.adjacentWallSpots.length);
         */
    }
}