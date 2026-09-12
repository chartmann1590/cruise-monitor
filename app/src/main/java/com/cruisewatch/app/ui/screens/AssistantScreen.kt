package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cruisewatch.app.ai.AiReportReason
import com.cruisewatch.app.ai.AssistantState
import com.cruisewatch.app.ai.AssistantViewModel
import com.cruisewatch.app.ai.ChatMessage
import com.cruisewatch.app.ai.LlmModelCatalog
import com.cruisewatch.app.ui.PhotoHero
import com.cruisewatch.app.R
import com.cruisewatch.app.i18n.tr
import com.cruisewatch.app.ui.theme.Coral
import com.cruisewatch.app.ui.theme.OceanDeep
import com.cruisewatch.app.ui.theme.Teal
import kotlinx.coroutines.launch

@Composable
fun AssistantScreen(focusCruiseId: String? = null, viewModel: AssistantViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkModelState(focusCruiseId) }

    Column(modifier = Modifier.fillMaxSize()) {
        PhotoHero(photoRes = R.drawable.hero_policies, height = 150.dp) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = Color.White)
                Text(tr(R.string.assistant_title), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(
                    tr(R.string.assistant_hero_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }

        when (val s = state) {
            is AssistantState.CheckingModel -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            is AssistantState.NeedsSetup -> SetupPrompt(model = s.recommended, ramGb = viewModel.deviceRamGb, onStart = { viewModel.startSetup(s.recommended) })
            is AssistantState.Downloading -> DownloadingView(s.model.displayName, s.downloadedMb, s.totalMb)
            is AssistantState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(tr(R.string.assistant_loading_model), modifier = Modifier.padding(top = 12.dp))
                }
            }
            is AssistantState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(tr(R.string.assistant_setup_error, s.message ?: ""), color = MaterialTheme.colorScheme.error)
            }
            is AssistantState.Ready -> ChatView(viewModel)
        }
    }
}

@Composable
private fun SetupPrompt(model: com.cruisewatch.app.ai.LlmModel, ramGb: Double, onStart: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(tr(R.string.assistant_setup_title), style = MaterialTheme.typography.titleLarge)
        Text(
            tr(R.string.assistant_ram_recommend, "%.1f".format(ramGb)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    tr(R.string.assistant_download_size, model.approxSizeMb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        androidx.compose.material3.Button(
            onClick = onStart,
            shape = MaterialTheme.shapes.large,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Coral),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            Text(tr(R.string.assistant_download_button))
        }
        Text(
            tr(R.string.assistant_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun DownloadingView(modelName: String, downloadedMb: Int, totalMb: Int) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(tr(R.string.assistant_downloading, modelName), style = MaterialTheme.typography.titleMedium)
        val progress = if (totalMb > 0) downloadedMb.toFloat() / totalMb else 0f
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        Text(tr(R.string.assistant_download_progress, downloadedMb, totalMb), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatView(viewModel: AssistantViewModel) {
    val messages by viewModel.messages.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var reportTargetIndex by remember { mutableStateOf<Int?>(null) }
    var reportedIndices by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)) {
            itemsIndexed(messages) { index, message ->
                ChatBubble(
                    message = message,
                    alreadyReported = index in reportedIndices,
                    onReportClick = { reportTargetIndex = index },
                )
            }
            if (isThinking) {
                item {
                    Row(modifier = Modifier.padding(vertical = 6.dp)) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                        Text(tr(R.string.assistant_thinking), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(tr(R.string.assistant_input_placeholder)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val text = input
                input = ""
                scope.launch { viewModel.sendMessage(text) }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = tr(R.string.assistant_send_description), tint = Teal)
            }
        }
    }

    val targetIndex = reportTargetIndex
    if (targetIndex != null) {
        val aiMessage = messages[targetIndex].text
        // The preceding message is the user's question in the normal turn-taking flow. The very first
        // assistant message (a proactive greeting with no real preceding question) has none — fall back to
        // a clear placeholder so the report still carries useful context.
        val userMessage = messages.getOrNull(targetIndex - 1)?.takeIf { it.fromUser }?.text
            ?: "(automatic greeting, no user question)"
        ReportBottomSheet(
            onDismiss = { reportTargetIndex = null },
            onSubmit = { reason, detail ->
                viewModel.reportMessage(userMessage, aiMessage, reason, detail)
                reportedIndices = reportedIndices + targetIndex
                reportTargetIndex = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportBottomSheet(onDismiss: () -> Unit, onSubmit: (AiReportReason, String?) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    var selectedReason by remember { mutableStateOf<AiReportReason?>(null) }
    var detail by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(tr(R.string.assistant_report_title), style = MaterialTheme.typography.titleMedium)

            val reasons = listOf(
                AiReportReason.INACCURATE to tr(R.string.assistant_report_reason_inaccurate),
                AiReportReason.UNSAFE to tr(R.string.assistant_report_reason_unsafe),
                AiReportReason.OFF_TOPIC to tr(R.string.assistant_report_reason_offtopic),
                AiReportReason.OTHER to tr(R.string.assistant_report_reason_other),
            )
            reasons.forEach { (reason, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason })
                    Text(label, modifier = Modifier.padding(start = 4.dp))
                }
            }

            if (selectedReason == AiReportReason.OTHER) {
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    placeholder = { Text(tr(R.string.assistant_report_detail_placeholder)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(tr(R.string.assistant_report_cancel)) }
                TextButton(
                    onClick = { selectedReason?.let { onSubmit(it, detail.ifBlank { null }) } },
                    enabled = selectedReason != null,
                ) { Text(tr(R.string.assistant_report_submit)) }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, alreadyReported: Boolean, onReportClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        if (message.fromUser) Teal.copy(alpha = 0.85f) else OceanDeep.copy(alpha = 0.9f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(message.text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!message.fromUser) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onReportClick, enabled = !alreadyReported, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Flag,
                        contentDescription = tr(R.string.assistant_report_description),
                        tint = if (alreadyReported) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (alreadyReported) {
                    Text(
                        tr(R.string.assistant_report_sent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
    }
}
