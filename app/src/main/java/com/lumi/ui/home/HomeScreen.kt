package com.lumi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumi.ui.chat.ChatTranscript
import com.lumi.ui.chat.ChatViewModel
import com.lumi.ui.components.LumiBlob
import com.lumi.ui.components.LumiGlassPanel
import com.lumi.ui.components.LumiInput
import com.lumi.ui.components.BORDER_ALPHA
import com.lumi.ui.theme.spacing
import java.util.Calendar
import androidx.compose.animation.animateContentSize

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    var query by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var reactionCount by remember { mutableStateOf(0) }

    val turns by viewModel.turns.collectAsStateWithLifecycle()
    val canSend by viewModel.canSend.collectAsStateWithLifecycle()
    val thinkingVerb by viewModel.thinkingVerb.collectAsStateWithLifecycle()

    val conversationStarted = turns.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .imePadding()
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // DESIGN_LANGUAGE §6 ties the mascot's typing pose to the keyboard opening, not to the field
        // holding text — the mascot should already be out of the way by the time the first character
        // lands. Text presence is kept as a second trigger so a hardware keyboard behaves the same.
        val isTyping = WindowInsets.isImeVisible || query.isNotEmpty()

        // Once a conversation exists the transcript is the screen, so the mascot and greeting give up
        // their space to it rather than competing for the middle. With the live mascot on it reappears
        // docked next to the menu button; with it off it simply stays away until the chat is cleared.
        if (conversationStarted) {
            ChatTranscript(
                turns = turns,
                thinkingVerb = thinkingVerb,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))

            LumiBlob(
                modifier = Modifier.size(69.dp),
                isTyping = isTyping,
                reactionTrigger = reactionCount
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = !isTyping,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(32.dp))
                    val greeting = remember { getGreeting() }
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Bottom spacer goes away when typing so blob sits right above input
            val bottomWeight by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isTyping) 0.001f else 1f, label = "bottomWeight")
            Spacer(Modifier.weight(bottomWeight))
        }

        LumiInput(
            value = query,
            onValueChange = { query = it },
            onSendText = { text ->
                viewModel.send(text)
                query = ""
                reactionCount++
            },
            // False while the model loads or is already decoding. Before this, the send button looked
            // alive at all times and a tap silently did nothing.
            canSend = canSend,
            showAttach = true,
            onAttach = {
                showAttachmentSheet = true
                reactionCount++
            },
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }

    if (showAttachmentSheet) {
        AttachmentBottomSheet(onDismiss = { showAttachmentSheet = false })
    }
}

fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning."
        in 12..16 -> "Good afternoon."
        in 17..20 -> "Good evening."
        else -> "What's up?"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentBottomSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        // Hairline glass border at top edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(com.lumi.ui.theme.LumiSize.hairline)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = BORDER_ALPHA))
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
                Spacer(Modifier.weight(1f))
                Text("Add to chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp)) // balance center
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AttachmentOption(icon = Icons.Rounded.CameraAlt, label = "Camera", modifier = Modifier.weight(1f))
                AttachmentOption(icon = Icons.Rounded.PhotoLibrary, label = "Photos", modifier = Modifier.weight(1f))
                AttachmentOption(icon = Icons.Rounded.UploadFile, label = "Files", modifier = Modifier.weight(1f))
                AttachmentOption(icon = Icons.Rounded.GraphicEq, label = "Audio", modifier = Modifier.weight(1f))
            }
            
            Spacer(Modifier.height(24.dp))
            
            val webRowShape = com.lumi.ui.theme.LumiShape.default
            LumiGlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = webRowShape,
            ) {
                Row(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.md, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(Modifier.width(14.dp))
                    Text("Web search", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Box(modifier = Modifier.scale(0.78f)) {
                    Switch(
                        checked = true,
                        onCheckedChange = {},
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    )
                    }
                }
            }
            
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun AttachmentOption(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    LumiGlassPanel(
        modifier = modifier.height(com.lumi.ui.theme.LumiSize.tile),
        shape = com.lumi.ui.theme.LumiShape.tile,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(MaterialTheme.spacing.sm))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
