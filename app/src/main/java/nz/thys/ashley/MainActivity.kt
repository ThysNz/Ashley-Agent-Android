package nz.thys.ashley

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import nz.thys.ashley.ui.ChatViewModel
import nz.thys.ashley.ui.chat.ChatScreen
import nz.thys.ashley.ui.settings.InfoScreen
import nz.thys.ashley.ui.settings.InspectScreen
import nz.thys.ashley.ui.settings.SettingsAgentScreen
import nz.thys.ashley.ui.settings.SettingsModelsScreen
import nz.thys.ashley.ui.settings.SettingsScreen
import nz.thys.ashley.ui.settings.SettingsVoiceScreen
import nz.thys.ashley.ui.theme.AshleyTheme

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AshleyTheme {
                AppNav(vm)
            }
        }
    }
}

object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val MODELS = "settings/models"
    const val VOICE = "settings/voice"
    const val AGENT = "settings/agent"
    const val INSPECT = "settings/inspect"
    const val INFO = "settings/info/{topic}"
    fun info(topic: String) = "settings/info/$topic"
}

@Composable
fun AppNav(vm: ChatViewModel) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(navController = nav, startDestination = Routes.CHAT) {
        composable(Routes.CHAT) {
            ChatScreen(vm, openSettings = { nav.navigate(Routes.SETTINGS) }, openModels = { nav.navigate(Routes.MODELS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(vm, back = back, go = { nav.navigate(it) })
        }
        composable(Routes.MODELS) { SettingsModelsScreen(vm, back) }
        composable(Routes.VOICE) { SettingsVoiceScreen(vm, back) }
        composable(Routes.AGENT) { SettingsAgentScreen(vm, back) }
        composable(Routes.INSPECT) { InspectScreen(vm, back) }
        composable(Routes.INFO) { entry -> InfoScreen(vm, entry.arguments?.getString("topic") ?: "", back) }
    }
}
