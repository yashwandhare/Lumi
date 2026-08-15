package com.trace.di

import com.trace.core.ai.ModelHarness
import com.trace.data.ai.LiteRtModelHarness
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the one implementation of [ModelHarness].
 *
 * `@Singleton` is not a convenience here — it is the mechanism that enforces "the model loads once
 * and stays resident". A second instance would mean a second `Engine`, and a cold load costs tens of
 * seconds. Anything that needs generation injects the interface and gets the same object.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindModelHarness(implementation: LiteRtModelHarness): ModelHarness
}
