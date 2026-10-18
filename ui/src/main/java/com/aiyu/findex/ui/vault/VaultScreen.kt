package com.aiyu.findex.ui.vault

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.provider.DocumentsContract
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.aiyu.findex.core.vault.VaultContentProvider
import com.aiyu.findex.core.vault.VaultEntry
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.categoryOf
import com.aiyu.findex.ui.components.icon
import com.aiyu.findex.ui.components.isImageExtension
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.theme.tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.crypto.Cipher

@Composable
fun VaultScreen(onBack: () -> Unit, viewModel: VaultViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()

    // Keep vault contents out of screenshots and the recents preview.
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    LaunchedEffect(state.message) {
        state.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.consumeMessage() }
    }

    val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = state.phase::class,
        transitionSpec = { fadeIn(enter) togetherWith fadeOut(exit) },
        label = "vaultPhase"
    ) { phase ->
        when (phase) {
            VaultPhase.Setup::class -> SetupScreen(state.busy, viewModel.biometricSupported, onBack, viewModel::create)
            VaultPhase.Locked::class -> LockedScreen(state, viewModel, onBack)
            VaultPhase.Unlocked::class -> UnlockedScreen(state, viewModel, onBack)
            VaultPhase.Unreadable::class -> UnreadableScreen(onBack)
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }
    }

    if (state.phase == VaultPhase.Unlocked) {
        state.pendingImport?.let { paths ->
            AlertDialog(
                onDismissRequest = viewModel::dismissImport,
                title = { Text(stringResource(R.string.action_move_to_vault)) },
                text = { Text(stringResource(R.string.vault_import_ready, paths.size)) },
                confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text(stringResource(R.string.action_move_to_vault)) } },
                dismissButton = { TextButton(onClick = viewModel::dismissImport) { Text(stringResource(R.string.cancel)) } }
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@RequiresApi(Build.VERSION_CODES.P)
private fun authenticate(activity: Activity, cipher: Cipher, onSuccess: (Cipher) -> Unit) {
    val prompt = BiometricPrompt.Builder(activity)
        .setTitle(activity.getString(R.string.vault_biometric_prompt_title))
        .setSubtitle(activity.getString(R.string.vault_biometric_prompt_subtitle))
        .setNegativeButton(activity.getString(R.string.vault_biometric_prompt_negative), activity.mainExecutor) { _, _ -> }
        .build()
    prompt.authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), activity.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(onSuccess)
            }
        })
}

private fun enroll(activity: Activity, viewModel: VaultViewModel) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val cipher = viewModel.biometricEnrollCipher() ?: return
    authenticate(activity, cipher, viewModel::onBiometricEnrolled)
}

@Composable
private fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
        actions = { actions() }
    )
}

@Composable
private fun LockIllustration(icon: ImageVector = Icons.Rounded.Lock) {
    ShapeIcon(
        icon = icon,
        shape = MaterialShapes.Cookie9Sided,
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        size = 96.dp,
        iconSize = 40.dp
    )
}

@Composable
private fun PasswordField(state: TextFieldState, label: Int, modifier: Modifier = Modifier) {
    OutlinedSecureTextField(state = state, label = { Text(stringResource(label)) }, modifier = modifier.fillMaxWidth())
}

private fun TextFieldState.take(): CharArray = text.toString().toCharArray().also { clearText() }

// region Setup

@Composable
private fun SetupScreen(busy: Boolean, biometricSupported: Boolean, onBack: () -> Unit, onCreate: (CharArray, Boolean) -> Unit) {
    val password = rememberTextFieldState()
    val confirm = rememberTextFieldState()
    var biometric by rememberSaveable { mutableStateOf(biometricSupported) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    val pw = password.text.toString()
    val strength = when {
        pw.length >= 12 && pw.any(Char::isDigit) && pw.any { !it.isLetterOrDigit() } -> 3
        pw.length >= 10 -> 2
        pw.isNotEmpty() -> 1
        else -> 0
    }
    val mismatch = confirm.text.isNotEmpty() && confirm.text.toString() != pw
    val valid = pw.length >= 8 && !mismatch && confirm.text.isNotEmpty() && acknowledged && !busy

    Scaffold(topBar = { BackBar(stringResource(R.string.vault_setup_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Dimens.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.md)
        ) {
            LockIllustration()
            PasswordField(password, R.string.vault_setup_password)
            if (strength > 0) {
                LinearProgressIndicator(progress = { strength / 3f }, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(
                        when (strength) { 1 -> R.string.vault_setup_strength_weak; 2 -> R.string.vault_setup_strength_fair; else -> R.string.vault_setup_strength_strong }
                    ) + if (pw.length < 8) " · " + stringResource(R.string.vault_setup_min_length) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            PasswordField(confirm, R.string.vault_setup_confirm)
            if (mismatch) Text(stringResource(R.string.vault_setup_mismatch), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth())
            if (biometricSupported) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.vault_setup_biometric), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = biometric, onCheckedChange = { biometric = it })
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                Column(Modifier.padding(Dimens.lg), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    Text(stringResource(R.string.vault_setup_warning_no_recovery), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.vault_setup_warning_not_secure_erase), style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                        Text(stringResource(R.string.vault_setup_acknowledge), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Button(onClick = { onCreate(password.take(), biometric); confirm.clearText() }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
                if (busy) LoadingIndicator(Modifier.size(24.dp)) else Text(stringResource(R.string.vault_setup_create))
            }
        }
    }
}

// endregion

// region Unreadable

@Composable
private fun UnreadableScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackBar(stringResource(R.string.vault_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Dimens.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.lg)
        ) {
            LockIllustration(Icons.Rounded.ErrorOutline)
            Text(stringResource(R.string.vault_unreadable_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.vault_unreadable_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

// endregion

// region Locked

@Composable
private fun LockedScreen(state: VaultUiState, viewModel: VaultViewModel, onBack: () -> Unit) {
    val locked = state.phase as? VaultPhase.Locked ?: return
    val password = rememberTextFieldState()
    val activity = LocalContext.current.findActivity()
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(locked.lockoutUntil) {
        while (locked.lockoutUntil > now) { delay(1_000); now = System.currentTimeMillis() }
    }
    val lockedOut = locked.lockoutUntil > now
    val promptBiometric = {
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) scope.launch {
            viewModel.biometricUnlockCipher()?.let { authenticate(activity, it, viewModel::onBiometricAuthenticated) }
        }
    }
    // Auto-prompt once per screen entry.
    var prompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(locked.biometric) { if (locked.biometric && !prompted && !lockedOut) { prompted = true; promptBiometric() } }

    Scaffold(topBar = { BackBar(stringResource(R.string.vault_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Dimens.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.lg)
        ) {
            LockIllustration()
            Text(stringResource(R.string.vault_unlock_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.vault_locked_banner), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            PasswordField(password, R.string.vault_unlock_password, Modifier.widthIn(max = 480.dp))
            if (lockedOut) {
                Text(
                    stringResource(R.string.vault_unlock_lockout, "${(locked.lockoutUntil - now + 999) / 1000} s"),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge
                )
            }
            Button(onClick = { viewModel.unlock(password.take()) }, enabled = !lockedOut && !state.busy && password.text.isNotEmpty(), modifier = Modifier.widthIn(min = 200.dp)) {
                if (state.busy) LoadingIndicator(Modifier.size(24.dp)) else Text(stringResource(R.string.vault_unlock_action))
            }
            if (locked.biometric) {
                FilledTonalButton(onClick = { promptBiometric() }, enabled = !state.busy) {
                    Icon(Icons.Rounded.Fingerprint, null)
                    Text(stringResource(R.string.vault_unlock_biometric), Modifier.padding(start = Dimens.sm))
                }
            }
        }
    }
}

// endregion

// region Unlocked

/** Primary-storage or SD tree URI to a file path; null for other providers. */
private fun treeToPath(uri: Uri): String? {
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    return idToStoragePath(id)
}

/** Resolves a picked document URI on device storage to its file path; null for remote/virtual providers. */
private fun docToPath(context: Context, uri: Uri): String? {
    if (uri.scheme == "file") return uri.path
    val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
    if (id != null) {
        when (uri.authority) {
            "com.android.externalstorage.documents" -> idToStoragePath(id)?.let { return it }
            "com.android.providers.downloads.documents" -> if (id.startsWith("raw:")) return id.removePrefix("raw:")
            "com.android.providers.media.documents" -> {
                val numId = id.substringAfter(':', "")
                if (numId.all(Char::isDigit) && numId.isNotEmpty()) {
                    runCatching {
                        context.contentResolver.query(
                            android.provider.MediaStore.Files.getContentUri("external"),
                            arrayOf(android.provider.MediaStore.MediaColumns.DATA),
                            "_id = ?",
                            arrayOf(numId),
                            null
                        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    }.getOrNull()?.let { return it }
                }
            }
        }
    }
    return runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
}

private fun idToStoragePath(id: String): String? {
    val volume = id.substringBefore(':', "")
    if (volume.isEmpty()) return null
    val rel = id.substringAfter(':', "")
    val root = if (volume.equals("primary", ignoreCase = true)) Environment.getExternalStorageDirectory() else File("/storage/$volume")
    return File(root, rel).absolutePath
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnlockedScreen(state: VaultUiState, viewModel: VaultViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val autoLock by viewModel.autoLockMinutes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    var selected by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf<VaultEntry?>(null) }
    val selection = selected.isNotEmpty()

    LaunchedEffect(state.enrollBiometric) { if (state.enrollBiometric && activity != null) { viewModel.enrollHandled(); enroll(activity, viewModel) } }
    LaunchedEffect(items) { selected = selected.intersect(items.map { it.id }.toSet()) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(::treeToPath)?.let { viewModel.exportTo(selected, it); selected = emptySet() }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val paths = uris.mapNotNull { docToPath(context, it) }.distinct().filter { File(it).exists() }
        if (paths.isNotEmpty()) viewModel.requestImport(paths) else dialog = "add"
    }

    Scaffold(
        topBar = {
            if (selection) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selected.size)) },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Rounded.Close, stringResource(R.string.cd_clear_selection)) } },
                    actions = {
                        IconButton(onClick = { dialog = "restore" }) { Icon(Icons.Rounded.Restore, stringResource(R.string.vault_export_original)) }
                        IconButton(onClick = { folderPicker.launch(null) }) { Icon(Icons.Rounded.SaveAlt, stringResource(R.string.vault_export_choose_folder)) }
                        IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
                    }
                )
            } else {
                BackBar(stringResource(R.string.vault_title), onBack) {
                    IconButton(onClick = viewModel::lock) { Icon(Icons.Rounded.Lock, stringResource(R.string.cd_vault_lock)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_more_options)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.vault_menu_change_password)) }, onClick = { menu = false; dialog = "password" })
                            if (viewModel.biometricSupported) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.vault_menu_biometric)) },
                                    trailingIcon = { Switch(checked = state.biometricEnabled, onCheckedChange = null) },
                                    onClick = {
                                        menu = false
                                        if (state.biometricEnabled) viewModel.disableBiometric() else activity?.let { enroll(it, viewModel) }
                                    }
                                )
                            }
                            DropdownMenuItem(text = { Text(stringResource(R.string.vault_menu_auto_lock)) }, onClick = { menu = false; dialog = "autolock" })
                            DropdownMenuItem(text = { Text(stringResource(R.string.vault_menu_lock_now)) }, onClick = { menu = false; viewModel.lock() })
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selection) ExtendedFloatingActionButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Rounded.NoteAdd, null) },
                text = { Text(stringResource(R.string.vault_add_files)) }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = padding.calculateTopPadding() + Dimens.sm, bottom = padding.calculateBottomPadding() + 96.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.restorable > 0) item(span = { GridItemSpan(maxLineSpan) }) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(Dimens.lg), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        Text(stringResource(R.string.vault_rebuild_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.vault_rebuild_body), style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton(onClick = viewModel::rebuildIndex, enabled = !state.busy) { Text(stringResource(R.string.vault_rebuild_action)) }
                    }
                }
            }
            if (items.isEmpty() && state.restorable == 0) item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(top = Dimens.xxxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.lg)) {
                    LockIllustration(Icons.Rounded.LockOpen)
                    Text(stringResource(R.string.vault_empty), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(items, key = { it.id }) { entry ->
                val isSelected = entry.id in selected
                VaultCell(
                    entry = entry,
                    selected = isSelected,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (selection) selected = if (isSelected) selected - entry.id else selected + entry.id
                            else opening = entry
                        },
                        onLongClick = { selected = if (isSelected) selected - entry.id else selected + entry.id }
                    )
                )
            }
        }
    }

    when (dialog) {
        "restore" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.vault_restore_title)) },
            text = { Text(stringResource(R.string.vault_restore_body)) },
            confirmButton = { TextButton(onClick = { viewModel.restoreToOriginal(selected); selected = emptySet(); dialog = null }) { Text(stringResource(R.string.vault_restore_action)) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(stringResource(R.string.vault_delete_confirm, selected.size)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(selected); selected = emptySet(); dialog = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
        "add" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.vault_add_files)) },
            text = { Text(stringResource(R.string.vault_add_files_hint)) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.confirm)) } }
        )
        "autolock" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.vault_menu_auto_lock)) },
            text = {
                Column {
                    listOf(0 to R.string.vault_auto_lock_immediately, 1 to R.string.vault_auto_lock_1min, 5 to R.string.vault_auto_lock_5min).forEach { (minutes, label) ->
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(onClick = { viewModel.setAutoLock(minutes); dialog = null }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = autoLock == minutes, onClick = { viewModel.setAutoLock(minutes); dialog = null })
                            Text(stringResource(label))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
        "password" -> ChangePasswordDialog(onDismiss = { dialog = null }) { old, new -> viewModel.changePassword(old, new); dialog = null }
    }

    opening?.let { entry ->
        AlertDialog(
            onDismissRequest = { opening = null },
            title = { Text(stringResource(R.string.action_open_in_another_app)) },
            text = { Text(stringResource(R.string.vault_open_external_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    opening = null
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(VaultContentProvider.uri(context, entry.id), entry.mimeType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(Intent.createChooser(intent, entry.name)) }
                }) { Text(stringResource(R.string.action_open)) }
            },
            dismissButton = { TextButton(onClick = { opening = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun VaultCell(entry: VaultEntry, selected: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val ext = entry.name.substringAfterLast('.', "")
    val category = categoryOf(ext)
    Column(
        modifier.clip(MaterialTheme.shapes.medium).padding(Dimens.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.xs)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
            val tone = category?.tone
            ShapeIcon(
                icon = category?.icon ?: Icons.Rounded.Lock,
                shape = MaterialShapes.Square,
                container = tone?.container ?: MaterialTheme.colorScheme.surfaceContainerHigh,
                content = tone?.onContainer ?: MaterialTheme.colorScheme.onSurfaceVariant,
                size = 64.dp,
                iconSize = 28.dp
            )
            if (isImageExtension(ext)) {
                // Memory cache only: a plaintext thumbnail must never reach the disk cache.
                val request = remember(entry.id) {
                    ImageRequest.Builder(context)
                        .data(VaultContentProvider.uri(context, entry.id))
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .size(256)
                        .build()
                }
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (selected) {
                Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.TopEnd) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(Dimens.xs))
                }
            }
        }
        Text(entry.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onChange: (CharArray, CharArray) -> Unit) {
    val old = rememberTextFieldState()
    val new = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_menu_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                PasswordField(old, R.string.vault_password_current)
                PasswordField(new, R.string.vault_password_new)
                if (new.text.isNotEmpty() && new.text.length < 8) Text(stringResource(R.string.vault_setup_min_length), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = { onChange(old.take(), new.take()) }, enabled = old.text.isNotEmpty() && new.text.length >= 8) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// endregion
