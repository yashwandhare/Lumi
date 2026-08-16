package com.lumi.router.dispatch

import com.lumi.core.Capability
import com.lumi.core.CapabilityInput
import com.lumi.core.CapabilityResult
import com.lumi.core.InteractionOrigin
import com.lumi.core.StructuredIntent
import com.lumi.core.model.CapabilityId
import com.lumi.core.network.GateDecision
import com.lumi.core.network.NetworkFeature
import com.lumi.core.network.NetworkGate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dispatcher is the seam between the router and every capability, so its tests pin the
 * behaviour another developer could break by re-binding: the registry wins over a `when`,
 * the network gate runs before the capability does, and a route nobody implements is an
 * honest failure rather than a crash.
 */
class CapabilityDispatcherTest {

    @Test
    fun `an intent reaches the capability registered under its id`() = runBlocking {
        val chat = RecordingCapability(CapabilityId.CHAT)
        val dispatcher = dispatcherOf(listOf(chat), gate = allowEverything())

        val result = dispatcher.dispatch(input(CapabilityId.CHAT, "tell me a joke"))

        assertEquals(listOf("tell me a joke"), chat.received.map { it.intent.rawText })
        assertTrue(result is CapabilityResult.Ok)
    }

    @Test
    fun `a capability the registry does not know fails with a recovery hint`() = runBlocking {
        val dispatcher = dispatcherOf(emptyList(), gate = allowEverything())

        val result = dispatcher.dispatch(input(CapabilityId.ROUTINE, "set up a routine"))

        assertTrue(result is CapabilityResult.Failed)
    }

    @Test
    fun `a network capability passes through the gate before it runs`() = runBlocking {
        val search = RecordingCapability(CapabilityId.SEARCH)
        val dispatcher = dispatcherOf(
            listOf(search),
            gate = RecordingGate(decision = GateDecision.Refused("Web search is switched off.")),
        )

        val result = dispatcher.dispatch(input(CapabilityId.SEARCH, "latest trains to pune"))

        assertTrue("the gate's refusal reaches the user", result is CapabilityResult.Failed)
        assertTrue("the capability never ran", search.received.isEmpty())
    }

    @Test
    fun `the gate sees the utterance's subject when it opens`() = runBlocking {
        val gate = RecordingGate(decision = GateDecision.Allowed)
        val dispatcher = dispatcherOf(listOf(RecordingCapability(CapabilityId.MAIL)), gate = gate)

        dispatcher.dispatch(input(CapabilityId.MAIL, "check my gmail"))

        assertEquals(NetworkFeature.GMAIL, gate.opened.firstOrNull()?.first)
        assertEquals("check my gmail", gate.opened.firstOrNull()?.second)
    }

    @Test
    fun `a non network capability never touches the gate`() = runBlocking {
        val gate = RecordingGate(decision = GateDecision.Allowed)
        val dispatcher = dispatcherOf(listOf(RecordingCapability(CapabilityId.DEVICE)), gate = gate)

        dispatcher.dispatch(input(CapabilityId.DEVICE, "turn on wifi"))

        assertFalse("the gate saw nothing", gate.opened.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `two capabilities bound to the same id fail fast at construction`() {
        dispatcherOf(
            listOf(RecordingCapability(CapabilityId.CHAT), RecordingCapability(CapabilityId.CHAT)),
            gate = allowEverything(),
        )
    }

    private fun dispatcherOf(capabilities: List<Capability>, gate: NetworkGate) =
        CapabilityDispatcher(capabilities.toSet(), gate)

    private fun input(capability: CapabilityId, text: String) =
        CapabilityInput(
            intent = StructuredIntent(
                capability = capability,
                rawText = text,
                slots = mapOf(StructuredIntent.SLOT_QUERY to text),
            ),
            origin = InteractionOrigin.TEXT,
        )

    private fun allowEverything() = RecordingGate(decision = GateDecision.Allowed)
}

private class RecordingCapability(
    override val id: CapabilityId,
) : Capability {

    val received = mutableListOf<CapabilityInput>()

    override suspend fun execute(input: CapabilityInput): CapabilityResult {
        received += input
        return CapabilityResult.Ok("done")
    }
}

private class RecordingGate(
    private val decision: GateDecision,
) : NetworkGate {

    val opened = mutableListOf<Pair<NetworkFeature, String?>>()

    override suspend fun open(feature: NetworkFeature, subject: String?): GateDecision {
        opened += feature to subject
        return decision
    }
}
