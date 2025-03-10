# Huffman Compression Algorithm

## Overview

This project is an implementation of the Huffman Compression Algorithm for ASCII text files.

It was inspired by [this assignment](https://www.seas.upenn.edu/~cit5940/current/assignments/hw03/) from a data structures and algorithms course.

A PDF of the spec is included [here](./Huffman_Algorithm_Assignment_Spec.pdf).

## Testing

JUnit tests are implemented with files `*Test.java`. These tests use JUnit v5.

They can probably be adapted for JUnit v4, with a little tweaking of the `import` statements.

The text of the `long-original.test.txt` was generated with [lipsum.com](https://www.lipsum.com/).

A code coverage HTML report is available in `code-coverage-report/`.

Many tests use `teststring` to assert the functionality. A breakdown of one potential Huffman tree for `teststring` can be seen below:

![Huffman Tree for teststring](./img/teststring_tree_diagram.png)