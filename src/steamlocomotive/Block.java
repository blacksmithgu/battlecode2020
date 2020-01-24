package steamlocomotive;

import battlecode.common.Team;

public class Block {
    final int[] content;
    final Team team;

    /**
     * Create a block from 6 ints
     */
    private Block(int[] content, Team team) {
        this.content = content;
        this.team = team;
    }

    /**
     * Create a block from a 7 int array or return null if the message is not ours
     */
    public static Block extractBlock(int[] block, Team team) {
        if (block.length != 7) {
            return null;
        }

        int checksum = extractChecksum(block);
        int[] content = extractContent(block);

        if (!correctChecksum(content, checksum, team)) {
            return null;
        }
        return new Block(content, team);
    }

    private static int[] extractContent(int[] block) {
        int[] content = new int[6];

        for (int index = 0; index < content.length; index++) {
            content[index] = block[index];
        }
        return content;
    }

    /**
     * Extract the checksum from a 7 int message
     */
    private static int extractChecksum(int[] content) {
        return content[6];
    }

    /**
     * Verify that a checksum is valid for a message of 6 ints
     */
    private static boolean correctChecksum(int[] message, int checksum, Team team) {
        int correct = (team == Team.A) ? 6123412:32742361;
        for(int val: message) {
            correct^=val;
        }
        return correct == checksum;
    }

    /**
     * Compute a checksum from a 6 int message
     */
    private static int computeChecksum(int[] message, Team team) {
        int val = (team == Team.A) ? 6123412 : 32742361;
        for(int msg: message) {
            val^=msg;
        }
        return val;
    }

    /**
     * Creates a block from a message of 6 ints or returns null if input is the wrong size
     */
    public static Block createBlock(int[] message, Team team) {
        if(message.length != 6) {
            return null;
        }

        return new Block(message, team);
    }

    /**
     * Returns the 7 int message we will send to the blockchain including the checksum
     */
    public int[] getBlockMessage() {
        int[] message = new int[7];
        for (int index = 0; index < 6; index++) {
            message[index] = content[index];
        }
        message[6] = computeChecksum(content, team);
        return message;
    }

    /**
     * Returns the content of a message
     */
    public int[] getMessage() {
        return content;
    }


}