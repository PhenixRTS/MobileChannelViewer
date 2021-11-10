/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.framereadyrotation.injection

import com.phenixrts.suite.framereadyrotation.FrameReadyApplication
import com.phenixrts.suite.phenixcore.PhenixCore
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class InjectionModule(private val context: FrameReadyApplication) {

    @Singleton
    @Provides
    fun providePhenixCore() = PhenixCore(context)

}
