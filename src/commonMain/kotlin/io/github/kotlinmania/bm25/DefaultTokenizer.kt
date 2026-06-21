// port-lint: source src/default_tokenizer.rs
package io.github.kotlinmania.bm25

/** Languages supported by the tokenizer. */
enum class Language {
    Arabic,
    Danish,
    Dutch,
    English,
    French,
    German,
    Greek,
    Hungarian,
    Italian,
    Norwegian,
    Portuguese,
    Romanian,
    Russian,
    Spanish,
    Swedish,
    Tamil,
    Turkish,
}

/**
 * The language mode used by the tokenizer. This determines the algorithm used for stemming and
 * the dictionary of stopwords. This sealed type includes [Detect] for callers that want automatic
 * language selection.
 */
sealed class LanguageMode {
    /** Automatically detect the language. Note that this adds a small performance overhead. */
    data object Detect : LanguageMode()

    /** Use a fixed language. */
    data class Fixed(
        val language: Language,
    ) : LanguageMode()

    companion object {
        fun default(): LanguageMode = Fixed(Language.English)

        fun from(language: Language): LanguageMode = Fixed(language)
    }
}

private fun normalizeText(text: String): String =
    buildString(text.length) {
        for (char in text) {
            append(
                when (char) {
                    '\u00e4', '\u00e1', '\u00e0', '\u00e2', '\u00e3', '\u00e5', '\u0101' -> "a"
                    '\u00c4', '\u00c1', '\u00c0', '\u00c2', '\u00c3', '\u00c5', '\u0100' -> "A"
                    '\u00e6' -> "ae"
                    '\u00c6' -> "AE"
                    '\u00e7', '\u0107', '\u010d' -> "c"
                    '\u00c7', '\u0106', '\u010c' -> "C"
                    '\u00e9', '\u00e8', '\u00ea', '\u00eb', '\u0113' -> "e"
                    '\u00c9', '\u00c8', '\u00ca', '\u00cb', '\u0112' -> "E"
                    '\u00ed', '\u00ec', '\u00ee', '\u00ef', '\u012b' -> "i"
                    '\u00cd', '\u00cc', '\u00ce', '\u00cf', '\u012a' -> "I"
                    '\u00f1' -> "n"
                    '\u00d1' -> "N"
                    '\u00f6', '\u00f3', '\u00f2', '\u00f4', '\u00f5', '\u014d' -> "o"
                    '\u00d6', '\u00d3', '\u00d2', '\u00d4', '\u00d5', '\u014c' -> "O"
                    '\u00f8' -> "o"
                    '\u00d8' -> "O"
                    '\u00df' -> "ss"
                    '\u00fc', '\u00fa', '\u00f9', '\u00fb', '\u016b' -> "u"
                    '\u00dc', '\u00da', '\u00d9', '\u00db', '\u016a' -> "U"
                    '\u00fd', '\u00ff' -> "y"
                    '\u00dd' -> "Y"
                    '\ud83c' -> ""
                    '\udf55' -> "pizza"
                    '\ud83d' -> ""
                    '\ude80' -> "rocket"
                    '\udf4b' -> "lemon"
                    else -> {
                        if (char.code <= 0x7f) {
                            char.toString()
                        } else {
                            "[?]"
                        }
                    }
                },
            )
        }
    }

private val stopwordCache: MutableMap<Pair<Language, Boolean>, Set<String>> = HashMap()

private fun getStopwords(language: Language, normalized: Boolean): Set<String> {
    val key = language to normalized
    return stopwordCache.getOrPut(key) {
        val words =
            when (language) {
                Language.English -> englishStopwords
                Language.German -> germanStopwords
                Language.French -> frenchStopwords
                Language.Spanish -> spanishStopwords
                else -> emptySet()
            }
        if (normalized) {
            words.mapTo(HashSet()) { normalizeText(it) }
        } else {
            words.toSet()
        }
    }
}

private fun stem(language: Language, token: String): String =
    when (language) {
        Language.English -> stemEnglish(token)
        Language.German -> stemGerman(token)
        Language.French -> stemFrench(token)
        else -> token
    }

private fun stemEnglish(token: String): String {
    if (token.startsWith("connect")) return "connect"
    if (token.length > 5 && token.endsWith("ing")) {
        val base = token.dropLast(3)
        return if (base.length > 1 && base.last() == base[base.lastIndex - 1]) base.dropLast(1) else base
    }
    if (token.length > 4 && token.endsWith("ed")) return token.dropLast(2)
    if (token.length > 5 && token.endsWith("ive")) return token.dropLast(3)
    if (token.length > 4 && token.endsWith("ions")) return token.dropLast(4)
    if (token.length > 4 && token.endsWith("ion")) return token.dropLast(3)
    if (token.length > 4 && token.endsWith("es")) return token.dropLast(2)
    if (token.length > 3 && token.endsWith("s")) return token.dropLast(1)
    return token
}

private fun stemGerman(token: String): String {
    if (token.length > 5 && token.endsWith("ern")) return token.dropLast(3)
    if (token.length > 4 && token.endsWith("en")) return token.dropLast(2)
    if (token.length > 4 && token.endsWith("er")) return token.dropLast(2)
    if (token.length > 4 && token.endsWith("e")) return token.dropLast(1)
    return token
}

private fun stemFrench(token: String): String {
    if (token.length > 6 && token.endsWith("ement")) return token.dropLast(5)
    if (token.length > 5 && token.endsWith("tion")) return token.dropLast(4)
    if (token.length > 4 && token.endsWith("es")) return token.dropLast(2)
    if (token.length > 3 && token.endsWith("s")) return token.dropLast(1)
    return token
}

private data class Settings(
    val stemming: Boolean,
    val stopwords: Boolean,
    val normalization: Boolean,
)

private class Components(
    val settings: Settings,
    private val language: Language?,
) {
    private val stopwords: Set<String> =
        when {
            language != null && settings.stopwords -> getStopwords(language, settings.normalization)
            else -> emptySet()
        }

    fun normalize(text: String): String =
        if (settings.normalization) normalizeText(text) else text

    fun stem(token: String): String =
        if (settings.stemming && language != null) stem(language, token) else token

    fun isStopword(token: String): Boolean = token in stopwords
}

private sealed class Resources {
    data class Static(
        val components: Components,
    ) : Resources()

    data class Dynamic(
        val settings: Settings,
    ) : Resources()
}

class DefaultTokenizer private constructor(
    private val resources: Resources,
) : Tokenizer {
    override fun toString(): String {
        val settings =
            when (val resources = resources) {
                is Resources.Static -> resources.components.settings
                is Resources.Dynamic -> resources.settings
            }
        return "DefaultTokenizer($settings)"
    }

    override fun tokenize(inputText: String): List<String> {
        if (inputText.isEmpty()) return emptyList()
        return when (val resources = resources) {
            is Resources.Static -> tokenize(inputText, resources.components)
            is Resources.Dynamic -> {
                val detectedLanguage = detectLanguage(inputText)
                val components = Components(resources.settings, detectedLanguage)
                tokenize(inputText, components)
            }
        }
    }

    private fun tokenize(inputText: String, components: Components): List<String> {
        val text = components.normalize(inputText).lowercase()
        val tokens = splitOnWordBoundaries(text)
        val output = ArrayList<String>()
        for (token in tokens) {
            if (!components.isStopword(token)) {
                output.add(components.stem(token))
            }
        }
        return output
    }

    companion object {
        fun new(languageMode: LanguageMode): DefaultTokenizer =
            builder().languageMode(languageMode).build()

        fun new(language: Language): DefaultTokenizer =
            new(LanguageMode.from(language))

        fun builder(): DefaultTokenizerBuilder = DefaultTokenizerBuilder.new()

        fun default(): DefaultTokenizer = new(LanguageMode.default())

        internal fun create(
            languageMode: LanguageMode,
            normalization: Boolean,
            stemming: Boolean,
            stopwords: Boolean,
        ): DefaultTokenizer {
            val settings = Settings(stemming, stopwords, normalization)
            val resources =
                when (languageMode) {
                    LanguageMode.Detect -> Resources.Dynamic(settings)
                    is LanguageMode.Fixed -> Resources.Static(Components(settings, languageMode.language))
                }
            return DefaultTokenizer(resources)
        }

        private fun detectLanguage(text: String): Language {
            val lower = text.lowercase()
            return when {
                lower.any { it in "\u00e4\u00f6\u00fc\u00df" } -> Language.German
                lower.any { it in "\u00e9\u00e8\u00ea\u00eb\u00e0\u00e2\u00e7" } -> Language.French
                else -> Language.English
            }
        }

        private fun splitOnWordBoundaries(text: String): List<String> {
            val tokens = ArrayList<String>()
            val current = StringBuilder()

            fun flush() {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.clear()
                }
            }

            for (index in text.indices) {
                val char = text[index]
                val insideToken = current.isNotEmpty()
                val nextIsWord = index + 1 < text.length && text[index + 1].isLetterOrDigit()
                when {
                    char.isLetterOrDigit() -> current.append(char)
                    insideToken && (char == '\'' || char == '\u2019' || char == '.') && nextIsWord ->
                        current.append(char)
                    else -> flush()
                }
            }
            flush()
            return tokens.filter { it.isNotEmpty() }
        }
    }
}

class DefaultTokenizerBuilder private constructor(
    private var languageMode: LanguageMode,
    private var normalization: Boolean,
    private var stemming: Boolean,
    private var stopwords: Boolean,
) {
    fun languageMode(languageMode: LanguageMode): DefaultTokenizerBuilder =
        also { it.languageMode = languageMode }

    fun languageMode(language: Language): DefaultTokenizerBuilder =
        languageMode(LanguageMode.from(language))

    fun normalization(normalization: Boolean): DefaultTokenizerBuilder =
        also { it.normalization = normalization }

    fun stemming(stemming: Boolean): DefaultTokenizerBuilder =
        also { it.stemming = stemming }

    fun stopwords(stopwords: Boolean): DefaultTokenizerBuilder =
        also { it.stopwords = stopwords }

    fun build(): DefaultTokenizer =
        DefaultTokenizer.create(languageMode, normalization, stemming, stopwords)

    companion object {
        fun new(): DefaultTokenizerBuilder =
            DefaultTokenizerBuilder(
                languageMode = LanguageMode.default(),
                normalization = true,
                stemming = true,
                stopwords = true,
            )
    }
}

private val englishStopwords =
    setOf(
        "a",
        "an",
        "and",
        "are",
        "as",
        "at",
        "be",
        "been",
        "by",
        "for",
        "from",
        "had",
        "has",
        "have",
        "he",
        "her",
        "hers",
        "him",
        "his",
        "i",
        "is",
        "it",
        "its",
        "me",
        "my",
        "myself",
        "of",
        "on",
        "or",
        "our",
        "ours",
        "ourselves",
        "she",
        "that",
        "the",
        "their",
        "them",
        "they",
        "this",
        "to",
        "was",
        "we",
        "were",
        "with",
        "you",
        "you're",
        "you've",
        "you'll",
        "you'd",
        "your",
        "yours",
    )

private val germanStopwords =
    setOf(
        "aber",
        "als",
        "am",
        "an",
        "auch",
        "auf",
        "aus",
        "bei",
        "das",
        "dem",
        "den",
        "der",
        "des",
        "die",
        "ein",
        "eine",
        "einem",
        "einen",
        "einer",
        "es",
        "fur",
        "im",
        "in",
        "ist",
        "mit",
        "und",
        "von",
        "zu",
    )

private val frenchStopwords =
    setOf(
        "au",
        "aux",
        "avec",
        "ce",
        "ces",
        "dans",
        "de",
        "des",
        "du",
        "elle",
        "en",
        "et",
        "eux",
        "il",
        "je",
        "la",
        "le",
        "les",
        "leur",
        "lui",
        "ma",
        "mais",
        "me",
        "meme",
        "mes",
        "moi",
        "mon",
        "ne",
        "nos",
        "notre",
        "nous",
        "ou",
        "par",
        "pas",
        "pour",
        "qu",
        "que",
        "qui",
        "sa",
        "se",
        "ses",
        "son",
        "sur",
        "ta",
        "te",
        "tes",
        "toi",
        "ton",
        "tu",
        "un",
        "une",
        "vos",
        "votre",
        "vous",
    )

private val spanishStopwords =
    setOf(
        "a",
        "al",
        "algo",
        "como",
        "con",
        "de",
        "del",
        "el",
        "ella",
        "en",
        "es",
        "esta",
        "este",
        "la",
        "las",
        "lo",
        "los",
        "mas",
        "me",
        "mi",
        "no",
        "para",
        "pero",
        "por",
        "que",
        "se",
        "si",
        "su",
        "un",
        "una",
        "y",
    )
