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

// Основная палитра OneBar: seed Green #006200, разложенный по ролям Material 3
// (схема Tonal Spot, HCT). Точный #006200 закреплён за primary светлой темы, inversePrimary
// тёмной и фоном знака: у него HCT-тон ~35.47, а не стандартные 40. Остальные роли пересчитаны
// @material/material-color-utilities из того же seed; нейтрали (chroma 6) и neutralVariant
// (chroma 14) подмешаны в surface и outline. Менять seed и пересчитывать роли целиком.

// Светлая тема

val primaryLight = Color(0xFF006200)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFC0EFB0)
val onPrimaryContainerLight = Color(0xFF285020)
val secondaryLight = Color(0xFF54634D)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFD7E8CD)
val onSecondaryContainerLight = Color(0xFF3C4B37)
val tertiaryLight = Color(0xFF386568)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFBCEBEE)
val onTertiaryContainerLight = Color(0xFF1E4D50)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF93000A)
val backgroundLight = Color(0xFFF8FBF1)
val onBackgroundLight = Color(0xFF191D17)
val surfaceLight = Color(0xFFF8FBF1)
val onSurfaceLight = Color(0xFF191D17)
val surfaceVariantLight = Color(0xFFDFE4D7)
val onSurfaceVariantLight = Color(0xFF43483F)
val outlineLight = Color(0xFF73796E)
val outlineVariantLight = Color(0xFFC3C8BC)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF2E322B)
val inverseOnSurfaceLight = Color(0xFFEFF2E8)
val inversePrimaryLight = Color(0xFFA5D396)
val surfaceDimLight = Color(0xFFD8DBD2)
val surfaceBrightLight = Color(0xFFF8FBF1)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF2F5EB)
val surfaceContainerLight = Color(0xFFECEFE5)
val surfaceContainerHighLight = Color(0xFFE6E9E0)
val surfaceContainerHighestLight = Color(0xFFE1E4DA)

// Тёмная тема

val primaryDark = Color(0xFFA5D396)
val onPrimaryDark = Color(0xFF11380B)
val primaryContainerDark = Color(0xFF285020)
val onPrimaryContainerDark = Color(0xFFC0EFB0)
val secondaryDark = Color(0xFFBBCBB2)
val onSecondaryDark = Color(0xFF263422)
val secondaryContainerDark = Color(0xFF3C4B37)
val onSecondaryContainerDark = Color(0xFFD7E8CD)
val tertiaryDark = Color(0xFFA0CFD2)
val onTertiaryDark = Color(0xFF003739)
val tertiaryContainerDark = Color(0xFF1E4D50)
val onTertiaryContainerDark = Color(0xFFBCEBEE)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF11140F)
val onBackgroundDark = Color(0xFFE1E4DA)
val surfaceDark = Color(0xFF11140F)
val onSurfaceDark = Color(0xFFE1E4DA)
val surfaceVariantDark = Color(0xFF43483F)
val onSurfaceVariantDark = Color(0xFFC3C8BC)
val outlineDark = Color(0xFF8D9387)
val outlineVariantDark = Color(0xFF43483F)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFE1E4DA)
val inverseOnSurfaceDark = Color(0xFF2E322B)
val inversePrimaryDark = Color(0xFF006200)
val surfaceDimDark = Color(0xFF11140F)
val surfaceBrightDark = Color(0xFF363A34)
val surfaceContainerLowestDark = Color(0xFF0B0F0A)
val surfaceContainerLowDark = Color(0xFF191D17)
val surfaceContainerDark = Color(0xFF1D211B)
val surfaceContainerHighDark = Color(0xFF272B25)
val surfaceContainerHighestDark = Color(0xFF32362F)

// Фирменный знак: тема иконку запуска не перекрашивает, фон всегда точный brand #006200.

val launcherBackground = Color(0xFF006200)
val launcherForeground = Color(0xFFFFFFFF)

// Фирменные цвета поисковиков сохранены отдельно от палитры OneBar.

val yandexBadgeBackground = Color(0xFFFC3F1D)
val yandexBadgeContent = Color(0xFFFFFFFF)
val googleBadgeBackground = Color(0xFFF1F3F4)
val googleBadgeContent = Color(0xFF4285F4)
