package com.spacebrowser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AddressBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    isEditing: Boolean,
    isSecure: Boolean,
    isPrivateTab: Boolean,
    isLoading: Boolean,
    progress: Int,
    blockedCount: Int,
    shieldActive: Boolean,
    tabCount: Int,
    onShieldClick: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onClearClick: () -> Unit,
    onReloadClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            // Tracker shield ---------------------------------------------------
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .glass(RoundedCornerShape(14.dp), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onShieldClick),
            ) {
                Icon(
                    if (shieldActive) Icons.Filled.Shield else Icons.Outlined.Shield,
                    contentDescription = if (shieldActive) {
                        "Shields on: $blockedCount trackers blocked on this page"
                    } else {
                        "Shields off"
                    },
                    tint = if (shieldActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                if (shieldActive && blockedCount > 0) {
                    Text(
                        text = if (blockedCount > 99) "99+" else blockedCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .glass(RoundedCornerShape(8.dp), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary, alpha = 1f)
                            .padding(horizontal = 3.dp),
                    )
                }
            }

            // Address pill -----------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(44.dp)
                    .glass(shape, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary),
            ) {
                val leadIcon = when {
                    isEditing -> Icons.Filled.Search
                    isSecure -> Icons.Filled.Lock
                    else -> Icons.Filled.LockOpen
                }
                Icon(
                    leadIcon,
                    contentDescription = if (isSecure) "Secure connection" else "Not secure",
                    tint = when {
                        isEditing -> MaterialTheme.colorScheme.onSurfaceVariant
                        isSecure -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier
                        .padding(start = 14.dp, end = 8.dp)
                        .size(16.dp),
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { onSubmit(value.text) }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.text.isEmpty()) {
                                Text(
                                    text = if (isPrivateTab) "Search privately" else "Search or type URL",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusChanged(it.isFocused) },
                )
                if (isEditing && value.text.isNotEmpty()) {
                    IconButton(onClick = onClearClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else if (!isEditing && value.text.isNotEmpty()) {
                    IconButton(
                        onClick = { if (isLoading) onStopClick() else onReloadClick() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            if (isLoading) Icons.Filled.Stop else Icons.Filled.Refresh,
                            contentDescription = if (isLoading) "Stop loading" else "Reload",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Tab count --------------------------------------------------------
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .glass(RoundedCornerShape(14.dp), MaterialTheme.colorScheme.surface,
                        if (isPrivateTab) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onTabsClick),
            ) {
                Text(
                    text = tabCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPrivateTab) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }

            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
    }
}
