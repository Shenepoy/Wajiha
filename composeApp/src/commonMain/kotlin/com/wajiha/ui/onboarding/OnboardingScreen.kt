package com.wajiha.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadKeys
import com.wajiha.input.requestContentFocus
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.platform.PermissionStates
import com.wajiha.platform.SystemControls
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.components.WajihaScreen
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.WajihaGlyphAction
import com.wajiha.ui.settings.SettingsViewModel
import com.wajiha.ui.theme.WajihaMotion
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import wajiha.composeapp.generated.resources.Res
import wajiha.composeapp.generated.resources.logo

/**
 * Cocoon-style first-run: conversational copy, typewriter welcome,
 * Grant Access cards, theme pick, add-games — not a form checklist.
 */
private enum class SetupStep {
    Welcome,
    Theme,
    GrantAccess,
    DisplayLayout,
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
    val settings by settingsViewModel.settings.collectAsState()
    val singleScreen = settings.singleScreen
    val contentFocus = remember { FocusRequester() }
    var welcomeReady by remember { mutableStateOf(false) }
    val backLabel =
        when (step) {
            SetupStep.Welcome -> "Skip"
            SetupStep.Done -> null
            else -> "Back"
        }
    val onBack: (() -> Unit)? =
        when (step) {
            SetupStep.Welcome -> {
                onFinished
            }

            SetupStep.Theme -> {
                { step = SetupStep.Welcome }
            }

            SetupStep.GrantAccess -> {
                { step = SetupStep.Theme }
            }

            SetupStep.DisplayLayout -> {
                { step = SetupStep.GrantAccess }
            }

            SetupStep.DefaultHome -> {
                { step = SetupStep.DisplayLayout }
            }

            SetupStep.AddGames -> {
                { step = SetupStep.DefaultHome }
            }

            SetupStep.Done -> {
                null
            }
        }
    val continueLabel =
        when (step) {
            SetupStep.Theme, SetupStep.DisplayLayout -> null
            else -> "Continue"
        }
    val continueEnabled = step != SetupStep.Welcome || welcomeReady
    val onContinue: (() -> Unit)? =
        when (step) {
            SetupStep.Welcome -> {
                { step = SetupStep.Theme }
            }

            SetupStep.GrantAccess -> {
                { step = SetupStep.DisplayLayout }
            }

            SetupStep.DefaultHome -> {
                { step = SetupStep.AddGames }
            }

            SetupStep.AddGames -> {
                { step = SetupStep.Done }
            }

            SetupStep.Done -> {
                onFinished
            }

            SetupStep.Theme, SetupStep.DisplayLayout -> {
                null
            }
        }

    LaunchedEffect(step) {
        if (step == SetupStep.Welcome) welcomeReady = false
    }
    LaunchedEffect(Unit) {
        while (true) {
            perms = controls.permissionStates()
            delay(1200)
        }
    }

    WajihaScreen(
        layerId = "onboarding",
        modifier = modifier,
        showActionBar = false,
        gamepadOwner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        onOwnerGainedFocus = { contentFocus.requestContentFocus() },
        onPreviewKey = { event ->
            when {
                GamepadKeys.isL1(event.type, event.key) && onBack != null -> {
                    onBack.invoke()
                    true
                }

                GamepadKeys.isR1(event.type, event.key) && onContinue != null && continueEnabled -> {
                    onContinue.invoke()
                    true
                }

                else -> {
                    false
                }
            }
        },
    ) {
        Column(
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
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        fadeIn(WajihaMotion.fadeInSpec()) togetherWith fadeOut(WajihaMotion.fadeOutSpec())
                    },
                    label = "setup",
                    modifier = Modifier.fillMaxSize(),
                ) { current ->
                    val scroll = rememberScrollState()
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth()
                                .widthIn(max = 560.dp)
                                .focusRequester(contentFocus)
                                .wajihaGamepadFocus(),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scroll)
                                    .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
                        ) {
                            when (current) {
                                SetupStep.Welcome -> {
                                    WelcomeBeat(
                                        singleScreen = singleScreen,
                                        onReady = { welcomeReady = true },
                                    )
                                }

                                SetupStep.Theme -> {
                                    ThemeBeat { theme ->
                                        settingsViewModel.setTheme(theme)
                                        step = SetupStep.GrantAccess
                                    }
                                }

                                SetupStep.GrantAccess -> {
                                    GrantAccessBeat(
                                        perms = perms,
                                        controls = controls,
                                        singleScreen = singleScreen,
                                    )
                                }

                                SetupStep.DisplayLayout -> {
                                    DisplayLayoutBeat { single ->
                                        settingsViewModel.setSingleScreen(single)
                                        step = SetupStep.DefaultHome
                                    }
                                }

                                SetupStep.DefaultHome -> {
                                    HomeBeat(
                                        isDefault = perms.isDefaultLauncher,
                                        singleScreen = singleScreen,
                                        onOpenHome = controls::openHomeSettings,
                                    )
                                }

                                SetupStep.AddGames -> {
                                    AddGamesBeat(
                                        folderCount = folders.size,
                                        onAddPlatform = {
                                            onOpenPlatformPicker?.invoke() ?: onFinished()
                                        },
                                    )
                                }

                                SetupStep.Done -> {
                                    DoneBeat(singleScreen = singleScreen)
                                }
                            }
                        }
                        OnboardingScrollbar(
                            scrollState = scroll,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (backLabel != null && onBack != null) {
                    WajihaGlyphAction(
                        button = GamepadHintButton.L1,
                        label = backLabel,
                        onClick = onBack,
                    )
                } else {
                    Spacer(modifier = Modifier)
                }
                if (continueLabel != null && onContinue != null) {
                    WajihaGlyphAction(
                        button = GamepadHintButton.R1,
                        label = continueLabel,
                        onClick = onContinue,
                        glyphAtEnd = true,
                        enabled = continueEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeBeat(
    singleScreen: Boolean,
    onReady: () -> Unit,
) {
    val full = "Welcome to Wajiha! Let's get you ready to go."
    var typed by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        full.forEachIndexed { i, _ ->
            typed = full.take(i + 1)
            delay(28)
        }
        onReady()
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp).clip(CircleShape),
        )
        Text(
            text = "Wajiha",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = typed + if (typed.length < full.length) "|" else "",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text =
                if (singleScreen) {
                    "Preview on top, library below — all on one display.\n" +
                        "Even games you start yourself show up as Now Running."
                } else {
                    "Top screen for game art. Bottom screen to browse and launch.\n" +
                        "Even games you start yourself show up as Now Running."
                },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeBeat(onPick: (String) -> Unit) {
    BeatTitle("Choose your look")
    BeatBody("Dark feels like a handheld console. Light is closer to the classic 3DS home menu. You can change this anytime.")
    ChoiceCard(
        title = "Dark",
        description = "Deep navy chrome — easy on OLED panels",
    ) { onPick("dark") }
    ChoiceCard(
        title = "Light",
        description = "Soft blue-greys — bright play sessions",
    ) { onPick("light") }
    ChoiceCard(
        title = "Follow system",
        description = "Match Android's light / dark setting",
    ) { onPick("system") }
}

@Composable
private fun GrantAccessBeat(
    perms: PermissionStates,
    controls: SystemControls,
    singleScreen: Boolean,
) {
    val items =
        listOf(
            GrantItem(
                "Usage access",
                if (singleScreen) {
                    "So Wajiha can follow games you launch yourself"
                } else {
                    "So the bottom screen can follow games you launch yourself"
                },
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
    items.forEach { item ->
        GrantCard(item)
    }
}

@Composable
private fun DisplayLayoutBeat(onPick: (singleScreen: Boolean) -> Unit) {
    BeatTitle("One screen or two?")
    BeatBody(
        "Wajiha can use both screens on clamshell handhelds, or combine everything " +
            "on the main display. Change anytime in Settings → Screens.",
    )
    ChoiceCard(
        title = "Dual screens",
        description =
            "Art on one display, browse and launch on the other. " +
                "Best on AYN Thor and clamshell handhelds.",
    ) { onPick(false) }
    ChoiceCard(
        title = "Single screen",
        description =
            "Combined preview + library on the main display. " +
                "Use this on phones or when you only want one panel.",
    ) { onPick(true) }
}

@Composable
private fun HomeBeat(
    isDefault: Boolean,
    singleScreen: Boolean,
    onOpenHome: () -> Unit,
) {
    BeatTitle("Make Wajiha home")
    BeatBody(
        when {
            isDefault && singleScreen -> {
                "Perfect — pressing HOME brings you back here on the main display."
            }

            isDefault -> {
                "Perfect — pressing HOME on either screen brings you back here, " +
                    "and the bottom screen stays in Wajiha."
            }

            singleScreen -> {
                "Set Wajiha as your default home app so HOME always returns here " +
                    "on the main display."
            }

            else -> {
                "This is the big one for dual-screen devices. Set Wajiha as your " +
                    "default home app so HOME always returns here — and the bottom " +
                    "screen doesn't fall back to the stock launcher."
            }
        },
    )
    if (isDefault) {
        StatusPill("Default home set")
    } else {
        GamepadButton(
            text = "Open home settings",
            onClick = onOpenHome,
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
        )
    }
}

@Composable
private fun AddGamesBeat(
    folderCount: Int,
    onAddPlatform: () -> Unit,
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
    GamepadButton(
        text = "Choose a platform",
        onClick = onAddPlatform,
        modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
    )
    Text(
        text = "You’ll pick from the full catalog, then set emulator and folders.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DoneBeat(singleScreen: Boolean) {
    Text(
        text = "All set up!",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
        text =
            if (singleScreen) {
                "Time to show you around.\n\n" +
                    "Combined view — preview above, library below.\n" +
                    "Settings → Library — Add platform, then folders / Scraper.\n" +
                    "Settings → Screens — switch to dual anytime."
            } else {
                "Time to show you around.\n\n" +
                    "Bottom screen — browse and launch.\n" +
                    "Top screen — art and info for what's focused.\n" +
                    "Settings → Library — Add platform, then folders / Scraper."
            },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// —— shared beats ——

@Composable
private fun BeatTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
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
    )
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
        Column(
            modifier =
                Modifier.padding(horizontal = WajihaSpacing.md, vertical = WajihaSpacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = WajihaSpacing.xs),
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = WajihaSpacing.md,
                        vertical = WajihaShapes.heroInnerCornerRadius,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(WajihaShapes.heroInnerCornerRadius)
                        .clip(CircleShape)
                        .background(
                            if (item.granted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        ),
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = WajihaShapes.heroInnerCornerRadius),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
                WajihaGlyphAction(
                    button = GamepadHintButton.A,
                    label = "Grant Access",
                    onClick = item.onRequest,
                    glyphAtEnd = true,
                    outlined = true,
                )
            }
        }
    }
}

@Composable
private fun OnboardingScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    if (scrollState.maxValue <= 0) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier
            .padding(vertical = WajihaSpacing.xs, horizontal = WajihaSpacing.micro)
            .fillMaxHeight()
            .width(WajihaSpacing.xs),
    ) {
        val viewport = scrollState.viewportSize.coerceAtLeast(1)
        val total = viewport + scrollState.maxValue
        val minThumb = WajihaSpacing.lg.toPx()
        val thumbHeight = (size.height * viewport / total).coerceIn(minThumb, size.height)
        val travel = (size.height - thumbHeight).coerceAtLeast(0f)
        val y = travel * (scrollState.value.toFloat() / scrollState.maxValue)
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, y),
            size = Size(size.width, thumbHeight),
            cornerRadius = CornerRadius(size.width / 2f),
        )
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
            modifier =
                Modifier.padding(
                    horizontal = WajihaSpacing.md,
                    vertical = WajihaSpacing.sm,
                ),
        )
    }
}
