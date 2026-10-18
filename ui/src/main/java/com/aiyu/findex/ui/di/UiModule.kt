package com.aiyu.findex.ui.di

import android.content.Context
import com.aiyu.findex.core.model.FailureReason
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.operation.OperationStrings
import com.aiyu.findex.ui.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject

class ResourceOperationStrings @Inject constructor(
    @ApplicationContext private val context: Context
) : OperationStrings {
    override val channelName: String get() = context.getString(R.string.notification_channel_name)
    override val channelDescription: String get() = context.getString(R.string.notification_channel_description)
    override val cancel: String get() = context.getString(R.string.cancel)
    override val updateChannelName: String get() = context.getString(R.string.update_notification_channel_name)
    override val updateNotificationBody: String get() = context.getString(R.string.update_notification_body)

    override fun updateNotificationTitle(version: String): String = context.getString(R.string.update_notification_title, version)

    override fun title(operation: FileOperation): String = context.getString(operationTitleRes(operation), operationCount(operation))

    override fun progress(currentItem: String, percent: Int, queued: Int): String {
        val base = context.getString(R.string.operation_progress, percent, currentItem)
        return if (queued > 0) context.resources.getQuantityString(R.plurals.operation_queued, queued, base, queued) else base
    }

    override fun result(result: OperationResult): String = when (result) {
        is OperationResult.Success -> context.getString(R.string.operation_completed)
        is OperationResult.Cancelled -> context.getString(R.string.operation_cancelled)
        is OperationResult.Failed -> context.getString(failureRes(result.reason))
    }
}

fun operationTitleRes(operation: FileOperation): Int = when (operation) {
    is FileOperation.Copy -> R.string.op_title_copy
    is FileOperation.Move -> R.string.op_title_move
    is FileOperation.Delete -> R.string.op_title_delete
    is FileOperation.Trash -> R.string.op_title_trash
    is FileOperation.Restore -> R.string.op_title_restore
    is FileOperation.Clean -> R.string.op_title_clean
    is FileOperation.Compress -> R.string.op_title_compress
    is FileOperation.Extract -> R.string.op_title_extract
    is FileOperation.BatchRename -> R.string.op_title_batch_rename
    is FileOperation.ImageTransform -> R.string.op_title_image_transform
    is FileOperation.VaultImport -> R.string.op_title_vault_import
    is FileOperation.VaultExport -> R.string.op_title_vault_export
    is FileOperation.VaultDelete -> R.string.op_title_vault_delete
}

fun operationCount(operation: FileOperation): Any = when (operation) {
    is FileOperation.Copy -> operation.sources.size
    is FileOperation.Move -> operation.sources.size
    is FileOperation.Delete -> operation.targets.size
    is FileOperation.Trash -> operation.targets.size
    is FileOperation.Restore -> operation.ids.size
    is FileOperation.Clean -> operation.targets.size
    is FileOperation.Compress -> File(operation.destinationArchive).name
    is FileOperation.Extract -> File(operation.archivePath).name
    is FileOperation.BatchRename -> operation.renames.size
    is FileOperation.ImageTransform -> operation.sources.size
    is FileOperation.VaultImport -> operation.sources.size
    is FileOperation.VaultExport -> operation.itemIds.size
    is FileOperation.VaultDelete -> operation.itemIds.size
}

fun failureRes(reason: FailureReason): Int = when (reason) {
    FailureReason.WRONG_PASSWORD -> R.string.error_wrong_password
    FailureReason.NO_SPACE -> R.string.error_no_space
    FailureReason.INTO_ITSELF -> R.string.error_into_itself
    FailureReason.ACCESS_DENIED -> R.string.error_access_denied
    FailureReason.UNSUPPORTED_ARCHIVE -> R.string.error_unsupported_archive
    FailureReason.NETWORK -> R.string.error_network
    FailureReason.NOT_SUPPORTED -> R.string.error_not_supported_network
    FailureReason.UNKNOWN -> R.string.operation_failed
    FailureReason.VAULT_LOCKED -> R.string.error_vault_locked
    FailureReason.NAME_CONFLICT -> R.string.error_name_conflict
    FailureReason.INVALID_NAME -> R.string.error_op_invalid_name
    FailureReason.UNSUPPORTED_IMAGE -> R.string.error_unsupported_image
    FailureReason.NESTED_SELECTION -> R.string.error_nested_selection
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UiModule {
    @Binds
    abstract fun bindOperationStrings(impl: ResourceOperationStrings): OperationStrings
}
