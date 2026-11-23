package rocks.dearanayji.gloss

import android.app.Application
import android.content.ComponentCallbacks2
import rocks.dearanayji.gloss.data.EngineRepository
import rocks.dearanayji.gloss.data.PreferencesStore
import rocks.dearanayji.gloss.model.ModelManager

class GlossApplication : Application() {

    lateinit var preferencesStore: PreferencesStore
        private set

    lateinit var modelManager: ModelManager
        private set

    lateinit var engineRepository: EngineRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesStore = PreferencesStore(this)
        modelManager = ModelManager(this)
        engineRepository = EngineRepository(modelManager)
        engineRepository.refreshInitialState()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!::engineRepository.isInitialized) return
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                engineRepository.evictSessionsAsync(dropTokenizer = true)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                (!engineRepository.isForeground && level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) -> {
                engineRepository.evictSessionsAsync(dropTokenizer = false)
            }
        }
    }
}
