package com.lumi

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Nothing is initialised eagerly here.
 *
 * In particular the model is not loaded at process start: cold load costs real seconds and
 * doing it in `onCreate` would delay every launch, including launches that only fire a
 * background routine. Loading is triggered by the first screen that needs generation, via
 * [com.lumi.core.ai.ModelHarness.prepare].
 */
@HiltAndroidApp
class LumiApplication : Application()
