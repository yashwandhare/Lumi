package com.lumi.router

import java.util.Locale

/**
 * One representation for everything the user might type, speak, or tap in from a widget.
 *
 * Typed input carries capitalisation and stray space; a voice transcript carries punctuation the
 * recogniser guessed at and double spaces where it hesitated; a widget paste can carry a trailing
 * newline. The router's tiers compare strings, and three sources producing three spellings of the
 * same request would make every rule and every example phrase miss on one of them.
 *
 * The normalization is deliberately mechanical — case, whitespace, and nothing else. Stripping
 * punctuation or stemming here would be a guess about meaning, and meaning is the router's job,
 * not the preprocessor's. The original text is kept alongside as [StructuredIntent.rawText]:
 * capabilities show the user what they said, not what the normalizer rewrote.
 */
object InputNormalizer {

    fun normalize(text: String): String =
        text.trim()
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
}
