package com.soumo.child.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soumo.child.id.DeviceIdManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ChildUI is the main UI of the child app.
 * It shows the Child ID from DeviceIdManager (if generated),
 * and a scrollable, color-coded log view.
 */
@Composable
fun ChildUI(
    context: Context,
    logs: SnapshotStateList<String>
) {
    // Local state for childId
    var childId by remember { mutableStateOf<String?>(null) }

    // Load ID from DeviceIdManager (cached or prefs)
    LaunchedEffect(Unit) {
        childId = DeviceIdManager.cachedId
            ?: context.getSharedPreferences("phantom_prefs", Context.MODE_PRIVATE)
                .getString("device_id", null)
                ?.also { Log.d("ChildUI", "Loaded from prefs: $it") }
    }

    val colorDebug = Color(0xFF2196F3)
    val colorInfo = Color(0xFF4CAF50)
    val colorWarn = Color(0xFFFFA000)
    val colorError = Color(0xFFD32F2F)
    val colorFatal = Color(0xFFB000B0)

    val listState = rememberLazyListState()
    val maxLines = 500

    // Logcat capture: only keep relevant lines
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("logcat -v time *:V")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    if (line != null &&
                        (line.contains("DeviceID") ||
                                line.contains("ChildUI") ||
                                line.contains("BackgroundService"))) {

                        withContext(Dispatchers.Main) {
                            logs.add(line)
                            if (logs.size > maxLines) {
                                while (logs.size > maxLines) logs.removeAt(0)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChildUI", "Logcat capture failed", e)
            }
        }
    }

    // Auto-scroll on new logs
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(12.dp)
    ) {
        // Child ID header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (childId == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Waiting for Child ID...",
                        color = Color(0xFFBDBDBD),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = DeviceIdManager.format(childId!!),
                    color = Color(0xFF69F0AE), // vivid green
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Logs viewer
        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                reverseLayout = false,
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                itemsIndexed(logs) { _, rawLine ->
                    val parts = parseLogLine(rawLine)

                    val msgColor = when (parts.level) {
                        'D' -> colorDebug
                        'I' -> colorInfo
                        'W' -> colorWarn
                        'E' -> colorError
                        'F', 'A' -> colorFatal
                        else -> colorInfo
                    }

                    val annotated = buildAnnotatedString {
                        parts.tag?.let {
                            withStyle(
                                style = SpanStyle(
                                    color = msgColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) {
                                append(it)
                                append("  ")
                            }
                        }
                        withStyle(
                            style = SpanStyle(
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        ) {
                            append(parts.message ?: "")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = annotated,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Visible
                        )
                    }
                }
            }
        }
    }
}

private data class LogParts(
    val level: Char? = null,
    val tag: String? = null,
    val message: String? = null
)

private fun parseLogLine(line: String): LogParts {
    val regex = Regex("""^\s*(?:\d{2}-\d{2}|\d{4}-\d{2}-\d{2})\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+([VDIWEAF])/([^:]+):\s*(.*)$""")
    val m = regex.find(line)
    if (m != null) {
        val (lvl, tag, msg) = m.destructured
        return LogParts(level = lvl.firstOrNull(), tag = tag.trim(), message = msg)
    }
    val regex2 = Regex("""^\s*([VDIWEAF])/([^:]+):\s*(.*)$""")
    val m2 = regex2.find(line)
    if (m2 != null) {
        val (lvl, tag, msg) = m2.destructured
        return LogParts(level = lvl.firstOrNull(), tag = tag.trim(), message = msg)
    }
    return LogParts(level = 'I', tag = null, message = line)
}
