package com.catlink.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 全局统一输入框：
 * - label 为固定顶部小字（不浮动、不出现背景色块）
 * - 容器色聚焦/未聚焦统一（不再点击变白/变小）
 * - 聚焦只改边框色
 * - 支持前/后缀图标（后缀可点击，如日历）
 * - 支持只读、多行
 */
@Composable
fun CatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    isError: Boolean = false,
    onTrailingClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
            )
        }
        // readOnly + onClick：整个字段可点击（下拉选择等场景），
        // clickable 放在 TextField 外层 Box，避免被输入框内部手势拦截
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (readOnly && onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                ),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    // 54dp：M3 垂直 padding 16dp×2 后内容区 66px，容纳 14sp/20sp 行高（60px）不裁剪
                    .then(if (singleLine && minLines <= 1) Modifier.height(54.dp) else Modifier),
                placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)) } },
            singleLine = singleLine,
            minLines = minLines,
            readOnly = readOnly,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(),
            leadingIcon = leadingIcon?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
            trailingIcon = when {
                trailingContent != null -> trailingContent
                trailingIcon != null -> {
                    {
                        if (onTrailingClick != null) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onTrailingClick)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = trailingIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        } else {
                            Icon(
                                imageVector = trailingIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                else -> null
            },
            colors = catFieldColors(),
            )
        }
    }
}

/** 密文输入（PIN 等） */
@Composable
fun CatPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.NumberPassword,
) {
    CatTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        keyboardType = keyboardType,
        imeAction = ImeAction.Done,
    )
}

/**
 * 日期选择字段：点击日历图标弹出日期选择器（替代手输文本）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatDateField(
    value: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("yyyy年M月d日")
    val display = value?.format(fmt) ?: ""

    CatTextField(
        value = display,
        onValueChange = {},
        label = label,
        placeholder = "选择日期",
        readOnly = true,
        trailingIcon = Icons.Outlined.CalendarMonth,
        onTrailingClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth(),
    )

    if (showPicker) {
        val initialMillis = value
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            initialDisplayMode = DisplayMode.Input,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateChange(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(
                state = pickerState,
                modifier = Modifier.width(320.dp),
            )
        }
    }
}

@Composable
fun catFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    // 容器色统一：聚焦/未聚焦都用白色表面，不再点击变白/变小
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    cursorColor = MaterialTheme.colorScheme.primary,
    errorBorderColor = MaterialTheme.colorScheme.error,
    errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
)
