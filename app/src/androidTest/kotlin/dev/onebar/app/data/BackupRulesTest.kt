package dev.onebar.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.onebar.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class BackupRulesTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `legacy_backup_исключает_персональный_журнал`() {
        assertEquals(listOf(HISTORY_FILE), exclusions(R.xml.backup_rules))
    }

    @Test
    fun `облачный_backup_и_перенос_исключают_персональный_журнал`() {
        assertEquals(listOf(HISTORY_FILE, HISTORY_FILE), exclusions(R.xml.data_extraction_rules))
    }

    private fun exclusions(resource: Int): List<Pair<String, String>> {
        val parser = context.resources.getXml(resource)
        val found = mutableListOf<Pair<String, String>>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                found += parser.getAttributeValue(null, "domain") to parser.getAttributeValue(null, "path")
            }
            parser.next()
        }
        return found
    }

    private companion object {
        val HISTORY_FILE = "file" to "datastore/history.preferences_pb"
    }
}
