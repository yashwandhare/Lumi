package com.lumi.di

import com.lumi.core.voice.AsrEngine
import com.lumi.core.voice.ReplySpeaker
import com.lumi.data.ai.SherpaAsrEngine
import com.lumi.data.voice.KokoroReplySpeaker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the voice stack: one recognition engine, one speaker.
 *
 * Both are singletons because each holds a loaded native/platform resource. A second sherpa
 * recognizer would be a second ~160MB load plus a second contended microphone; a second TTS
 * engine is a second IPC handshake. Anything that listens or speaks injects the interface and
 * gets the same instance.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    /**
     * sherpa-onnx hosts the recognition stack — Whisper base.en since the 2026-08-16 accuracy
     * ruling; see decisions_devb.md. On-device, no token.
     */
    @Binds
    @Singleton
    abstract fun bindAsrEngine(implementation: SherpaAsrEngine): AsrEngine

    /**
     * Sherpa-onnx-hosted Kokoro voice for spoken replies, falling back to the platform engine
     * when the voice models are not yet on the device. Speaks only turns whose origin is VOICE.
     */
    @Binds
    @Singleton
    abstract fun bindReplySpeaker(implementation: KokoroReplySpeaker): ReplySpeaker
}
