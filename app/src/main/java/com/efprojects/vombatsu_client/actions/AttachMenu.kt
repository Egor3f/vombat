package com.efprojects.vombatsu_client.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.efprojects.vombatsu_client.R
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private fun Context.primaryClipItem(): ClipData.Item? =
    getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)

private fun Context.clipboardImage(item: ClipData.Item?): Uri? =
    item?.uri?.takeIf { contentResolver.getType(it)?.startsWith("image/") == true }

private fun clipboardUrl(item: ClipData.Item?): String =
    item?.text?.toString()?.trim()?.takeIf { it.toHttpUrlOrNull() != null }.orEmpty()

@Composable
fun AttachMenu(
    maxItems: Int,
    onFiles: (List<Uri>) -> Unit,
    onUrl: (String) -> Unit,
    anchor: @Composable (open: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems),
        onFiles,
    )
    Box {
        anchor { menuOpen = true }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.attach_upload_image)) },
                onClick = {
                    menuOpen = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.attach_paste_image)) },
                onClick = {
                    menuOpen = false
                    val item = context.primaryClipItem()
                    val image = context.clipboardImage(item)
                    if (image != null) onFiles(listOf(image)) else urlInput = clipboardUrl(item)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.attach_upload_video)) },
                onClick = {
                    menuOpen = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
            )
        }
    }
    urlInput?.let { url ->
        AlertDialog(
            onDismissRequest = { urlInput = null },
            title = { Text(stringResource(R.string.attach_url_title)) },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { urlInput = it },
                    placeholder = { Text(stringResource(R.string.attach_url_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = url.trim().toHttpUrlOrNull() != null,
                    onClick = {
                        urlInput = null
                        onUrl(url.trim())
                    },
                ) { Text(stringResource(R.string.attach_paste)) }
            },
            dismissButton = {
                TextButton(onClick = { urlInput = null }) { Text(stringResource(R.string.reply_cancel)) }
            },
        )
    }
}
