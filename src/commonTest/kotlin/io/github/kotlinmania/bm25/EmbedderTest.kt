// port-lint: source src/embedder.rs
package io.github.kotlinmania.bm25

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object WhitespaceTokenizer : Tokenizer {
    override fun tokenize(inputText: String): List<String> =
        inputText.split(' ', '\t', '\n').filter { it.isNotEmpty() }
}

private data class MyType(
    val value: UInt,
)

private object MyTypeEmbedder : TokenEmbedder<MyType> {
    override fun embed(token: String): MyType = MyType(42u)
}

private object SplitOnTTokenizer : Tokenizer {
    override fun tokenize(inputText: String): List<String> =
        inputText.split('T').filter { it.isNotEmpty() }
}

class EmbedderTest {
    @Test
    fun itWeightsUniqueWordsEqually() {
        val embedder =
            EmbedderBuilder
                .withAvgdl(UIntTokenEmbedder, WhitespaceTokenizer, 3.0f)
                .build()
        val embedding = embedder.embed("banana apple orange")

        assertTrue(embedding.size == 3)
        assertTrue(embedding.windowed(2).all { it[0].value == it[1].value })
    }

    @Test
    fun itHandlesEmptyInput() {
        val embedder =
            EmbedderBuilder
                .withAvgdl(UIntTokenEmbedder, WhitespaceTokenizer, 1.0f)
                .build()

        val embedding = embedder.embed("")

        assertTrue(embedding.isEmpty())
    }

    @Test
    fun itAllowsCustomisationOfEmbedder() {
        val embedder =
            EmbedderBuilder
                .withAvgdl(MyTypeEmbedder, WhitespaceTokenizer, 2.0f)
                .build()

        val embedding = embedder.embed("space station")

        assertEquals(
            listOf(MyType(42u), MyType(42u)),
            embedding.indices().toList(),
        )
    }

    @Test
    fun itAllowsCustomisationOfTokenizer() {
        val embedder =
            EmbedderBuilder
                .withAvgdl(UIntTokenEmbedder, SplitOnTTokenizer, 1.0f)
                .build()

        val embedding = embedder.embed("CupTofTtea")

        assertEquals(
            listOf(3568447556u, 3221979461u, 415655421u),
            embedding.indices().toList(),
        )
    }
}
