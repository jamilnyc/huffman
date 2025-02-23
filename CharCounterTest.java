import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
class CharCounterTest {

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
    }

    @org.junit.jupiter.api.Test
    void getCount() {
    }

    @org.junit.jupiter.api.Test
    void countAll() {
        CharCounter cc = new CharCounter();

        InputStream ins = new ByteArrayInputStream("teststring".getBytes(StandardCharsets.UTF_8));
        try {
            int bitCount = cc.countAll(ins);
            assertEquals("teststring".length() * CharCounter.BITS_PER_WORD, bitCount, "The bit count was incorrect");
            Map<Integer, Integer> frequencyTable = cc.getTable();

            int key = 't';
            int count = frequencyTable.get(key);
            assertEquals(3, count, "The letter 't' should be counted 3 times");
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @org.junit.jupiter.api.Test
    void add() {
    }

    @org.junit.jupiter.api.Test
    void set() {
    }

    @org.junit.jupiter.api.Test
    void clear() {
    }

    @org.junit.jupiter.api.Test
    void getTable() {
    }
}