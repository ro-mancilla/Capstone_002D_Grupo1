@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.f776.ecopan.feature.home.unittest

import com.f776.ecopan.domain.reminder.permission.PermissionResult
import com.f776.ecopan.feature.home.screens.home.HomeAction
import com.f776.ecopan.feature.home.screens.home.HomeViewModel
import com.f776.ecopan.feature.home.unittest.fake.FakeNoteRepository
import com.f776.ecopan.feature.home.unittest.fake.FakeReminderPermissionManager
import com.f776.ecopan.feature.home.unittest.fake.FakeReminderScheduler
import com.f776.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class HomeViewModelTest {

    private lateinit var viewModel: HomeViewModel
    private lateinit var noteRepository: FakeNoteRepository
    private lateinit var reminderScheduler: FakeReminderScheduler
    private lateinit var permissionManager: FakeReminderPermissionManager
    private val testDispatcher = MainDispatcherRule.createTestDispatcher()

    @BeforeTest
    fun setUp() {
        MainDispatcherRule.setMainDispatcher(testDispatcher)
        noteRepository = FakeNoteRepository()
        reminderScheduler = FakeReminderScheduler()
        permissionManager = FakeReminderPermissionManager()

        viewModel = HomeViewModel(
            noteRepository = noteRepository,
            reminderScheduler = reminderScheduler,
            reminderPermissionManager = permissionManager,
        )
    }

    @AfterTest
    fun tearDown() {
        MainDispatcherRule.resetMainDispatcher()
    }

    @Test
    fun `initial notes state is empty`() = runTest {
        assertEquals(emptyList(), viewModel.notes.first())
    }

    @Test
    fun `add note appends to repository`() = runTest {
        viewModel.onAction(HomeAction.OnAddNote(title = "Groceries", body = "Milk, eggs"))
        advanceUntilIdle()

        val notes = viewModel.notes.first { it.isNotEmpty() }
        assertEquals("Groceries", notes.single().title)
        assertEquals("Milk, eggs", notes.single().body)
    }

    @Test
    fun `delete note cancels its reminder and removes it`() = runTest {
        viewModel.onAction(HomeAction.OnAddNote(title = "Groceries", body = "Milk, eggs"))
        advanceUntilIdle()
        val note = viewModel.notes.first { it.isNotEmpty() }.single()

        viewModel.onAction(HomeAction.OnDeleteNote(note.id))
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.notes.first())
        assertEquals(listOf(note.id.toString()), reminderScheduler.cancelCalls)
    }

    @Test
    fun `remind note schedules a reminder when permission is granted`() = runTest {
        viewModel.onAction(HomeAction.OnAddNote(title = "Groceries", body = "Milk, eggs"))
        advanceUntilIdle()
        val note = viewModel.notes.first { it.isNotEmpty() }.single()

        val eventCollector = backgroundScope.launch { viewModel.eventChannel.collect { } }

        val triggerAt = Clock.System.now()
        viewModel.onAction(HomeAction.OnRemindNote(note.id, triggerAt))
        advanceUntilIdle()

        assertEquals(1, reminderScheduler.scheduleCalls.size)
        assertEquals(note.id.toString(), reminderScheduler.scheduleCalls.single().id)
        assertEquals(triggerAt, reminderScheduler.scheduleCalls.single().triggerAt)

        eventCollector.cancel()
    }

    @Test
    fun `remind note does not schedule when permission is denied`() = runTest {
        permissionManager.setNotificationPermissionResult(PermissionResult.DENIED)
        viewModel.onAction(HomeAction.OnAddNote(title = "Groceries", body = "Milk, eggs"))
        advanceUntilIdle()
        val note = viewModel.notes.first { it.isNotEmpty() }.single()

        val eventCollector = backgroundScope.launch { viewModel.eventChannel.collect { } }

        viewModel.onAction(HomeAction.OnRemindNote(note.id, Clock.System.now()))
        advanceUntilIdle()

        assertTrue(reminderScheduler.scheduleCalls.isEmpty())

        eventCollector.cancel()
    }
}
