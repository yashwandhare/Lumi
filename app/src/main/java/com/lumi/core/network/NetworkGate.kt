package com.lumi.core.network

import com.lumi.core.model.CapabilityId

/**
 * The only things Lumi may use the network for.
 *
 * A deliberately closed enum rather than a per-capability flag: auditing the privacy claim
 * means enumerating this list, and a capability that could reach the network without naming
 * itself here would make that audit incomplete. Adding a third feature needs a decision
 * entry — see decisions.md's 2026-08-16 network reversal, which allows exactly two.
 */
enum class NetworkFeature(val capability: CapabilityId) {
    WEB_SEARCH(CapabilityId.SEARCH),
    GMAIL(CapabilityId.MAIL),
    ;

    companion object {
        /** The feature behind [capability], or null when the capability never touches the network. */
        fun forCapability(capability: CapabilityId): NetworkFeature? =
            entries.firstOrNull { it.capability == capability }
    }
}

/**
 * Whether the user has opted a network feature in. Both features default to off,
 * deliberately: a privacy-first product asks before it reaches.
 */
interface NetworkToggles {
    fun isEnabled(feature: NetworkFeature): Boolean
}

/**
 * What the gate concluded about a request.
 *
 * [Refused] carries the words the user will see, because the refusal is a product moment,
 * not an error: the feature exists, the user just has not switched it on, and the message
 * says where to do that.
 */
sealed interface GateDecision {
    data object Allowed : GateDecision
    data class Refused(val userMessage: String) : GateDecision
}

/**
 * The one door every network request walks through.
 *
 * Web search and mail fetch are the only intents that may leave the device, and they both
 * route here. The gate checks the per-feature opt-in and writes an audit event *before* the
 * request it allows — the audit entry exists regardless of how the fetch later ends, which is
 * what makes the privacy claim provable: a judge can watch the network being used, and see
 * the record of it, without reading the source.
 */
interface NetworkGate {

    /**
     * @param feature the feature about to touch the network.
     * @param subject what is being looked up, in the user's words. Shown in the audit log.
     * @return [GateDecision.Allowed] once the opt-in is checked and the pre-request entry is
     *   written, or [GateDecision.Refused] when the feature is switched off.
     */
    suspend fun open(feature: NetworkFeature, subject: String? = null): GateDecision
}
