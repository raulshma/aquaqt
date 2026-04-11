package com.keepaside.aquapt.core.di

import com.keepaside.aquapt.core.backup.AppStateBackupCompatibilityService
import com.keepaside.aquapt.core.backup.BackupCompatibilityGateway
import com.keepaside.aquapt.core.assistant.AssistantGateway
import com.keepaside.aquapt.core.assistant.OpenRouterAssistantGateway
import com.keepaside.aquapt.core.database.dao.*
import com.keepaside.aquapt.core.repository.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val repositoryModule = module {
    single<AppSettingsStore> { AppSettingsRepository(androidContext()) }
    single<AssistantConversationsStore> { AssistantConversationsRepository(androidContext()) }
    single<AssistantGateway> { OpenRouterAssistantGateway() }

    single { AquariumRepository(get()) }
    single { LivestockRepository(get()) }
    single { TaskTemplateRepository(get()) }
    single { TaskExecutionRepository(get()) }
    single { WaterParameterLogRepository(get()) }
    single { ReminderGroupRepository(get()) }
    single { DosingLogRepository(get()) }
    single { AssetRepository(get()) }
    single { ConsumableRepository(get()) }
    single { IssueRepository(get()) }
    single { MemoRepository(get()) }
    single { TimelineEventRepository(get()) }

    single {
        AppStateBackupCompatibilityService(
            aquariumRepository = get(),
            livestockRepository = get(),
            taskTemplateRepository = get(),
            taskExecutionRepository = get(),
            reminderGroupRepository = get(),
            dosingLogRepository = get(),
            assetRepository = get(),
            consumableRepository = get(),
            waterParameterLogRepository = get(),
            issueRepository = get(),
            memoRepository = get(),
            timelineEventRepository = get()
        )
    }
    single<BackupCompatibilityGateway> { get<AppStateBackupCompatibilityService>() }
}
