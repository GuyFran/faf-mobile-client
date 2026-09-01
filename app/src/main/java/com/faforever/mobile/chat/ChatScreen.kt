package com.faforever.mobile.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.faforever.mobile.chat.model.ChatMessage
import com.faforever.mobile.chat.model.ChatUser
import com.faforever.mobile.chat.model.MessageType
import com.faforever.mobile.chat.model.UserElevation
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val channels by viewModel.channels.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val selectedChannelName by viewModel.selectedChannel.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val channelList = channels.keys.toList()
    val selectedChannel = selectedChannelName?.let { channels[it] }

    LaunchedEffect(channelList) {
        if (selectedChannelName == null && channelList.isNotEmpty()) {
            viewModel.selectChannel(channelList.first())
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Users (${selectedChannel?.users?.size ?: 0})",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                HorizontalDivider()
                LazyColumn {
                    items(selectedChannel?.users?.sortedBy { it.name } ?: emptyList()) { user ->
                        UserRow(user)
                    }
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(selectedChannel?.name ?: "Chat") },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch { drawerState.open() }
                    }) {
                        Icon(Icons.Default.People, "Users")
                    }
                },
            )

            if (connectionState == IrcConnectionState.CONNECTING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (channelList.size > 1) {
                val selectedIndex = channelList.indexOf(selectedChannelName).coerceAtLeast(0)
                ScrollableTabRow(selectedTabIndex = selectedIndex) {
                    channelList.forEachIndexed { index, name ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { viewModel.selectChannel(name) },
                            text = { Text(name) },
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (selectedChannel != null) {
                    MessageList(
                        messages = selectedChannel.messages,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (connectionState == IrcConnectionState.DISCONNECTED) "Disconnected"
                            else "Connecting...",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            ChatInput(
                onSend = { viewModel.sendMessage(it) },
                enabled = connectionState == IrcConnectionState.CONNECTED,
            )
        }
    }
}

@Composable
private fun MessageList(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 8.dp),
    ) {
        items(messages, key = { "${it.id}_${it.timestamp}" }) { message ->
            MessageRow(message)
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(message.timestamp))

    when (message.type) {
        MessageType.SYSTEM -> {
            Text(
                text = "--- ${message.content}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }

        MessageType.ACTION -> {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))) {
                        append("$timeStr ")
                    }
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append("* ${message.sender} ${message.content}")
                    }
                },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }

        MessageType.MESSAGE -> {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))) {
                        append("$timeStr ")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                        append("${message.sender}: ")
                    }
                    append(message.content)
                },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun UserRow(user: ChatUser) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val elevationColor = when (user.elevation) {
            UserElevation.OWNER -> MaterialTheme.colorScheme.error
            UserElevation.ADMIN -> MaterialTheme.colorScheme.error
            UserElevation.OPERATOR -> MaterialTheme.colorScheme.secondary
            UserElevation.HALF_OP -> MaterialTheme.colorScheme.tertiary
            UserElevation.VOICE -> MaterialTheme.colorScheme.primary
            UserElevation.NORMAL -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        }

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(elevationColor),
        )

        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (user.isAway) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ChatInput(onSend: (String) -> Unit, enabled: Boolean) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .imePadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message...") },
            enabled = enabled,
            singleLine = true,
        )

        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSend(text)
                    text = ""
                }
            },
            enabled = enabled && text.isNotBlank(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send")
        }
    }
}
