package dev.otherworld.shoppinglist.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.data.repo.TagRepository
import dev.otherworld.shoppinglist.domain.model.TagModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.otherworld.shoppinglist.ui.common.TextEntryDialog

data class TagsUiState(
    val loading: Boolean = false,
    val tags: List<TagModel> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ManageTagsViewModel @Inject constructor(
    private val repository: TagRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TagsUiState(loading = true))
    val state: StateFlow<TagsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                _state.update { it.copy(loading = false, tags = repository.getTags()) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Failed to load tags") }
            }
        }
    }

    fun createTag(name: String) = mutate { repository.createTag(name) }
    fun deleteTag(id: Long) = mutate { repository.deleteTag(id) }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(tags = repository.getTags()) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Action failed") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTagsScreen(
    onBack: () -> Unit,
    viewModel: ManageTagsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            androidx.compose.material3.TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Tags") },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New tag") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.tags.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.tags.isEmpty() ->
                    Text(
                        "No tags yet. Tap “New tag” to create one.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center,
                    )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.tags, key = { it.id }) { tag ->
                        ListItem(
                            headlineContent = { Text("#${tag.name}") },
                            trailingContent = {
                                IconButton(onClick = { viewModel.deleteTag(tag.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showCreate) {
        TextEntryDialog(
            title = "New tag",
            label = "Tag name",
            confirmLabel = "Create",
            onConfirm = { showCreate = false; viewModel.createTag(it) },
            onDismiss = { showCreate = false },
        )
    }
}
