package com.catlink.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.catlink.app.data.AppContainer
import com.catlink.app.data.SettingsStore
import com.catlink.app.data.SeedData
import com.catlink.app.data.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CatLinkApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 首次启动写入演示档案（异步，不阻塞启动）
        CoroutineScope(Dispatchers.IO).launch {
            if (!container.settings.seeded.first()) {
                SeedData.seed(container.repository, container.dao)
                container.settings.markSeeded()
            }
            // 回收站过期清理（保留期 30 天）
            container.dao.purgeExpiredTrash(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
        }
    }
}

/** 全局可用的 ViewModel 基类（提供 repository 与 settings） */
open class VaultViewModel(app: Application) : AndroidViewModel(app) {
    val repository: VaultRepository get() = getApplication<CatLinkApplication>().container.repository
    val settings: SettingsStore get() = getApplication<CatLinkApplication>().container.settings
}
