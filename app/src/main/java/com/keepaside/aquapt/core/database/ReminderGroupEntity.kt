package com.keepaside.aquapt.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.keepaside.aquapt.core.model.*

@Entity(tableName = "reminder_groups")
data class ReminderGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val hours: List<Int> = emptyList()
) {
    fun toDomain() = ReminderGroup(
        id = id,
        name = name,
        hours = hours
    )

    companion object {
        fun fromDomain(domain: ReminderGroup) = ReminderGroupEntity(
            id = domain.id,
            name = domain.name,
            hours = domain.hours
        )
    }
}
