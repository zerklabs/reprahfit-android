package com.reprahfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reprahfit.ui.theme.ReprahfitTheme

private enum class Screen { Detailed, Simple, History }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReprahfitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var screen by rememberSaveable { mutableStateOf(Screen.Detailed) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (screen) {
                            Screen.Detailed -> OutdoorRideScreen()
                            Screen.Simple -> SimpleDashboardScreen()
                            Screen.History -> RideHistoryScreen(
                                onBack = { screen = Screen.Detailed }
                            )
                        }

                        if (screen != Screen.History) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TextButton(onClick = {
                                    screen = if (screen == Screen.Simple) {
                                        Screen.Detailed
                                    } else {
                                        Screen.Simple
                                    }
                                }) {
                                    Text(
                                        text = if (screen == Screen.Simple) {
                                            stringResource(R.string.switch_to_detailed_cta)
                                        } else {
                                            stringResource(R.string.switch_to_simple_cta)
                                        }
                                    )
                                }
                                TextButton(onClick = { screen = Screen.History }) {
                                    Text(text = stringResource(R.string.history_cta))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
