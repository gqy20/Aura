package com.xiaoqi.companion.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionTopAppBar(
    title: String,
    navigationIcon: ImageVector? = null,
    navigationContentDescription: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    extraActions: @Composable (() -> Unit)? = null,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            if (navigationIcon != null && onNavigationClick != null) {
                IconButton(onClick = onNavigationClick) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = navigationContentDescription,
                    )
                }
            }
        },
        actions = {
            extraActions?.invoke()
            if (actionText != null && onActionClick != null) {
                TextButton(onClick = onActionClick) {
                    Text(actionText)
                }
            }
        },
    )
}
