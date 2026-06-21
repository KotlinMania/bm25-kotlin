// port-lint: source src/default_tokenizer.rs
package io.github.kotlinmania.bm25

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultTokenizerTest {
    @Test
    fun itCanTokenizeEnglish() {
        val text = "space station"
        val tokenizer = DefaultTokenizer.new(Language.English)

        val tokens = tokenizer.tokenize(text)

        assertEquals(listOf("space", "station"), tokens)
    }

    @Test
    fun itConvertsToLowercase() {
        val text = "SPACE STATION"
        val tokenizer = DefaultTokenizer.new(Language.English)

        val tokens = tokenizer.tokenize(text)

        assertEquals(listOf("space", "station"), tokens)
    }

    @Test
    fun itRemovesWhitespace() {
        val text = "\tspace\r\nstation\n space       station"
        val tokenizer = DefaultTokenizer.new(Language.English)

        val tokens = tokenizer.tokenize(text)

        assertEquals(listOf("space", "station", "space", "station"), tokens)
    }

    @Test
    fun itRemovesStopwords() {
        val text = "i me my myself we our ours ourselves you you're you've you'll you'd"
        val tokenizer = DefaultTokenizer.new(Language.English)

        val tokens = tokenizer.tokenize(text)

        assertTrue(tokens.isEmpty())
    }

    @Test
    fun itKeepsNumbers() {
        val text = "42 1337 3.14"
        val tokenizer = DefaultTokenizer.new(Language.English)

        val tokens = tokenizer.tokenize(text)

        assertEquals(listOf("42", "1337", "3.14"), tokens)
    }

    @Test
    fun itKeepsContractedWords() {
        val text = "can't you're won't let's couldn't've"
        val tokenizer =
            DefaultTokenizer
                .builder()
                .languageMode(Language.English)
                .stemming(false)
                .stopwords(false)
                .build()

        val tokens = tokenizer.tokenize(text)

        assertEquals(listOf("can't", "you're", "won't", "let's", "couldn't've"), tokens)
    }

    @Test
    fun itRemovesPunctuation() {
        val testCases =
            listOf(
                "space, station!" to listOf("space", "station"),
                "space,station" to listOf("space", "station"),
                "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~" to emptyList(),
            )
        val tokenizer = DefaultTokenizer.new(Language.English)

        for ((text, expected) in testCases) {
            val tokens = tokenizer.tokenize(text)
            assertEquals(expected, tokens)
        }
    }

    @Test
    fun itStemsWords() {
        val text = "connection connections connective connected connecting connect"
        val tokenizer = DefaultTokenizer.new(Language.English)

        val tokens = tokenizer.tokenize(text)

        assertEquals(listOf("connect", "connect", "connect", "connect", "connect", "connect"), tokens)
    }

    @Test
    fun itTokenizesEmojisAsText() {
        val text = "\ud83c\udf55 \ud83d\ude80 \ud83c\udf4b"
        val tokenizer = DefaultTokenizer.new(Language.English)

        val tokens = tokenizer.tokenize(text)

        assertEquals(listOf("pizza", "rocket", "lemon"), tokens)
    }

    @Test
    fun itConvertsUnicodeToAscii() {
        val text = "gem\u00fcse, Gie\u00dfen"
        val tokenizer =
            DefaultTokenizer
                .builder()
                .languageMode(Language.German)
                .stemming(false)
                .build()

        val tokens = tokenizer.tokenize(text)

        assertEquals(listOf("gemuse", "giessen"), tokens)
    }

    @Test
    fun itHandlesEmptyInput() {
        val tokenizer = DefaultTokenizer.new(LanguageMode.Detect)

        val tokens = tokenizer.tokenize("")

        assertTrue(tokens.isEmpty())
    }

    @Test
    fun itDoesNotConvertUnicodeWhenNormalizationDisabled() {
        val text = "\u00e9tude"
        val tokenizer =
            DefaultTokenizer
                .builder()
                .languageMode(Language.French)
                .normalization(false)
                .stemming(false)
                .build()

        val tokens = tokenizer.tokenize(text)

        assertEquals(listOf("\u00e9tude"), tokens)
    }

    @Test
    fun itDoesNotRemoveStopwordsWhenStopwordsDisabled() {
        val text = "i my myself we you have"
        val tokenizer =
            DefaultTokenizer
                .builder()
                .languageMode(Language.English)
                .stopwords(false)
                .build()

        val tokens = tokenizer.tokenize(text)

        assertEquals(listOf("i", "my", "myself", "we", "you", "have"), tokens)
    }

    @Test
    fun itDoesNotStemWhenStemmingDisabled() {
        val text = "connection connections connective connect"
        val tokenizer =
            DefaultTokenizer
                .builder()
                .languageMode(Language.English)
                .stemming(false)
                .build()

        val tokens = tokenizer.tokenize(text)

        assertEquals(listOf("connection", "connections", "connective", "connect"), tokens)
    }
}
