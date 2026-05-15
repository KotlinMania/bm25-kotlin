// port-lint: source src/search.rs
package io.github.kotlinmania.bm25

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object SearchWhitespaceTokenizer : Tokenizer {
    override fun tokenize(inputText: String): List<String> =
        inputText.split(' ', '\t', '\n').filter { it.isNotEmpty() }
}

class SearchTest {
    @Test
    fun itCanInsertADocument() {
        val searchEngine = SearchEngineBuilder
            .withAvgdl<String, UInt, Tokenizer>(UIntTokenEmbedder, SearchWhitespaceTokenizer, 2.0f)
            .build()
        val document = Document(id = "hello world", contents = "bananas and apples")
        val documentId = document.id

        searchEngine.upsert(document.copy())
        val result = searchEngine.get(documentId)

        assertEquals(document, result)
    }

    @Test
    fun itCanRemoveADocument() {
        val searchEngine = SearchEngineBuilder
            .withAvgdl<Int, UInt, Tokenizer>(UIntTokenEmbedder, SearchWhitespaceTokenizer, 2.0f)
            .build()
        val document = Document(id = 123, contents = "bananas and apples")
        val documentId = document.id

        searchEngine.upsert(document)
        searchEngine.remove(documentId)

        assertNull(searchEngine.get(documentId))
    }

    @Test
    fun handlesEmptyInput() {
        val searchEngine = SearchEngineBuilder
            .withAvgdl<UInt, UInt, Tokenizer>(UIntTokenEmbedder, SearchWhitespaceTokenizer, 2.0f)
            .build()
        val document = Document(id = 123u, contents = "")

        searchEngine.upsert(document)

        val results = searchEngine.search("bacon sandwich", 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun handlesEmptySearch() {
        val searchEngine = SearchEngineBuilder
            .withAvgdl<UInt, UInt, Tokenizer>(UIntTokenEmbedder, SearchWhitespaceTokenizer, 2.0f)
            .build()
        val document = Document(id = 123u, contents = "pencil and paper")

        searchEngine.upsert(document)

        val results = searchEngine.search("", 5)
        assertTrue(results.isEmpty())
    }
}
