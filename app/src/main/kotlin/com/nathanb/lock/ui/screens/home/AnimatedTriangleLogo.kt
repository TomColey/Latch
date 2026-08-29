package com.nathanb.lock.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.R

private val LatchDevOrange = Color(0xFFFF7A00)

/**
 * Temporary Latch development identity.
 *
 * The original Lock animated triangle is deliberately replaced during the early fork so a
 * development build is immediately distinguishable from the production Lock app on a real
 * device. The full home-screen treatment will be rebuilt later as part of the Latch UI phase.
 */
@Composable
internal fun AnimatedTriangleLogo(
    isLocked: Boolean,
    iconScale: Float,
    onVisualLockedChange: (Boolean) -> Unit,
    fillProgress: Float = 0f,
    accentColor: Color? = null,
) {
    // Keep HomeScreen's visual state in sync while the original logo animation is temporarily
    // absent. The unused parameters remain in the signature to avoid changing existing callers.
    LaunchedEffect(isLocked) {
        onVisualLockedChange(isLocked)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(190.dp)
            .scale(iconScale),
    ) {
        Spacer(Modifier.height(55.dp))

        Image(
            painter = painterResource(R.drawable.ic_latch_logo),
            contentDescription = "Latch",
            modifier = Modifier
                .width(180.dp)
                .height(43.dp),
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "DEV BUILD ${BuildConfig.VERSION_NAME}",
            color = LatchDevOrange,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
    }
}
