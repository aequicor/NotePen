package ru.kyamshanov.notepen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.blur.GlassSurface

/**
 * Floating page indicator. When [onNavigateToPage] is provided the current-page
 * number is tappable: clicking it enters edit mode, where the user types a
 * destination page and confirms with Enter / Done. Escape or focus-loss cancels.
 *
 * @param currentPage 1-based current page number.
 * @param totalPages Total page count.
 * @param onNavigateToPage Called with a 0-based page index on confirmation.
 *   Pass `null` to render a non-interactive indicator.
 * @param containerColor Glass tint; `null` keeps the default [GlassSurface] look.
 *   Used to repaint the airbar under the active reader theme.
 * @param contentColor Text colour; `null` keeps `onSurface`. Pairs with
 *   [containerColor] for reader-theme tinting.
 */
@Composable
fun PageIndicatorAirbar(
    currentPage: Int,
    totalPages: Int,
    onNavigateToPage: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val textColor = contentColor ?: MaterialTheme.colorScheme.onSurface
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        tint = containerColor ?: MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = AIRBAR_PADDING_H,
                    vertical = AIRBAR_PADDING_V,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Страница ",
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
            )
            if (onNavigateToPage != null) {
                EditablePageNumber(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onNavigateToPage = onNavigateToPage,
                    contentColor = textColor,
                )
            } else {
                Text(
                    text = currentPage.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                )
            }
            Text(
                text = " / $totalPages",
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
            )
        }
    }
}

@Composable
private fun EditablePageNumber(
    currentPage: Int,
    totalPages: Int,
    onNavigateToPage: (Int) -> Unit,
    contentColor: Color,
) {
    var editing by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(currentPage.toString())) }
    val focusRequester = remember { FocusRequester() }
    // Prevents onFocusChanged from cancelling editing before focus is actually granted.
    var everFocused by remember { mutableStateOf(false) }

    fun confirm() {
        val target = fieldValue.text.trim().toIntOrNull()
        if (target != null && target in 1..totalPages) onNavigateToPage(target - 1)
        editing = false
        everFocused = false
    }

    fun cancel() {
        editing = false
        everFocused = false
    }

    if (editing) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        // Invisible Text drives the Box width via IntrinsicSize.Min;
        // BasicTextField fills that exact space via matchParentSize.
        Box(modifier = Modifier.width(IntrinsicSize.Min)) {
            Text(
                text = fieldValue.text.ifEmpty { "0" },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.alpha(0f),
            )
            BasicTextField(
                value = fieldValue,
                onValueChange = { new ->
                    if (new.text.all { it.isDigit() } && new.text.length <= totalPages.toString().length) {
                        fieldValue = new
                    }
                },
                textStyle =
                    MaterialTheme.typography.labelLarge.copy(
                        color = contentColor,
                    ),
                cursorBrush = SolidColor(contentColor),
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go,
                    ),
                keyboardActions = KeyboardActions(onGo = { confirm() }),
                modifier =
                    Modifier.matchParentSize()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                everFocused = true
                            } else if (everFocused) {
                                cancel()
                            }
                        }
                        .onKeyEvent { event ->
                            if (event.key == Key.Escape) {
                                cancel()
                                true
                            } else {
                                false
                            }
                        },
            )
        }
    } else {
        Text(
            text = currentPage.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier =
                Modifier.clickable {
                    fieldValue =
                        TextFieldValue(
                            text = currentPage.toString(),
                            selection = TextRange(0, currentPage.toString().length),
                        )
                    editing = true
                },
        )
    }
}

private val AIRBAR_PADDING_H = 16.dp
private val AIRBAR_PADDING_V = 8.dp
