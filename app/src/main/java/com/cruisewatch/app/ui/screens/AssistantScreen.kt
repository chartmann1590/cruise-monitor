package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

@Composable
private fun ChatView(viewModel: AssistantViewModel) {
    val messages by viewModel.messages.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)) {
            items(messages) { message -> ChatBubble(message) }
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
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
}
