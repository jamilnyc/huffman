import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HuffTest {
    public static String ORIGINAL_TEST_FILE = "original.test.txt";
    public static String COMPRESSED_TEST_FILE = "compressed.test.bin";
    public static String UNCOMPRESSED_TEST_FILE = "uncompressed.test.txt";

    public static String LONG_ORIGINAL_TEST_FILE = "long-original.test.txt";
    public static String LONG_COMPRESSED_TEST_FILE = "long-compressed.test.txt";
    public static String LONG_UNCOMPRESSED_TEST_FILE = "long-uncompressed.test.txt";

    public static String FAKE_COMPRESSED_TEST_FILE = "fake-compressed.test.txt";

    private Huff getTestHuff() {
        String test = "teststring";
        InputStream ins = new ByteArrayInputStream(test.getBytes());
        Huff h = new Huff();
        try {
            h.makeHuffTree(ins);
        } catch (IOException e) {
            fail("Exception while counting characters");
        }

        HuffTree tree = h.getHuffTree();
        assertEquals(11, tree.root().weight(), "Root of the HuffTree should be weight 10");
        assertEquals(87, tree.size());

        return h;
    }

    @Test
    void testCharacterFrequency() {
        Huff h = getTestHuff();

        Map<Integer, Integer> frequencies = h.showCounts();
        int ch = 't';
        assertEquals(3, frequencies.get(ch), "There should be 3 instances of letter t");
    }

    @Test
    void testCharacterEncodings() {
        Huff h = getTestHuff();

        Map<Integer, String> characterEncodings = h.makeTable();

        assertEquals(8, characterEncodings.size(), "There should be 8 characters encoded");
        int c = 't';
        assertEquals(2, h.getCode(c).length(), "The character t should have the shortest code of length 2");
    }

    @Test
    void testHeader() {
        Huff h = getTestHuff();

        int expectedHeaderSize = (
          IHuffConstants.BITS_PER_INT // 32 bits for magic number
          + 7 // 7 0's for internal nodes
          + 8 // 7 1's for leaves
          + ((IHuffConstants.BITS_PER_WORD + 1) * 8) // 9 bits for each of the 7 ASCII characters in the leaves
        );

        FileOutputStream fos = null;
        try {
             fos = new FileOutputStream("headertest1.txt");
        } catch (FileNotFoundException e) {
            fail(e.getMessage());
        }
        BitOutputStream bos = new BitOutputStream(fos);
        int actualHeaderSize =  h.writeHeader(bos);
        assertEquals(expectedHeaderSize, actualHeaderSize, "Header size did not match");

        try {
            fos.close(); //do not forget to close the stream
        } catch (IOException e) {
            fail("Exception while closing output stream");
        }

        BitInputStream bis = new BitInputStream("headertest1.txt");
        HuffTree tree = null;
        try {
            tree = h.readHeader(bis);
        } catch (IOException e) {
            fail(e.getMessage());
        }

        // Test that the tree was reconstructed
        assertEquals(87, tree.size(), "Tree size did not match");
        assertFalse(tree.root().isLeaf(), "Root should NOT be a leaf");
    }

    private int getExpectedCompressedFileSize() {
        return
        // Header Size
        IHuffConstants.BITS_PER_INT // 32 bits for magic number
        + 7 // 7 0's for internal nodes
        + 8 // 7 1's for leaves
        + ((IHuffConstants.BITS_PER_WORD + 1) * 8) // 9 bits for each of the 7 ASCII characters in the leaves

        // Body Size
        + (3 * 2) // Letter 't' appears 3 times, encoding length is 2
        + (2 * 3) // Letter 's' appears 2 times, encoding length is 3
        + (1 * 3) // Letter 'r' appears 1 time, encoding length is 3
        + (1 * 3) // Letter 'e' appears 1 time, encoding length is 3
        + (1 * 3) // Letter 'i' appears 1 time, encoding length is 3
        + (1 * 3) // Letter 'n' appears 1 time, encoding length is 3
        + (1 * 4) // Letter 'g' appears 1 time, encoding length is 4
        + (1 * 4) // PSEUDO_EOF
        ;
    }

    /**
     * This function is used to clean up files before actual testing, so we can re-run tests.
     *
     * @param fileName Name of file to delete
     */
    private void deleteFileIfExists(String fileName) {
        File file = new File(fileName);
        file.delete();
    }

    @Test
    void testCompressionWithForce() {
        deleteFileIfExists("compressed.test.bin");

        Huff h = getTestHuff();
        int actualCompressedSize = h.write("original.test.txt", "compressed.test.bin", true);
        assertEquals(getExpectedCompressedFileSize(), actualCompressedSize, "Compressed size did not match");

        // Check that compressed file was created
        File compressedFile = new File("compressed.test.bin");
        assertTrue(compressedFile.exists(), "Compressed file did not exist");
    }

    @Test
    void testCompressionWithoutForce() {
        deleteFileIfExists("compressed.test.bin");

        Huff h = getTestHuff();
        int actualCompressedSize = h.write("original.test.txt", "compressed.test.bin", false);
        assertEquals(getExpectedCompressedFileSize(), actualCompressedSize, "Compressed size did not match");

        // Check that compressed file was NOT created
        File compressedFile = new File("compressed.test.bin");
        assertFalse(compressedFile.exists(), "Compressed file should not exist");
    }

    @Test
    void testDecompression() {
        deleteFileIfExists(COMPRESSED_TEST_FILE);
        deleteFileIfExists(UNCOMPRESSED_TEST_FILE);

        // Force compression
        Huff h = getTestHuff();
        int actualCompressedSize = h.write(ORIGINAL_TEST_FILE, COMPRESSED_TEST_FILE, true);
        assertEquals(getExpectedCompressedFileSize(), actualCompressedSize, "Compressed size did not match");

        // Check that compressed file was created
        File compressedFile = new File(COMPRESSED_TEST_FILE);
        assertTrue(compressedFile.exists(), "Compressed file did not exist");

        int actualUncompressedSize = h.uncompress(COMPRESSED_TEST_FILE, UNCOMPRESSED_TEST_FILE);

        File uncompressedFile = new File(UNCOMPRESSED_TEST_FILE);
        assertTrue(uncompressedFile.exists(), "Uncompressed file did not exist");

        int expectedUncompressedSize = IHuffConstants.BITS_PER_WORD * 10;
        assertEquals(expectedUncompressedSize, actualUncompressedSize, "Uncompressed size did not match");

        try {
            List<String> lines = Files.readAllLines(Paths.get(UNCOMPRESSED_TEST_FILE));
            assertEquals(1, lines.size(), "Uncompressed size did not match");
            assertEquals("teststring", lines.get(0), "Uncompressed size did not match");
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    void testDecompressionWithLongFile() {
        deleteFileIfExists(LONG_COMPRESSED_TEST_FILE);
        deleteFileIfExists(LONG_UNCOMPRESSED_TEST_FILE);

        File originalFile = new File(LONG_ORIGINAL_TEST_FILE);

        Huff h = getTestHuff();
        int actualCompressedSize = h.write(LONG_ORIGINAL_TEST_FILE, LONG_COMPRESSED_TEST_FILE, false);
        int originalFileSize = (int) originalFile.length() * IHuffConstants.BITS_PER_WORD;

        assertTrue(actualCompressedSize < originalFileSize, "Compression did not save space");

        int actualUncompressedSize = h.uncompress(LONG_COMPRESSED_TEST_FILE, LONG_UNCOMPRESSED_TEST_FILE);
        File uncompressedFile = new File(LONG_UNCOMPRESSED_TEST_FILE);
        assertTrue(uncompressedFile.exists(), "Uncompressed file did not exist");
        assertTrue(actualUncompressedSize > actualCompressedSize, "Uncompressed file should be bigger than compressed file");


        try {
            List<String> originalLines = Files.readAllLines(Paths.get(LONG_ORIGINAL_TEST_FILE));
            List<String> uncompressedLines = Files.readAllLines(Paths.get(LONG_UNCOMPRESSED_TEST_FILE));

            assertEquals(originalLines.size(), uncompressedLines.size(), "The original and uncompressed files should have the same number of lines");
            for (int i = 0; i < originalLines.size(); i++) {
                assertEquals(originalLines.get(i), uncompressedLines.get(i), "Line number " + (i+1) + " did not match");
            }
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    void testMalformedDecompression() {
        deleteFileIfExists(UNCOMPRESSED_TEST_FILE);
        Huff h = getTestHuff();

        try {
            int actualUncompressedSize = h.uncompress(FAKE_COMPRESSED_TEST_FILE, UNCOMPRESSED_TEST_FILE);

            // If we reach this, something went wrong
            fail("You should not be able to uncompress this file");
        } catch (RuntimeException e) {
            String message = e.getMessage();
            boolean matches = message.contains("Magic Number did not match. Expected: 1234567873");
            assertTrue(matches, "Exception should be thrown about magic number not matching");
        }

    }

}