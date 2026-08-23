// port-lint: source default_tokenizer.rs
package io.github.kotlinmania.bm25

// Porter stemmer algorithm, matching the behaviour of the Snowball English stemmer.
// Reference: Martin Porter, "An algorithm for suffix stripping" (1980).

private fun isConsonant(word: String, i: Int): Boolean {
    val ch = word[i]
    return when (ch) {
        'a', 'e', 'i', 'o', 'u' -> false
        'y' -> i == 0 || !isVowel(word, i - 1)
        else -> true
    }
}

private fun isVowel(word: String, i: Int): Boolean = !isConsonant(word, i)

/**
 * Measures the number of VC sequences in [str]. Each vowel group followed by a
 * consonant group counts as one VC. Returns the measure (m) used by the Porter rules.
 */
private fun measure(str: String): Int {
    var m = 0
    var i = 0
    var inVowelRun = false
    while (i < str.length) {
        val v = isVowel(str, i)
        if (v && !inVowelRun) {
            inVowelRun = true
        } else if (!v && inVowelRun) {
            inVowelRun = false
            m++
        }
        i++
    }
    return m
}

private fun containsVowel(str: String): Boolean = str.indices.any { isVowel(str, it) }

private fun endsWithDoubleConsonant(str: String): Boolean {
    if (str.length < 2) return false
    val last = str.lastIndex
    return str[last] == str[last - 1] && isConsonant(str, last)
}

private fun endsCvc(word: String): Boolean {
    if (word.length < 3) return false
    val n = word.length
    if (!isConsonant(word, n - 1)) return false
    if (!isVowel(word, n - 2)) return false
    if (!isConsonant(word, n - 3)) return false
    val last = word[n - 1]
    return last != 'w' && last != 'x' && last != 'y'
}

private fun step1a(word: String): String =
    when {
        word.endsWith("sses") -> word.dropLast(2)
        word.endsWith("ies") -> word.dropLast(2)
        word.endsWith("ss") -> word
        word.endsWith("s") -> word.dropLast(1)
        else -> word
    }

private fun step1b(word: String): String {
    if (word.endsWith("eed")) {
        val stem = word.dropLast(3)
        return if (measure(stem) > 0) stem + "ee" else word
    }
    if (word.endsWith("ed")) {
        val stem = word.dropLast(2)
        return if (containsVowel(stem)) step1bPostfix(stem) else word
    }
    if (word.endsWith("ing")) {
        val stem = word.dropLast(3)
        return if (containsVowel(stem)) step1bPostfix(stem) else word
    }
    return word
}

private fun step1bPostfix(stem: String): String {
    if (stem.endsWith("at") || stem.endsWith("bl") || stem.endsWith("iz")) {
        return stem + "e"
    }
    if (endsWithDoubleConsonant(stem) &&
        !stem.endsWith("l") &&
        !stem.endsWith("s") &&
        !stem.endsWith("z")
    ) {
        return stem.dropLast(1)
    }
    if (measure(stem) == 1 && endsCvc(stem)) {
        return stem + "e"
    }
    return stem
}

private fun step1c(word: String): String {
    if (word.endsWith("y") && word.length > 1 && containsVowel(word.dropLast(1))) {
        return word.dropLast(1) + "i"
    }
    return word
}

private data class SuffixReplacement(
    val suffix: String,
    val replacement: String,
)

private val step2Rules: List<SuffixReplacement> =
    listOf(
        SuffixReplacement("ational", "ate"),
        SuffixReplacement("tional", "tion"),
        SuffixReplacement("enci", "ence"),
        SuffixReplacement("anci", "ance"),
        SuffixReplacement("izer", "ize"),
        SuffixReplacement("abli", "able"),
        SuffixReplacement("alli", "al"),
        SuffixReplacement("entli", "ent"),
        SuffixReplacement("eli", "e"),
        SuffixReplacement("ousli", "ous"),
        SuffixReplacement("ization", "ize"),
        SuffixReplacement("ation", "ate"),
        SuffixReplacement("ator", "ate"),
        SuffixReplacement("alism", "al"),
        SuffixReplacement("iveness", "ive"),
        SuffixReplacement("fulness", "ful"),
        SuffixReplacement("ousness", "ous"),
        SuffixReplacement("aliti", "al"),
        SuffixReplacement("iviti", "ive"),
        SuffixReplacement("biliti", "ble"),
    )

private val step3Rules: List<SuffixReplacement> =
    listOf(
        SuffixReplacement("icate", "ic"),
        SuffixReplacement("ative", ""),
        SuffixReplacement("alize", "al"),
        SuffixReplacement("iciti", "ic"),
        SuffixReplacement("ical", "ic"),
        SuffixReplacement("ful", ""),
        SuffixReplacement("ness", ""),
    )

private val step4Suffixes: List<String> =
    listOf(
        "al",
        "ance",
        "ence",
        "er",
        "ic",
        "able",
        "ible",
        "ant",
        "ement",
        "ment",
        "ent",
        "ou",
        "ism",
        "ate",
        "iti",
        "ous",
        "ive",
        "ize",
    )

private fun applyReplacementRule(word: String, rules: List<SuffixReplacement>): String {
    for (rule in rules) {
        if (word.endsWith(rule.suffix)) {
            val stem = word.dropLast(rule.suffix.length)
            if (measure(stem) > 0) return stem + rule.replacement
            return word
        }
    }
    return word
}

private fun step2(word: String): String = applyReplacementRule(word, step2Rules)

private fun step3(word: String): String = applyReplacementRule(word, step3Rules)

private fun step4(word: String): String {
    for (suffix in step4Suffixes) {
        if (word.endsWith(suffix)) {
            val stem = word.dropLast(suffix.length)
            if (measure(stem) > 1) return stem
            return word
        }
    }
    if (word.endsWith("ion")) {
        val stem = word.dropLast(3)
        if (measure(stem) > 1 && stem.isNotEmpty() && (stem.last() == 's' || stem.last() == 't')) {
            return stem
        }
    }
    return word
}

private fun step5a(word: String): String {
    if (word.endsWith("e")) {
        val stem = word.dropLast(1)
        val m = measure(stem)
        if (m > 1) return stem
        if (m == 1 && !endsCvc(stem)) return stem
    }
    return word
}

private fun step5b(word: String): String {
    if (measure(word) > 1 && endsWithDoubleConsonant(word) && word.endsWith("l")) {
        return word.dropLast(1)
    }
    return word
}

/**
 * Stems [token] using the Porter stemmer algorithm (Snowball English variant).
 */
internal fun porterStemmerEnglish(token: String): String {
    if (token.length <= 2) return token
    var word = step1a(token)
    word = step1b(word)
    word = step1c(word)
    word = step2(word)
    word = step3(word)
    word = step4(word)
    word = step5a(word)
    word = step5b(word)
    return word
}
