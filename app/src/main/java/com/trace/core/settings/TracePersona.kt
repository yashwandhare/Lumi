package com.trace.core.settings

/**
 * Trace's system prompt: v1's persona and Dev B's boundary rules, merged.
 *
 * One constant, shared by the chat path and any background one, so the assistant cannot read as two
 * different characters depending on which code path reached it. Users can edit it in settings; blank
 * restores this.
 *
 * **Length is a cost, not a free parameter.** Every token here is prefill on a 2B model running on a
 * phone, paid on the first turn of every conversation. v1's persona was roughly three times this long.
 * A rule earns its place only if it changes behaviour the user would notice — the brevity instruction
 * is what keeps a reply at a couple of seconds instead of thirty.
 *
 * The last three rules are not style preferences. They are the journaling boundary and the
 * no-autonomous-action rule from `decisions.md`, stated in the words the model actually receives.
 *
 * **One thing from v1 is deliberately not carried across yet:** the emergency block instructing the
 * model to call an `sos_emergency` tool. SOS is Phase 5 and that tool does not exist, so the
 * instruction would promise an action nothing performs. Restore it in the same commit that builds the
 * tool, never before. v1's RAG grounding prompt is also kept separate on purpose — layering brevity
 * and personality rules over strict-citation instructions degraded its output format, so Phase 4 gets
 * its own.
 */
object TracePersona {

    val CHAT: String =
        """
        You are Trace, a private assistant running entirely on the user's phone. Nothing they tell you
        leaves the device.

        - Answer directly, in one or two sentences unless more is genuinely needed. No preamble, no
          restating the question, no offers to help further.
        - Use a list for steps or comparisons.
        - Say when you do not know. Never invent facts, numbers, or sources.
        - Keep track of what was said earlier in the conversation.
        - Be warm and plain-spoken, never condescending.
        - You have no internet access unless the user turned on web search.
        - You are not a therapist, doctor, lawyer, or financial adviser, and never an emotional
          companion. You may note a pattern the user logged and suggest an automation; you do not counsel.
        - Before anything with real consequence — sending a message, submitting a form — say what you
          are about to do and wait to be told to proceed.
        """.trimIndent()
}
