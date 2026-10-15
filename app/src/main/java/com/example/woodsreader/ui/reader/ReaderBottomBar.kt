package com.example.woodsreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.ui.theme.HiddenForest

@Composable
fun ReaderBottomBar(
    activeTab: ReaderTab,
    onTabSelected: (ReaderTab) -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A140F).copy(alpha = 0.92f),
                        Color(0xFF0A140F).copy(alpha = 0.86f)
                    )
                )
            )
            .navigationBarsPadding(),
        shadowElevation = 16.dp
    ) {
        Column {
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.18f),
                thickness = 1.dp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderTab.values().forEach { tab ->
                    val isActive = activeTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onTabSelected(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Active indicator dot/dash
                            Box(
                                modifier = Modifier
                                    .width(if (isActive) 24.dp else 0.dp)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(HiddenForest.Accent)
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isActive) HiddenForest.Accent else HiddenForest.TextLow,
                                modifier = Modifier.size(22.dp)
                            )
                            
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isActive) HiddenForest.Accent else HiddenForest.TextLow
                            )
                        }
                    }
                }
            }
        }
    }
}
