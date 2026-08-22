package com.lumi.ui.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lumi.MainActivity
import com.lumi.core.model.ReminderKind
import com.lumi.core.model.ReminderStatus
import com.lumi.data.local.ReminderEntity
import com.lumi.reminders.ReminderFormat
import com.lumi.reminders.ReminderSweepWorker
import com.lumi.ui.theme.DarkBackground
import com.lumi.ui.theme.LightSurfaceVariant
import com.lumi.ui.theme.SlimeBlue
import com.lumi.ui.theme.TextDark
import com.lumi.ui.theme.TextLight
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

/**
 * Lumi on the home screen: the next few things Lumi is keeping track of, nothing more.
 *
 * Sparse on purpose — it reads as a piece of the app, not a second product. One tap anywhere
 * opens Lumi, where capture happens; the widget itself only mirrors what is stored. Data is
 * read straight from Room at render time rather than held in widget state, so there is exactly
 * one source of truth and the widget cannot disagree with the list screen.
 */
class LumiWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = EntryPointAccessors
            .fromApplication(context, ReminderSweepWorker.RemindersEntryPoint::class.java)
            .reminderDao()

        // Pending only: a fired-but-uncleared reminder already announced itself with a
        // notification, and repeating it here would read as a nag.
        val upcoming = dao.observeActive().first()
            .filter { it.status == ReminderStatus.PENDING }
            .take(MAX_ITEMS)

        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        provideContent { WidgetContent(upcoming, dark) }
    }

    @Composable
    private fun WidgetContent(upcoming: List<ReminderEntity>, dark: Boolean) {
        val background = if (dark) DarkBackground else LightSurfaceVariant
        val onSurface = if (dark) TextLight else TextDark
        val onSurfaceFaded = onSurface.copy(alpha = 0.55f)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(background)
                .clickable(actionStartActivity(openLumi))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = GlanceModifier
                            .width(9.dp)
                            .height(9.dp)
                            .cornerRadius(5.dp)
                            .background(SlimeBlue),
                    ) {}
                    Spacer(modifier = GlanceModifier.width(7.dp))
                    Text(
                        text = "LUMI",
                        style = TextStyle(
                            color = ColorProvider(onSurface),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
                Spacer(modifier = GlanceModifier.height(10.dp))

                if (upcoming.isEmpty()) {
                    Text(
                        text = "Ask me to keep track of something.",
                        style = TextStyle(color = ColorProvider(onSurfaceFaded), fontSize = 13.sp),
                    )
                } else {
                    upcoming.forEachIndexed { index, reminder ->
                        if (index > 0) Spacer(modifier = GlanceModifier.height(8.dp))
                        Column {
                            Text(
                                text = reminder.text.ellipsized(MAX_TITLE_CHARS),
                                style = TextStyle(color = ColorProvider(onSurface), fontSize = 14.sp),
                            )
                            whenOf(reminder)?.let { whenLabel ->
                                Text(
                                    text = whenLabel,
                                    style = TextStyle(
                                        color = ColorProvider(onSurfaceFaded),
                                        fontSize = 11.sp,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** The time line under a title; a todo without a time says nothing, same as the list screen. */
    private fun whenOf(reminder: ReminderEntity): String? = when {
        reminder.dueAtMs == Long.MAX_VALUE -> null
        else -> ReminderFormat.dueLabel(reminder.dueAtMs, System.currentTimeMillis())
    }

    private fun String.ellipsized(max: Int): String =
        if (length <= max) this else take(max - 1).trimEnd() + "…"

    companion object {
        const val MAX_ITEMS = 3
        const val MAX_TITLE_CHARS = 30

        val openLumi: Intent = Intent()
            .setClassName("com.lumi", "com.lumi.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/**
 * Re-renders every bound widget from current Room state.
 *
 * Safe to call when none are bound — the manager returns no ids and this does nothing. Called
 * after anything that changes the reminders table so the widget never contradicts the app.
 */
suspend fun refreshRemindersWidget(context: Context) {
    runCatching { LumiWidget().updateAll(context) }
}

/** The manifest entry point; [LumiWidget] holds all behaviour. */
class LumiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LumiWidget()
}
