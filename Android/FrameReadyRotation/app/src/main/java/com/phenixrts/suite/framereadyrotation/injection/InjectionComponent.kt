/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.framereadyrotation.injection

import com.phenixrts.suite.framereadyrotation.ui.MainActivity
import com.phenixrts.suite.framereadyrotation.ui.SplashActivity
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [InjectionModule::class])
interface InjectionComponent {
    fun inject(target: MainActivity)
    fun inject(target: SplashActivity)
}
