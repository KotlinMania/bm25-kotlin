// port-lint: source src/tokenizer.rs
package io.github.kotlinmania.bm25

/**
 * A tokenizer splits text into a sequence of tokens. Implement this interface to use this crate
 * with your own tokenizer.
 */
fun interface Tokenizer {
    /** Tokenizes the input text. */
    fun tokenize(inputText: String): List<String>
}
