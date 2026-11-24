package dev.onebar.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityOptionsCompat
import dev.onebar.app.ui.SearchScreen
import dev.onebar.app.ui.theme.OneBarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            OneBarTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SearchScreen(
                        onOpenSettings = {
                            val options = ActivityOptionsCompat.makeCustomAnimation(
                                this,
                                R.anim.slide_in_left,
                                R.anim.slide_out_right,
                            )
                            startActivity(Intent(this, SettingsActivity::class.java), options.toBundle())
                        },
                    )
                }
            }
        }
    }
}
