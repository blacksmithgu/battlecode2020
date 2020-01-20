package steamlocomotive;

import java.net.BindException;
import java.time.chrono.MinguoDate;

public class Block {
    public static class Message {
        public final int[] message;
        private final int numBits;

        public Message(int[] message, int numBits) {
            this.message = message;
            this.numBits = numBits + 8;
        }
    }

    int remainingBits = 162;
    final DynamicArray<Message> messages;
    final int robotId;
    final int mapX;
    final int mapY;

    public Block(int robotId, int mapX, int mapY) {
        messages = new DynamicArray<>(5);
        this.robotId = robotId % 1024;
        this.mapX = mapX;
        this.mapY = mapY;
    }

    public boolean addMessage(Message message) {
        if(remainingBits < message.numBits) {
            return false;
        }
        remainingBits -= message.numBits;
        messages.add(message);
        return true;
    }

    public int[] getBlockchainMessage() {
        int numBitsInMessage = 0;
        for(Message message: messages) {
            numBitsInMessage += message.numBits-8;
        }
        int numIntsInMessage = (int)Math.ceil(((double)numBitsInMessage) / 32);

        int[] content = new int[4 + messages.size() + numIntsInMessage];
        int[] sizes = new int[4 + messages.size() + numIntsInMessage];

        content[0] = robotId;
        content[1] = mapX;
        content[2] = mapY;

        sizes[0] = 10;
        sizes[1] = 6;
        sizes[2] = 6;

        for(int count = 0; count < messages.size(); count++) {
            content[count+3] = messages.get(count).numBits-8;
            sizes[count+3] = 8;
        }
        content[messages.size() + 3] = 0b11111111;
        sizes[messages.size()+3] = 8;
        for(int index = 0; index < numIntsInMessage; index++) {
            sizes[messages.size() + 4 + index] = 32;
        }
        sizes[messages.size()+3 + numIntsInMessage] = numBitsInMessage % 32;

        int startingBit = (messages.size() + 3)*32 + 1;
        for(int index = 0; index < messages.size(); index++) {
            for(int messageIndex = 0; messageIndex < messages.get(index).message.length; index++) {
                if(messageIndex + 1 == messages.size()) {
                    setBits(content, startingBit, messages.get(index).numBits % 32, messages.get(index).message[messageIndex]);
                    startingBit+=messages.get(index).numBits % 32;
                } else {
                    setBits(content, startingBit, 32, messages.get(index).message[messageIndex]);
                    startingBit += 32;
                }
            }
        }
        int[] message = Bitconnect.compressInts(content, sizes);
        int checksum = computeChecksum(message);

        int[] toReturn = new int[7];
        for(int index = 0; index < Math.min(6, message.length); index++) {
            toReturn[index] = message[index];
        }

        toReturn[6] = checksum;

        return toReturn;
    }

    private int computeChecksum(int[] message) {
        return 0;
    }


    public DynamicArray<Message> getMessages() {
        return messages;
    }

    public static void setBits(int[] array, int index, int numBits, int value) {
        int currentInt = index/32;
        int currentPosition = index % 32;
        for(int count = 0; count < numBits; count++) {
            array[currentInt] = setBit(array[currentInt], currentPosition, getBit(value, count));
            currentPosition++;
            if(currentPosition == 32) {
                currentPosition=0;
                currentInt++;
            }
        }
    }


    public static int getBits(int[] array, int index, int numBits) {
        int value = 0;
        int currentInt = index/32;
        int currentPosition = index % 32;
        for(int count = 0; count < numBits; count++) {
            value = value>>1;
            value |= getBit(array[currentInt], currentPosition)? 1<<(numBits-1):0;
            currentPosition++;
            if(currentPosition == 32) {
                currentPosition=0;
                currentInt++;
            }
        }
        return value;
    }

    /**
     * Get a bit at an index of an integer.
     */
    public static boolean getBit(int integer, int index) {
        return (integer >> index) % 2 == 1 || (integer >> index) % 2 == -1;
    }

    /**
     * Set the bit at an index of an integer, returning the modified integer.
     */
    public static int setBit(int integer, int index, boolean value) {
        return value ? integer | (1 << index) : integer & ~(1 << index);
    }

    public static Block extractBlock(int[] rawMessage) {
        int firstMessageStart = 22;
        int numMessages = 0;
        int bits = getBits(rawMessage, 22+ 8*numMessages,8);
        while(bits != 0b11111111) {
            bits = getBits(rawMessage, 22+ 8*numMessages,8);
            firstMessageStart+=8;
            numMessages++;
        }

        firstMessageStart+=8;

        DynamicArray<Message> messages = new DynamicArray<>(numMessages);
        for(int index = 0; index < numMessages; index++) {
            int numBits = getBits(rawMessage, 22+ 8* index, 8);
            int numInts = (int) Math.ceil(numBits/32.0);
            int[] message = new int[numInts];
            messages.add(new Message(getBits(rawMessage, firstMessageStart, numBits), numBits));
        }
        return null;

    }


}