package com.journai.journai

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import java.util.concurrent.TimeUnit
import com.journai.journai.work.IndexWorker

@HiltAndroidApp
class JournAIApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleIndexer()
    }

    private fun scheduleIndexer() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .build()

        val work = PeriodicWorkRequestBuilder<IndexWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag("indexer")
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "journai-indexer",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }
}
