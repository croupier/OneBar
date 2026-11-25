package dev.onebar.app

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackIntentTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `письмо_содержит_получателя_тему_сообщение_и_обезличенный_дебаг`() {
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        val intent = feedbackIntent(context, "  Нужна помощь с приложением  ")
        val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:onebar@3406.ru", intent.data.toString())
        assertArrayEquals(arrayOf("onebar@3406.ru"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("Приложение OneBar - обратная связь", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(body.startsWith(context.getString(R.string.settings_feedback_notice)))
        assertTrue(body.contains("Нужна помощь с приложением"))
        assertTrue(body.contains("OneBar ${versionName.ifBlank { "—" }}"))
        assertTrue(body.contains("${Build.MANUFACTURER} ${Build.MODEL}"))
        assertTrue(body.contains("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
    }

    @Test
    fun `пустое_сообщение_не_добавляет_лишнюю_строку`() {
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        val debug = context.getString(
            R.string.settings_debug,
            versionName.ifBlank { "—" },
            Build.MANUFACTURER,
            Build.MODEL,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
        )

        val body = feedbackIntent(context, "   ").getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        assertEquals("${context.getString(R.string.settings_feedback_notice)}\n\n$debug", body)
    }
}
