package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WebShortcut
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun WebBrowserBar(
    modifier: Modifier = Modifier,
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onLaunchBrowser: () -> Unit,
    onRefreshPage: () -> Unit,
    onOpenKeyboardSheet: () -> Unit,
    shortcuts: List<WebShortcut>,
    recentUrls: List<String>
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceContainer)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "WEB BROWSER & INTERNET",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Row {
                IconButton(
                    onClick = onRefreshPage,
                    modifier = Modifier.size(32.dp).testTag("browser_refresh_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload Page",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onOpenKeyboardSheet,
                    modifier = Modifier.size(32.dp).testTag("open_keyboard_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Open TV Keyboard",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // URL / Search Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlInputChange,
                placeholder = {
                    Text("Type URL or search query...", fontSize = 13.sp, color = TextMuted)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (urlInput.isNotEmpty()) {
                        IconButton(
                            onClick = { onUrlInputChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (urlInput.isNotBlank()) onOpenUrl(urlInput)
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceContainerHigh,
                    unfocusedContainerColor = SurfaceContainerHigh
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("browser_url_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (urlInput.isNotBlank()) {
                        onOpenUrl(urlInput)
                    } else {
                        onLaunchBrowser()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = Color(0xFF00222B)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("browser_open_button")
            ) {
                Icon(
                    imageVector = if (urlInput.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (urlInput.isNotBlank()) "GO" else "BROWSER",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Website Chips (Horizontal Scroll)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            shortcuts.forEach { item ->
                SuggestionChip(
                    onClick = { onOpenUrl(item.url) },
                    label = {
                        Text(
                            text = item.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = SurfaceContainerHigh,
                        labelColor = TextPrimary
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = SurfaceBorder
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("shortcut_${item.id}")
                )
            }
        }
    }
}
