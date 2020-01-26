package steamlocomotive;

import org.junit.Test;

import static org.junit.Assert.*;

public class BitconnectTest {

    @Test
    public void testBlockReaderWriter() {
        Bitconnect.BlockBuilder builder = new Bitconnect.BlockBuilder();
        builder.append(true);
        builder.append(false);
        builder.append(false);
        builder.append(63, 6);
        builder.append(14, 6);
        builder.append(31, 5);
        builder.append(0xFFFFFF, 24);
        builder.append(19, 5);
        builder.append(18, 5);
        builder.append(26, 6);
        builder.append(5, 6);
        builder.append(true);

        int[] result = builder.finish();

        Bitconnect.BlockReader reader = new Bitconnect.BlockReader(result);

        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertEquals(63, reader.readInteger(6));
        assertEquals(14, reader.readInteger(6));
        assertEquals(31, reader.readInteger(5));
        assertEquals(0xFFFFFF, reader.readInteger(24));
        assertEquals(19, reader.readInteger(5));
        assertEquals(18, reader.readInteger(5));
        assertEquals(26, reader.readInteger(6));
        assertEquals(5, reader.readInteger(6));
        assertTrue(reader.readBoolean());
    }
}