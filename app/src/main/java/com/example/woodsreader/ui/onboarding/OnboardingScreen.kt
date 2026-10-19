package com.example.woodsreader.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.woodsreader.ui.theme.HiddenForest
import kotlinx.coroutines.launch

data class OnboardingPageData(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val glowColor: Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onOnboardingFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val pages = remember {
        listOf(
            OnboardingPageData(
                title = "Tu Biblioteca Inteligente",
                description = "Organiza automáticamente tus novelas EPUB y PDF con portadas HD, colecciones y búsqueda instantánea.",
                icon = Icons.Default.Book,
                glowColor = HiddenForest.Accent
            ),
            OnboardingPageData(
                title = "Voces de Estudio e IA Local",
                description = "Escucha con las voces humanas de Microsoft Edge Neural o 100% sin internet con modelos ultraligeros Sherpa ONNX.",
                icon = Icons.Default.Headset,
                glowColor = HiddenForest.Amber
            ),
            OnboardingPageData(
                title = "Traducción Contextual y Audiolibros",
                description = "Traduce y define términos tocando el texto, o exporta capítulos enteros a audio comprimido .M4A.",
                icon = Icons.Default.Translate,
                glowColor = Color(0xFF4DD0E1)
            ),
            OnboardingPageData(
                title = "Estadísticas y Bóveda Segura",
                description = "Mide tu racha diaria, velocidad de lectura y protege tu progreso con copias de seguridad completas.",
                icon = Icons.Default.Security,
                glowColor = Color(0xFF81C784)
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.size - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HiddenForest.Background)
    ) {
        // Ambient background glow from page's specific color
        val animatedGlowColor by animateColorAsState(
            targetValue = pages[pagerState.currentPage].glowColor.copy(alpha = 0.12f),
            animationSpec = tween(700),
            label = "BgGlow"
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(380.dp)
                .blur(90.dp)
                .clip(CircleShape)
                .background(animatedGlowColor)
        )

        // Saltar / Skip button (top right)
        Surface(
            onClick = {
                viewModel.completeOnboarding(onOnboardingFinished)
            },
            shape = RoundedCornerShape(20.dp),
            color = HiddenForest.Surface.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 20.dp)
                .heightIn(min = 40.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Saltar",
                    color = HiddenForest.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)
        ) { pageIndex ->
            val page = pages[pageIndex]

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Modern Multi-layer Hero Illustration
                OnboardingHeroIllustration(pageIndex = pageIndex)

                Spacer(modifier = Modifier.height(36.dp))

                // Title
                Text(
                    text = page.title,
                    color = HiddenForest.Text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                Text(
                    text = page.description,
                    color = HiddenForest.TextSecondary,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        // Bottom Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            // Page Indicators
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index, _ ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "IndicatorWidth"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isSelected) HiddenForest.Accent else HiddenForest.OutlineHi,
                        animationSpec = tween(300),
                        label = "IndicatorColor"
                    )

                    Box(
                        modifier = Modifier
                            .size(width = width, height = 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // Next / Finish Button
            Button(
                onClick = {
                    if (isLastPage) {
                        viewModel.completeOnboarding(onOnboardingFinished)
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(50.dp)
                    .widthIn(min = 148.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HiddenForest.Accent,
                    contentColor = HiddenForest.OnAccent
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp
                )
            ) {
                AnimatedContent(
                    targetState = isLastPage,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "ButtonText"
                ) { last ->
                    Text(
                        text = if (last) "Comenzar" else "Siguiente",
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingHeroIllustration(pageIndex: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroFloat")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FloatOffset"
    )

    Box(
        modifier = Modifier
            .size(width = 280.dp, height = 205.dp)
            .offset(y = floatOffset.dp),
        contentAlignment = Alignment.Center
    ) {
        when (pageIndex) {
            0 -> LibraryHeroIllustration(infiniteTransition)
            1 -> AudioHeroIllustration(infiniteTransition)
            2 -> ToolsHeroIllustration(infiniteTransition)
            else -> StatsVaultHeroIllustration(infiniteTransition)
        }
    }
}

@Composable
private fun LibraryHeroIllustration(transition: InfiniteTransition) {
    val pulseGlow by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutQuad), RepeatMode.Reverse),
        label = "pulseGlow"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Ambient Radial Glow
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            HiddenForest.Accent.copy(alpha = pulseGlow),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Left Book (Rotated -14 deg)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F261A),
            border = BorderStroke(1.dp, Color(0xFF234C36)),
            modifier = Modifier
                .size(width = 82.dp, height = 114.dp)
                .offset(x = (-46).dp, y = 14.dp)
                .rotate(-14f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.size(18.dp).background(Color(0xFF1B3D2A), CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF25523A), RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(4.dp).background(Color(0xFF25523A), RoundedCornerShape(2.dp)))
                }
            }
        }

        // Right Book (Rotated +14 deg)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF142E1F),
            border = BorderStroke(1.dp, Color(0xFF29563D)),
            modifier = Modifier
                .size(width = 82.dp, height = 114.dp)
                .offset(x = 46.dp, y = 14.dp)
                .rotate(14f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.size(18.dp).background(Color(0xFF1E452E), CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF285C3E), RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.5f).height(4.dp).background(Color(0xFF285C3E), RoundedCornerShape(2.dp)))
                }
            }
        }

        // Center Main Book Card (Liquid Glass)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF132B1D).copy(alpha = 0.95f),
            border = BorderStroke(
                1.5.dp,
                Brush.verticalGradient(listOf(Color.White.copy(0.45f), HiddenForest.Accent.copy(0.65f)))
            ),
            shadowElevation = 12.dp,
            modifier = Modifier
                .size(width = 106.dp, height = 138.dp)
                .offset(y = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = HiddenForest.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("HD", color = HiddenForest.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(Color.White.copy(0.75f), RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.7f).height(5.dp).background(Color.White.copy(0.45f), RoundedCornerShape(2.dp)))
                }

                // Mini progress bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("LEÍDO", color = HiddenForest.TextLow, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("68%", color = HiddenForest.Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(0.15f), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .fillMaxHeight()
                                .background(HiddenForest.Accent, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        // Floating pill: EPUB • PDF
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF08150D).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.55f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-14).dp, y = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).background(HiddenForest.Accent, CircleShape))
                Text(
                    text = "EPUB • PDF",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Sparkle icon top-left
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = HiddenForest.Amber,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 18.dp, y = 12.dp)
                .size(18.dp)
        )
    }
}

@Composable
private fun AudioHeroIllustration(transition: InfiniteTransition) {
    val wave1 by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(550, easing = EaseInOutSine), RepeatMode.Reverse), label = "w1")
    val wave2 by transition.animateFloat(0.55f, 0.9f, infiniteRepeatable(tween(700, 100, easing = EaseInOutSine), RepeatMode.Reverse), label = "w2")
    val wave3 by transition.animateFloat(0.25f, 0.85f, infiniteRepeatable(tween(480, 50, easing = EaseInOutSine), RepeatMode.Reverse), label = "w3")
    val wave4 by transition.animateFloat(0.5f, 1f, infiniteRepeatable(tween(620, 150, easing = EaseInOutSine), RepeatMode.Reverse), label = "w4")

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Radial Glow
        Box(
            modifier = Modifier
                .size(210.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            HiddenForest.Amber.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Waveform Bars - Left Side
        Row(
            modifier = Modifier
                .offset(x = (-76).dp)
                .height(70.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight(wave3).background(Brush.verticalGradient(listOf(HiddenForest.Accent, HiddenForest.Amber)), RoundedCornerShape(3.dp)))
            Box(modifier = Modifier.width(5.dp).fillMaxHeight(wave1).background(Brush.verticalGradient(listOf(HiddenForest.Amber, HiddenForest.Accent)), RoundedCornerShape(3.dp)))
            Box(modifier = Modifier.width(5.dp).fillMaxHeight(wave2).background(Brush.verticalGradient(listOf(HiddenForest.Accent, Color(0xFF00E676))), RoundedCornerShape(3.dp)))
        }

        // Waveform Bars - Right Side
        Row(
            modifier = Modifier
                .offset(x = 76.dp)
                .height(70.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight(wave2).background(Brush.verticalGradient(listOf(Color(0xFF00E676), HiddenForest.Accent)), RoundedCornerShape(3.dp)))
            Box(modifier = Modifier.width(5.dp).fillMaxHeight(wave4).background(Brush.verticalGradient(listOf(HiddenForest.Amber, HiddenForest.Accent)), RoundedCornerShape(3.dp)))
            Box(modifier = Modifier.width(5.dp).fillMaxHeight(wave1).background(Brush.verticalGradient(listOf(HiddenForest.Accent, HiddenForest.Amber)), RoundedCornerShape(3.dp)))
        }

        // Studio Core Pod
        Surface(
            shape = CircleShape,
            color = Color(0xFF18150D).copy(alpha = 0.95f),
            border = BorderStroke(2.dp, Brush.verticalGradient(listOf(HiddenForest.Amber, Color(0xFF4CAF7D)))),
            shadowElevation = 12.dp,
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(HiddenForest.Amber.copy(alpha = 0.12f), CircleShape)
                )
                Icon(
                    imageVector = Icons.Default.Headset,
                    contentDescription = null,
                    tint = HiddenForest.Amber,
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        // Floating Pill: ☁️ Azure Neural HD
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0B140E).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, HiddenForest.Amber.copy(alpha = 0.6f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-8).dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("☁️", fontSize = 11.sp)
                Text(
                    text = "Azure Neural HD",
                    color = HiddenForest.Amber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ToolsHeroIllustration(transition: InfiniteTransition) {
    val highlightSweep by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "highlightSweep"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Cyan/Teal Radial Glow
        Box(
            modifier = Modifier
                .size(210.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4DD0E1).copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Reading Text Card with Underline/Highlight
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0E1F1A).copy(alpha = 0.95f),
            border = BorderStroke(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(0.35f), Color(0xFF2E6354)))),
            shadowElevation = 10.dp,
            modifier = Modifier
                .size(width = 175.dp, height = 115.dp)
                .offset(y = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(0.9f).height(6.dp).background(Color.White.copy(0.35f), RoundedCornerShape(3.dp)))

                // Highlighted Text Line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(HiddenForest.Accent.copy(alpha = highlightSweep))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(modifier = Modifier.fillMaxWidth(0.7f).height(5.dp).background(Color.White, RoundedCornerShape(2.dp)))
                }

                Box(modifier = Modifier.fillMaxWidth(0.6f).height(6.dp).background(Color.White.copy(0.25f), RoundedCornerShape(3.dp)))
            }
        }

        // Floating Translation Bubble: EN → ES
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF07191B).copy(alpha = 0.95f),
            border = BorderStroke(1.5.dp, Color(0xFF4DD0E1)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 18.dp, y = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = Color(0xFF4DD0E1),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "EN → ES",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Floating Audio Export Pill: .M4A Audio
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF142416).copy(alpha = 0.95f),
            border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.6f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-12).dp, y = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = HiddenForest.Accent,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = ".M4A Audio",
                    color = HiddenForest.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatsVaultHeroIllustration(transition: InfiniteTransition) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Emerald/Orange Radial Glow
        Box(
            modifier = Modifier
                .size(210.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            HiddenForest.Accent.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Progress Ring Container
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(112.dp)
        ) {
            // Track circle
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .border(7.dp, Color(0xFF183324), CircleShape)
            )

            // Progress Arc Indicator at 85%
            CircularProgressIndicator(
                progress = { 0.85f },
                strokeWidth = 7.dp,
                color = HiddenForest.Accent,
                trackColor = Color.Transparent,
                modifier = Modifier.size(104.dp)
            )

            // Center Content: 85% and small label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "85%",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "META",
                    color = HiddenForest.Accent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Floating Streak Badge: 🔥 Racha Activa
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF221308).copy(alpha = 0.95f),
            border = BorderStroke(1.5.dp, Color(0xFFFF6D00).copy(alpha = 0.7f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-10).dp, y = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = Color(0xFFFF6D00),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Racha Activa",
                    color = Color(0xFFFFB74D),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Floating Vault Shield Badge: 🛡️ Bóveda Segura
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0D2116).copy(alpha = 0.95f),
            border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.6f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 10.dp, y = (-2).dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = HiddenForest.Accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Bóveda Segura",
                    color = HiddenForest.Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
