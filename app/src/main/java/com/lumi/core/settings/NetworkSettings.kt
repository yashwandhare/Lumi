package com.lumi.core.settings

/**
 * The two network features, each opted in on its own.
 *
 * Both default to off: a privacy-first product asks before it reaches, and the phase plan is
 * explicit that neither search nor mail may run on its own, ever. The values live in
 * [SettingsStore]; the gate reads them through [com.lumi.core.network.NetworkToggles] so a
 * JVM test can switch a feature on without touching SharedPreferences.
 */
data class NetworkSettings(
    val webSearch: Boolean = false,
    val gmail: Boolean = false,
) {
    /** True when any feature is allowed to reach. The kill switch turns both off at once. */
    val anyEnabled: Boolean get() = webSearch || gmail
}
