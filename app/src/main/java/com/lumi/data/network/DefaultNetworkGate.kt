package com.lumi.data.network

import com.lumi.core.audit.AuditLog
import com.lumi.core.model.AuditOutcome
import com.lumi.core.network.GateDecision
import com.lumi.core.network.NetworkFeature
import com.lumi.core.network.NetworkGate
import com.lumi.core.network.NetworkToggles
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The working implementation of [NetworkGate]: one chokepoint for the two network features.
 *
 * The audit entry is written here, before the request, for everything the gate lets through —
 * a capability that later fails mid-fetch still has its "left the device" moment on record,
 * which is the evidence the privacy claim sells on. Refusals are recorded too, as [AuditOutcome.SKIPPED],
 * so the log reads as a true history: the user asked, Lumi declined, nothing went out.
 */
@Singleton
class DefaultNetworkGate @Inject constructor(
    private val toggles: NetworkToggles,
    private val audit: AuditLog,
) : NetworkGate {

    override suspend fun open(feature: NetworkFeature, subject: String?): GateDecision {
        if (!toggles.isEnabled(feature)) {
            audit.record(
                capability = feature.capability,
                summary = refuseSummary(feature),
                outcome = AuditOutcome.SKIPPED,
                detail = "This network feature is switched off in Settings.",
            )
            return GateDecision.Refused(refuseMessage(feature))
        }

        audit.record(
            capability = feature.capability,
            summary = requestSummary(feature),
            outcome = AuditOutcome.SUCCESS,
            subject = subject,
            detail = "The request left the device after the opt-in was checked.",
        )
        return GateDecision.Allowed
    }

    private fun requestSummary(feature: NetworkFeature): String = when (feature) {
        NetworkFeature.WEB_SEARCH -> "Sent a web search request"
        NetworkFeature.GMAIL -> "Sent a mail fetch request"
    }

    private fun refuseSummary(feature: NetworkFeature): String = when (feature) {
        NetworkFeature.WEB_SEARCH -> "Web search declined — switched off in Settings"
        NetworkFeature.GMAIL -> "Mail fetch declined — switched off in Settings"
    }

    private fun refuseMessage(feature: NetworkFeature): String = when (feature) {
        NetworkFeature.WEB_SEARCH ->
            "Web search is switched off. Turn it on in Settings under Network features."
        NetworkFeature.GMAIL ->
            "Reading your mail is switched off. Turn it on in Settings under Network features."
    }
}
