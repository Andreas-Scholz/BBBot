package io.github.andreasscholz.bbbot.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.andreasscholz.bbbot.R
import io.github.andreasscholz.bbbot.bot.BotService

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
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
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
    }
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
