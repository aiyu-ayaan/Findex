package com.aiyu.findex.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
fun OnboardingScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (context.hasStorageAccess()) onGranted()
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    // Returning from the system settings page lands here.
    LifecycleResumeEffect(Unit) {
        if (context.hasStorageAccess()) onGranted()
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
        Spacer(Modifier.height(Dimens.xxl))
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
        Spacer(Modifier.height(Dimens.xxl))

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.md), modifier = Modifier.widthIn(max = 420.dp)) {
            Feature(Icons.Rounded.Folder, R.string.onboarding_point_access)
            Feature(Icons.Rounded.Speed, R.string.onboarding_point_fast)
            Feature(Icons.Rounded.Lock, R.string.onboarding_point_private)
            Feature(Icons.Rounded.NotificationsActive, R.string.onboarding_point_notify)
        }

        Spacer(Modifier.weight(1f, fill = true).heightIn(min = Dimens.xxl))

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    settingsLauncher.launch(context.allFilesAccessIntent())
                } else {
                    legacyLauncher.launch(legacyStoragePermissions)
                }
            },
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .heightIn(min = ButtonDefaults.MediumContainerHeight)
        ) {
            Text(stringResource(R.string.onboarding_grant), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
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
