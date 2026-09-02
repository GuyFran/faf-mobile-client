package com.faforever.mobile.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.faforever.mobile.companion.RelayState
import com.faforever.mobile.games.model.Game
import com.faforever.mobile.games.model.GameState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(viewModel: GamesViewModel = hiltViewModel()) {
    val games by viewModel.games.collectAsState()
    val relayState by viewModel.relayState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val expandedGameId by viewModel.expandedGameId.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val config by viewModel.config.collectAsState()
    val editing by viewModel.editing.collectAsState()

    // Revive a given-up reconnect loop whenever the tab becomes visible.
    LaunchedEffect(Unit) { viewModel.onTabVisible() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Games (${games.size})") },
            actions = {
                if (config?.isConfigured == true) {
                    IconButton(onClick = { viewModel.startEditing() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Edit connection")
                    }
                }
            },
        )

        val cfg = config
        if (cfg == null) {
            // DataStore hasn't emitted yet — don't flash the first-run setup form.
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            return@Column
        }

        if (!cfg.isConfigured || editing) {
            CompanionSetup(
                initialHost = cfg.host,
                initialPort = cfg.port,
                initialToken = cfg.token,
                showCancel = cfg.isConfigured,
                onCancel = { viewModel.cancelEditing() },
                onSave = { h, p, t -> viewModel.saveAndConnect(h, p, t) },
            )
            return@Column
        }

        if (relayState == RelayState.CONNECTING || relayState == RelayState.WAITING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (relayState == RelayState.WAITING) {
            Text(
                text = "Paired with your PC. Waiting for the FAF client there to log in…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        if (relayState == RelayState.DISCONNECTED) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Not connected to PC relay",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = lastError ?: "Make sure the FAF client is running on your PC " +
                            "(${cfg.host}:${cfg.port}) and on the same network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                    Row {
                        TextButton(onClick = { viewModel.reconnect() }) { Text("Retry") }
                        TextButton(onClick = { viewModel.startEditing() }) { Text("Edit connection") }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GameFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { viewModel.setFilter(f) },
                    label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        if (games.isEmpty() && relayState == RelayState.CONNECTED) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = when (filter) {
                        GameFilter.OPEN -> "No open lobbies right now"
                        GameFilter.PLAYING -> "No games in progress"
                        GameFilter.ALL -> "No games right now"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(games, key = { it.uid }) { game ->
                    GameCard(
                        game = game,
                        isExpanded = expandedGameId == game.uid,
                        onClick = { viewModel.toggleGameExpanded(game.uid) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompanionSetup(
    initialHost: String,
    initialPort: Int,
    initialToken: String,
    showCancel: Boolean,
    onCancel: () -> Unit,
    onSave: (String, Int, String) -> Unit,
) {
    var host by remember { mutableStateOf(initialHost) }
    var port by remember { mutableStateOf(if (initialPort > 0) initialPort.toString() else "6900") }
    var token by remember { mutableStateOf(initialToken) }

    // A host must be a bare IP/name — a ':' means the user pasted host:port into the wrong field.
    val hostTrimmed = host.trim()
    val hostValid = hostTrimmed.isNotEmpty() &&
        !hostTrimmed.contains(':') && !hostTrimmed.any { it.isWhitespace() }
    val portNum = port.toIntOrNull()
    val portValid = portNum != null && portNum in 1..65535
    val tokenValid = token.isNotBlank()

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Connect to your PC",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Open lobbies come from the FAF client running on your PC (same wifi). " +
                "Enable the companion in the FAF client first — it writes the pairing info " +
                "(IP, port, token) to the file faf_companion_pairing.txt in your home folder " +
                "on the PC. Enter those values here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("PC local IP (e.g. 192.168.1.20)") },
            singleLine = true,
            isError = host.isNotBlank() && !hostValid,
            supportingText = {
                if (host.isNotBlank() && !hostValid) {
                    Text("IP or hostname only — no spaces, no \":port\" (use the Port field)")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
            label = { Text("Port") },
            singleLine = true,
            isError = port.isNotBlank() && !portValid,
            supportingText = {
                if (port.isNotBlank() && !portValid) Text("Port must be 1–65535")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Pairing token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onSave(hostTrimmed, portNum ?: 6900, token.trim()) },
            enabled = hostValid && portValid && tokenValid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save & Connect")
        }

        if (showCancel) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameCard(game: Game, isExpanded: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (game.state == GameState.PLAYING) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (game.state == GameState.PLAYING) Icons.Default.PlayArrow
                    else Icons.Default.SportsEsports,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (game.state == GameState.PLAYING) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (game.passwordProtected) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Password protected",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    }

                    Text(
                        text = "Host: ${game.host}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${game.numPlayers}/${game.maxPlayers}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    modifier = Modifier.size(20.dp),
                )
            }

            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatMapName(game.mapName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = game.featuredMod.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                    val observerTeams = setOf("-1", "null")
                    val playingTeams = game.teams.filter { it.key !in observerTeams }
                    val observers = game.teams.filter { it.key in observerTeams }
                        .values.flatten()

                    playingTeams.forEach { (teamId, players) ->
                        Text(
                            text = "Team $teamId",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            players.forEach { player ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(player, style = MaterialTheme.typography.bodySmall) },
                                )
                            }
                        }
                    }

                    if (observers.isNotEmpty()) {
                        Text(
                            text = "Observers",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Text(
                            text = observers.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }

                    if (game.simMods.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Mods: ${game.simMods.values.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }

                    if (game.ratingMin != null || game.ratingMax != null) {
                        Text(
                            text = "Rating: ${game.ratingMin?.toInt() ?: "?"} - ${game.ratingMax?.toInt() ?: "?"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun formatMapName(mapName: String): String =
    mapName
        .replace("_", " ")
        .replace(".v0001", "")
        .replaceFirstChar { it.uppercase() }
