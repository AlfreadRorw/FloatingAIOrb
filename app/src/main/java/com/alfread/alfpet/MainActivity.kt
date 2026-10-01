package com.alfread.alfpet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Background = Color(0xFFF7F7F8)
private val SurfaceWhite = Color(0xFFFFFFFF)
private val Primary = Color(0xFF5B7CFF)
private val Secondary = Color(0xFF8A72D8)
private val TextPrimary = Color(0xFF17181C)
private val Muted = Color(0xFF777980)
private val HungerColor = Color(0xFFE88A4A)
private val HappinessColor = Color(0xFFDB5D8A)
private val EnergyColor = Color(0xFF5D8BDB)
private val CleanColor = Color(0xFF59A88D)

class MainActivity : ComponentActivity() {
    private val petViewModel: PetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlfPetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    AlfPetApp(petViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        petViewModel.refreshFromClock()
    }

    override fun onStop() {
        petViewModel.persistNow()
        super.onStop()
    }
}

@Composable
private fun AlfPetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Primary,
            secondary = Secondary,
            background = Background,
            surface = SurfaceWhite,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        content = content
    )
}

@Composable
private fun AlfPetApp(viewModel: PetViewModel) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val levelUp by viewModel.levelUp.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.persistNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var idleFrame by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(state.moodScore) {
        while (true) {
            val interval = (120L + (100 - state.moodScore) * 1L).coerceIn(120L, 220L)
            delay(interval)
            idleFrame = (idleFrame + 1) % 8
        }
    }

    var interaction by remember { mutableIntStateOf(0) }
    var interactionType by remember { mutableStateOf("none") }
    val petMotion = remember { Animatable(0f) }
    LaunchedEffect(interaction) {
        if (interaction == 0) return@LaunchedEffect
        val amplitude = when (interactionType) {
            "sleep" -> 0.5f
            else -> 1f
        }
        petMotion.snapTo(0f)
        petMotion.animateTo(amplitude, tween(90, easing = FastOutSlowInEasing))
        petMotion.animateTo(-amplitude * 0.7f, tween(90, easing = FastOutSlowInEasing))
        petMotion.animateTo(0f, tween(120, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(2200L)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(levelUp) {
        if (levelUp != null) {
            delay(2800L)
            viewModel.clearLevelUp()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        val wide = maxWidth >= 720.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(state)

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    PetRoom(
                        modifier = Modifier.weight(1.1f),
                        state = state,
                        frame = idleFrame,
                        petMotion = petMotion.value
                    )
                    Column(
                        modifier = Modifier.weight(0.9f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StatusCard(state)
                        ActionsCard(
                            onFeed = { interactionType = "feed"; interaction++; viewModel.feed() },
                            onPlay = { interactionType = "play"; interaction++; viewModel.play() },
                            onSleep = { interactionType = "sleep"; interaction++; viewModel.sleep() },
                            onClean = { interactionType = "clean"; interaction++; viewModel.clean() }
                        )
                    }
                }
            } else {
                PetRoom(
                    modifier = Modifier.fillMaxWidth(),
                    state = state,
                    frame = idleFrame,
                    petMotion = petMotion.value
                )
                StatusCard(state)
                ActionsCard(
                    onFeed = { interactionType = "feed"; interaction++; viewModel.feed() },
                    onPlay = { interactionType = "play"; interaction++; viewModel.play() },
                    onSleep = { interactionType = "sleep"; interaction++; viewModel.sleep() },
                    onClean = { interactionType = "clean"; interaction++; viewModel.clean() }
                )
            }

            if (message != null) {
                MessageCard(message!!)
            }

            Text(
                text = "Offline pet · Progress is saved on this device",
                modifier = Modifier.fillMaxWidth(),
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
        }

        AnimatedVisibility(
            visible = levelUp != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(180)) + scaleIn(tween(220)),
            exit = fadeOut(tween(180))
        ) {
            LevelUpCard(levelUp ?: state.level)
        }
    }
}

@Composable
private fun Header(state: PetState) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..21 -> "Good Evening"
        else -> "Good Night"
    }
    val progress = state.xp.toFloat() / state.xpRequired().toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        greeting,
                        color = Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "ALF PET",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        state.mood,
                        color = Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF2F4FF))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.MonetizationOn,
                        contentDescription = "Coins",
                        tint = Color(0xFFD49A22),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        state.coins.toString(),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Divider(color = Color(0xFFECECF0))
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEEF0FF)
                ) {
                    Text(
                        "LEVEL ${state.level}",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        color = Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "XP ${state.xp} / ${state.xpRequired()}",
                    color = Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = Primary,
                trackColor = Color(0xFFE9EBF4)
            )
        }
    }
}

@Composable
private fun PetRoom(
    modifier: Modifier,
    state: PetState,
    frame: Int,
    petMotion: Float
) {
    val context = LocalContext.current
    val frameId = remember(frame) {
        context.resources.getIdentifier(
            "idle_%02d".format(frame + 1),
            "drawable",
            context.packageName
        )
    }
    val petAlpha = (0.72f + state.moodScore / 100f * 0.28f).coerceIn(0.72f, 1f)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .clip(RoundedCornerShape(28.dp))
        ) {
            RoomBackground()

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 66.dp)
                    .size(width = 190.dp, height = 42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x334F5D7A))
            )

            if (frameId != 0) {
                androidx.compose.foundation.Image(
                    painter = painterResource(frameId),
                    contentDescription = "ALF cat pet",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 6.dp)
                        .size(220.dp)
                        .alpha(petAlpha)
                        .scale(1f + (petMotion / 180f))
                        .semantics { contentDescription = "ALF virtual cat" }
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.86f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Mood score",
                        tint = Color(0xFFD49A22),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${state.moodScore}% mood",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomBackground() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF8F1E8), Color(0xFFF3EEF8))
                )
            )
    ) {
        val w = size.width
        val h = size.height
        val floorTop = h * 0.72f

        drawRect(Color(0xFFF8F0E8), size = Size(w, floorTop))
        drawRect(Color(0xFFD9C3A5), topLeft = Offset(0f, floorTop), size = Size(w, h - floorTop))

        for (i in 0..7) {
            val y = floorTop + i * (h - floorTop) / 8f
            drawLine(
                color = Color(0x55A7896B),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.5f
            )
        }

        val rugCenter = Offset(w / 2f, h * 0.79f)
        drawOval(
            color = Color(0xFFE4DDF2),
            topLeft = Offset(rugCenter.x - w * 0.26f, rugCenter.y - 18f),
            size = Size(w * 0.52f, 48f)
        )
        drawOval(
            color = Color(0x33FFFFFF),
            topLeft = Offset(rugCenter.x - w * 0.21f, rugCenter.y - 13f),
            size = Size(w * 0.42f, 38f),
            style = Stroke(width = 2f)
        )

        // Wall frames.
        drawRoundRect(Color(0xFFE7D5BE), Offset(w * 0.08f, h * 0.17f), Size(w * 0.14f, h * 0.15f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f))
        drawRoundRect(Color(0xFFE7D5BE), Offset(w * 0.78f, h * 0.15f), Size(w * 0.14f, h * 0.14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f))
        drawCircle(Color(0xFF9CB8E8), radius = 10f, center = Offset(w * 0.15f, h * 0.245f))
        drawCircle(Color(0xFFD7A4C4), radius = 7f, center = Offset(w * 0.85f, h * 0.22f))

        // Shelf.
        drawRoundRect(Color(0xFFB98F6B), Offset(w * 0.64f, h * 0.38f), Size(w * 0.28f, 9f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f))
        drawRoundRect(Color(0xFFB98F6B), Offset(w * 0.66f, h * 0.39f), Size(7f, 42f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
        drawRoundRect(Color(0xFFB98F6B), Offset(w * 0.88f, h * 0.39f), Size(7f, 42f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f))
        drawCircle(Color(0xFFB8D2A8), radius = 12f, center = Offset(w * 0.71f, h * 0.35f))
        drawCircle(Color(0xFF95B786), radius = 10f, center = Offset(w * 0.75f, h * 0.33f))

        // Lamp.
        drawLine(Color(0xFF8C8D95), Offset(w * 0.20f, h * 0.30f), Offset(w * 0.20f, h * 0.43f), 4f, StrokeCap.Round)
        drawCircle(Color(0xFFF4DDA8), radius = 19f, center = Offset(w * 0.20f, h * 0.29f))
        drawCircle(Color(0xFFFFF6D9), radius = 9f, center = Offset(w * 0.20f, h * 0.29f))

        // Tiny toy balls.
        drawCircle(Color(0xFFD99AB7), radius = 6f, center = Offset(w * 0.29f, h * 0.75f))
        drawCircle(Color(0xFF86A7D8), radius = 5f, center = Offset(w * 0.72f, h * 0.76f))
    }
}

@Composable
private fun StatusCard(state: PetState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pet status",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    statusDescription(state.moodScore),
                    color = Muted,
                    fontSize = 12.sp
                )
            }
            StatusRow("Hunger", state.hunger, Icons.Filled.Fastfood, HungerColor)
            StatusRow("Happiness", state.happiness, Icons.Filled.Favorite, HappinessColor)
            StatusRow("Energy", state.energy, Icons.Filled.Bolt, EnergyColor)
            StatusRow("Cleanliness", state.cleanliness, Icons.Filled.CleaningServices, CleanColor)
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(statusLabel(value), color = statusColor(value), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("$value", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = value / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(CircleShape),
            color = color,
            trackColor = Color(0xFFECEEF2)
        )
    }
}

private fun statusLabel(value: Int): String = when (value) {
    in 0..20 -> "Critical"
    in 21..40 -> "Low"
    in 41..60 -> "Normal"
    in 61..80 -> "Good"
    else -> "Excellent"
}

private fun statusColor(value: Int): Color = when (value) {
    in 0..20 -> Color(0xFFD44E4E)
    in 21..40 -> Color(0xFFE88A4A)
    in 41..60 -> Color(0xFF8B7D68)
    in 61..80 -> Color(0xFF4F8D76)
    else -> Color(0xFF4D6BC7)
}

private fun statusDescription(score: Int): String = when (score) {
    in 80..100 -> "All needs are in great shape"
    in 60..79 -> "ALF is doing well"
    in 40..59 -> "A little care would help"
    in 20..39 -> "ALF needs some attention"
    else -> "Give ALF some care"
}

@Composable
private fun ActionsCard(
    onFeed: () -> Unit,
    onPlay: () -> Unit,
    onSleep: () -> Unit,
    onClean: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Care actions",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("FEED", Icons.Filled.Fastfood, Color(0xFFFFF0E5), HungerColor, onFeed, Modifier.weight(1f))
                ActionButton("PLAY", Icons.Filled.PlayArrow, Color(0xFFFFEEF5), HappinessColor, onPlay, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("SLEEP", Icons.Filled.Bedtime, Color(0xFFEEF2FF), EnergyColor, onSleep, Modifier.weight(1f))
                ActionButton("CLEAN", Icons.Filled.CleaningServices, Color(0xFFEAF7F2), CleanColor, onClean, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    Button(
        onClick = {
            onClick()
            scope.launch {
                scale.snapTo(0.96f)
                scale.animateTo(1f, tween(130, easing = FastOutSlowInEasing))
            }
        },
        modifier = modifier
            .height(62.dp)
            .scale(scale.value),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = TextPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun MessageCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.WaterDrop,
                contentDescription = "Activity",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(message, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LevelUpCard(level: Int) {
    Card(
        modifier = Modifier
            .padding(24.dp)
            .shadow(18.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFEEF0FF),
                modifier = Modifier.size(62.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Level up",
                        tint = Primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("LEVEL UP", color = Primary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(4.dp))
            Text("Level $level", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.height(4.dp))
            Text("ALF is growing stronger.", color = Muted, fontSize = 12.sp)
        }
    }
}

