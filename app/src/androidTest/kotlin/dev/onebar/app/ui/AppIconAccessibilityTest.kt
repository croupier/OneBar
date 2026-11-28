package dev.onebar.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.onebar.app.ui.theme.OneBarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconAccessibilityTest {

    @get:Rule
    val ui = createComposeRule()

    @Test
    fun `заглушка_иконки_сохраняет_описание_для_доступности`() {
        val label = "Тестовый мессенджер"
        ui.setContent {
            OneBarTheme {
                AppIcon(
                    packageName = "dev.onebar.app.missing",
                    contentDescription = label,
                )
            }
        }

        ui.onNodeWithContentDescription(label).assertExists()
    }
}
