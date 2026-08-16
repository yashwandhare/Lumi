package com.lumi.router.dispatch

import com.lumi.core.Capability
import com.lumi.core.CapabilityInput
import com.lumi.core.CapabilityResult
import com.lumi.core.Dispatcher
import com.lumi.core.StructuredIntent
import com.lumi.core.model.CapabilityId
import com.lumi.core.network.GateDecision
import com.lumi.core.network.NetworkFeature
import com.lumi.core.network.NetworkGate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends a routed intent to the capability that owns it.
 *
 * The registry is a map keyed by [com.lumi.core.model.CapabilityId], built from every bound
 * [Capability] — adding a capability means binding it in DI, not editing a dispatch `when`
 * with a new branch. A route nobody implements is answered honestly as a failure, not a crash.
 *
 * **The network gate lives here, not in the two network capabilities.** Search and mail both
 * pass through [NetworkGate] before the capability runs, so neither can ever reach the
 * network by another road, and a third network feature cannot appear without this file
 * refusing it at construction time: the gate's feature set is closed, and a capability whose
 * id maps to nothing in it is treated as "never leaves the device". One chokepoint, not two
 * code paths — see decisions.md's 2026-08-16 network reversal.
 */
@Singleton
class CapabilityDispatcher @Inject constructor(
    capabilities: Set<@JvmSuppressWildcards Capability>,
    private val networkGate: NetworkGate,
) : Dispatcher {

    private val registry: Map<CapabilityId, Capability> =
        capabilities.groupBy { it.id }.also { byId ->
            val duplicates = byId.filterValues { it.size > 1 }.keys
            require(duplicates.isEmpty()) {
                "duplicate capability ids bound: " +
                    duplicates.joinToString { it.name }
            }
        }.mapValues { it.value.single() }

    override suspend fun dispatch(input: CapabilityInput): CapabilityResult {
        val capability = registry[input.intent.capability]
            ?: return CapabilityResult.Failed(
                userMessage = "Lumi cannot do that yet.",
                recovery = "Try phrasing it differently, or check back after the next update.",
            )

        val feature = NetworkFeature.forCapability(capability.id)
        if (feature != null) {
            when (val gate = networkGate.open(feature, subject = input.intent.auditSubject)) {
                is GateDecision.Allowed -> Unit
                is GateDecision.Refused -> return CapabilityResult.Failed(gate.userMessage)
            }
        }

        return capability.execute(input)
    }
}

/**
 * The user-facing name of what an intent is about, for audit entries and confirmation
 * surfaces. Falls back to the raw text the user said — a capability showing what was
 * understood is the product's ease-of-use rule.
 */
val StructuredIntent.auditSubject: String
    get() = this[StructuredIntent.SLOT_QUERY] ?: rawText
