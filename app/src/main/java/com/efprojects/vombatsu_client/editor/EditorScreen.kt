package com.efprojects.vombatsu_client.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efprojects.vombatsu_client.R
import com.efprojects.vombatsu_client.actions.AttachMenu
import com.efprojects.vombatsu_client.content.MAX_CONTENT_WIDTH
import com.efprojects.vombatsu_client.content.MediaView
import com.efprojects.vombatsu_client.content.parseMediaList
import com.efprojects.vombatsu_client.post.BackButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val MAX_MEDIA_PER_PICK = 10
private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel, onPublished: (Int?) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }
    val leave = { if (state.hasContent) confirmExit = true else onBack() }

    BackHandler(enabled = state.hasContent && !state.published) { confirmExit = true }
    LaunchedEffect(state.published) {
        if (state.published) onPublished(state.publishedPostId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = { BackButton(leave) },
                actions = {
                    if (state.sending) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = viewModel::publish, enabled = state.uploading == 0) {
                            Text(stringResource(R.string.editor_publish))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = MAX_CONTENT_WIDTH)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.preflightMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitle,
                    label = { Text(stringResource(R.string.editor_post_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.blocks.forEach { block -> BlockEditor(block, state.blocks.size > 1, viewModel) }
                AttachMenu(MAX_MEDIA_PER_PICK, viewModel::addMedia, viewModel::addImageUrl) { open ->
                    OutlinedButton(onClick = open) {
                        Text(
                            if (state.uploading > 0) {
                                stringResource(R.string.comment_uploading, state.uploading)
                            } else {
                                stringResource(R.string.editor_add_media)
                            },
                        )
                    }
                }
                TagsEditor(state, viewModel)
                Flag(R.string.editor_own, state.isOwn, viewModel::onOwn)
                Flag(R.string.filter_nsfw, state.isNsfw, viewModel::onNsfw)
                Flag(R.string.filter_cruel, state.isCruel, viewModel::onCruel)
                Flag(R.string.filter_politics, state.isPolitics, viewModel::onPolitics)
                Flag(R.string.editor_postponed, state.postponedAt != null, viewModel::onPostponed)
                state.postponedAt?.let { PostponedPicker(it, viewModel::onPostponedAt) }
                Messages(state)
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.editor_exit_title)) },
            text = { Text(stringResource(R.string.editor_exit_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmExit = false
                        onBack()
                    },
                ) { Text(stringResource(R.string.editor_exit_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.reply_cancel)) }
            },
        )
    }
}

@Composable
private fun BlockEditor(block: EditorBlock, removable: Boolean, viewModel: EditorViewModel) {
    when (block) {
        is EditorBlock.Text -> OutlinedTextField(
            value = block.text,
            onValueChange = { viewModel.onText(block.id, it) },
            placeholder = { Text(stringResource(R.string.editor_text_hint)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        is EditorBlock.Media -> {
            val media = remember(block.file) { parseMediaList("[${block.file}]").firstOrNull() }
            Box {
                media?.let { MediaView(it) }
                if (removable) {
                    Text(
                        stringResource(R.string.comment_attachment_remove),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { viewModel.removeBlock(block.id) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(state: EditorState, viewModel: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.tagInput,
            onValueChange = viewModel::onTagInput,
            label = { Text(stringResource(R.string.editor_tags, state.tags.size, TAGS_MAX)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.addTag(state.tagInput) }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.suggestions.forEach { tag ->
                    AssistChip(onClick = { viewModel.addTag(tag) }, label = { Text(tag) })
                }
            }
        }
        if (state.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.tags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { viewModel.removeTag(tag) },
                        label = { Text(tag) },
                        trailingIcon = { Text(stringResource(R.string.comment_attachment_remove)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostponedPicker(at: Instant, onChange: (Instant) -> Unit) {
    val zone = ZoneId.systemDefault()
    val local = at.atZone(zone)
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f)) {
            Text(DATE_FORMAT.format(local), maxLines = 1)
        }
        OutlinedButton(onClick = { pickTime = true }) { Text(TIME_FORMAT.format(local)) }
    }
    if (pickDate) {
        PostponedDateDialog(
            selected = local.toLocalDate(),
            onPick = { onChange(at.withLocalDate(it, zone)) },
            onDismiss = { pickDate = false },
        )
    }
    if (pickTime) {
        PostponedTimeDialog(
            hour = local.hour,
            minute = local.minute,
            onPick = { hour, minute -> onChange(at.withLocalTime(hour, minute, zone)) },
            onDismiss = { pickTime = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostponedDateDialog(selected: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return date in today..today.plusDays(POSTPONE_MAX_DAYS)
                }

                override fun isSelectableYear(year: Int) = year in today.year..today.plusDays(POSTPONE_MAX_DAYS).year
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.editor_postponed_pick)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reply_cancel)) }
        },
    ) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostponedTimeDialog(hour: Int, minute: Int, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(hour, minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(
                onClick = {
                    onPick(state.hour, state.minute)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.editor_postponed_pick)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reply_cancel)) }
        },
    )
}

@Composable
private fun Flag(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Messages(state: EditorState) {
    val color = MaterialTheme.colorScheme.error
    state.problems.forEach { problem ->
        Text(
            stringResource(
                when (problem) {
                    PostProblem.Title -> R.string.editor_problem_title
                    PostProblem.Tags -> R.string.editor_problem_tags
                    PostProblem.Empty -> R.string.editor_problem_empty
                    PostProblem.TooLong -> R.string.editor_problem_too_long
                    PostProblem.PostponedTooSoon -> R.string.editor_problem_postponed_soon
                    PostProblem.PostponedTooLate -> R.string.editor_problem_postponed_late
                },
            ),
            color = color,
        )
    }
    state.error?.let { error ->
        Text(
            when (error) {
                EditorError.Network -> stringResource(R.string.editor_network_error)
                EditorError.UploadNetwork -> stringResource(R.string.upload_network_error)
                is EditorError.Upload -> error.message ?: stringResource(R.string.upload_failed)
                is EditorError.Rejected -> error.message ?: stringResource(R.string.editor_rejected)
            },
            color = color,
        )
    }
}
