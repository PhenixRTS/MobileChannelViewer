/*
 * Copyright 2022 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All rights reserved.
 */

package com.phenixrts.suite.phenixcore.injection

import com.phenixrts.suite.phenixcore.PhenixCore
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [InjectionModule::class])
internal interface InjectionComponent {
    fun inject(target: PhenixCore)
}
