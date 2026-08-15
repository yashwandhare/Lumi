package com.trace.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trace.core.settings.ModelBackend
import com.trace.core.settings.ModelSettings
import com.trace.ui.components.TraceGlassPanel
import com.trace.ui.components.hairlineBorder
import com.trace.ui.theme.TraceShape
import com.trace.ui.theme.spacing

/**
 * One screen for everything configurable: how the model runs, how it is told to behave, and how the
 * app looks.
 *
 * Merged on purpose. Splitting "settings" from "model parameters" made the user hunt for which of two
 * screens held the thing they wanted, when in practice the interesting controls are all model
 * parameters. Sections give the separation that two screens were providing.
 */
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.model.collectAsStateWithLifecycle()
    val switchingBackend by viewModel.switchingBackend.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.spacing.md),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = MaterialTheme.spacing.md),
        )

        SectionHeader("Appearance")
        SettingRow(
            title = "Dark mode",
            detail = "Follows your system setting until you change it here.",
        ) {
            Box(scale = 0.78f) {
                Switch(checked = isDarkTheme, onCheckedChange = { onThemeToggle() })
            }
        }
        SettingRow(
            title = "Live mascot",
            detail = "Moves beside the menu while you chat and reacts to what Trace is doing.",
        ) {
            Box(scale = 0.78f) {
                Switch(checked = settings.liveMascot, onCheckedChange = viewModel::setLiveMascot)
            }
        }

        SectionHeader("Processor")
        BackendPicker(
            selected = settings.backend,
            active = viewModel.activeBackend(),
            switching = switchingBackend,
            onSelect = viewModel::setBackend,
        )

        SectionHeader("Model parameters")
        Text(
            // Stated rather than hidden. The runtime fixes sampling when a conversation is created, so
            // pretending these apply instantly would be a lie the user would catch.
            text = "Applies to your next conversation. The current chat keeps the values it started with.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.sm),
        )

        SliderRow(
            label = "Temperature",
            value = settings.temperature,
            valueLabel = "%.2f".format(settings.temperature),
            range = ModelSettings.TEMPERATURE_RANGE,
            detail = "Higher is more varied, lower is more predictable.",
            onChange = viewModel::setTemperature,
        )
        SliderRow(
            label = "Top P",
            value = settings.topP,
            valueLabel = "%.2f".format(settings.topP),
            range = ModelSettings.TOP_P_RANGE,
            detail = "Share of probability the model is allowed to pick from.",
            onChange = viewModel::setTopP,
        )
        SliderRow(
            label = "Top K",
            value = settings.topK.toFloat(),
            valueLabel = settings.topK.toString(),
            range = ModelSettings.TOP_K_RANGE.first.toFloat()..ModelSettings.TOP_K_RANGE.last.toFloat(),
            detail = "How many candidate tokens are considered at each step.",
            onChange = { viewModel.setTopK(it.toInt()) },
        )
        SliderRow(
            label = "Reply limit",
            value = settings.maxTokens.toFloat(),
            valueLabel = "${settings.maxTokens} tokens",
            range = ModelSettings.MAX_TOKENS_RANGE.first.toFloat()..ModelSettings.MAX_TOKENS_RANGE.last.toFloat(),
            detail = "Longest single reply. Lower means faster, and cut off sooner.",
            onChange = { viewModel.setMaxTokens(it.toInt()) },
        )

        SettingRow(
            title = "Show reply timings",
            detail = "Response time and decode speed under each reply.",
        ) {
            Box(scale = 0.78f) {
                Switch(checked = settings.showMetrics, onCheckedChange = viewModel::setShowMetrics)
            }
        }

        SectionHeader("System prompt")
        SystemPromptEditor(
            prompt = settings.systemPrompt,
            onSave = viewModel::setSystemPrompt,
        )

        TextButton(
            onClick = viewModel::resetDefaults,
            modifier = Modifier.padding(vertical = MaterialTheme.spacing.md),
        ) {
            Text("Reset parameters to defaults", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(MaterialTheme.spacing.xxl))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = MaterialTheme.spacing.lg, bottom = MaterialTheme.spacing.sm),
    )
}

@Composable
private fun SettingRow(
    title: String,
    detail: String,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    detail: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = MaterialTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

/**
 * Backend choice, with what actually happened reported back.
 *
 * [active] matters most for `Auto`: the user picked "decide for me", so the screen owes them the
 * decision. It also catches the case where GPU was requested, failed, and the app is on CPU.
 */
@Composable
private fun BackendPicker(
    selected: ModelBackend,
    active: ModelBackend?,
    switching: Boolean,
    onSelect: (ModelBackend) -> Unit,
) {
    Column {
        ModelBackend.entries.forEach { backend ->
            val isSelected = backend == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.spacing.xs)
                    .clip(TraceShape.default)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .hairlineBorder(TraceShape.default)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(enabled = !switching) { onSelect(backend) }
                    .padding(MaterialTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = backend.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = backend.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isSelected) {
                    Text(
                        text = if (switching) "switching…" else "on",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (active != null && active != selected) {
            Text(
                text = buildAnnotatedString {
                    append("Currently running on ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(active.label) }
                    append(" — the requested backend would not load.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MaterialTheme.spacing.xs),
            )
        }

        Text(
            text = "Changing this reloads the model and clears what it remembers of the current chat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MaterialTheme.spacing.xs),
        )
    }
}

/**
 * The system prompt, editable.
 *
 * Held in local state and committed on Save rather than on every keystroke: writing to the store per
 * character would be fine, but it would also mean a half-typed instruction was briefly the live prompt.
 */
@Composable
private fun SystemPromptEditor(
    prompt: String,
    onSave: (String) -> Unit,
) {
    var draft by remember(prompt) { mutableStateOf(prompt) }
    val dirty = draft != prompt

    Column {
        Text(
            text = "What Trace is told about itself before every conversation. Clearing this restores the default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.sm),
        )
        TraceGlassPanel(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.md),
            )
        }
        Row(modifier = Modifier.padding(top = MaterialTheme.spacing.xs)) {
            TextButton(onClick = { onSave(draft) }, enabled = dirty) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { draft = "" }, enabled = draft.isNotEmpty()) {
                Text("Clear", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A scaled wrapper, because Material's switch is oversized against this type scale. */
@Composable
private fun Box(scale: Float, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.scale(scale)) { content() }
}
