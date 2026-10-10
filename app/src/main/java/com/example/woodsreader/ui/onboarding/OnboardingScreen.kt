package com.example.woodsreader.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
                title = "Escanea tus libros",
                description = "Busca y organiza automáticamente tus archivos EPUB, PDF y cómics en una biblioteca limpia y hermosa.",
                icon = Icons.Default.Book,
                glowColor = HiddenForest.Accent
            ),
            OnboardingPageData(
                title = "Lee con voz neural",
                description = "Escucha tus libros favoritos con nuestro narrador local offline de voz neural hiperrealista.",
                icon = Icons.Default.Headset,
                glowColor = HiddenForest.Amber
            ),
            OnboardingPageData(
                title = "Rastrea tu progreso",
                description = "Registra tus estadísticas de lectura diarias, palabras procesadas y visualiza tu avance en gráficos integrados.",
                icon = Icons.Default.Star,
                glowColor = HiddenForest.TextLow
            ),
            OnboardingPageData(
                title = "¡Comienza tu viaje!",
                description = "Tu biblioteca personal y tus anotaciones están listas para acompañarte. Que disfrutes de tu lectura.",
                icon = Icons.Default.Check,
                glowColor = HiddenForest.Accent
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
        if (!isLastPage) {
            TextButton(
                onClick = {
                    viewModel.completeOnboarding(onOnboardingFinished)
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Saltar",
                    color = HiddenForest.TextSecondary,
                    fontSize = 15.sp,
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
                // Floating/Pulsing Icon Container
                val infiniteTransition = rememberInfiniteTransition(label = "iconPulse")
                val floatOffset by infiniteTransition.animateFloat(
                    initialValue = -8f,
                    targetValue = 8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2200, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "FloatOffset"
                )

                Box(
                    modifier = Modifier
                        .offset(y = floatOffset.dp)
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(HiddenForest.Surface)
                        .border(1.dp, page.glowColor.copy(alpha = 0.3f), CircleShape)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer glow inside the circle
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(8.dp)
                            .clip(CircleShape)
                            .background(page.glowColor.copy(alpha = 0.15f))
                    )

                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = page.glowColor
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

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
                    .height(48.dp)
                    .width(if (isLastPage) 150.dp else 110.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HiddenForest.Accent,
                    contentColor = HiddenForest.OnAccent
                ),
                shape = RoundedCornerShape(12.dp),
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
                        text = if (last) "Empezar" else "Siguiente",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
