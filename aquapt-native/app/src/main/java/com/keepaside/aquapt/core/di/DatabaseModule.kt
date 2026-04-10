package com.keepaside.aquapt.core.di

import android.content.Context
import androidx.room.Room
import com.keepaside.aquapt.core.database.AquaPTDatabase
import com.keepaside.aquapt.core.database.dao.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AquaPTDatabase::class.java,
            "aquapt.db"
        ).build()
    }

    single { get<AquaPTDatabase>().aquariumDao() }
    single { get<AquaPTDatabase>().livestockDao() }
    single { get<AquaPTDatabase>().taskTemplateDao() }
    single { get<AquaPTDatabase>().taskExecutionDao() }
    single { get<AquaPTDatabase>().waterParameterLogDao() }
    single { get<AquaPTDatabase>().reminderGroupDao() }
    single { get<AquaPTDatabase>().dosingLogDao() }
    single { get<AquaPTDatabase>().assetDao() }
    single { get<AquaPTDatabase>().consumableDao() }
    single { get<AquaPTDatabase>().issueDao() }
    single { get<AquaPTDatabase>().memoDao() }
    single { get<AquaPTDatabase>().timelineEventDao() }
}
