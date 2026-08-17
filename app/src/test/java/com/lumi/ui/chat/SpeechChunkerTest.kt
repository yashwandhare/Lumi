package com.lumi.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chunker is the pause fix, so the tests assert the shape a judge hears: one continuous
 * explanation, not a TTS engine reading one sentence at a time. The cases mirror the owner's
 * acceptance list — normal sentences, numbered lists, bullets, headings, long streamed replies,
 * tiny replies, and comma/colon/semicolon punctuation.
 */
class SpeechChunkerTest {

    /** Feed a whole reply at once (non-streaming simulation) and collect every fragment. */
    private fun chunks(text: String): List<String> {
        val chunker = SpeechChunker()
        return chunker.feed(text) + listOfNotNull(chunker.finish())
    }

    /** Feed one character at a time; the result must be identical to the non-streamed run. */
    private fun streamedChunks(text: String): List<String> {
        val chunker = SpeechChunker()
        val out = text.flatMap { chunker.feed(it.toString()) }
        return out + listOfNotNull(chunker.finish())
    }

    @Test
    fun `multiple normal sentences batch into one fragment`() {
        val fragments = chunks(
            "Gradient descent is an optimization algorithm. It works by taking small steps " +
                "downhill. Each step follows the gradient.",
        )
        assertEquals(1, fragments.size)
        assertTrue(fragments[0].startsWith("Gradient descent"))
        assertTrue(fragments[0].endsWith("follows the gradient."))
    }

    @Test
    fun `lead sentence colon and list items stay in one fragment`() {
        val reply = "Gradient Descent is an optimization algorithm. Here are the following steps: " +
            "1. Initialize the weights. 2. Calculate the gradient. 3. Update the weights."
        assertEquals(1, chunks(reply).size)
    }

    @Test
    fun `numbered list on its own lines keeps every number with its item`() {
        val reply = "Here are the steps:\n1. Initialize the weights.\n2. Calculate the gradient.\n" +
            "3. Update the weights.\n4. Repeat until converged."
        val fragments = chunks(reply)
        val joined = fragments.joinToString(" ")
        for (n in 1..4) assertTrue("number $n lost: $fragments", joined.contains("$n."))
        fragments.forEach { fragment ->
            assertTrue("list number orphaned: $fragment", fragment.trim().length >= MIN_BREAK)
        }
    }

    @Test
    fun `numbered list markers are never split from their content`() {
        val reply = "Steps:\n1. Weights get set to random values before anything else happens here.\n" +
            "2. The gradient is computed from all of the current weights in the model.\n" +
            "3. Weights move against the gradient by the learning rate every single time."
        val fragments = chunks(reply)
        fragments.forEach { fragment ->
            val dangling = Regex("""^\d{1,2}[.)]?\s*$""").containsMatchIn(fragment.trim())
            assertTrue("bare marker fragment: $fragment", !dangling)
        }
    }

    @Test
    fun `bullet points stay attached to their text`() {
        val reply = "Key ideas:\n- Momentum smooths the path.\n- A learning rate that is too big " +
            "overshoots.\n- Early stopping avoids overfitting the training data."
        val fragments = chunks(reply)
        val joined = fragments.joinToString(" ")
        assertTrue(joined.contains("Momentum smooths the path."))
        assertTrue("bullets not stripped: $fragments", joined.none { it == '-' })
    }

    @Test
    fun `heading hashes are stripped and the heading text is spoken`() {
        val reply = "## How it works\nThe optimizer follows the steepest descent direction. " +
            "It repeats until the loss stops falling."
        val fragments = chunks(reply)
        val joined = fragments.joinToString(" ")
        assertTrue(joined.startsWith("How it works"))
        assertTrue("hashes spoken aloud: $fragments", '#' !in joined)
    }

    @Test
    fun `long streamed response produces full batches and one remainder`() {
        val sentence = "The optimizer takes a small step downhill and repeats the process. "
        val reply = sentence.repeat(12)
        val fragments = streamedChunks(reply)
        assertTrue("expected several batches, got $fragments", fragments.size >= 2)
        fragments.dropLast(1).forEach { fragment ->
            assertTrue("batch under the minimum: $fragment", fragment.length >= MIN_CHUNK)
        }
        assertEquals(reply.trim(), fragments.joinToString(" ").trim())
    }

    @Test
    fun `streaming yields the same fragments regardless of delta size`() {
        val reply = "First, set the weights. Then compute the gradient. Next, update the weights. " +
            "Finally, repeat the loop until the model converges on the answer."
        assertEquals(chunks(reply), streamedChunks(reply))
    }

    @Test
    fun `very short reply is one fragment from the flush`() {
        assertEquals(listOf("Yes, it can."), chunks("Yes, it can."))
    }

    @Test
    fun `commas colons and semicolons do not create fragments`() {
        val reply = "It is fast, simple, and robust: one loop; one update; one answer."
        assertEquals(listOf(reply), chunks(reply))
    }

    @Test
    fun `a period inside a number is not a sentence boundary`() {
        val reply = "The clock runs at 3.4 GHz. That is fast."
        assertEquals(listOf(reply), chunks(reply))
    }

    @Test
    fun `markdown links read their text not their address`() {
        val reply = "See [the docs](https://example.com) for more. The rest is standard."
        assertEquals(listOf("See the docs for more. The rest is standard."), chunks(reply))
    }

    @Test
    fun `finish is idempotent and reset clears a buffer mid-reply`() {
        val chunker = SpeechChunker()
        chunker.feed("Half a thought that never finished")
        assertEquals("Half a thought that never finished", chunker.finish())
        assertNull(chunker.finish())
        chunker.feed("A new turn entirely.")
        chunker.reset()
        assertNull(chunker.finish())
    }
}
