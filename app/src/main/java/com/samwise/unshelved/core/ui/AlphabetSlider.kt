package com.samwise.unshelved.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ALPHABET = ('A'..'Z').toList()

@Composable
fun AlphabetSlider(
    activeLetters: Set<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var currentLetter by remember { mutableStateOf<Char?>(null) }
    var sliderHeightPx by remember { mutableIntStateOf(0) }
    var currentYPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    fun letterFromY(y: Float): Char? {
        if (sliderHeightPx <= 0) return null
        val index = ((y / sliderHeightPx) * ALPHABET.size).toInt().coerceIn(0, ALPHABET.size - 1)
        return ALPHABET[index]
    }

    fun handlePosition(y: Float) {
        val letter = letterFromY(y) ?: return
        currentLetter = letter
        currentYPx = y
        if (letter in activeLetters) onLetterSelected(letter)
    }

    Box(modifier = modifier) {
        // Floating bubble
        AnimatedVisibility(
            visible = isDragging && currentLetter != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            val bubbleSize = 48.dp
            val bubbleSizePx = with(density) { bubbleSize.toPx() }
            val bubbleOffsetY = with(density) {
                (currentYPx - bubbleSizePx / 2)
                    .coerceIn(0f, (sliderHeightPx - bubbleSizePx).coerceAtLeast(0f))
                    .toDp()
            }
            Box(
                modifier = Modifier
                    .offset(x = (-48).dp, y = bubbleOffsetY)
                    .size(bubbleSize)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (currentLetter ?: ' ').toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp)
                .align(Alignment.CenterEnd)
                .onSizeChanged { sliderHeightPx = it.height }
                .pointerInput(activeLetters) {
                    detectTapGestures { offset ->
                        handlePosition(offset.y)
                    }
                }
                .pointerInput(activeLetters) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            handlePosition(offset.y)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            handlePosition(change.position.y)
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ALPHABET.forEach { letter ->
                val isActive = letter in activeLetters
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter.toString(),
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
