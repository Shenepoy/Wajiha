package com.wajiha.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wajiha.input.requestContentFocus
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.platform.PermissionStates
import com.wajiha.platform.SystemControls
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.GamepadForm
import com.wajiha.ui.settings.SettingsViewModel
import com.wajiha.ui.theme.WajihaMotion
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * Cocoon-style first-run: conversational copy, typewriter welcome,
 * Grant Access cards, theme pick, add-games — not a form checklist.
 */
private enum class SetupStep {
    Welcome,
    Theme,
    GrantAccess,
    DefaultHome,
    AddGames,
    Done,
}

@Composable
fun OnboardingScreen(
    settingsViewModel: SettingsViewModel,
    onFinished: () -> Unit,
    onOpenPlatformPicker: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
) {
    val controls = koinInject<SystemControls>()
    var step by remember { mutableStateOf(SetupStep.Welcome) }
    var perms by remember { mutableStateOf(PermissionStates()) }
    val folders by settingsViewModel.folders.collectAsState()
    val contentFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        while (true) {
            perms = controls.permissionStates()
            delay(1200)
        }
    }

    WajihaScreen(
        layerId = "onboarding",
        modifier = modifier,
        showActionBar = true,
        gamepadHints =
            listOf(
                "A" to "Continue",
                "B" to "Back / Skip",
                "L2" to "Focus screen",
            ),
        gamepadOwner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        onOwnerGainedFocus = { contentFocus.requestContentFocus() },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface,
                            ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    fadeIn(WajihaMotion.fadeInSpec()) togetherWith fadeOut(WajihaMotion.fadeOutSpec())
                },
                label = "setup",
            ) { current ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp)
                            .focusRequester(contentFocus)
                            .wajihaGamepadFocus(),
                ) {
                    GamepadForm(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        when (current) {
                            SetupStep.Welcome -> {
                                WelcomeBeat(
                                    onContinue = { step = SetupStep.Theme },
                                    onSkip = onFinished,
                                )
                            }

                            SetupStep.Theme -> {
                                ThemeBeat(
                                    onPick = { theme ->
                                        settingsViewModel.setTheme(theme)
                                        step = SetupStep.GrantAccess
                                    },
                                    onBack = { step = SetupStep.Welcome },
                                )
                            }

                            SetupStep.GrantAccess -> {
                                GrantAccessBeat(
                                    perms = perms,
                                    controls = controls,
                                    onBack = { step = SetupStep.Theme },
                                    onContinue = { step = SetupStep.DefaultHome },
                                )
                            }

                            SetupStep.DefaultHome -> {
                                HomeBeat(
                                    isDefault = perms.isDefaultLauncher,
                                    onOpenHome = controls::openHomeSettings,
                                    onBack = { step = SetupStep.GrantAccess },
                                    onContinue = { step = SetupStep.AddGames },
                                )
                            }

                            SetupStep.AddGames -> {
                                AddGamesBeat(
                                    folderCount = folders.size,
                                    onAddPlatform = {
                                        onOpenPlatformPicker?.invoke() ?: onFinished()
                                    },
                                    onBack = { step = SetupStep.DefaultHome },
                                    onContinue = { step = SetupStep.Done },
                                    onSkip = { step = SetupStep.Done },
                                )
                            }

                            SetupStep.Done -> {
                                DoneBeat(onFinished = onFinished)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeBeat(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val full = "Welcome to Wajiha! Let's get you ready to go."
    var typed by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        full.forEachIndexed { i, _ ->
            typed = full.take(i + 1)
            delay(28)
        }
    }
    Spacer(modifier = Modifier.height(48.dp))
    Text(
        text = "Wajiha",
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = typed + if (typed.length < full.length) "|" else "",
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text =
            "Top screen for game art. Bottom screen to browse and launch.\n" +
                "Even games you start yourself show up as Now Playing.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(36.dp))
    GamepadButton(
        text = "Let's go",
        onClick = onContinue,
        enabled = typed.length >= full.length,
        modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
    )
    GamepadButton(text = "Skip for now", onClick = onSkip, outlined = true)
}

@Composable
private fun ThemeBeat(
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    BeatTitle("Choose your look")
    BeatBody("Dark feels like a handheld console. Light is closer to the classic 3DS home menu. You can change this anytime.")
    Spacer(modifier = Modifier.height(20.dp))
    ChoiceCard(
        title = "Dark",
        description = "Deep navy chrome — easy on OLED panels",
    ) { onPick("dark") }
    Spacer(modifier = Modifier.height(10.dp))
    ChoiceCard(
        title = "Light",
        description = "Soft blue-greys — bright play sessions",
    ) { onPick("light") }
    Spacer(modifier = Modifier.height(10.dp))
    ChoiceCard(
        title = "Follow system",
        description = "Match Android's light / dark setting",
    ) { onPick("system") }
    BackOnly(onBack)
}

@Composable
private fun GrantAccessBeat(
    perms: PermissionStates,
    controls: SystemControls,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val items =
        listOf(
            GrantItem(
                "Usage access",
                "So the bottom screen can follow games you launch yourself",
                perms.usageAccess,
                controls::requestUsageAccess,
            ),
            GrantItem(
                "Modify system settings",
                "Brightness and screen timeout from Quick Settings",
                perms.writeSettings,
                controls::requestWriteSettings,
            ),
            GrantItem(
                "Notifications",
                "Progress while scanning and scraping your library",
                perms.notifications,
                controls::requestNotifications,
            ),
            GrantItem(
                "All files access",
                "Optional — some emulators need direct paths",
                perms.allFilesAccess,
                controls::requestAllFilesAccess,
            ),
        )
    val granted = items.count { it.granted }

    BeatTitle("Grant Access")
    BeatBody(
        if (granted == items.size) {
            "Nice — you're fully unlocked. Continue whenever you're ready."
        } else {
            "$granted of ${items.size} ready. Tap Grant on each card; " +
                "Android will bounce you to Settings — come back and they'll light up."
        },
    )
    Spacer(modifier = Modifier.height(16.dp))
    items.forEach { item ->
        GrantCard(item)
        Spacer(modifier = Modifier.height(8.dp))
    }
    BeatNav(onBack = onBack, onNext = onContinue, nextLabel = if (granted > 0) "Continue" else "Continue anyway")
}

@Composable
private fun HomeBeat(
    isDefault: Boolean,
    onOpenHome: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    BeatTitle("Make Wajiha home")
    BeatBody(
        if (isDefault) {
            "Perfect — pressing HOME on either screen brings you back here, " +
                "and the bottom screen stays in Wajiha."
        } else {
            "This is the big one for dual-screen devices. Set Wajiha as your " +
                "default home app so HOME always returns here — and the bottom " +
                "screen doesn't fall back to the stock launcher."
        },
    )
    Spacer(modifier = Modifier.height(20.dp))
    if (isDefault) {
        StatusPill("Default home set")
    } else {
        GamepadButton(
            text = "Open home settings",
            onClick = onOpenHome,
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
        )
    }
    BeatNav(
        onBack = onBack,
        onNext = onContinue,
        nextLabel = if (isDefault) "Continue" else "I'll do this later",
    )
}

@Composable
private fun AddGamesBeat(
    folderCount: Int,
    onAddPlatform: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    BeatTitle("Add Games")
    BeatBody(
        if (folderCount > 0) {
            "$folderCount folder${if (folderCount == 1) "" else "s"} linked. Add another, or continue."
        } else {
            "Choose a system, then point Wajiha at its ROM folder. " +
                "It scans in the background — neat box art comes later in Scraper."
        },
    )
    Spacer(modifier = Modifier.height(16.dp))
    GamepadButton(
        text = "Choose a platform",
        onClick = onAddPlatform,
        modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "You’ll pick from the full catalog, then set emulator and folders.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GamepadButton(text = "Back", onClick = onBack, outlined = true)
        Row {
            GamepadButton(text = "Skip", onClick = onSkip, outlined = true)
            GamepadButton(
                text = if (folderCount > 0) "Continue" else "Continue anyway",
                onClick = onContinue,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun DoneBeat(onFinished: () -> Unit) {
    Spacer(modifier = Modifier.height(40.dp))
    Text(
        text = "All set up!",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text =
            "Time to show you around.\n\n" +
                "Bottom screen — browse and launch.\n" +
                "Top screen — art and info for what's focused.\n" +
                "Settings → Library — Add platform, then folders / Scraper.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(32.dp))
    GamepadButton(
        text = "Show me around",
        onClick = onFinished,
        modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
    )
}

// —— shared beats ——

@Composable
private fun BeatTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BeatBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun BeatNav(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GamepadButton(text = "Back", onClick = onBack, outlined = true)
        GamepadButton(text = nextLabel, onClick = onNext)
    }
}

@Composable
private fun BackOnly(onBack: () -> Unit) {
    GamepadButton(text = "Back", onClick = onBack, outlined = true)
}

@Composable
private fun ChoiceCard(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = WajihaShapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private data class GrantItem(
    val label: String,
    val description: String,
    val granted: Boolean,
    val onRequest: () -> Unit,
)

@Composable
private fun GrantCard(item: GrantItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = WajihaShapes.card,
        color =
            if (item.granted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (item.granted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        ),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.granted) {
                Text(
                    text = "GRANTED",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                GamepadButton(text = "Grant Access", onClick = item.onRequest, outlined = true)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        shape = WajihaShapes.chip,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
