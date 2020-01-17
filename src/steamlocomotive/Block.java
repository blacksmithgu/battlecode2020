package steamlocomotive;

public class Block {
    int[] content;

    /**
     * Create a block from 6 ints
     */
    private Block(int[] content) {
        this.content = content;
    }

    /**
     * Create a block from a 7 int array or return null if the message is not ours
     */
    public static Block extractBlock(int[] block) {
        if (block.length != 7) {
            return null;
        }

        int checksum = getChecksum(block);
        int[] content = getContent(block);

        if (!correctChecksum(content, checksum)) {
            return null;
        }
        return new Block(content);
    }

    private static int[] getContent(int[] block) {
        int[] content = new int[6];

        for (int index = 0; index < content.length; index++) {
            content[index] = block[index];
        }
        return content;
    }

    /**
     * Gets the checksum of a 6 int message
     */
    private static int getChecksum(int[] content) {
        return 0;
    }

    /**
     * Verify that a checksum is valid for a message of 6 ints
     */
    private static boolean correctChecksum(int[] message, int checksum) {
        return true;
    }

    /**
     * Creates a block from a message of 6 ints or returns null if input is wrong size
     */
    public static Block createBlock(int[] message) {
        if(message.length != 6) {
            return null;
        }

        return new Block(message);
    }

    /**
     * Returns the 6 int message contained in the block
     */
    public int[] getMessage() {
        int[] message = new int[7];
        for (int index = 0; index < 6; index++) {
            message[index] = content[index];
        }
        message[6] = getChecksum(content);
        return message;
    }
}