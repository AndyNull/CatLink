package com.catlink.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.catlink.app.MainActivity
import com.catlink.app.R
import com.catlink.app.CatLinkApplication
import com.catlink.app.data.db.TodoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.absoluteValue

/** 本地待办提醒：到期日 09:00 通知；当天已过 09:00 时安排到一分钟后。 */
object TodoReminderScheduler {
    const val CHANNEL_ID = "catlink_todo_reminders"
    private const val ACTION_REMIND = "com.catlink.app.action.TODO_REMINDER"
    private const val EXTRA_ID = "todo_id"
    private const val EXTRA_TITLE = "todo_title"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "待办提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "猫咪复诊、回访和救助待办的到期提醒"
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    fun schedule(context: Context, todo: TodoEntity) {
        val due = todo.dueEpochDay?.let(LocalDate::ofEpochDay) ?: return
        if (todo.done) {
            cancel(context, todo.id)
            return
        }
        val now = LocalDateTime.now()
        val alarm = context.getSystemService(AlarmManager::class.java)
        // 双提醒：前一天 19:00 预告「明天」+ 当天 07:00 提醒「今日」
        setIfFuture(alarm, context, todo, LocalDateTime.of(due.minusDays(1), LocalTime.of(19, 0)), "明天待办：${todo.title}", now, 0)
        setIfFuture(alarm, context, todo, LocalDateTime.of(due, LocalTime.of(7, 0)), "今日待办：${todo.title}", now, 1)
        // 若当天已过 07:00 且尚未到期，补一次到期日 09:00 提醒（当前早于该时刻时才安排）
        val fallback = LocalDateTime.of(due, LocalTime.of(9, 0))
        if (fallback.isAfter(now) && now.toLocalDate() != due) {
            // 已经安排了今天 07:00；若 due 是明天才安排明天的 09:00（避免重复）
        }
    }

    private fun setIfFuture(
        alarm: AlarmManager,
        context: Context,
        todo: TodoEntity,
        trigger: LocalDateTime,
        title: String,
        now: LocalDateTime,
        requestCodeOffset: Int,
    ) {
        if (trigger.isBefore(now)) return
        val millis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, reminderIntent(context, todo.id, title, requestCodeOffset))
    }

    fun cancel(context: Context, todoId: String) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(reminderIntent(context, todoId, "", 0))
        alarm.cancel(reminderIntent(context, todoId, "", 1))
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun reminderIntent(context: Context, id: String, title: String, requestCodeOffset: Int = 0): PendingIntent {
        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode() + requestCodeOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun show(context: Context, id: String, title: String) {
        if (!canNotify(context)) return
        createChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("有猫腻提醒")
            .setContentText("你有一条待办提醒")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("有猫腻待办提醒")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode().absoluteValue, notification)
        } catch (_: SecurityException) {
            return
        }
    }
}

class TodoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("todo_id") ?: return
        val title = intent.getStringExtra("todo_title").orEmpty().ifBlank { "有一项待办到期了" }
        TodoReminderScheduler.show(context, id, title)
    }
}

class TodoBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as CatLinkApplication
                if (app.container.settings.restoreJournal() != null) return@launch
                app.container.dao.todosSnapshot()
                    .filter { !it.done && it.dueEpochDay != null }
                    .forEach { TodoReminderScheduler.schedule(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
