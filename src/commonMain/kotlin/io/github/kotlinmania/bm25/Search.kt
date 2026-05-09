// port-lint: source src/search.rs
package io.github.kotlinmania.bm25

/**
 * A document that you can insert into a search engine. K is the type of the document id. Note
 * that it is more efficient to use a numeric type.
 */
data class Document<K>(
    /** A unique identifier for the document. */
    val id: K,
    /** The contents of the document. */
    val contents: String,
) {
    override fun toString(): String = contents

    companion object {
        /** Creates a new document with the given id and contents. */
        fun <K> new(id: K, contents: String): Document<K> = Document(id, contents)
    }
}

/** A search result, containing a document and its BM25 score. */
data class SearchResult<K>(
    /** The document that was found. */
    val document: Document<K>,
    /**
     * The BM25 score of the document. A higher score means the document is more relevant to the
     * query.
     */
    val score: Float,
)

/**
 * A search engine that ranks documents with BM25. K is the type of the document id, S is the
 * embedding space type and T is the type of the tokenizer.
 */
class SearchEngine<K, S, T : Tokenizer> internal constructor(
    private val embedder: Embedder<S, T>,
    private val scorer: Scorer<K, S>,
    private val documents: MutableMap<K, String>,
) {
    /**
     * Upserts a document into the search engine. If a document with the same id already exists,
     * it will be replaced. Note that upserting a document will change the true value of `avgdl`.
     * The more `avgdl` drifts from its true value, the less accurate the BM25 scores will be.
     */
    fun upsert(document: Document<K>) {
        val embedding = embedder.embed(document.contents)

        if (documents.containsKey(document.id)) {
            remove(document.id)
        }
        documents[document.id] = document.contents

        scorer.upsert(document.id, embedding)
    }

    /** Removes a document from the search engine if it exists. */
    fun remove(documentId: K) {
        documents.remove(documentId)
        scorer.remove(documentId)
    }

    /** Gets the contents of a document by its id. */
    fun get(documentId: K): Document<K>? {
        val contents = documents[documentId] ?: return null
        return Document(documentId, contents)
    }

    /** Returns a sequence over the documents in the search engine. */
    fun iter(): Sequence<Document<K>> = documents.asSequence().map { (id, contents) ->
        Document(id, contents)
    }

    /**
     * Searches the documents for the given query and returns the top `limit` results. Only the
     * document contents are searched, not the document ids. Pass `null` for `limit` to return all
     * matches.
     */
    fun search(query: String, limit: Int?): List<SearchResult<K>> {
        val queryEmbedding = embedder.embed(query)

        val matches = scorer.matches(queryEmbedding)

        val effectiveLimit = limit ?: Int.MAX_VALUE
        val results = ArrayList<SearchResult<K>>()
        var taken = 0
        for (scored in matches) {
            if (taken >= effectiveLimit) break
            val document = get(scored.id) ?: continue
            results.add(SearchResult(document, scored.score))
            taken += 1
        }
        return results
    }

    override fun toString(): String =
        "SearchEngine { embedder: $embedder, documents: $documents }"
}

/**
 * A consuming builder for [SearchEngine]. K is the type of the document id, S is the embedding
 * space type and T is the type of the tokenizer.
 */
class SearchEngineBuilder<K, S, T : Tokenizer> private constructor(
    private var embedderBuilder: EmbedderBuilder<S, T>,
    private val documents: MutableList<Document<K>>,
) {
    /** Sets the tokenizer of the embedder. */
    fun tokenizer(tokenizer: T): SearchEngineBuilder<K, S, T> = also {
        it.embedderBuilder = it.embedderBuilder.tokenizer(tokenizer)
    }

    /** Sets the k1 parameter of the embedder. */
    fun k1(k1: Float): SearchEngineBuilder<K, S, T> = also {
        it.embedderBuilder = it.embedderBuilder.k1(k1)
    }

    /** Sets the b parameter of the embedder. */
    fun b(b: Float): SearchEngineBuilder<K, S, T> = also {
        it.embedderBuilder = it.embedderBuilder.b(b)
    }

    /** Overrides the average document length of the embedder. */
    fun avgdl(avgdl: Float): SearchEngineBuilder<K, S, T> = also {
        it.embedderBuilder = it.embedderBuilder.avgdl(avgdl)
    }

    /** Builds the search engine. */
    fun build(): SearchEngine<K, S, T> {
        val searchEngine = SearchEngine(
            embedder = embedderBuilder.build(),
            scorer = Scorer<K, S>(),
            documents = HashMap(),
        )
        for (document in documents) {
            searchEngine.upsert(document)
        }
        return searchEngine
    }

    companion object {
        /**
         * Constructs a new SearchEngineBuilder with the given average document length. Use this
         * if you know the average document length in advance. If you don't, but you have your
         * full corpus ahead of time, use [withTokenizerAndDocuments] or
         * [withTokenizerAndCorpus] instead.
         *
         * If you have neither the full corpus nor a sample of it, you can configure the embedder
         * to disregard document length by setting `b` to 0.0. In this case, it doesn't matter
         * what value you pass to `withAvgdl`.
         *
         * The average document length is the average number of tokens in a document from your
         * corpus; if you need access to this value, you can construct an Embedder and call
         * [Embedder.avgdl] on it.
         */
        fun <K, S, T : Tokenizer> withAvgdl(
            tokenEmbedder: TokenEmbedder<S>,
            tokenizer: T,
            avgdl: Float,
        ): SearchEngineBuilder<K, S, T> = SearchEngineBuilder(
            embedderBuilder = EmbedderBuilder.withAvgdl(tokenEmbedder, tokenizer, avgdl),
            documents = ArrayList(),
        )

        /**
         * Constructs a new SearchEngineBuilder with the given documents. The search engine will
         * fit to the given documents, using the given tokenizer. When you call [build], the
         * builder will pre-populate the search engine with the given documents, and pass on the
         * tokenizer.
         */
        fun <K, S, T : Tokenizer> withTokenizerAndDocuments(
            tokenEmbedder: TokenEmbedder<S>,
            tokenizer: T,
            documents: Iterable<Document<K>>,
        ): SearchEngineBuilder<K, S, T> {
            val docs = documents.toMutableList()
            return SearchEngineBuilder(
                embedderBuilder = EmbedderBuilder.withTokenizerAndFitToCorpus(
                    tokenEmbedder,
                    tokenizer,
                    docs.map { it.contents },
                ),
                documents = docs,
            )
        }

        /**
         * Constructs a new SearchEngineBuilder with the corpus. The search engine will fit to
         * the given corpus, using the given tokenizer. When you call [build], the builder will
         * pre-populate the search engine with the given corpus, and pass on the tokenizer. This
         * function will automatically generate [UInt] ids for each entry in your corpus.
         */
        fun <S, T : Tokenizer> withTokenizerAndCorpus(
            tokenEmbedder: TokenEmbedder<S>,
            tokenizer: T,
            corpus: Iterable<String>,
        ): SearchEngineBuilder<UInt, S, T> {
            val documents = corpus.withIndex().map { (id, document) ->
                Document(id.toUInt(), document)
            }
            return withTokenizerAndDocuments(tokenEmbedder, tokenizer, documents)
        }
    }
}
