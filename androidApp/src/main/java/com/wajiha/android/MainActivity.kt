package com.wajiha.android

import android.content.Intent
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import com.wajiha.App
import com.wajiha.android.display.DisplayCoordinator
import com.wajiha.android.input.GamepadGate
import com.wajiha.android.input.GamepadKeyRouter
import com.wajiha.android.input.handleGamepadKey
import com.wajiha.android.launch.PlaySessionTracker
import com.wajiha.android.library.RomFolderManager
import com.wajiha.android.monitor.ForegroundAppMonitor
import com.wajiha.android.platform.AndroidLibraryActions
import com.wajiha.android.service.KeepAliveService
import com.wajiha.android.system.SystemController
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val sessionTracker: PlaySessionTracker by inject()
    private val dualScreenStore: DualScreenStore by inject()
    private val displayCoordinator: DisplayCoordinator by inject()
    private val gamepadKeyRouter: GamepadKeyRouter by inject()
    private val gamepadGate: GamepadGate by inject()
    private val foregroundAppMonitor: ForegroundAppMonitor by inject()
    private val libraryActions: AndroidLibraryActions by inject()
    private val romFolderManager: RomFolderManager by inject()
    private val systemController: SystemController by inject()

    private var pendingFolderPlatformId: String? = null

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val platformId = pendingFolderPlatformId ?: return@registerForActivityResult
            pendingFolderPlatformId = null
            uri ?: return@registerForActivityResult
            lifecycleScope.launch { romFolderManager.addFolder(uri, platformId) }
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val displayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "onCreate: displayId=$displayId taskId=$taskId " +
                "intent=${intent?.action ?: intent?.categories?.joinToString()}"
        )

        // Drawer/HOME can land singleTask MainActivity on the bottom display;
        // hop to display 0 before painting hero UI (RetroHrai performTrampoline).
        if (displayCoordinator.redirectMainToPrimaryIfNeeded(this)) {
            WajihaLog.i(WajihaTags.DISPLAY, "onCreate: redirecting — skipping UI setup")
            return
        }

        displayCoordinator.start()
        gamepadKeyRouter.attach(GamepadOwner.Primary, this)
        foregroundAppMonitor.start()
        libraryActions.folderPickHandler = { platformId ->
            pendingFolderPlatformId = platformId
            folderPicker.launch(null)
        }
        systemController.notificationPermissionHandler = {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // HOME launcher: swallow BACK so the home screen can't be dismissed
        // (in-app screens navigate with their own back buttons).
        onBackPressedDispatcher.addCallback(this) { }

        setContent {
            App()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (gamepadGate.shouldBlockGamepad()) return false
        return gamepadKeyRouter.dispatch(GamepadOwner.Primary, event) { remappedOrRaw ->
            handleGamepadKey(this, remappedOrRaw) { super.dispatchKeyEvent(it) } ||
                super.dispatchKeyEvent(remappedOrRaw)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val displayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        WajihaLog.i(
            WajihaTags.DISPLAY,
            "onNewIntent: displayId=$displayId taskId=$taskId " +
                "intent=${intent.action ?: intent.categories?.joinToString()}"
        )
        // singleTask can deliver LAUNCHER/HOME while the task sits on the
        // wrong display — re-hop to display 0.
        if (displayCoordinator.redirectMainToPrimaryIfNeeded(this)) return
        // HOME while MainActivity is already resumed does not call onResume again;
        // reclaim the bottom screen when stock SECONDARY_HOME stole display 4.
        if (intent.hasCategory(Intent.CATEGORY_HOME) &&
            (display?.displayId ?: Display.DEFAULT_DISPLAY) == Display.DEFAULT_DISPLAY
        ) {
            displayCoordinator.scheduleSecondaryHome(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // Returning from an external game: close the session, restore browsing
        sessionTracker.onLauncherResumed()
        foregroundAppMonitor.onLauncherForegrounded()
        gamepadGate.onLauncherForegrounded()
        KeepAliveService.stop(this)
        // Bring the bottom screen back after primary has focus (covers first
        // start as a regular app and coming back after HOME sent the bottom
        // screen elsewhere). Secondary must not run before focusPrimary or
        // display 4 steals topDisplayFocusedRootTask on Thor drawer launch.
        if ((display?.displayId ?: Display.DEFAULT_DISPLAY) == Display.DEFAULT_DISPLAY) {
            displayCoordinator.onPrimaryMainResumed()
            displayCoordinator.scheduleSecondaryHome(this)
        }
    }

    override fun onPause() {
        super.onPause()
        gamepadGate.onLauncherBackgrounded()
        if ((display?.displayId ?: Display.DEFAULT_DISPLAY) == Display.DEFAULT_DISPLAY) {
            displayCoordinator.onPrimaryMainStopped()
        }
    }

    override fun onDestroy() {
        gamepadKeyRouter.detach(GamepadOwner.Primary, this)
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
