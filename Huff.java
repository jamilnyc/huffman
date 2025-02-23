import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class Huff implements ITreeMaker, IHuffEncoder, IHuffModel, IHuffHeader
{
    private Map<Integer, Integer> characterFrequency;
    private HuffTree huffTree;
    Map<Integer, String> characterEncodings;

    public Huff() {
        characterFrequency = new HashMap<>();
    }

    @Override
    public Map<Integer, String> makeTable() {
        characterEncodings = new HashMap<>();
        makeCode(characterEncodings, huffTree.root(), "");
        return characterEncodings;
    }

    /**
     * Recursively populate the characterEncodings map, by traversing the HuffTree with pre-order traversal.
     *
     * @param characterEncodings Map of character encodings to populate. Key is the character, value is the encoding in 0's and 1's
     * @param root The root node of the HuffTree to start traversing from
     * @param encoding The encoding string that is built one bit at a time as we traverse the tree. Should be empty string to start.
     */
    private void makeCode(Map<Integer, String> characterEncodings, IHuffBaseNode root, String encoding) {
        if (root == null) {
            throw new NullPointerException("Null root node");
        }

        if (root.isLeaf()) {
            // Cast it to a leaf so we can get the character out
            HuffLeafNode leaf = (HuffLeafNode) root;
            characterEncodings.put(leaf.element(), encoding);
        } else {
            // Must be an internal node
            HuffInternalNode internalNode = (HuffInternalNode) root;
            makeCode(characterEncodings, internalNode.left(), encoding + "0");
            makeCode(characterEncodings, internalNode.right(), encoding + "1");
        }
    }

    @Override
    public String getCode(int i) {
        return characterEncodings.get(i);
    }

    @Override
    public Map<Integer, Integer> showCounts() {
        return characterFrequency;
    }

    @Override
    public int headerSize() {
        return huffTree.size() + BITS_PER_INT;
    }

    @Override
    public int writeHeader(BitOutputStream out) {
        out.write(BITS_PER_INT, MAGIC_NUMBER);
        return BITS_PER_INT + writeHelper(out, huffTree.root());
    }

    /**
     * Recursively write the entire HuffTree as part of the compressed file's header.
     *
     * @param out BitOutputStream that represents the file we are writing the header to
     * @param root The root node of the HuffTree
     * @return The number of bits written for the HuffTree, to be included in the total header size
     */
    private int writeHelper(BitOutputStream out, IHuffBaseNode root) {
        if (root.isLeaf()) {
            // For leaf nodes, we write a 1, followed by the ASCII value
            // with 9 bits
            out.write(1, 1);
            out.write(BITS_PER_WORD + 1, ((HuffLeafNode)root).element());
            return 1 + 1 + BITS_PER_WORD;
        } else {
            // For internal nodes, we just write a 0
            out.write(1, 0);

            // Then recursively call the method on the children
            // with pre-order traversal
            return 1 + writeHelper(out, ((HuffInternalNode) root).left())
                    + writeHelper(out, ((HuffInternalNode) root).right());
        }
    }

    @Override
    public HuffTree readHeader(BitInputStream in) throws IOException {
        // Read the first 32 bits and interpret them as an integer
       int magicNumber = in.read(BITS_PER_INT);

       if (magicNumber != MAGIC_NUMBER) {
           throw new IOException("Magic Number did not match. Expected: " + MAGIC_NUMBER + ", Actual: " + magicNumber);
       }

       // After reading past the first 32 bits, the tree starts
       huffTree = buildTreeFromHeader(in);
       return huffTree;
    }

    private HuffTree buildTreeFromHeader(BitInputStream in) throws IOException {
        // Leaves are 1, Internal nodes are 0
        int firstBit = in.read(1);
        if (firstBit == -1) {
            throw new IOException("Ran out of bits reading the header");
        }

        // We only care about the ordering of nodes/children
        // We don't need the weight in this case, so it doesn't matter what it is
        int placeHolderWeight = -1;

        boolean isLeaf = (firstBit == 1);
        if (isLeaf) {
            // The next 9 bits are the ASCII representation of the character in the leaf
            int character = in.read(BITS_PER_WORD + 1);
            return new HuffTree(character, placeHolderWeight);
        } else {
            // Recursively build the left and right child, because this is an internal node
            HuffTree left;
            HuffTree right;

            left = buildTreeFromHeader(in);
            right = buildTreeFromHeader(in);

            return new HuffTree(left.root(), right.root(), placeHolderWeight);
        }
    }

    @Override
    public int write(String inFile, String outFile, boolean force) {
        // We need two input streams for the file
        // One stream reads through the whole file to make the HuffTree
        // The other input stream is used to read one character at a time and encode it

        BitInputStream treeStream = new BitInputStream(inFile);
        BitInputStream inputStream = new BitInputStream(inFile);

        try {
            makeHuffTree(treeStream);
            makeTable();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Once the tree is built, we don't need this stream anymore
        treeStream.close();

        int originalSize = 0;
        int compressedSize = headerSize();
        for (Integer character : characterFrequency.keySet()) {
            int frequency = characterFrequency.get(character);

            // In the original file, each character is 8 bits long,
            // and we multiply that by the number of instances of this character
            originalSize += frequency * BITS_PER_WORD;

            // In the compressed file, each character takes up space, equal to its encoding
            // Likewise, we multiply this by the frequency of that character
            String encoding = characterEncodings.get(character);
            compressedSize += frequency * encoding.length();
        }
        // Remember that the compressed version ends in Pseudo EOF
        compressedSize += characterEncodings.get(PSEUDO_EOF).length();

        if ((compressedSize >= originalSize) && !force) {
            // If compression actually doesn't save space, and we are not forcing
            // there is nothing else to do.
            inputStream.close();
            return compressedSize;
        }

        // Start writing to the compressed file, with the header
        BitOutputStream out = new BitOutputStream(outFile);
        writeHeader(out);

        while(true) {
            int character;
            try {
                character = inputStream.read(BITS_PER_WORD);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // Reached the end of the original file
            if (character == -1) {
                break;
            }

            String encoding = characterEncodings.get(character);
            // Parse the encoding as binary 0's and 1's into a regular decimal integer
            int encodingAsInteger = Integer.parseInt(encoding, 2);
            out.write(encoding.length(), encodingAsInteger);
        }

        // Add the PSEUDO_EOF to the end of the compressed file, which we will use
        // to know when to stop decompressing.
        String pseudoEofEncoding = characterEncodings.get(PSEUDO_EOF);
        int pseudoEofEncodingAsInteger = Integer.parseInt(pseudoEofEncoding, 2);
        out.write(pseudoEofEncoding.length(), pseudoEofEncodingAsInteger);

        inputStream.close();
        out.close();
        return compressedSize;
    }

    @Override
    public int uncompress(String inFile, String outFile) {
        BitInputStream in  = new BitInputStream(inFile);
        BitOutputStream out = new BitOutputStream(outFile);

        try {
            readHeader(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // After reading the header, the huffTree should be populated
        IHuffBaseNode nodePointer = huffTree.root();
        int uncompressedSize = 0;
        int nextBit = -1;
        while (true) {
            try {
                nextBit = in.read(1);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (nextBit == -1) {
                throw new RuntimeException("Unexpected end of file while uncompressing");
            }

            // At this point the header has already been processed, so we are now reading the encodings
            // Each 0 we read goes left in the Huffman Tree
            // Each 1 goes to the right
            // If we reach a Leaf Node, we can decode a character unless it is the PSEUDO_EOF
            if ( nextBit == 0) {
                // Go left in the tree
                nodePointer = ((HuffInternalNode) nodePointer).left() ;
            } else {
                nodePointer = ((HuffInternalNode) nodePointer).right();
            }

            // After going left/right, check if we are at a leaf
            if (nodePointer.isLeaf()) {
                HuffLeafNode leaf = (HuffLeafNode) nodePointer;
                if (leaf.element() == PSEUDO_EOF) {
                    // End of file, break out of while loop
                    break;
                }

                // Leaf node must be a regular character
                try {
                    // Write this character to the uncompressed file stream
                    out.write(leaf.element());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                uncompressedSize += BITS_PER_WORD;

                // Reset the node to the root, so we can decode the next character
                nodePointer = huffTree.root();
            }
        }

        in.close();
        out.close();
        return uncompressedSize;
    }

    @Override
    public HuffTree makeHuffTree(InputStream stream) throws IOException {
        CharCounter cc = new CharCounter();
        cc.countAll(stream);
        characterFrequency = cc.getTable();

        // Normally we should use a PriorityQueue<IHuffBaseNode>, but there
        // is a bug in the two node classes in how they implement the compareTo() method.
        // Specifically they are only able to compareTo other instances of their own class.
        // The HuffTree class has a more robust compareTo() method.
        PriorityQueue<HuffTree> queue = new PriorityQueue<>();
        for (Map.Entry<Integer, Integer> entry : characterFrequency.entrySet()) {
            Integer character = entry.getKey();
            Integer frequency = entry.getValue();
            // All nodes are initially leaves
            HuffTree t = new HuffTree(character, frequency);
            queue.add(t);
        }

        // Add one more leaf for the PSEUDO_EOF with a weight of 1
        // per the specification.
        // This will give the PSEUDO_EOF a unique encoding that will be
        // used when we write the compressed file.
        queue.add(new HuffTree(PSEUDO_EOF, 1));

        HuffTree tempTree = null;
        while(queue.size() > 1) {
            HuffTree temp1 = queue.poll();
            HuffTree temp2 = queue.poll();
            tempTree = new HuffTree(temp1.root(), temp2.root(), temp1.weight() + temp2.weight());
            queue.add(tempTree);
        }

        huffTree = tempTree;
        return huffTree;
    }

    public HuffTree getHuffTree() {
        return huffTree;
    }
}
