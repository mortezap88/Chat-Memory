package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.data.ApiKeyItem
import com.example.data.AppSettings
import com.example.data.ChatMessage
import com.example.data.CorrectionType
import com.example.data.FailoverEvent
import com.example.data.LinguisticCorrection
import com.example.ui.components.CorrectionDialog
import com.example.ui.components.CustomVirtualKeyboard
import com.example.ui.components.FailoverBanner
import com.example.ui.components.InteractiveCorrectedText

sealed class ChatPage {
    data class UserPage(val message: ChatMessage, val index: Int) : ChatPage()
    data class AiPage(val message: ChatMessage, val index: Int) : ChatPage()
}

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    settings: AppSettings,
    apiKeys: List<ApiKeyItem>,
    activeKeyIndex: Int,
    failoverEvent: FailoverEvent?,
    isRecordingAudio: Boolean,
    onStartAudioRecording: () -> Unit,
    onStopAudioRecording: () -> Unit,
    onSendMessage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onClearChat: () -> Unit,
    onDismissFailover: () -> Unit,
    onRetryMessage: (ChatMessage) -> Unit,
    onApplyCorrection: (ChatMessage, LinguisticCorrection) -> Unit
) {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var isKeyboardVisible by remember { mutableStateOf(false) }
    var typedText by remember { mutableStateOf("") }
    var activeCorrectionModal by remember { mutableStateOf<Pair<ChatMessage, LinguisticCorrection>?>(null) }

    // Build the list of pages based on whether streamlining is on or off
    val pages = remember(messages.toList(), settings.messagingStreamlining) {
        if (settings.messagingStreamlining) {
            messages.mapIndexed { idx, msg -> ChatPage.AiPage(msg, idx) }
        } else {
            val list = mutableListOf<ChatPage>()
            messages.forEachIndexed { idx, msg ->
                list.add(ChatPage.UserPage(msg, idx))
                list.add(ChatPage.AiPage(msg, idx))
            }
            list
        }
    }

    val pagerState = rememberPagerState(
        initialPage = (pages.size - 1).coerceAtLeast(0),
        pageCount = { if (pages.isEmpty()) 1 else pages.size }
    )

    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty()) {
            pagerState.animateScrollToPage(pages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0C))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Navigation & Status Bar (pool and mic ready removed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // If actively recording, show a subtle active recording pill with pulse dot
                if (isRecordingAudio) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF3B1214))
                            .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(16.dp))
                            .clickable { onStopAudioRecording() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recording (release to send)",
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (pages.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${pages.size}",
                        color = Color(0xFF71717A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier.size(34.dp).testTag("clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Session",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(34.dp).testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            FailoverBanner(
                event = failoverEvent,
                onDismiss = onDismissFailover,
                modifier = Modifier.testTag("failover_banner")
            )

            // Page-Based Message Container
            // Hold down anywhere to record; releasing finger immediately stops and sends the audio
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var isHeld = false
                            // Launch a short delay check for long press / hold
                            val holdJob = coroutineScope.launch {
                                delay(300L)
                                isHeld = true
                                onStartAudioRecording()
                            }
                            // Wait for pointer release or cancellation
                            val upOrCancel = waitForUpOrCancellation()
                            holdJob.cancel()
                            if (isHeld) {
                                // Finger was held and now released -> immediately send!
                                onStopAudioRecording()
                            } else if (upOrCancel != null) {
                                // Short tap -> toggle keyboard
                                isKeyboardVisible = !isKeyboardVisible
                            }
                        }
                    }
            ) {
                if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF141923))
                                .border(1.dp, Color(0xFF00A3FF).copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF00A3FF),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Tap to type\nHold to speak, release to send",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "${settings.primaryModel} • Max ${settings.maxFontSizeSp}sp (Auto-shrink)",
                            color = Color(0xFF71717A),
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        val pageItem = pages.getOrNull(pageIndex)
                        if (pageItem != null) {
                            when (pageItem) {
                                is ChatPage.UserPage -> {
                                    UserMessagePageView(
                                        message = pageItem.message,
                                        settings = settings,
                                        onCorrectionClick = { corr ->
                                            activeCorrectionModal = Pair(pageItem.message, corr)
                                        }
                                    )
                                }
                                is ChatPage.AiPage -> {
                                    AiResponsePageView(
                                        message = pageItem.message,
                                        settings = settings,
                                        onRetry = { onRetryMessage(pageItem.message) }
                                    )
                                }
                            }
                        }
                    }
                }

                // If recording audio, show recording banner with tap-to-send option
                if (isRecordingAudio) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF2E1012))
                            .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(24.dp))
                            .clickable { onStopAudioRecording() }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recording... Release screen or tap here to send",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        CustomVirtualKeyboard(
            visible = isKeyboardVisible,
            text = typedText,
            onTextChange = { typedText = it },
            onSend = { sent ->
                onSendMessage(sent)
                typedText = ""
                isKeyboardVisible = false
            },
            onClose = { isKeyboardVisible = false },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .testTag("custom_virtual_keyboard")
        )
    }

    // Active Correction Dialog modal
    activeCorrectionModal?.let { (msg, corr) ->
        CorrectionDialog(
            correction = corr,
            onApply = { appliedCorr ->
                onApplyCorrection(msg, appliedCorr)
                activeCorrectionModal = null
            },
            onDismiss = { activeCorrectionModal = null }
        )
    }
}

/**
 * Full page view for USER's message with subtle white frame and interactive linguistic corrections.
 */
@Composable
internal fun UserMessagePageView(
    message: ChatMessage,
    settings: AppSettings,
    onCorrectionClick: (LinguisticCorrection) -> Unit
) {
    val scrollState = rememberScrollState()

    val grammarCount = message.corrections.count { it.type == CorrectionType.GRAMMAR }
    val wordChoiceCount = message.corrections.count { it.type == CorrectionType.WORD_CHOICE }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Center
    ) {
        // Suggestions count banner matching screenshot
        if (message.corrections.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Suggestions (tap highlighted words to fix):",
                    color = Color(0xFFA1A1AA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (grammarCount > 0) {
                        Text(
                            text = "• $grammarCount Grammar (Red)",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (wordChoiceCount > 0) {
                        if (grammarCount > 0) Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• $wordChoiceCount Word Choice (Yellow)",
                            color = Color(0xFFF59E0B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Subtle thin white frame container around user message
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF101114))
                .border(
                    width = 0.75.dp,
                    color = Color.White.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.isAudioInput) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (message.isAudioInput) "VOICE INPUT" else "YOUR MESSAGE",
                        color = Color(0xFFA1A1AA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (message.isAnalyzingCorrections) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            color = Color(0xFFF59E0B),
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Inspecting...",
                            color = Color(0xFF71717A),
                            fontSize = 10.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                InteractiveCorrectedText(
                    text = message.userText,
                    corrections = message.corrections,
                    fontSizeSp = settings.maxFontSizeSp,
                    onCorrectionClick = onCorrectionClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Full page view for AI's response with subtle blue frame (or full screen in streamlined mode)
 * and auto-shrinking text.
 */
@Composable
internal fun AiResponsePageView(
    message: ChatMessage,
    settings: AppSettings,
    onRetry: () -> Unit
) {
    if (settings.messagingStreamlining) {
        // STREAMLINING ON: Full screen with no frames
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (message.error != null) {
                ErrorCard(error = message.error, onRetry = onRetry)
            } else if (message.aiResponse.isEmpty() && message.isStreaming) {
                StreamingIndicator(model = message.modelUsed)
            } else {
                AutoShrinkText(
                    text = message.aiResponse,
                    maxFontSizeSp = settings.maxFontSizeSp,
                    enableScroll = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        // STREAMLINING OFF: Dedicated page with subtle thin blue frame around AI response
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0E141E))
                    .border(
                        width = 0.75.dp,
                        color = Color(0xFF00A3FF).copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.modelUsed.uppercase().replace("-", " "),
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        if (message.isStreaming) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                color = Color(0xFF00A3FF),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (message.error != null) {
                        ErrorCard(error = message.error, onRetry = onRetry)
                    } else if (message.aiResponse.isEmpty() && message.isStreaming) {
                        StreamingIndicator(model = message.modelUsed)
                    } else {
                        AutoShrinkText(
                            text = message.aiResponse,
                            maxFontSizeSp = settings.maxFontSizeSp,
                            enableScroll = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF261214))
            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "Generation Error",
                color = Color(0xFFFCA5A5),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = error,
                color = Color(0xFFF87171),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF3F191D))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StreamingIndicator(model: String) {
    Row(
        modifier = Modifier.padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = Color(0xFF00A3FF),
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Streaming response from $model...",
            color = Color(0xFFA1A1AA),
            fontSize = 14.sp
        )
    }
}

/**
 * Auto-shrinks text dynamically to fit within available page height and width.
 * Prevents nesting scrollable containers inside unbounded parents to prevent Compose crashes.
 */
@Composable
fun AutoShrinkText(
    text: String,
    maxFontSizeSp: Int,
    minFontSizeSp: Int = 12,
    color: Color = Color(0xFFF4F4F5),
    enableScroll: Boolean = false,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val maxAvailableHeightPx = with(density) { maxHeight.toPx() }
        val maxAvailableWidthPx = with(density) { maxWidth.toPx() }

        var chosenSizeSp = maxFontSizeSp
        if (maxAvailableHeightPx.isFinite() && maxAvailableWidthPx.isFinite() && maxAvailableWidthPx > 0f) {
            for (candidateSp in maxFontSizeSp downTo minFontSizeSp) {
                val measured = textMeasurer.measure(
                    text = text,
                    style = TextStyle(
                        fontSize = candidateSp.sp,
                        lineHeight = (candidateSp * 1.4f).sp
                    ),
                    constraints = Constraints(maxWidth = maxAvailableWidthPx.toInt().coerceAtLeast(1))
                )
                if (measured.size.height <= maxAvailableHeightPx || candidateSp == minFontSizeSp) {
                    chosenSizeSp = candidateSp
                    break
                }
            }
        }

        val scrollState = rememberScrollState()
        val textModifier = if (enableScroll && maxAvailableHeightPx.isFinite()) {
            Modifier.verticalScroll(scrollState)
        } else {
            Modifier
        }

        Text(
            text = text,
            color = color,
            fontSize = chosenSizeSp.sp,
            lineHeight = (chosenSizeSp * 1.45f).sp,
            fontWeight = FontWeight.Normal,
            modifier = textModifier
        )
    }
}
