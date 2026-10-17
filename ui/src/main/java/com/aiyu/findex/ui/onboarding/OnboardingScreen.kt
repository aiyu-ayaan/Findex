package com.aiyu.findex.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import com.aiyu.findex.ui.components.groupedShape
import com.aiyu.findex.ui.util.hasNotificationAccess
import com.aiyu.findex.ui.util.notificationSettingsIntent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.MorphShape
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.allFilesAccessIntent
import com.aiyu.findex.ui.util.hasStorageAccess
import com.aiyu.findex.ui.util.legacyStoragePermissions

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var storageGranted by remember { mutableStateOf(context.hasStorageAccess()) }
    var notifyGranted by remember { mutableStateOf(context.hasNotificationAccess()) }
    // First tap shows the system prompt; after a denial Android won't show it again, so open settings instead.
    var notifyAsked by rememberSaveable { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifyGranted = context.hasNotificationAccess()
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        storageGranted = context.hasStorageAccess()
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    // Returning from a system settings page lands here.
    LifecycleResumeEffect(Unit) {
        storageGranted = context.hasStorageAccess()
        notifyGranted = context.hasNotificationAccess()
        if (storageGranted && notifyGranted) onFinished()
        onPauseOrDispose { }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.xl, vertical = Dimens.xxl)
    ) {
        Spacer(Modifier.height(Dimens.xl))
        MorphingHero()
        Spacer(Modifier.height(Dimens.xxl))
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.displaySmallEmphasized,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Dimens.md))
        Text(
            text = stringResource(R.string.onboarding_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 420.dp)
        )
        Spacer(Modifier.height(Dimens.xl))

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.md), modifier = Modifier.widthIn(max = 420.dp)) {
            Feature(Icons.Rounded.Folder, R.string.onboarding_point_access)
            Feature(Icons.Rounded.Speed, R.string.onboarding_point_fast)
            Feature(Icons.Rounded.Lock, R.string.onboarding_point_private)
        }

        Spacer(Modifier.height(Dimens.xl))
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.xxs), modifier = Modifier.widthIn(max = 420.dp)) {
            Text(
                stringResource(R.string.onboarding_permissions),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Dimens.xs, bottom = Dimens.sm)
            )
            PermissionCard(
                icon = Icons.Rounded.FolderOpen,
                titleRes = R.string.onboarding_storage_title,
                bodyRes = R.string.onboarding_storage_body,
                granted = storageGranted,
                shape = groupedShape(0, 2),
                onAllow = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) settingsLauncher.launch(context.allFilesAccessIntent())
                    else legacyLauncher.launch(legacyStoragePermissions)
                }
            )
            PermissionCard(
                icon = Icons.Rounded.NotificationsActive,
                titleRes = R.string.onboarding_notify_title,
                bodyRes = R.string.onboarding_notify_body,
                granted = notifyGranted,
                shape = groupedShape(1, 2),
                onAllow = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifyAsked) {
                        notifyAsked = true
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        settingsLauncher.launch(context.notificationSettingsIntent())
                    }
                }
            )
        }

        Spacer(Modifier.weight(1f, fill = true).heightIn(min = Dimens.xl))

        val hint = when {
            !storageGranted -> R.string.onboarding_storage_required
            !notifyGranted -> R.string.onboarding_notify_hint
            else -> null
        }
        if (hint != null) {
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 420.dp).padding(bottom = Dimens.md)
            )
        }
        Button(
            onClick = onFinished,
            enabled = storageGranted,
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .heightIn(min = ButtonDefaults.MediumContainerHeight)
        ) {
            Text(stringResource(R.string.onboarding_continue), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    titleRes: Int,
    bodyRes: Int,
    granted: Boolean,
    shape: Shape,
    onAllow: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Dimens.lg)
    ) {
        ShapeIcon(
            icon = icon,
            shape = MaterialShapes.Cookie4Sided,
            container = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            content = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            size = 40.dp,
            iconSize = 20.dp
        )
        Spacer(Modifier.width(Dimens.lg))
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Dimens.md))
        val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(granted, transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) }, label = "granted") { ok ->
            if (ok) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.onboarding_granted),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.minTouch).padding(Dimens.md)
                )
            } else {
                FilledTonalButton(onClick = onAllow) { Text(stringResource(R.string.onboarding_allow)) }
            }
        }
    }
}

@Composable
private fun MorphingHero() {
    val transition = rememberInfiniteTransition(label = "hero")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "heroMorph"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16000)),
        label = "heroRotation"
    )
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Clover8Leaf) }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Box(
            Modifier
                .size(200.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(MorphShape(morph, progress))
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        ShapeIcon(
            icon = Icons.Rounded.Folder,
            shape = MaterialShapes.Sunny,
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
            size = 104.dp,
            iconSize = 52.dp
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, textRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ShapeIcon(
            icon = icon,
            shape = MaterialShapes.Cookie4Sided,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 40.dp,
            iconSize = 20.dp
        )
        Spacer(Modifier.size(Dimens.lg))
        Text(
            stringResource(textRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
