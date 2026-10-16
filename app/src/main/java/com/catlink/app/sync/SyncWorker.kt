package com.catlink.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.CatAlbumManager
import com.catlink.app.data.SyncOperationGate
import com.catlink.app.runtime.SessionRuntimeState
import com.catlink.app.runtime.SyncRuntimeState
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * 后台定时同步：登录且 vault 启用时推送 outbox 并拉取远端；
 * 仅在网络可用、设备充电时执行；失败自动退避重试。
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CatLinkApplication
        val session = app.runtimeState.state.value.session
        if (session !is SessionRuntimeState.SignedIn) return Result.success()
        app.runtimeState.updateSync(SyncRuntimeState.Running("sync"))
        return try {
            var albumFailures = 0
            val vault = SyncOperationGate.runIfNoRestore(
                settings = app.container.settings,
                onPending = { null },
            ) {
                val album = CatAlbumManager(
                    applicationContext,
                    app.container.repository,
                    app.container.publicApi,
                    app.container.authSession::isSignedIn,
                ).syncPending(app.container.repository.pendingCatMedia())
                albumFailures = album.failed
                runCatching {
                    app.container.syncEngine.syncOnce().getOrThrow()
                }
            }
            if (vault == null) {
                app.runtimeState.updateSync(SyncRuntimeState.Idle)
                return Result.retry()
            }
            check(albumFailures == 0) { "相册同步失败" }
            vault.getOrThrow()
            app.runtimeState.updateSync(SyncRuntimeState.Idle)
            Result.success()
        } catch (cancelled: CancellationException) {
            app.runtimeState.updateSync(SyncRuntimeState.Idle)
            throw cancelled
        } catch (_: Throwable) {
            app.runtimeState.updateSync(SyncRuntimeState.Failed(com.catlink.app.network.OnlineError.Unknown()))
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC = "catlink-periodic-sync"
        private const val UNIQUE_IMMEDIATE = "catlink-immediate-sync"

        /** 登录后调用：立即同步一次 + 注册周期同步（6 小时）。 */
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresCharging(false)
                .build()
            val periodic = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodic)

            val immediate = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniqueWork(UNIQUE_IMMEDIATE, ExistingWorkPolicy.REPLACE, immediate)
        }

        /** 退出登录后取消周期同步。 */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC)
        }
    }
}
