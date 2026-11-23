package rocks.dearanayji.gloss

import android.app.Application
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
}
