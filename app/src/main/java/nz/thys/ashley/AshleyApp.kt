package nz.thys.ashley

import android.app.Application
import nz.thys.ashley.config.ConfigStore
import nz.thys.ashley.config.Paths
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AshleyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** App-wide singletons. Files live in app-specific external storage so models can be copied in over USB. */
class AppContainer(app: Application) {
    /** Android/data/nz.thys.ashley/files  (agents/, models/, voices/, skills/) */
    val paths = Paths(app.getExternalFilesDir(null) ?: app.filesDir, agent = "ashley")
    val store = ConfigStore(app, paths).also { it.installDefaults() }
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
