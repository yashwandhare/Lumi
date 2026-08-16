package com.lumi.core

import com.lumi.core.ai.GenerationRequest
import com.lumi.core.ai.ModelHarness
import com.lumi.core.ai.SessionId
import com.lumi.core.model.CapabilityId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chat as a routed capability, so every input — typed, spoken, widget — reaches the model
 * through the same dispatcher as every other capability. One dispatch path, not two.
 *
 * Returns [CapabilityResult.Streaming] because replies arrive incrementally. Conversation
 * persistence and the chat-turn audit entry stay with the chat view model: they need the
 * conversation id and the per-turn backend evidence, which the capability does not have and
 * should not grow only to hold them. When Phase 3 capabilities land with their own audit
 * writes, this split is revisited — see the decision entry in `decisions_devb.md`.
 */
@Singleton
class ChatCapability @Inject constructor(
    private val harness: ModelHarness,
) : Capability {

    override val id: CapabilityId = CapabilityId.CHAT

    override suspend fun execute(input: CapabilityInput): CapabilityResult {
        val session = input.intent[StructuredIntent.SLOT_SESSION]
            ?.let(::SessionId)

        return CapabilityResult.Streaming(
            userMessage = "",
            chunks = harness.generate(
                GenerationRequest(
                    prompt = input.intent.rawText,
                    sessionId = session,
                )
            ),
        )
    }
}
