package steamlocomotive;

import org.junit.Test;
import static org.junit.Assert.*;

class BitconnectTest {

    @Test
    public void testBitsetting() {
        assertTrue(Bitconnect.getBit(1,0));
        assertTrue(Bitconnect.getBit(Bitconnect.setBit(0, 10, true), 10));
        assertFalse(Bitconnect.getBit(Bitconnect.setBit(1, 0, false), 0));
    }
}