package io.github.andreasscholz.bbbot.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.andreasscholz.bbbot.R
import io.github.andreasscholz.bbbot.bot.BotService
import io.github.andreasscholz.bbbot.bot.BotSettings
import io.github.andreasscholz.bbbot.game.Strategy

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val serviceConnected by BotService.connected.collectAsStateWithLifecycle()
    val gameIntent = context.packageManager.getLaunchIntentForPackage(BotService.GAME_PACKAGE)

    Column(
        modifier = Modifier
            // The insets come first, or the text scrolls underneath the status bar.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.main_intro),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )

        Step(
            title = stringResource(R.string.main_service_title),
            text = stringResource(if (serviceConnected) R.string.main_service_on else R.string.main_service_off),
            button = stringResource(R.string.main_open_settings).takeUnless { serviceConnected },
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )
        Step(
            title = stringResource(R.string.main_game_title),
            text = stringResource(if (gameIntent != null) R.string.main_game_text else R.string.main_game_missing),
            button = stringResource(R.string.main_open_game).takeIf { gameIntent != null },
            onClick = { gameIntent?.let(context::startActivity) },
        )
        val settings = remember { BotSettings(context) }
        StrategyChoice(settings)
        MeasureSwitch(settings)
            EndlessSwitch(settings)
            AlertSwitch(settings)
    }
}

@Composable
private fun StrategyChoice(settings: BotSettings) {
    var chosen by remember { mutableStateOf(settings.strategy) }

    Text(
        text = stringResource(R.string.main_strategy_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 32.dp),
    )
    Text(
        text = stringResource(R.string.main_strategy_text),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    for (strategy in Strategy.entries) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = strategy == chosen,
                    role = Role.RadioButton,
                    onClick = {
                        chosen = strategy
                        settings.strategy = strategy
                    },
                )
                .padding(vertical = 8.dp),
        ) {
            RadioButton(selected = strategy == chosen, onClick = null)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = stringResource(strategy.label), style = MaterialTheme.typography.bodyLarge)
                Text(text = stringResource(strategy.description), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MeasureSwitch(settings: BotSettings) {
    var measure by remember { mutableStateOf(settings.measure) }

    Text(
        text = stringResource(R.string.main_measure_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 32.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.main_measure_text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )
        Switch(
            checked = measure,
            onCheckedChange = {
                measure = it
                settings.measure = it
            },
        )
    }
}

@Composable
private fun EndlessSwitch(settings: BotSettings) {
    var endless by remember { mutableStateOf(settings.endless) }

    Text(
        text = stringResource(R.string.main_endless_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 32.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.main_endless_text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )
        Switch(
            checked = endless,
            onCheckedChange = {
                endless = it
                settings.endless = it
            },
        )
    }
}

@Composable
private fun AlertSwitch(settings: BotSettings) {
    var alert by remember { mutableStateOf(settings.alert) }

    Text(
        text = stringResource(R.string.main_alert_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 32.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.main_alert_text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )
        Switch(
            checked = alert,
            onCheckedChange = {
                alert = it
                settings.alert = it
            },
        )
    }
}

/** The name of a strategy as shown to the user. */
@get:StringRes
val Strategy.label: Int
    get() = when (this) {
        Strategy.SAFE -> R.string.strategy_safe
        Strategy.BALANCED -> R.string.strategy_balanced
        Strategy.RISKY -> R.string.strategy_risky
    }

@get:StringRes
private val Strategy.description: Int
    get() = when (this) {
        Strategy.SAFE -> R.string.strategy_safe_text
        Strategy.BALANCED -> R.string.strategy_balanced_text
        Strategy.RISKY -> R.string.strategy_risky_text
    }

@Composable
private fun Step(title: String, text: String, button: String?, onClick: () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 32.dp),
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (button != null) {
        Button(onClick = onClick, modifier = Modifier.padding(top = 12.dp)) {
            Text(button)
        }
    }
}
