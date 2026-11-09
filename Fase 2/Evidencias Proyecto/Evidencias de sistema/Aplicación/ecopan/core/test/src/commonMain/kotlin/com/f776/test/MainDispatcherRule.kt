@file:OptIn(ExperimentalCoroutinesApi::class)

package com.f776.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain


@OptIn(ExperimentalCoroutinesApi::class)
object MainDispatcherRule {
    private val testScheduler = TestCoroutineScheduler()

    fun createTestDispatcher(): TestDispatcher = UnconfinedTestDispatcher(testScheduler)

    fun setMainDispatcher(dispatcher: TestDispatcher) {
        Dispatchers.setMain(dispatcher)
    }

    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }
}