package com.soumo.child.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soumo.child.MainActivity
import com.soumo.child.id.DeviceIdManager

/**
 * ChildUI is the main UI of the child app.
 * It shows the Child ID from DeviceIdManager (if generated), a status line,
 * a bottom-left settings menu, and a bottom-right helper text.
 */

private val DefaultFontSize = 14.sp

@Composable
fun ChildUI(
    context: Context,
    statusText: String
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
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(24.dp)
    ) {
        // Centered ID and status
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (childId == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp),
                            color = Color(0xFFFFFFFF)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Waiting for Child ID...",
                            color = Color(0xFFBDBDBD),
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        text = DeviceIdManager.format(childId!!),
                        color = Color(0xFFFFFFFF),
                        fontSize = 36.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusText,
                        color = Color(0xFFBDBDBD),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Bottom row: settings (left) and helper text (right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Bottom-left: Settings gear with dropdown
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )

                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Request permissions") },
                        onClick = {
                            // No-op for now
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Hide app") },
                        onClick = {
                            menuExpanded = false
                            try {
                                val intent = Intent(context, com.soumo.child.BackgroundService::class.java)
                                intent.action = "STEALTH_ON"
                                context.startService(intent)
                            } catch (e: Exception) {
                                Log.e("ChildUI", "Failed to send STEALTH_ON intent", e)
                            }
                            try {
                                val pm = context.packageManager
                                val componentName = ComponentName(context, MainActivity::class.java)
                                pm.setComponentEnabledSetting(
                                    componentName,
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                    PackageManager.DONT_KILL_APP
                                )
                            } catch (e: Exception) {
                                Log.e("ChildUI", "Failed to hide launcher icon", e)
                            }
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .background(
                        color = Color(0xFF252525), // faint translucent black
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFFB60000), // neon red border
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(5.dp) // inner padding
            ) {
                val helperText = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = Color(0xFFFFFFFF),
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Normal,
                            fontSize = DefaultFontSize
                        )
                    ) {
                        append("Shhh… 🤫\n\n")
                        append("Grant all permissions, copy the ID, and hide this app. 🫣\n")
                        append("Use that ID in the Parent to connect. 👀\n\n")
                        append("<< Click ⚙️ to manage permissions and hide the app.")
                    }
                }

                Text(
                    text = helperText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
