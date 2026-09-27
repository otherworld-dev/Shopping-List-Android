package dev.otherworld.shoppinglist.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.prefs.DisplayPrefs
import dev.otherworld.shoppinglist.data.prefs.ThemeMode
import dev.otherworld.shoppinglist.data.repo.ListOrdering
import dev.otherworld.shoppinglist.data.repo.ListSettingsRepository
import dev.otherworld.shoppinglist.domain.sort.ListSortMode
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val displayPrefs: DisplayPrefs,
    listSettings: ListSettingsRepository,
    private val ordering: ListOrdering,
) : ViewModel() {
    val themeMode = displayPrefs.themeMode
    val listSort = listSettings.listSort
    val listOrderSupported = listSettings.listOrderSupported

    fun setThemeMode(mode: ThemeMode) = displayPrefs.setThemeMode(mode)

    fun setListSort(mode: ListSortMode) {
        viewModelScope.launch { ordering.setListSort(mode) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val listSort by viewModel.listSort.collectAsStateWithLifecycle()
    val supported by viewModel.listOrderSupported.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Needs a server that keeps list orders (server app 1.10.0 or later).
            if (supported) {
                Section(stringResource(R.string.settings_sort_lists)) {
                    Choice(stringResource(R.string.sort_recently_updated), listSort == ListSortMode.UPDATED) { viewModel.setListSort(ListSortMode.UPDATED) }
                    Choice(stringResource(R.string.sort_a_to_z), listSort == ListSortMode.ALPHA) { viewModel.setListSort(ListSortMode.ALPHA) }
                    Choice(stringResource(R.string.sort_custom), listSort == ListSortMode.CUSTOM) { viewModel.setListSort(ListSortMode.CUSTOM) }
                }
            }
            Section(stringResource(R.string.settings_theme)) {
                Choice(stringResource(R.string.theme_system), themeMode == ThemeMode.SYSTEM) { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                Choice(stringResource(R.string.theme_light), themeMode == ThemeMode.LIGHT) { viewModel.setThemeMode(ThemeMode.LIGHT) }
                Choice(stringResource(R.string.theme_dark), themeMode == ThemeMode.DARK) { viewModel.setThemeMode(ThemeMode.DARK) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    Column(Modifier.selectableGroup()) { content() }
}

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
