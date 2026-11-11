package ai.jessejohnston.bookmend.ui

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.data.DeviceFamily
import ai.jessejohnston.bookmend.ui.screens.DriveScreen
import ai.jessejohnston.bookmend.ui.screens.EraseScreen
import ai.jessejohnston.bookmend.ui.screens.FamilyScreen
import ai.jessejohnston.bookmend.ui.screens.FlashScreen
import ai.jessejohnston.bookmend.ui.screens.IdentifyScreen
import ai.jessejohnston.bookmend.ui.screens.LicensesScreen
import ai.jessejohnston.bookmend.ui.screens.ModelBrowserScreen
import ai.jessejohnston.bookmend.ui.screens.WelcomeScreen
import android.net.Uri
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/**
 * Bookmend's wizard: Welcome, then Device (family, model), Drive, Write.
 *
 * Choices live in [WizardViewModel] (activity-scoped), so every screen reads
 * the same state and rotation, resizing and process death keep it. Screens
 * get callbacks, not the controller, which keeps them previewable and
 * testable on their own.
 *
 * @param onRouteChanged Lets the activity follow the wizard (Continue On is
 *   offered on the choosing steps and withheld while a drive is being written).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryApp(
    wizard: WizardViewModel,
    navController: NavHostController = rememberNavController(),
    onRouteChanged: (Route?) -> Unit = {},
) {
    val entry by navController.currentBackStackEntryAsState()
    val route = Route.of(entry?.destination?.route)
    LaunchedEffect(route) { onRouteChanged(route) }

    val family by wizard.family.collectAsStateWithLifecycle()
    val image by wizard.image.collectAsStateWithLifecycle()

    // A choice made on another device (Continue On) lands on the same model.
    // It waits for the graph's first destination: on a cold start the handoff
    // can be ready before the NavHost has set its graph.
    val handoff by wizard.handoffRoute.collectAsStateWithLifecycle()
    val graphReady = entry != null
    LaunchedEffect(handoff, graphReady) {
        if (graphReady) handoff?.let { target ->
            navController.popBackStack(Route.Welcome.pattern, inclusive = false)
            navController.navigate(Route.Family.pattern)
            navController.navigate(target)
            wizard.handoffShown()
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            wizard.chooseLocalImage(uri.toString())
            navController.navigate(Route.Drive.pattern)
        }
    }

    fun home() {
        wizard.reset()
        navController.popBackStack(Route.Welcome.pattern, inclusive = false)
    }

    val busy = route == Route.Flash || route == Route.EraseFlash
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (route != null && route.step > 0) {
                        WizardSteps(current = route.step)
                    } else if (route != Route.Welcome) {
                        Text(stringResource(if (route == Route.Licenses) R.string.licenses_title else R.string.menu_erase_drive))
                    }
                },
                navigationIcon = {
                    when {
                        route == null || route == Route.Welcome -> Unit
                        busy -> IconButton(onClick = ::home) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_home))
                        }
                        // The same back as the system gesture, so a screen's own back
                        // handling (the model browser's list and detail) applies first.
                        else -> IconButton(onClick = { backDispatcher?.onBackPressed() ?: navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    // Tools are hidden while a drive is being written or erased:
                    // leaving would abandon real work on the drive.
                    if (!busy) {
                        var open by remember { mutableStateOf(false) }
                        IconButton(onClick = { open = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_use_file)) },
                                onClick = { open = false; filePicker.launch(arrayOf("*/*")) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_erase_drive)) },
                                onClick = { open = false; navController.navigate(Route.EraseDrive.pattern) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_licenses)) },
                                onClick = { open = false; navController.navigate(Route.Licenses.pattern) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        // Forward slides in on the expressive spatial spring while the old step
        // fades; back mirrors it. With predictive back, the system drives the pop.
        val motion = MaterialTheme.motionScheme
        NavHost(
            navController = navController,
            startDestination = Route.Welcome.pattern,
            modifier = Modifier.padding(padding),
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, motion.fastSpatialSpec()) +
                    fadeIn(motion.fastEffectsSpec())
            },
            exitTransition = { fadeOut(motion.fastEffectsSpec()) },
            popEnterTransition = { fadeIn(motion.fastEffectsSpec()) },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, motion.fastSpatialSpec()) +
                    fadeOut(motion.fastEffectsSpec())
            }
        ) {
            composable(Route.Welcome.pattern) {
                WelcomeScreen(
                    onStart = { navController.navigate(Route.Family.pattern) },
                    onUseFile = { filePicker.launch(arrayOf("*/*")) }
                )
            }
            composable(Route.Family.pattern) {
                FamilyScreen(
                    selected = family,
                    onChoose = { chosen ->
                        wizard.chooseFamily(chosen)
                        navController.navigate(
                            if (chosen == DeviceFamily.CHROMEBOOK) Route.Identify.pattern else Route.Models.withQuery()
                        )
                    }
                )
            }
            composable(Route.Identify.pattern) {
                val catalog by wizard.catalog.collectAsStateWithLifecycle()
                IdentifyScreen(
                    catalog = catalog,
                    identify = wizard::identify,
                    onRetry = wizard::retryCatalog,
                    onFound = { name -> navController.navigate(Route.Models.withQuery(name)) },
                    onBrowse = { navController.navigate(Route.Models.withQuery()) }
                )
            }
            composable(
                Route.Models.pattern,
                arguments = listOf(navArgument(Route.ARG_QUERY) { type = NavType.StringType; nullable = true })
            ) { backStack ->
                val catalog by wizard.catalog.collectAsStateWithLifecycle()
                ModelBrowserScreen(
                    family = family ?: DeviceFamily.CHROMEBOOK,
                    catalog = catalog,
                    initialQuery = backStack.arguments?.getString(Route.ARG_QUERY)?.let(Uri::decode),
                    selected = image,
                    onRetry = wizard::retryCatalog,
                    onChoose = { chosen ->
                        wizard.chooseImage(chosen)
                        navController.navigate(Route.Drive.pattern)
                    }
                )
            }
            composable(Route.Drive.pattern) {
                DriveScreen(
                    requiredBytes = image?.imageBytes,
                    targetName = image?.name,
                    eraseOnly = false,
                    onConfirmed = { device ->
                        wizard.chooseDrive(device)
                        navController.navigate(Route.Flash.pattern)
                    }
                )
            }
            composable(Route.EraseDrive.pattern) {
                DriveScreen(
                    requiredBytes = null,
                    targetName = null,
                    eraseOnly = true,
                    onConfirmed = { device ->
                        wizard.chooseDrive(device)
                        navController.navigate(Route.EraseFlash.pattern)
                    }
                )
            }
            composable(Route.Flash.pattern) {
                val request = remember { wizard.flashRequest() }
                if (request != null) {
                    FlashScreen(request = request, onFinish = ::home)
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
            composable(Route.EraseFlash.pattern) {
                val device by wizard.drive.collectAsStateWithLifecycle()
                val current = device
                if (current != null) {
                    EraseScreen(device = current, onFinish = ::home)
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
            composable(Route.Licenses.pattern) { LicensesScreen() }
        }
    }
}

/**
 * Three segments under the title, the current one in the accent: where you
 * are in Device, Drive, Write. Read by screen readers as one phrase.
 */
@Composable
fun WizardSteps(current: Int, modifier: Modifier = Modifier) {
    val labels = listOf(R.string.step_label_device, R.string.step_label_drive, R.string.step_label_write)
        .map { stringResource(it) }
    val description = stringResource(R.string.step_progress_description, current, Route.STEPS, labels[current - 1])
    Column(modifier.widthIn(max = 360.dp).clearAndSetSemantics { contentDescription = description }) {
        Text(labels[current - 1], style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth()
        ) {
            for (step in 1..Route.STEPS) {
                val fill by animateFloatAsState(if (step <= current) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "step$step")
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fill)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .align(Alignment.CenterStart)
                    )
                }
            }
        }
    }
}
