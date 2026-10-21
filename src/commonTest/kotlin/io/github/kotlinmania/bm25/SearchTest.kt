// port-lint: source search.rs
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
        val searchEngine =
            SearchEngineBuilder
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
        val searchEngine =
            SearchEngineBuilder
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
        val searchEngine =
            SearchEngineBuilder
                .withAvgdl<UInt, UInt, Tokenizer>(UIntTokenEmbedder, SearchWhitespaceTokenizer, 2.0f)
                .build()
        val document = Document(id = 123u, contents = "")

        searchEngine.upsert(document)

        val results = searchEngine.search("bacon sandwich", 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun handlesEmptySearch() {
        val searchEngine =
            SearchEngineBuilder
                .withAvgdl<UInt, UInt, Tokenizer>(UIntTokenEmbedder, SearchWhitespaceTokenizer, 2.0f)
                .build()
        val document = Document(id = 123u, contents = "pencil and paper")

        searchEngine.upsert(document)

        val results = searchEngine.search("", 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun searchReturnsRelevantDocuments() {
        val corpus = listOf("space station", "bacon and avocado sandwich")
        val searchEngine =
            SearchEngineBuilder.withCorpus(Language.English, corpus).build()

        val results = searchEngine.search("sandwich with bacon", 5)
        assertEquals(1, results.size)
        assertEquals("bacon and avocado sandwich", results[0].document.contents)
        assertTrue(results[0].score > 0.0f)
    }

    @Test
    fun searchDoesNotReturnUnrelatedDocuments() {
        val corpus = listOf("space station", "bacon and avocado sandwich")
        val searchEngine =
            SearchEngineBuilder.withCorpus(Language.English, corpus).build()

        val results = searchEngine.search("maths and computer science", 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun itCanUpdateADocument() {
        val documentId = "helloWorld"
        val document = Document.new(documentId, "bananas and apples")
        val searchEngine =
            SearchEngineBuilder.withDocuments(Language.English, listOf(document)).build()
        val newDocument = Document.new(documentId, "oranges and papayas")

        searchEngine.upsert(newDocument.copy())
        val result = searchEngine.get(documentId)

        assertEquals(newDocument, result)
    }

    @Test
    fun itRanksShorterDocumentsHigher() {
        val documents =
            listOf(
                Document(
                    id = 0u,
                    contents = "Correct horse battery staple bacon bacon bacon",
                ),
                Document(
                    id = 1u,
                    contents = "Correct horse battery staple",
                ),
            )
        val searchEngine =
            SearchEngineBuilder.withDocuments(Language.English, documents).build()

        val results = searchEngine.search("staple", 2)

        assertEquals(2, results.size)
        assertEquals(1u, results[0].document.id)
        assertEquals(0u, results[1].document.id)
        assertTrue(results[0].score > results[1].score)
    }

    @Test
    fun itMatchesCommonUnicodeEquivalents() {
        val corpus = listOf("étude")
        val searchEngine =
            SearchEngineBuilder.withCorpus(Language.French, corpus).build()

        val results1 = searchEngine.search("etude", null)
        val results2 = searchEngine.search("étude", null)

        assertEquals(1, results1.size)
        assertEquals(1, results2.size)
        assertEquals(results1, results2)
    }

    @Test
    fun itCanSearchForEmoji() {
        val corpus = listOf("🔥")
        val searchEngine =
            SearchEngineBuilder.withCorpus(Language.English, corpus).build()

        val results1 = searchEngine.search("🔥", null)
        val results2 = searchEngine.search("fire", null)

        assertEquals(1, results1.size)
        assertEquals(1, results2.size)
        assertEquals(results1, results2)
    }
}
