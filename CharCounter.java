/**
 * An interface for initializing and retrieving chunk/character
 * counts.
 *
 * @author Owen Astrachan
 */

import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

public class CharCounter implements IHuffConstants, ICharCounter {
    private final Map<Integer, Integer> frequency_counter;

    public CharCounter() {
        frequency_counter = new HashMap<Integer, Integer>();
    }

    /**
     * Returns the count associated with specified character.
     *
     * @param ch is the chunk/character for which count is requested
     * @return count of specified chunk
     * @throws NullPointerException appropriate exception if ch isn't a valid chunk/character
     */
    public int getCount(int ch) {
        return frequency_counter.get(ch);
    }

    /**
     * Initialize state by counting bits/chunks in a stream
     * @param stream is source of data
     * @return count of all chunks/read
     * @throws IOException if reading fails
     */
    public int countAll(InputStream stream) throws IOException {
        BitInputStream bitStream = new BitInputStream(stream);
        int currentCharacter = bitStream.read(BITS_PER_WORD);
        int bitCount = 0;

        // While we are able to read a valid character from the stream
        while (currentCharacter != -1) {
            // System.out.println(current_char);

            // Increment the current count for this character
            add(currentCharacter);
            bitCount += BITS_PER_WORD;

            // Read the next character from the stream
            currentCharacter = bitStream.read(BITS_PER_WORD);
        }

        // System.out.println(frequency_counter);
        return bitCount;
    }

    /**
     * Update state to record one occurrence of specified chunk/character.
     * @param i is the chunk being recorded
     */
    public void add(int i) {
        int count = frequency_counter.getOrDefault(i, 0);
        frequency_counter.put(i, count + 1);
    }

    /**
     * Set the value/count associated with a specific character/chunk.
     * @param i is the chunk/character whose count is specified
     * @param value is # occurrences of specified chunk
     */
    public void set(int i, int value) {
        frequency_counter.put(i, value);
    }

    /**
     * All counts cleared to zero.
     */
    public void clear() {
        frequency_counter.clear();
    }

    /**
     * @return a map of all characters and their frequency
     */
    public Map<Integer, Integer> getTable() {
        return frequency_counter;
    }
}