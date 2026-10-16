package com.catlink.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catlink.app.VaultViewModel
import com.catlink.app.data.HomeStats
import com.catlink.app.data.db.CatEntity
import com.catlink.app.data.db.TodoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** 应用级 ViewModel：猫咪/统计/待办实时数据 */
class MainViewModel(app: Application) : VaultViewModel(app) {

    val cats: StateFlow<List<CatEntity>> = repository.observeCats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<HomeStats> = repository.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeStats())

    val todos: StateFlow<List<TodoEntity>> = repository.observeActiveTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTodos: StateFlow<List<TodoEntity>> = repository.observeTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
