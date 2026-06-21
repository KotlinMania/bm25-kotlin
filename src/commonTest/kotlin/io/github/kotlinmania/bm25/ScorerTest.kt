// port-lint: source src/scorer.rs
package io.github.kotlinmania.bm25

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SCORE_EPSILON: Float = 1e-5f

private fun assertScoredDocumentsApproxEqual(
    expected: List<ScoredDocument<Int>>,
    actual: List<ScoredDocument<Int>>,
) {
    assertEquals(expected.size, actual.size, "List sizes differ")
    for (i in expected.indices) {
        assertEquals(expected[i].id, actual[i].id, "id at index $i")
        assertTrue(
            abs(expected[i].score - actual[i].score) < SCORE_EPSILON,
            "score at index $i: expected ${expected[i].score} got ${actual[i].score}",
        )
    }
}

private fun anyEmbedding(): Embedding<UInt> =
    Embedding(TokenEmbedding(index = 1u, value = 1.0f))

private fun scorerWithEmbeddings(embeddings: List<Embedding<UInt>>): Scorer<Int, UInt> {
    val scorer = Scorer<Int, UInt>()
    for ((i, documentEmbedding) in embeddings.withIndex()) {
        scorer.upsert(i, documentEmbedding)
    }
    return scorer
}

class ScorerTest {
    @Test
    fun itScoresMissingDocumentAsNone() {
        val scorer = Scorer<Int, UInt>()
        val queryEmbedding = anyEmbedding()
        val score = scorer.score(12345, queryEmbedding)
        val matches = scorer.matches(queryEmbedding)
        assertNull(score)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun itScoresMutuallyExclusiveIndicesAsZero() {
        val documentEmbeddings = listOf(Embedding(TokenEmbedding(1u, 1.0f)))
        val scorer = scorerWithEmbeddings(documentEmbeddings)

        val queryEmbedding = Embedding(TokenEmbedding(0u, 1.0f))
        val score = scorer.score(0, queryEmbedding)

        assertEquals(0.0f, score)
    }

    @Test
    fun itScoresRareIndicesHigherThanCommonOnes() {
        // BM25 should score rare token matches higher than common token matches.
        val documentEmbeddings =
            listOf(
                Embedding(TokenEmbedding(0u, 1.0f)),
                Embedding(TokenEmbedding(0u, 1.0f)),
                Embedding(TokenEmbedding(1u, 1.0f)),
            )
        val scorer = scorerWithEmbeddings(documentEmbeddings)

        val score1 = scorer.score(0, Embedding(TokenEmbedding(0u, 1.0f)))
        val score2 = scorer.score(2, Embedding(TokenEmbedding(1u, 1.0f)))

        assertTrue(score1!! < score2!!)
    }

    @Test
    fun itScoresLongerEmbeddingsLowerThanShorterOnes() {
        val documentEmbeddings =
            listOf(
                // Longer embeddings will have a lower value for unique tokens.
                Embedding(
                    TokenEmbedding(0u, 0.9f),
                    TokenEmbedding(1u, 0.9f),
                ),
                Embedding(TokenEmbedding(0u, 1.0f)),
            )
        val scorer = scorerWithEmbeddings(documentEmbeddings)

        val score1 = scorer.score(0, Embedding(TokenEmbedding(0u, 1.0f)))
        val score2 = scorer.score(1, Embedding(TokenEmbedding(0u, 1.0f)))

        assertTrue(score1!! < score2!!)
    }

    @Test
    fun itOnlyMatchesEmbeddingsWithNonZeroScore() {
        val documentEmbeddings =
            listOf(
                Embedding(TokenEmbedding(0u, 1.0f)),
                Embedding(TokenEmbedding(1u, 1.0f)),
            )
        val scorer = scorerWithEmbeddings(documentEmbeddings)

        val queryEmbedding = Embedding(TokenEmbedding(0u, 1.0f))
        val matches = scorer.matches(queryEmbedding)

        assertScoredDocumentsApproxEqual(
            listOf(ScoredDocument(id = 0, score = 0.6931472f)),
            matches,
        )
    }

    @Test
    fun itDoesNotScoreFrequentTermsNegatively() {
        // In versions 2.2.1 and earlier, the IDF considered the total occurrences of a token
        // where it should have considered the total number of documents containing the token. In
        // instances where the occurrences exceeded the number of documents, the IDF (and
        // therefore the score) would be negative.
        // See this bug report for more information: https://github.com/Michael-JB/bm25/pull/20
        val documentEmbeddings =
            listOf(
                Embedding(
                    TokenEmbedding(0u, 1.5f),
                    TokenEmbedding(0u, 1.5f),
                ),
            )
        val scorer = scorerWithEmbeddings(documentEmbeddings)
        val queryEmbedding = Embedding(TokenEmbedding(0u, 1.0f))

        val matches = scorer.matches(queryEmbedding)

        assertTrue(matches[0].score >= 0.0f)
    }

    @Test
    fun itSortsMatchesByScore() {
        val documentEmbeddings =
            listOf(
                Embedding(
                    TokenEmbedding(0u, 0.9f),
                    TokenEmbedding(1u, 0.9f),
                ),
                Embedding(TokenEmbedding(0u, 1.0f)),
            )
        val scorer = scorerWithEmbeddings(documentEmbeddings)

        val queryEmbedding = Embedding(TokenEmbedding(0u, 1.0f))
        val matches = scorer.matches(queryEmbedding)

        assertScoredDocumentsApproxEqual(
            listOf(
                ScoredDocument(id = 1, score = 0.1823216f),
                ScoredDocument(id = 0, score = 0.16408943f),
            ),
            matches,
        )
    }
}
