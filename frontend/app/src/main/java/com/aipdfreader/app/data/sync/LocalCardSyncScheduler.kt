package com.aipdfreader.app.data.sync

import android.content.Context
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import com.aipdfreader.app.util.BackendConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCardSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun enqueue() {
        if (!BackendConfiguration.isConfigured) return
        if (LocalCardSyncRuntime.requestWhileRunning()) return
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, LocalCardSyncJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        scheduler.schedule(job)
    }

    companion object {
        const val JOB_ID = 31_426
    }
}

/** Coalesces edits made while the background job is already uploading. */
object LocalCardSyncRuntime {
    private var running = false
    private var requestedAgain = false

    @Synchronized fun onStarted() { running = true }

    @Synchronized fun requestWhileRunning(): Boolean {
        if (!running) return false
        requestedAgain = true
        return true
    }

    @Synchronized fun onFinished(): Boolean {
        running = false
        return requestedAgain.also { requestedAgain = false }
    }
}
