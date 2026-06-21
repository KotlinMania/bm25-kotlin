// port-lint: source src/scorer.rs
package io.github.kotlinmania.bm25

import kotlin.math.ln

/**
 * A document scored by the BM25 algorithm. K is the type of the document id.
 */
data class ScoredDocument<K>(
    /** The id of the document. */
    val id: K,
    /** The BM25 score of the document. */
    val score: Float,
)

/**
 * Efficiently scores the relevance of a query embedding to document embeddings using BM25.
 * K is the type of the document id and D is the type of the embedding space.
 */
class Scorer<K, D> {
    // A mapping from document ids to the document embeddings.
    private val embeddings: MutableMap<K, Embedding<D>> = HashMap()

    // A mapping from token indices to the set of documents that contain that token.
    private val invertedTokenIndex: MutableMap<D, MutableSet<K>> = HashMap()

    /**
     * Upserts a document embedding into the scorer. If an embedding with the same id already
     * exists, it will be replaced. Note that upserting a document will change the true value of
     * `avgdl`. The more `avgdl` drifts from its true value, the less accurate the BM25 scores
     * will be.
     */
    fun upsert(documentId: K, embedding: Embedding<D>) {
        if (embeddings.containsKey(documentId)) {
            remove(documentId)
        }
        for (tokenIndex in embedding.indices()) {
            val documentsContainingToken = invertedTokenIndex.getOrPut(tokenIndex) { HashSet() }
            documentsContainingToken.add(documentId)
        }
        embeddings[documentId] = embedding
    }

    /** Removes a document embedding from the scorer if it exists. */
    fun remove(documentId: K) {
        val embedding = embeddings.remove(documentId) ?: return
        for (tokenIndex in embedding.indices()) {
            val matches = invertedTokenIndex[tokenIndex]
            matches?.remove(documentId)
        }
    }

    /**
     * Scores the embedding for the given document against a given query embedding. Returns `null`
     * if the document does not exist in the scorer.
     */
    fun score(documentId: K, queryEmbedding: Embedding<D>): Float? {
        val documentEmbedding = embeddings[documentId] ?: return null
        return computeScore(documentEmbedding, queryEmbedding)
    }

    /**
     * Returns all documents relevant (i.e., score > 0) to the given query embedding, sorted by
     * relevance.
     */
    fun matches(queryEmbedding: Embedding<D>): List<ScoredDocument<K>> {
        val candidateIds = HashSet<K>()
        for (tokenIndex in queryEmbedding.indices()) {
            val documentSet = invertedTokenIndex[tokenIndex] ?: continue
            candidateIds.addAll(documentSet)
        }

        val scores = ArrayList<ScoredDocument<K>>(candidateIds.size)
        for (documentId in candidateIds) {
            val documentEmbedding = embeddings[documentId] ?: continue
            scores.add(ScoredDocument(documentId, computeScore(documentEmbedding, queryEmbedding)))
        }

        scores.sortWith { a, b ->
            if (a.score.isNaN() || b.score.isNaN()) {
                0
            } else {
                b.score.compareTo(a.score)
            }
        }
        return scores
    }

    private fun idf(tokenIndex: D): Float {
        val tokenFrequency = (invertedTokenIndex[tokenIndex]?.size ?: 0).toFloat()
        val numerator = embeddings.size.toFloat() - tokenFrequency + 0.5f
        val denominator = tokenFrequency + 0.5f
        return ln(1.0f + (numerator / denominator))
    }

    private fun computeScore(
        documentEmbedding: Embedding<D>,
        queryEmbedding: Embedding<D>,
    ): Float {
        var documentScore = 0.0f

        for (tokenIndex in queryEmbedding.indices()) {
            val tokenIdf = idf(tokenIndex)
            val tokenIndexValue =
                documentEmbedding
                    .firstOrNull { it.index == tokenIndex }
                    ?.value
                    ?: 0.0f
            val tokenScore = tokenIdf * tokenIndexValue
            documentScore += tokenScore
        }
        return documentScore
    }
}
