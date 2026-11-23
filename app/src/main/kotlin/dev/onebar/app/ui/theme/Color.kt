/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.onebar.app.ui.theme

import androidx.compose.ui.graphics.Color

// Основная палитра OneBar: seed Tailwind teal-600 #0D9488, разложенный по ролям Material 3
// (схема Tonal Spot, HCT). Значения подобраны не на глаз — они пересчитаны
// @material/material-color-utilities: brand тон 40 даёт primary в светлой теме, тон 80 в
// тёмной, нейтрали (chroma 6) и neutralVariant (chroma 14) подмешаны в surface и outline,
// поэтому весь UI держит WCAG AA (primary/surface 6.18, текст 16.31, вторичный 8.87).
// Правильно менять seed и пересчитывать роли целиком, а не отдельный цвет.

// Светлая тема

val primaryLight = Color(0xFF006A61)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFF9EF2E6)
val onPrimaryContainerLight = Color(0xFF005049)
val secondaryLight = Color(0xFF4A635F)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFCCE8E3)
val onSecondaryContainerLight = Color(0xFF324B48)
val tertiaryLight = Color(0xFF46617A)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFCDE5FF)
val onTertiaryContainerLight = Color(0xFF2E4961)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF93000A)
val backgroundLight = Color(0xFFF4FBF8)
val onBackgroundLight = Color(0xFF161D1C)
val surfaceLight = Color(0xFFF4FBF8)
val onSurfaceLight = Color(0xFF161D1C)
val surfaceVariantLight = Color(0xFFDAE5E2)
val onSurfaceVariantLight = Color(0xFF3F4947)
val outlineLight = Color(0xFF6F7977)
val outlineVariantLight = Color(0xFFBEC9C6)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF2B3230)
val inverseOnSurfaceLight = Color(0xFFECF2F0)
val inversePrimaryLight = Color(0xFF82D5CA)
val surfaceDimLight = Color(0xFFD5DBD9)
val surfaceBrightLight = Color(0xFFF4FBF8)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFEFF5F2)
val surfaceContainerLight = Color(0xFFE9EFED)
val surfaceContainerHighLight = Color(0xFFE3EAE7)
val surfaceContainerHighestLight = Color(0xFFDDE4E1)

// Тёмная тема

val primaryDark = Color(0xFF82D5CA)
val onPrimaryDark = Color(0xFF003732)
val primaryContainerDark = Color(0xFF005049)
val onPrimaryContainerDark = Color(0xFF9EF2E6)
val secondaryDark = Color(0xFFB1CCC7)
val onSecondaryDark = Color(0xFF1C3531)
val secondaryContainerDark = Color(0xFF324B48)
val onSecondaryContainerDark = Color(0xFFCCE8E3)
val tertiaryDark = Color(0xFFAECAE6)
val onTertiaryDark = Color(0xFF163349)
val tertiaryContainerDark = Color(0xFF2E4961)
val onTertiaryContainerDark = Color(0xFFCDE5FF)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF0E1513)
val onBackgroundDark = Color(0xFFDDE4E1)
val surfaceDark = Color(0xFF0E1513)
val onSurfaceDark = Color(0xFFDDE4E1)
val surfaceVariantDark = Color(0xFF3F4947)
val onSurfaceVariantDark = Color(0xFFBEC9C6)
val outlineDark = Color(0xFF899390)
val outlineVariantDark = Color(0xFF3F4947)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFDDE4E1)
val inverseOnSurfaceDark = Color(0xFF2B3230)
val inversePrimaryDark = Color(0xFF006A61)
val surfaceDimDark = Color(0xFF0E1513)
val surfaceBrightDark = Color(0xFF343A39)
val surfaceContainerLowestDark = Color(0xFF090F0E)
val surfaceContainerLowDark = Color(0xFF161D1C)
val surfaceContainerDark = Color(0xFF1A2120)
val surfaceContainerHighDark = Color(0xFF252B2A)
val surfaceContainerHighestDark = Color(0xFF303635)

// Фирменный знак: тема иконку запуска не перекрашивает, фон всегда brand тон 40.

val launcherBackground = Color(0xFF006A61)
val launcherForeground = Color(0xFFFFFFFF)
