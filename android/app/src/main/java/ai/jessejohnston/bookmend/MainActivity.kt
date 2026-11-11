package ai.jessejohnston.bookmend

import ai.jessejohnston.bookmend.ui.RecoveryApp
import ai.jessejohnston.bookmend.ui.Route
import ai.jessejohnston.bookmend.ui.WizardViewModel
import ai.jessejohnston.bookmend.ui.theme.BookmendTheme
import android.app.HandoffActivityData
import android.app.HandoffActivityDataRequestInfo
import android.app.HandoffActivityParams
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class MainActivity : ComponentActivity() {

    private val wizard: WizardViewModel by viewModels {
        viewModelFactory { initializer { WizardViewModel(createSavedStateHandle()) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) receiveHandoff(intent)
        setContent {
            BookmendTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RecoveryApp(wizard = wizard, onRouteChanged = ::onRouteChanged)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveHandoff(intent)
    }

    /**
     * Continue On (Android 17): offered while the person is choosing what to
     * recover, so they can pick up on a Googlebook where the drive is plugged
     * in. Withdrawn while a drive is being written or erased: that drive is
     * physically in this device, and a second device can't continue it.
     */
    private fun onRouteChanged(route: Route?) {
        if (Build.VERSION.SDK_INT >= HANDOFF_API) updateHandoff(route)
    }

    @RequiresApi(HANDOFF_API)
    private fun updateHandoff(route: Route?) {
        val choosing = route == Route.Family || route == Route.Identify || route == Route.Models || route == Route.Drive
        setHandoffEnabled(
            choosing && wizard.family.value != null,
            HandoffActivityParams.Builder().setAllowHandoffWithoutPackageInstalled(true).build()
        )
    }

    /** What travels: the family and the chosen image. Without Bookmend there, the release page opens. */
    @RequiresApi(HANDOFF_API)
    override fun onHandoffActivityDataRequested(info: HandoffActivityDataRequestInfo): HandoffActivityData =
        HandoffActivityData.Builder(ComponentName(this, MainActivity::class.java))
            .setExtras(wizard.handoffExtras())
            .setFallbackUri(Uri.parse(RELEASES_URL))
            .build()

    private fun receiveHandoff(intent: Intent?) {
        val extras = intent?.extras ?: return
        wizard.restoreHandoff(extras.getString(WizardViewModel.KEY_FAMILY), extras.getString(WizardViewModel.KEY_IMAGE))
    }

    private companion object {
        /** Android 17, where Continue On's handoff API arrived. */
        const val HANDOFF_API = 37
        const val RELEASES_URL = "https://github.com/jessejamesjohnston/bookrecovery/releases/latest"
    }
}
