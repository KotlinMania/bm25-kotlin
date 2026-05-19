// port-lint: source src/embedder.rs
package io.github.kotlinmania.bm25

typealias DefaultTokenEmbedder = UInt
typealias DefaultEmbeddingSpace = UInt

/**
 * Represents a token embedded in a D-dimensional space.
 */
data class TokenEmbedding<D>(
    /** The index of the token in the embedding space. */
    val index: D,
    /** The value of the token in the embedding space. */
    val value: Float,
)

/**
 * Represents a document embedded in a D-dimensional space.
 */
class Embedding<D>(
    val tokens: List<TokenEmbedding<D>>,
) : List<TokenEmbedding<D>> by tokens {

    constructor(vararg tokens: TokenEmbedding<D>) : this(tokens.toList())

    /** Returns a sequence over the indices of the embedding. */
    fun indices(): Sequence<D> = tokens.asSequence().map { it.index }

    /** Returns a sequence over the values of the embedding. */
    fun values(): Sequence<Float> = tokens.asSequence().map { it.value }

    override fun equals(other: Any?): Boolean = other is Embedding<*> && other.tokens == tokens

    override fun hashCode(): Int = tokens.hashCode()

    override fun toString(): String = "Embedding($tokens)"
}

/** A trait for embedding. Implement this to customise the embedding space and function. */
fun interface TokenEmbedder<S> {
    /** Embeds a token into the embedding space. */
    fun embed(token: String): S
}

object UIntTokenEmbedder : TokenEmbedder<UInt> {
    override fun embed(token: String): UInt = Fxhash.hash32(token)
}

object ULongTokenEmbedder : TokenEmbedder<ULong> {
    override fun embed(token: String): ULong = Fxhash.hash64(token)
}

/**
 * Creates sparse embeddings from text. S is the embedding space type and T is the type of the
 * tokenizer.
 */
class Embedder<S, T : Tokenizer> internal constructor(
    private val tokenizer: T,
    private val tokenEmbedder: TokenEmbedder<S>,
    private val k1: Float,
    private val b: Float,
    private val avgdlValue: Float,
) {
    /** Returns the average document length used by the embedder. */
    fun avgdl(): Float = avgdlValue

    /** Embeds the given text into the embedding space. */
    fun embed(text: String): Embedding<S> {
        val tokens = tokenizer.tokenize(text)
        val avgdl = if (avgdlValue <= 0.0f) FALLBACK_AVGDL else avgdlValue
        val indices = tokens.map { tokenEmbedder.embed(it) }

        val counts = HashMap<S, Int>()
        for (token in indices) {
            counts[token] = (counts[token] ?: 0) + 1
        }

        val values = indices.map { i ->
            val tokenFrequency = (counts[i] ?: 0).toFloat()
            val numerator = tokenFrequency * (k1 + 1.0f)
            val denominator = tokenFrequency +
                k1 * (1.0f - b + b * (tokens.size.toFloat() / avgdl))
            numerator / denominator
        }

        val embedded = ArrayList<TokenEmbedding<S>>(indices.size)
        for (i in indices.indices) {
            embedded.add(TokenEmbedding(indices[i], values[i]))
        }
        return Embedding(embedded)
    }

    companion object {
        const val FALLBACK_AVGDL: Float = 256.0f
    }
}

/** A consuming builder for [Embedder]. */
class EmbedderBuilder<S, T : Tokenizer> private constructor(
    private val tokenEmbedder: TokenEmbedder<S>,
    private var tokenizer: T,
    private var k1: Float,
    private var b: Float,
    private var avgdl: Float,
) {
    /** Sets the k1 parameter for the embedder. The default value is 1.2. */
    fun k1(k1: Float): EmbedderBuilder<S, T> = also { it.k1 = k1 }

    /** Sets the b parameter for the embedder. The default value is 0.75. */
    fun b(b: Float): EmbedderBuilder<S, T> = also { it.b = b }

    /** Overrides the average document length for the embedder. */
    fun avgdl(avgdl: Float): EmbedderBuilder<S, T> = also { it.avgdl = avgdl }

    /** Sets the tokenizer for the embedder. */
    fun tokenizer(tokenizer: T): EmbedderBuilder<S, T> = also { it.tokenizer = tokenizer }

    /** Builds the [Embedder]. */
    fun build(): Embedder<S, T> = Embedder(
        tokenizer = tokenizer,
        tokenEmbedder = tokenEmbedder,
        k1 = k1,
        b = b,
        avgdlValue = avgdl,
    )

    companion object {
        /**
         * Constructs a new EmbedderBuilder with the given average document length. Use this if
         * you know the average document length in advance. If you don't, but you have your full
         * corpus ahead of time, use [withFitToCorpus] or [withTokenizerAndFitToCorpus] instead.
         *
         * If you have neither the full corpus nor a sample of it, you can configure the embedder
         * to disregard document length by setting `b` to 0.0. In this case, it doesn't matter
         * what value you pass to `withAvgdl`.
         *
         * The average document length is the average number of tokens in a document from your
         * corpus; if you need access to this value, you can construct an Embedder and call
         * [Embedder.avgdl] on it.
         */
        fun withAvgdl(avgdl: Float): EmbedderBuilder<DefaultTokenEmbedder, DefaultTokenizer> =
            withAvgdl(UIntTokenEmbedder, DefaultTokenizer.default(), avgdl)

        /**
         * Constructs a new EmbedderBuilder with the given average document length. Use this if
         * you know the average document length in advance. If you don't, but you have your full
         * corpus ahead of time, use [withTokenizerAndFitToCorpus] instead.
         *
         * If you have neither the full corpus nor a sample of it, you can configure the embedder
         * to disregard document length by setting `b` to 0.0. In this case, it doesn't matter
         * what value you pass to `withAvgdl`.
         *
         * The average document length is the average number of tokens in a document from your
         * corpus; if you need access to this value, you can construct an Embedder and call
         * [Embedder.avgdl] on it.
         */
        fun <S, T : Tokenizer> withAvgdl(
            tokenEmbedder: TokenEmbedder<S>,
            tokenizer: T,
            avgdl: Float,
        ): EmbedderBuilder<S, T> = EmbedderBuilder(
            tokenEmbedder = tokenEmbedder,
            tokenizer = tokenizer,
            k1 = 1.2f,
            b = 0.75f,
            avgdl = avgdl,
        )

        /**
         * Constructs a new EmbedderBuilder with its average document length fit to the given
         * corpus. Use this if you have the full corpus (or a sample of it) available in advance.
         * The embedder will assume the given tokenizer.
         */
        fun <S, T : Tokenizer> withTokenizerAndFitToCorpus(
            tokenEmbedder: TokenEmbedder<S>,
            tokenizer: T,
            corpus: List<String>,
        ): EmbedderBuilder<S, T> {
            val avgdl = if (corpus.isEmpty()) {
                Embedder.FALLBACK_AVGDL
            } else {
                var totalLen: Long = 0
                for (doc in corpus) {
                    totalLen += tokenizer.tokenize(doc).size.toLong()
                }
                (totalLen.toDouble() / corpus.size.toDouble()).toFloat()
            }
            return EmbedderBuilder(
                tokenEmbedder = tokenEmbedder,
                tokenizer = tokenizer,
                k1 = 1.2f,
                b = 0.75f,
                avgdl = avgdl,
            )
        }

        /**
         * Constructs a new EmbedderBuilder with its average document length fit to the given
         * corpus. Use this if you have the full corpus (or a sample of it) available in advance.
         * This function uses the default tokenizer configured with the input language mode. The
         * embedder will assume this tokenizer.
         */
        fun <S> withFitToCorpus(
            tokenEmbedder: TokenEmbedder<S>,
            languageMode: LanguageMode,
            corpus: List<String>,
        ): EmbedderBuilder<S, DefaultTokenizer> {
            val tokenizer = DefaultTokenizer.new(languageMode)
            return withTokenizerAndFitToCorpus(tokenEmbedder, tokenizer, corpus)
        }

        /**
         * Constructs a new EmbedderBuilder with its average document length fit to the given
         * corpus, using a fixed tokenizer language.
         */
        fun <S> withFitToCorpus(
            tokenEmbedder: TokenEmbedder<S>,
            language: Language,
            corpus: List<String>,
        ): EmbedderBuilder<S, DefaultTokenizer> =
            withFitToCorpus(tokenEmbedder, LanguageMode.from(language), corpus)

        /**
         * Constructs a new EmbedderBuilder with its average document length fit to the given
         * corpus, using the default token embedder and default tokenizer.
         */
        fun withFitToCorpus(
            languageMode: LanguageMode,
            corpus: List<String>,
        ): EmbedderBuilder<DefaultTokenEmbedder, DefaultTokenizer> =
            withFitToCorpus(UIntTokenEmbedder, languageMode, corpus)

        /**
         * Constructs a new EmbedderBuilder with its average document length fit to the given
         * corpus, using the default token embedder and a fixed tokenizer language.
         */
        fun withFitToCorpus(
            language: Language,
            corpus: List<String>,
        ): EmbedderBuilder<DefaultTokenEmbedder, DefaultTokenizer> =
            withFitToCorpus(LanguageMode.from(language), corpus)
    }
}

/** Sets the language mode for the embedder tokenizer. */
fun <S> EmbedderBuilder<S, DefaultTokenizer>.languageMode(
    languageMode: LanguageMode,
): EmbedderBuilder<S, DefaultTokenizer> =
    tokenizer(DefaultTokenizer.new(languageMode))

/** Sets the language mode for the embedder tokenizer. */
fun <S> EmbedderBuilder<S, DefaultTokenizer>.languageMode(
    language: Language,
): EmbedderBuilder<S, DefaultTokenizer> =
    languageMode(LanguageMode.from(language))
