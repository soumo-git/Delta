package com.soumo.child.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.soumo.child.BackgroundService

class PersistentJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Log.d("PersistentJob", "Job started")

        // Start BackgroundService if not running
        try {
            BackgroundService.startService(this)
            Log.d("PersistentJob", "BackgroundService started from JobService")
        } catch (e: Exception) {
            Log.e("PersistentJob", "Failed to start BackgroundService", e)
        }

        // No need to reschedule - job is already periodic
        return false // Job is finished
    }

    override fun onStopJob(params: JobParameters): Boolean {
        Log.d("PersistentJob", "Job stopped")
        return true // Reschedule job
    }

    companion object {
        private const val JOB_ID = 1001

        fun scheduleJob(context: Context) {
            val jobScheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            val jobInfo = JobInfo.Builder(JOB_ID,
                ComponentName(context, PersistentJobService::class.java)
            )
                .setPersisted(true) // Survive reboot
                .setPeriodic(15 * 60 * 1000) // 15 minutes minimum
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()

            val result = jobScheduler.schedule(jobInfo)
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d("PersistentJob", "Job scheduled successfully")
            } else {
                Log.e("PersistentJob", "Failed to schedule job")
            }
        }

    }
}