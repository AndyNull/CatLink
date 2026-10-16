package com.catlink.app.ui.screens.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catlink.app.data.Priority
import com.catlink.app.data.db.TodoEntity
import com.catlink.app.ui.MainViewModel
import com.catlink.app.ui.components.CatDateField
import com.catlink.app.ui.components.CatDialog
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.components.EmptyState
import com.catlink.app.ui.components.SoftCard
import com.catlink.app.ui.components.StatusChip
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 全部待办页：列出所有待办（含已完成），每条只显示标题 */
@Composable
fun TodoListScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenCat: (String) -> Unit,
) {
    val todos by vm.allTodos.collectAsState()
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // 顶部返回栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "全部待办",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            IconButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "添加待办",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (showAddDialog) {
            AddTodoDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { title, due, priority ->
                    scope.launch {
                        vm.repository.addTodo(title, "", due, priority)
                        showAddDialog = false
                    }
                },
            )
        }

        if (todos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    emoji = "🎉",
                    title = "没有待办",
                    subtitle = "好好休息一下吧",
                )
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(50),
                ) {
                    Text("添加待办", style = MaterialTheme.typography.labelMedium)
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(todos.size) { index ->
                val todo = todos[index]
                TodoListItem(
                    todo = todo,
                    onToggle = { scope.launch { vm.repository.toggleTodo(todo.id, !todo.done) } },
                    onDelete = { scope.launch { vm.repository.deleteTodo(todo.id) } },
                    onOpenCat = if (todo.catId != null) ({ onOpenCat(todo.catId!!) }) else null,
                )
            }
        }
    }
}

@Composable
private fun TodoListItem(
    todo: TodoEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onOpenCat: (() -> Unit)?,
) {
    var showMenu by remember { mutableStateOf(false) }
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        onClick = { showMenu = true },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (todo.done) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (todo.done) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "已完成",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (todo.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (todo.priority == Priority.HIGH.name) {
                StatusChip(
                    text = "紧急",
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else if (todo.dueEpochDay != null) {
                Text(
                    text = LocalDate.ofEpochDay(todo.dueEpochDay).format(DateTimeFormatter.ofPattern("M/d")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    if (showMenu) {
        CatDialog(
            onDismissRequest = { showMenu = false },
            title = { Text(if (todo.done) "恢复待办？" else "待办操作") },
            text = { Text(todo.title, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (onOpenCat != null) {
                        TextButton(onClick = { showMenu = false; onOpenCat() }) { Text("查看猫咪档案") }
                    }
                    TextButton(onClick = { showMenu = false; onToggle() }) {
                        Text(if (todo.done) "恢复未完成" else "标记为完成")
                    }
                    TextButton(
                        onClick = { showMenu = false; onDelete() },
                    ) {
                        Text("删除待办", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showMenu = false }) { Text("取消") }
            },
        )
    }
}

/** 手动新增待办：标题 + 日期（可选）+ 优先级 */
@Composable
private fun AddTodoDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, due: LocalDate?, priority: Priority) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    var priority by remember { mutableStateOf(Priority.NORMAL) }

    CatDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加待办") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CatTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题",
                    placeholder = "如：带小虎去复诊",
                    modifier = Modifier.fillMaxWidth(),
                )
                CatDateField(
                    value = due,
                    onDateChange = { due = it },
                    label = "日期（可选）",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { p ->
                        Text(
                            text = p.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (priority == p) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (priority == p) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable { priority = p }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title.trim(), due, priority) },
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
