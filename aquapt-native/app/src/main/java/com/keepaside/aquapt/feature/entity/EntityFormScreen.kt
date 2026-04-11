package com.keepaside.aquapt.feature.entity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.IssueRepository
import com.keepaside.aquapt.core.repository.MemoRepository
import com.keepaside.aquapt.core.repository.TimelineEventRepository
import org.koin.java.KoinJavaComponent

@Composable
fun EntityFormScreen(
    kind: EntityKind?,
    aquariumId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val aquariumRepository: AquariumRepository = remember {
        KoinJavaComponent.get(AquariumRepository::class.java)
    }
    val issueRepository: IssueRepository = remember {
        KoinJavaComponent.get(IssueRepository::class.java)
    }
    val memoRepository: MemoRepository = remember {
        KoinJavaComponent.get(MemoRepository::class.java)
    }
    val timelineEventRepository: TimelineEventRepository = remember {
        KoinJavaComponent.get(TimelineEventRepository::class.java)
    }

    val formViewModel: EntityFormViewModel = viewModel(
        factory = remember(
            kind,
            aquariumId,
            aquariumRepository,
            issueRepository,
            memoRepository,
            timelineEventRepository
        ) {
            EntityFormViewModel.factory(
                kind = kind,
                aquariumId = aquariumId,
                aquariumRepository = aquariumRepository,
                issueRepository = issueRepository,
                memoRepository = memoRepository,
                timelineEventRepository = timelineEventRepository
            )
        }
    )

    val uiState by formViewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = uiState.headline,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = uiState.supportingText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        uiState.aquariumName?.let { aquariumName ->
                            Text(
                                text = "Tank: $aquariumName",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (uiState.aquariumOptions.isNotEmpty()) {
                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Tank",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.aquariumOptions, key = { it.id }) { option ->
                                    FilterChip(
                                        selected = option.isSelected,
                                        onClick = { formViewModel.onAquariumSelected(option.id) },
                                        enabled = !uiState.isSaving,
                                        label = { Text(option.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.draft.createdAtInput,
                            onValueChange = formViewModel::onCreatedAtInputChanged,
                            label = { Text("When") },
                            supportingText = {
                                Text("Format: yyyy-MM-dd HH:mm")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isSaving
                        )

                        when (uiState.kind) {
                            EntityKind.ISSUE -> {
                                OutlinedTextField(
                                    value = uiState.draft.issueTitle,
                                    onValueChange = formViewModel::onIssueTitleChanged,
                                    label = { Text("Issue title") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isSaving
                                )
                            }

                            EntityKind.MEMO -> {
                                OutlinedTextField(
                                    value = uiState.draft.memoContent,
                                    onValueChange = formViewModel::onMemoContentChanged,
                                    label = { Text("Memo content") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isSaving,
                                    minLines = 3
                                )
                                OutlinedTextField(
                                    value = uiState.draft.memoPhotoUri,
                                    onValueChange = formViewModel::onMemoPhotoUriChanged,
                                    label = { Text("Photo URI (optional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isSaving
                                )
                            }

                            else -> {
                                Text(
                                    text = "This form kind is not supported yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            uiState.statusMessage?.let { message ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            item {
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = formViewModel::save,
                            enabled = uiState.canSave,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(uiState.saveButtonLabel)
                        }
                        OutlinedButton(
                            onClick = onDone,
                            enabled = !uiState.isSaving,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back")
                        }
                    }
                }
            }
        }
    }
}
