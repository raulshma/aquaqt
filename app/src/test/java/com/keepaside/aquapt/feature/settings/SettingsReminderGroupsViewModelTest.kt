package com.keepaside.aquapt.feature.settings

import com.keepaside.aquapt.core.database.ReminderGroupEntity
import com.keepaside.aquapt.core.database.TaskTemplateEntity
import com.keepaside.aquapt.core.database.dao.ReminderGroupDao
import com.keepaside.aquapt.core.database.dao.TaskTemplateDao
import com.keepaside.aquapt.core.model.ReminderGroup
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.repository.ReminderGroupRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsReminderGroupsViewModelTest {

    @Test
    fun `save draft creates reminder group with normalized hours`() = runTest {
        val fixture = ReminderGroupsFixture(this)
        val viewModel = fixture.createViewModel(idProvider = { "rg-1" })

        try {
            viewModel.onDraftNameChanged("Morning")
            viewModel.onDraftHoursChanged("18, 6; 18 8")
            viewModel.saveDraft()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Reminder group created.", state.statusMessage)
            assertEquals(1, state.groups.size)
            assertEquals("Morning", state.groups.single().name)
            assertEquals(listOf(6, 8, 18), state.groups.single().hours)
            assertEquals("6, 8, 18", state.groups.single().hoursLabel)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `save draft validates name and hours input`() = runTest {
        val fixture = ReminderGroupsFixture(this)
        val viewModel = fixture.createViewModel()

        try {
            viewModel.onDraftHoursChanged("8")
            viewModel.saveDraft()
            advanceUntilIdle()
            assertEquals(reminderGroupNameErrorMessage, viewModel.uiState.value.statusMessage)

            viewModel.onDraftNameChanged("Invalid")
            viewModel.onDraftHoursChanged("24")
            viewModel.saveDraft()
            advanceUntilIdle()
            assertEquals(reminderGroupHoursErrorMessage, viewModel.uiState.value.statusMessage)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `edit draft updates existing reminder group`() = runTest {
        val fixture = ReminderGroupsFixture(this)
        fixture.reminderGroupRepository.upsert(
            ReminderGroup(
                id = "rg-2",
                name = "Evening",
                hours = listOf(20)
            )
        )

        val viewModel = fixture.createViewModel()

        try {
            advanceUntilIdle()

            viewModel.startEditDraft("rg-2")
            advanceUntilIdle()
            assertEquals("rg-2", viewModel.uiState.value.draft.id)
            assertEquals("Evening", viewModel.uiState.value.draft.name)
            assertEquals("20", viewModel.uiState.value.draft.hoursInput)

            viewModel.onDraftNameChanged("Late evening")
            viewModel.onDraftHoursChanged("19, 22")
            viewModel.saveDraft()
            advanceUntilIdle()

            val updated = viewModel.uiState.value.groups.single()
            assertEquals("Late evening", updated.name)
            assertEquals(listOf(19, 22), updated.hours)
            assertEquals("Reminder group updated.", viewModel.uiState.value.statusMessage)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `delete group unassigns linked task templates`() = runTest {
        val fixture = ReminderGroupsFixture(this)
        fixture.reminderGroupRepository.upsert(
            ReminderGroup(
                id = "rg-3",
                name = "Cleanup",
                hours = listOf(9, 21)
            )
        )
        fixture.taskTemplateRepository.upsert(
            template = TaskTemplate(
                id = "task-1",
                title = "Water change",
                frequency = TaskFrequency.WEEKLY,
                aquariumIds = listOf("a-1"),
                reminderGroupId = "rg-3"
            ),
            primaryAquariumId = "a-1"
        )

        val viewModel = fixture.createViewModel()

        try {
            advanceUntilIdle()

            viewModel.deleteGroup("rg-3")
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.groups.isEmpty())
            assertEquals(
                "Reminder group deleted. Unassigned 1 task template.",
                viewModel.uiState.value.statusMessage
            )

            val templates = fixture.taskTemplateRepository.getAll().first()
            assertEquals(1, templates.size)
            assertNull(templates.single().reminderGroupId)
        } finally {
            viewModel.disposeForTests()
        }
    }

    @Test
    fun `hour parser normalizes and validates values`() {
        assertEquals(listOf(6, 8, 18), parseReminderGroupHoursInput("18 6,8;18"))
        assertEquals(emptyList<Int>(), parseReminderGroupHoursInput(" "))
        assertEquals(null, parseReminderGroupHoursInput("6, 24"))
    }
}

private class ReminderGroupsFixture(
    private val scope: TestScope
) {
    private val reminderGroupDao = FakeReminderGroupDao()
    private val taskTemplateDao = FakeTaskTemplateDao()

    val reminderGroupRepository = ReminderGroupRepository(reminderGroupDao)
    val taskTemplateRepository = TaskTemplateRepository(taskTemplateDao)

    fun createViewModel(idProvider: () -> String = { "generated-id" }): SettingsReminderGroupsViewModel =
        SettingsReminderGroupsViewModel(
            reminderGroupRepository = reminderGroupRepository,
            taskTemplateRepository = taskTemplateRepository,
            externalScope = scope,
            idProvider = idProvider
        )
}

private class FakeReminderGroupDao : ReminderGroupDao {
    private val entities = linkedMapOf<String, ReminderGroupEntity>()
    private val flow = MutableStateFlow<List<ReminderGroupEntity>>(emptyList())

    override fun getAll(): Flow<List<ReminderGroupEntity>> = flow

    override suspend fun getById(id: String): ReminderGroupEntity? = entities[id]

    override suspend fun upsert(entity: ReminderGroupEntity) {
        entities[entity.id] = entity
        emit()
    }

    override suspend fun delete(entity: ReminderGroupEntity) {
        entities.remove(entity.id)
        emit()
    }

    override suspend fun deleteById(id: String) {
        entities.remove(id)
        emit()
    }

    private fun emit() {
        flow.value = entities.values.sortedBy { it.name.lowercase() }
    }
}

private class FakeTaskTemplateDao : TaskTemplateDao {
    private val entities = linkedMapOf<String, TaskTemplateEntity>()
    private val flow = MutableStateFlow<List<TaskTemplateEntity>>(emptyList())

    override fun getAll(): Flow<List<TaskTemplateEntity>> = flow

    override fun getByAquariumId(aquariumId: String): Flow<List<TaskTemplateEntity>> =
        flow.map { list -> list.filter { it.aquariumId == aquariumId } }

    override suspend fun getById(id: String): TaskTemplateEntity? = entities[id]

    override suspend fun upsert(entity: TaskTemplateEntity) {
        entities[entity.id] = entity
        emit()
    }

    override suspend fun delete(entity: TaskTemplateEntity) {
        entities.remove(entity.id)
        emit()
    }

    override suspend fun deleteById(id: String) {
        entities.remove(id)
        emit()
    }

    override suspend fun clearReminderGroup(reminderGroupId: String): Int {
        var detachedCount = 0
        val updated = entities.values.map { entity ->
            if (entity.reminderGroupId == reminderGroupId) {
                detachedCount += 1
                entity.copy(reminderGroupId = null)
            } else {
                entity
            }
        }

        if (detachedCount > 0) {
            entities.clear()
            updated.forEach { entity -> entities[entity.id] = entity }
            emit()
        }

        return detachedCount
    }

    private fun emit() {
        flow.value = entities.values.sortedBy { it.title.lowercase() }
    }
}
