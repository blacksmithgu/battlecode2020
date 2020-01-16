package steamlocomotive;


import org.junit.Test;

import static org.junit.Assert.assertThat;


class BitconnectTest {

    @Test
    public void testBitsetting() {
        Bitconnect bitconnect = new Bitconnect(100,100);
        assert(bitconnect.getBit(1,0));
        assert(bitconnect.getBit(bitconnect.setBit(0, 10, true), 10));
        assert(!bitconnect.getBit(bitconnect.setBit(1, 0, false), 0));
    }
}