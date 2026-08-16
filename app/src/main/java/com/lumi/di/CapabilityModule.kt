package com.lumi.di

import com.lumi.core.Capability
import com.lumi.core.ChatCapability
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Joins a capability to the dispatcher's registry.
 *
 * Adding a capability means adding one `@IntoSet` binding here — no dispatch `when` to edit,
 * which is why the dispatcher takes a `Set<Capability>` instead of a concrete class.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CapabilityModule {

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindChatCapability(implementation: ChatCapability): Capability
}
