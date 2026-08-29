package com.nathanb.lock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

// Temporary development typography.
// The original Lock project uses the Satoshi font, but those font files are not
// redistributable with the repository. Keep the existing SatoshiFamily symbol so
// inherited UI components continue to compile, but map it to the system default font.
val SatoshiFamily: FontFamily = FontFamily.Default

val SatoshiTypography = Typography()
