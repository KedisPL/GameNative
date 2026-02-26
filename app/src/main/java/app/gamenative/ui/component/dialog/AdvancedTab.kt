package app.gamenative.ui.component.dialog

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.component.settings.SettingsCPUList
import app.gamenative.ui.component.settings.SettingsListDropdown
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.theme.settingsTileColorsAlt
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsSwitch

@Composable
fun AdvancedTabContent(state: ContainerConfigState) {
    val config = state.config.value
    SettingsGroup() {
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = "Run as Root") },
            subtitle = { Text(text = "Experimental: Requires rooted device") },
            state = config.runAsRoot,
            onCheckedChange = {
                state.config.value = config.copy(runAsRoot = it)
            },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.startup_selection)) },
            value = config.startupSelection.toInt().takeIf { it in state.getStartupSelectionOptions().indices } ?: 1,
            items = state.getStartupSelectionOptions(),
            onItemSelected = {
                state.config.value = config.copy(startupSelection = it.toByte())
            },
        )
        SettingsCPUList(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.processor_affinity)) },
            value = config.cpuList,
            onValueChange = {
                state.config.value = config.copy(cpuList = it)
            },
        )
        SettingsCPUList(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.processor_affinity_32bit)) },
            value = config.cpuListWoW64,
            onValueChange = { state.config.value = config.copy(cpuListWoW64 = it) },
        )
    }
}
