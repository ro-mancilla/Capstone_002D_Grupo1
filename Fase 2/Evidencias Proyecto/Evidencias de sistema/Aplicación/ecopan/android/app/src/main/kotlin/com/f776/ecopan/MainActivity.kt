package com.f776.ecopan

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.f776.ecopan.core.navigation.Navigator
import com.f776.ecopan.core.navigation.deeplink.DeepLinkMatcher
import com.f776.ecopan.core.navigation.deeplink.DeepLinkPattern
import com.f776.ecopan.core.navigation.deeplink.DeepLinkRequest
import com.f776.ecopan.domain.sync.BackgroundSyncEnqueuer
import com.f776.ecopan.feature.production.route.ProductionRoute
import dev.icerock.moko.permissions.PermissionsController
import kotlinx.serialization.Serializable
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val appViewModel by viewModel<AppViewModel>()
    private val backgroundSyncEnqueuer by inject<BackgroundSyncEnqueuer>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        // The app draws a navigation bar / rail of its own, so the system must not paint its
        // translucent scrim over it on three-button navigation -- the bar's own colour has to
        // reach the bottom of the screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        val initialRoute = intent?.data?.let { uri -> handleExampleDeepLink(uri) } ?: ProductionRoute

        val permissionsController: PermissionsController by inject()
        permissionsController.bind(this)

        setContent {
            val theme by appViewModel.theme.collectAsStateWithLifecycle()
            val useDynamicColors by appViewModel.useDynamicColors.collectAsStateWithLifecycle()

            // Exposes the screens' test tags as resource ids, which is how the Baseline Profile
            // journeys (:baselineProfile) find them through UI Automator. Nothing else reads them.
            Box(Modifier.semantics { testTagsAsResourceId = true }) {
                App(
                    theme = theme,
                    dynamicColor = useDynamicColors,
                    initialRoute = initialRoute
                )
            }
        }
    }

    /**
     * 03 §7's Android hardening: hand the outbox to WorkManager as the app leaves the foreground,
     * which is exactly when `SyncScheduler`'s in-process loop stops being able to help. Enqueuing
     * unconditionally is fine -- the work is uniquely named (so backgrounding repeatedly does not
     * pile up requests) and the worker returns immediately when the outbox is empty.
     */
    override fun onStop() {
        super.onStop()
        backgroundSyncEnqueuer.enqueueOutboxFlush()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        intent.data?.let { uri ->
            handleExampleDeepLink(uri)?.let {
                // Every currently supported deep link resolves to Producción, which is a
                // top-level destination, so it selects its tab rather than getting pushed as a
                // duplicate.
                val navigator by inject<Navigator>()
                navigator.switchTopLevel(ProductionRoute)
            }
        }
    }

    /**
     * Resolves an incoming deep link, or null if it matches nothing.
     *
     * The uri arrives from an exported, BROWSABLE intent filter, so it is fully untrusted: any
     * app or web page can hand us an arbitrary one. Matching is wrapped so a malformed link is a
     * no-op rather than a crash on launch.
     *
     * There is no per-entity detail route to land on yet, so the matched id is purely
     * informational for now -- it demonstrates the [DeepLinkMatcher]/[DeepLinkPattern] engine
     * still working end to end without inventing a fake destination for it to land on.
     */
    private fun handleExampleDeepLink(uri: Uri): NavKey? = runCatching {
        val request = DeepLinkRequest(uri)
        val match = DeepLinkMatcher(request, exampleDeepLink).match() ?: return@runCatching null
        val id = match.args["id"]
        Log.d("MainActivity", "Deep link matched example id=$id; routing to Producción")
        ProductionRoute
    }.onFailure { error ->
        Log.w("MainActivity", "Ignoring malformed deep link: $uri", error)
    }.getOrNull()

    private companion object {
        @Serializable
        private data class ExampleDeepLinkArgs(val id: String) : NavKey

        // ecopan://example/{id} -- id is currently informational only, see handleExampleDeepLink.
        val exampleDeepLink = DeepLinkPattern(
            ExampleDeepLinkArgs.serializer(),
            Uri.parse("ecopan://example/{id}")
        )
    }
}
