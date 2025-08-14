package com.soumo.parentandroid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random


@Composable
fun ConnectionScreenUI(
    onContinue: (String) -> Unit,
    isConnecting: Boolean
) {
    data class Particle(
        var pos: Offset,
        var velocity: Offset,
        val size: Float,
        val opacity: Float
    )

    val particleCount = 150
    val maxDistance = 250f
    val particleSizeMax = 2.3f
    val particleSpeedMax = 3.0f

    var screenWidth by remember { mutableFloatStateOf(0f) }
    var screenHeight by remember { mutableFloatStateOf(0f) }
    var particles by remember { mutableStateOf(listOf<Particle>()) }
    var touchPosition by remember { mutableStateOf(Offset(-1000f, -1000f)) }

    LaunchedEffect(screenWidth, screenHeight) {
        if (screenWidth > 0f && screenHeight > 0f) {
            particles = List(particleCount) {
                Particle(
                    pos = Offset(Random.nextFloat() * screenWidth, Random.nextFloat() * screenHeight),
                    velocity = Offset(
                        (Random.nextFloat() - 0.5f) * particleSpeedMax,
                        (Random.nextFloat() - 0.5f) * particleSpeedMax
                    ),
                    size = Random.nextFloat() * particleSizeMax,
                    opacity = 0.9f
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis {
                val w = screenWidth
                val h = screenHeight
                particles = particles.map { p ->
                    var newX = p.pos.x + p.velocity.x
                    var newY = p.pos.y + p.velocity.y
                    var vx = p.velocity.x
                    var vy = p.velocity.y

                    if (newX < 0f || newX > w) vx = -vx
                    if (newY < 0f || newY > h) vy = -vy

                    newX = newX.coerceIn(0f, w)
                    newY = newY.coerceIn(0f, h)

                    p.copy(pos = Offset(newX, newY), velocity = Offset(vx, vy))
                }
            }
        }
    }

    var childId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pos = event.changes.firstOrNull()?.position
                        touchPosition = if (pos != null && event.changes.first().pressed) {
                            pos
                        } else {
                            Offset(-1000f, -1000f)
                        }
                    }
                }
            }
            .onSizeChanged { size: IntSize ->
                screenWidth = size.width.toFloat()
                screenHeight = size.height.toFloat()
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in particles.indices) {
                for (j in i + 1 until particles.size) {
                    val p1 = particles[i]
                    val p2 = particles[j]
                    val dist = sqrt((p1.pos.x - p2.pos.x).pow(2) + (p1.pos.y - p2.pos.y).pow(2))
                    if (dist < maxDistance) {
                        val alpha = ((1f - dist / maxDistance) * 0.9f)
                        drawLine(
                            color = Color(0xFF006600).copy(alpha = alpha),
                            start = p1.pos,
                            end = p2.pos,
                            strokeWidth = 2f
                        )
                    }
                }
            }

            for (p in particles) {
                val dist = sqrt((p.pos.x - touchPosition.x).pow(2) + (p.pos.y - touchPosition.y).pow(2))
                if (dist < maxDistance) {
                    val alpha = ((1f - dist / maxDistance) * 0.85f)
                    drawLine(
                        color = Color(0xFF006600).copy(alpha = alpha),
                        start = p.pos,
                        end = touchPosition,
                        strokeWidth = 2f
                    )
                }
            }

            particles.forEach { p ->
                drawCircle(
                    color = Color.White.copy(alpha = p.opacity),
                    radius = p.size,
                    center = p.pos
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Enter 12-digit Child ID",
                fontSize = 18.sp,
                color = Color.Green,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = childId,
                onValueChange = {
                    if (it.length <= 12 && it.all { ch -> ch.isDigit() }) {
                        childId = it
                        error = null
                    } else {
                        error = "Only digits allowed, max 12 characters"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .shadow(8.dp, RoundedCornerShape(8.dp)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Red,
                    unfocusedBorderColor = Color.Red,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.LightGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    cursorColor = Color.White,
                    focusedContainerColor = Color(0xFF2F2F2F),
                    unfocusedContainerColor = Color(0xFF2F2F2F),
                ),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            if (!error.isNullOrEmpty()) {
                Text(text = error!!, color = Color.Yellow, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3D-styled Connect button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .shadow(16.dp, RoundedCornerShape(14.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                Color(0xFF2A2A2A), // top highlight
                                Color(0xFF1F1F1F)  // bottom shade
                            )
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .then(
                        if (childId.length == 12) Modifier else Modifier
                    )
                    .let { base ->
                        if (childId.length == 12) base.pointerInput(Unit) {
                            awaitPointerEventScope { }
                        } else base
                    }
                    .padding(0.dp)
                    .then(Modifier)
                    .pointerInput(childId) {},
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        if (childId.length == 12) {
                            onContinue(childId)
                        } else {
                            error = "Child ID must be exactly 12 digits"
                        }
                    },
                    enabled = childId.length == 12 && !isConnecting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .shadow(10.dp, RoundedCornerShape(14.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 14.dp,
                        pressedElevation = 6.dp
                    )
                ) {
                    Text(text = if (isConnecting) "Connecting…" else "Connect", fontSize = 18.sp)
                }
            }
        }
    }

    // Loading overlay while connecting
    if (isConnecting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color(0xFF00FF66),
                strokeWidth = 6.dp
            )
        }
    }
}
