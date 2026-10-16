package com.catlink.app.data

import android.content.Context
import com.catlink.app.data.AppContainer
import com.catlink.app.data.db.AppDatabase
import com.catlink.app.data.db.VaultDao
import com.catlink.app.data.VaultRepository

/** 应用级依赖容器（初版单例，后续可替换为 Hilt） */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val dao: VaultDao = database.vaultDao()
    val repository: VaultRepository = VaultRepository(dao)
    val settings: SettingsStore = SettingsStore(context)
}
