package com.lumi.di

import com.lumi.core.Capability
import com.lumi.core.Dispatcher
import com.lumi.core.network.NetworkGate
import com.lumi.core.network.NetworkToggles
import com.lumi.data.network.DefaultNetworkGate
import com.lumi.data.settings.SettingsStore
import com.lumi.router.dispatch.CapabilityDispatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/**
 * Wires the dispatch path: the registry, the network gate, and the capability set.
 *
 * Capabilities join the registry by adding an `@IntoSet` binding in their own module —
 * the dispatcher never gains a branch. The [Multibinds] declaration keeps the set validly
 * injectable while it is still empty, so the graph compiles before Phase 3 lands the first
 * capability.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DispatchModule {

    @Binds
    @Singleton
    abstract fun bindDispatcher(implementation: CapabilityDispatcher): Dispatcher

    /**
     * The gate is a singleton so its pre-request audit write and opt-in read share one
     * implementation — a second instance would be a second definition of "allowed".
     */
    @Binds
    @Singleton
    abstract fun bindNetworkGate(implementation: DefaultNetworkGate): NetworkGate

    /** The gate reads opt-ins from the one owner of user preferences. */
    @Binds
    @Singleton
    abstract fun bindNetworkToggles(implementation: SettingsStore): NetworkToggles

    @Multibinds
    abstract fun bindCapabilities(): Set<@JvmSuppressWildcards Capability>
}
