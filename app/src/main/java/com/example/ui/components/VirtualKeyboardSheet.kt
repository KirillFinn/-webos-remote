package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentInk
import com.example.ui.theme.AmberYellow
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VirtualKeyboardSheet(
    textInput: String,
    tvFieldFocused: Boolean?,
    youTubeSearchMode: Boolean = false,
    onTextInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onPageUp: () -> Unit,
    onPageDown: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainer,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        val focusRequester = remember { FocusRequester() }
        // Open the phone keyboard straight away so the user can start typing.
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = null,
                        tint = AccentInk,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "TV Text Input & Keyboard",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                youTubeSearchMode -> "● YouTube: type here, then tap SEARCH"
                                tvFieldFocused == true -> "● TV text field ready – type here"
                                tvFieldFocused == false -> "● Select a search field on the TV first"
                                else -> "Select a search field on the TV, then type here"
                            },
                            color = when {
                                youTubeSearchMode || tvFieldFocused == true -> StatusGreen
                                tvFieldFocused == false -> AmberYellow
                                else -> TextMuted
                            },
                            fontSize = 12.sp,
                            modifier = Modifier.testTag("tv_field_status")
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Text Input Field – mirrored live into the focused TV input field
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextInputChange,
                placeholder = { Text("Type to search on TV...", color = TextMuted) },
                supportingText = {
                    Text(
                        "Text appears live in the TV search field. Press CONFIRM to search.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                },
                trailingIcon = {
                    if (textInput.isNotEmpty()) {
                        IconButton(onClick = onClear, modifier = Modifier.testTag("clear_text_button")) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onConfirm() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentInk,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceContainerHigh,
                    unfocusedContainerColor = SurfaceContainerHigh
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("keyboard_text_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentInk,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("confirm_text_button")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (youTubeSearchMode) "SEARCH" else "CONFIRM", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onBackspace,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SurfaceContainerHigh,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("send_backspace_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Web Page Navigation Shortcuts Row
            Text(
                text = "INTERNET PAGE SHORTCUTS",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onPageUp,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SurfaceContainerHigh,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Text("PAGE UP", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                FilledTonalButton(
                    onClick = onPageDown,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SurfaceContainerHigh,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Text("PAGE DOWN", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                FilledTonalButton(
                    onClick = { onTextInputChange("$textInput ") },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SurfaceContainerHigh,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(imageVector = Icons.Default.SpaceBar, contentDescription = "Space", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SPACE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
