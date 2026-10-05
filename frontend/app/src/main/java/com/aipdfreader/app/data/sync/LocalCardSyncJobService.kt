package com.aipdfreader.app.data.sync

import android.app.job.JobParameters
import android.app.job.JobService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class LocalCardSyncJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeJob: Job? = null
    private var stopped = AtomicBoolean(false)

    override fun onStartJob(params: JobParameters): Boolean {
        LocalCardSyncRuntime.onStarted()
        stopped = AtomicBoolean(false)
        val runner = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncDependencies::class.java
        ).syncRunner()
        activeJob = serviceScope.launch(Dispatchers.IO) {
            val retry = try {
                runner.run()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                true
            }
            withContext(Dispatchers.Main.immediate) {
                if (stopped.compareAndSet(false, true)) {
                    val runAgain = LocalCardSyncRuntime.onFinished()
                    jobFinished(params, retry)
                    if (runAgain && !retry) LocalCardSyncScheduler(applicationContext).enqueue()
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped.set(true)
        activeJob?.cancel()
        LocalCardSyncRuntime.onFinished()
        return true
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncDependencies {
        fun syncRunner(): LocalCardSyncRunner
    }
}
