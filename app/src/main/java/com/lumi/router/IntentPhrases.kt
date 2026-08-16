package com.lumi.router

import com.lumi.core.model.CapabilityId

/**
 * Where an utterance can be sent.
 *
 * Keyed by a router-internal group rather than by [CapabilityId] directly because two groups —
 * reminder and todo — dispatch to the same capability. They stay separate here: their example
 * phrases differ, their markers differ, and the "add" collision the router has to settle is
 * between exactly these groups. Collapsing them into one entry would hide the collision instead
 * of testing it.
 *
 * Reminders and todos have no capability of their own — the closed [CapabilityId] set forbids
 * new values without a decision entry — so both ride on [CapabilityId.TOOLS], with [slotKind]
 * telling the capability which one it is. See `decisions_devb.md`.
 */
internal enum class PhraseGroup(
    val capability: CapabilityId,
    val slotKind: String? = null,
) {
    REMINDER(CapabilityId.TOOLS, slotKind = RulesTier.KIND_REMINDER),
    TODO(CapabilityId.TOOLS, slotKind = RulesTier.KIND_TODO),
    ROUTINE(CapabilityId.ROUTINE),
    RAG(CapabilityId.RAG),
    FILES(CapabilityId.FILES),
    SEARCH(CapabilityId.SEARCH),
    MAIL(CapabilityId.MAIL),
    CHAT(CapabilityId.CHAT),
}

/**
 * The one editable place for the router's intent vocabulary.
 *
 * `docs/todo.md` and the Phase 2 brief are explicit about where misrouting gets fixed: by adding
 * a phrase here, not by changing router code. Example phrases are the training data this router
 * has, and keeping them in a single file means a wrong classification at 2am on Aug 20 is a
 * one-line fix with no logic to re-verify.
 *
 * Phrases are written the way users say them — imperatives, fragments, politeness included —
 * because tier 2 compares the normalized utterance against these directly. The lists are
 * compared as documents ([com.lumi.core.ai.Embedder.embedDocument]), the utterance as a query.
 */
internal object IntentPhrases {

    val byGroup: Map<PhraseGroup, List<String>> = mapOf(
        PhraseGroup.REMINDER to listOf(
            "remind me to call mom at six",
            "remind me about the meeting tomorrow",
            "set a reminder for my dentist appointment",
            "remind me to submit the assignment on friday",
            "give me a reminder at nine",
            "remind me to water the plants every two days",
            "alert me to take my medicine tonight",
            "don't let me forget the fee payment",
        ),
        PhraseGroup.TODO to listOf(
            "add milk to my shopping list",
            "add buying a charger to my todo list",
            "put laundry on my checklist",
            "create a todo to finish the report",
            "add a task to book the train ticket",
            "make a list item for returning the library book",
            "note down that i need to print the notes",
            "add eggs bread and butter to my list",
        ),
        PhraseGroup.ROUTINE to listOf(
            "when i get to college put my phone on silent and turn on wifi",
            "every morning at seven turn on do not disturb",
            "when the battery is low turn on battery saver",
            "set up a routine for bedtime",
            "automate silencing my phone during class",
            "when i leave home turn off wifi and bluetooth",
            "create an automation for my morning",
        ),
        PhraseGroup.RAG to listOf(
            "what do my notes say about photosynthesis",
            "summarise my physics notes",
            "which of my documents mention the exam schedule",
            "explain the part of my notes about osmosis",
            "quiz me from my study material",
            "what did i write about the french revolution",
        ),
        PhraseGroup.FILES to listOf(
            "find my aadhaar card",
            "where is my college id",
            "attach the assignment pdf",
            "open my lab report file",
            "find the photo of the notice board",
            "attach my resume to the chat",
            "locate the downloaded ticket",
        ),
        PhraseGroup.SEARCH to listOf(
            "search the web for tomorrow's weather",
            "look up the latest news on the web",
            "duckduckgo the python release date",
            "do a web search for train timings",
            "find out online when the store closes",
        ),
        PhraseGroup.MAIL to listOf(
            "check my gmail",
            "do i have new mail",
            "fetch my latest emails",
            "what's in my inbox",
            "read my unread mail",
        ),
        PhraseGroup.CHAT to listOf(
            "what is a black hole",
            "write a short poem about rain",
            "tell me a joke",
            "how do i boil an egg",
            "who wrote hamlet",
            "thanks that helped",
        ),
    )

    /**
     * Keywords that settle the "add" collisions the Phase 2 brief calls out.
     *
     * Reminder, todo, and attach verbs all start with "add". Cosine similarity alone puts
     * "add milk to my list" close to a reminder phrase and an attach phrase alike, and guessing
     * between three is exactly what the ambiguity rule is for — except here a word in the
     * utterance already answers it. When a marker fires, its group is boosted ahead of a
     * near-tie rather than the user being asked a question their own sentence answered.
     *
     * Checked against the normalized utterance (lowercase, single-spaced).
     */
    val markers: Map<PhraseGroup, Regex> = mapOf(
        PhraseGroup.REMINDER to Regex("\\bremind|reminder|alert me|don't let me forget"),
        PhraseGroup.TODO to Regex("to-?do|my list|checklist|a task|list item|shopping list"),
        PhraseGroup.FILES to Regex("\\battach|\\bfile|\\bpdf|\\bphoto of|\\bfind my|\\blocate"),
        PhraseGroup.ROUTINE to Regex("\\broutine|automation|\\bat (bed)?time routine"),
        PhraseGroup.SEARCH to Regex("\\bsearch the web|web search|look up|duckduckgo|search online"),
        PhraseGroup.MAIL to Regex("\\bmail|inbox|gmail"),
    )
}
