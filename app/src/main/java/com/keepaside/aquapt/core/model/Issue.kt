package com.keepaside.aquapt.core.model

import java.util.UUID

data class Issue(
    val id: String = UUID.randomUUID().toString(),
    val aquariumId: String,
    val title: String,
    val status: IssueStatus = IssueStatus.OPEN,
    val createdAt: String,
    val resolutionNote: String? = null
)
