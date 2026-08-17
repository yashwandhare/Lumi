package com.lumi.ui.chat

/**
 * Turns a streamed reply into TTS-sized fragments, one continuous speaker rather than a reader
 * of isolated sentences.
 *
 * Two rules decide the shape. Each TTS request pays a fixed synthesis overhead — sherpa's Kokoro
 * path processes one sentence per model run regardless of batching, so extra requests buy
 * nothing but cost — and every request boundary is where a pipeline underrun sounds like a long
 * pause. Fragments are therefore packed toward [MAX_CHUNK]: a lead sentence, the colon before a
 * list, and the list's first items land in ONE request, which is what makes "Here are the
 * following steps: 1. ... 2. ..." read as a single explanation instead of a menu.
 *
 * Small fragments are never emitted: no standalone "1." from a mid-item cut, no leftover stub
 * shorter than [MIN_CHUNK]. The exception is the first cut of a turn, which leaves at the first
 * hard boundary so the voice starts without waiting for a batch to fill.
 */
internal class SpeechChunker {

    internal var buffer = ""
    internal var firstEmitted = false

    /**
     * Feed the next streamed delta. Fragments come off when the buffer holds enough speech
     * ending at a usable break — at most one per call, so streaming stays light.
     */
    fun feed(delta: String): List<String> {
        buffer += delta
        return drain(force = false)
    }

    /**
     * Flush whatever remains once the reply is complete. The tail is always spoken — a short
     * leftover is still the last part of the answer — and the chunker resets for the next turn.
     */
    fun finish(): String? {
        val rest = cutMarkdown(buffer).trim()
        buffer = ""
        firstEmitted = false
        return rest.ifEmpty { null }
    }

    fun reset() {
        buffer = ""
        firstEmitted = false
    }

    private fun drain(force: Boolean): List<String> {
        val out = ArrayList<String>(2)
        while (true) {
            if (!force && buffer.length < MIN_CHUNK) break
            val spoken = cutMarkdown(buffer)
            val cut = pickCut(spoken, force)
            if (cut <= 0) break

            val fragment = spoken.substring(0, cut).trim()
            buffer = spoken.substring(cut)
            if (fragment.isNotEmpty()) {
                out += fragment
                firstEmitted = true
            }
            if (!force) break
        }
        return out
    }

    /**
     * The cut position in [spoken], a half-open index. A hard cut swallows its boundary plus
     * trailing whitespace (a sentence-final period ends the fragment); a list-marker cut keeps
     * the number so the next fragment stays speakable.
     *
     * The first fragment of a turn is NOT split out early: the latency win of a tiny opening
     * fragment is bought with a boundary pause right after it, and a reply shorter than a batch
     * should leave as ONE request. The opening cut exists only as an overrun guard — when the
     * buffer passed the batch limit without finding any usable boundary (the rare unpunctuated
     * run), something must ship.
     */
    private fun pickCut(spoken: String, force: Boolean): Int {
        if (force) return spoken.length
        if (spoken.length < MAX_CHUNK) return 0
        if (!firstEmitted) {
            spoken.firstHardBreak().let { if (it >= MIN_CHUNK) return it }
        }
        spoken.lastHardBreak().let { if (it >= MIN_BREAK) return it }
        spoken.listBreak().let { if (it >= MIN_CHUNK) return it }
        spoken.lastIndexOf(' ').let { if (it >= MIN_CHUNK) return it }
        return 0
    }
}

/**
 * The FIRST usable sentence boundary: where the opening fragment leaves early so the voice
 * starts while the rest still batches.
 */
internal fun String.firstHardBreak(): Int {
    var i = 0
    while (true) {
        i = indexOfAny(HARD_BREAK_CHARS, i)
        if (i < 0) return 0
        if (isSentenceBreakAt(i)) {
            val end = skipSpaces(i + 1)
            if (end >= MIN_CHUNK) return end
        }
        i++
    }
}

/**
 * The LAST usable sentence boundary: where an oversize batch is cut to ship the most text per
 * request.
 */
internal fun String.lastHardBreak(): Int {
    var best = 0
    var i = 0
    while (true) {
        i = indexOfAny(HARD_BREAK_CHARS, i)
        if (i < 0) break
        if (isSentenceBreakAt(i)) {
            val end = skipSpaces(i + 1)
            if (end >= MIN_BREAK) best = end
        }
        i++
    }
    return best
}

private val HARD_BREAK_CHARS = charArrayOf(':', '.', '!', '?', '\n', ';')

/**
 * Break semantics: `! ? \n ;` always end a fragment. A colon counts only if text follows it in
 * the buffer — when it is the last character the list after it has not arrived yet, and cutting
 * would ship the colon alone. A period never counts if a digit follows it — "3.4 GHz" and "v1.0"
 * are not sentences.
 */
private fun String.isSentenceBreakAt(i: Int): Boolean = when (this[i]) {
    ':' -> i < length - 1
    '.' -> i + 1 >= length || !this[i + 1].isDigit()
    else -> true
}

private fun String.skipSpaces(from: Int): Int {
    var i = from
    while (i < length && this[i].isWhitespace()) i++
    return i
}

/**
 * The last boundary on which an oversize fragment may be split without orphaning a list number:
 * directly before a short marker ("1.", "2)", double digits too) that starts a line or follows a
 * sentence break. Cutting mid-item would orphan the digit as its own tiny request — exactly the
 * machine-reading-a-menu effect — so items are only ever separated, never cut.
 */
internal fun String.listBreak(): Int {
    var best = 0
    for (match in LIST_MARKER_HEAD.findAll(this)) {
        val at = match.range.first
        val startsLine = at == 0 || this[at - 1] == '\n'
        val afterSentence = at >= 3 && this[at - 1].isWhitespace() && this[at - 2].let {
            it == '.' || it == '!' || it == '?' || it == ';'
        }
        if ((startsLine || afterSentence) && at >= MIN_CHUNK) best = at
    }
    return best
}

private val LIST_MARKER_HEAD = Regex("""\d{1,2}[.)]\s""")

/**
 * Markdown spoken aloud is noise. Asterisks, backticks, heading hashes, image and link syntax,
 * and bullet markers are stripped rather than read; numbered items keep their numbers — "1."
 * aloud is natural, "hash hash" is not — and their spacing is normalised so the boundary search
 * and the synthesiser both see a clean "1. item". The transcript still renders the original.
 */
internal fun cutMarkdown(text: String): String =
    text
        .replace(MARKDOWN_IMAGE, "\$1")
        .replace(MARKDOWN_LINK, "\$1")
        .replace(Regex("`{1,3}"), "")
        .replace(Regex("\\*{1,2}"), "")
        .replace(Regex("(?m)^#{1,6}\\s+"), "")
        .replace(Regex("(?m)^[-+]\\s+"), "")
        .replace(Regex("(?m)^\\s*(\\d{1,2})[.)]\\."), "\$1.")
        .replace(Regex("(?m)^\\s*(\\d{1,2})[.)]\\s+"), "\$1. ")

private val MARKDOWN_IMAGE = Regex("""!\[([^\]]*)\]\([^)]*\)""")
private val MARKDOWN_LINK = Regex("""\[(.*?)\]\([^)]*\)""")

internal const val MIN_CHUNK = 12
internal const val MIN_BREAK = 8
internal const val MAX_CHUNK = 280
