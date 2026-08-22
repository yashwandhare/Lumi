package com.lumi.reminders

import com.lumi.core.CapabilityInput
import com.lumi.core.CapabilityResult
import com.lumi.core.InteractionOrigin
import com.lumi.core.StructuredIntent
import com.lumi.core.audit.AuditLog
import com.lumi.core.model.CapabilityId
import com.lumi.core.model.ReminderKind
import com.lumi.core.model.ReminderStatus
import com.lumi.router.RulesTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the creation path: what a routed sentence becomes in the table, on the alarm clock,
 * and in the audit log.
 *
 * The sentence is interpreted here and never again — these tests hold the stored row to the
 * resolved instant rather than the raw words. A time the parser cannot honour must still keep
 * the item (honestly, without arming anything), and both kinds land in the one table with the
 * label as the only difference.
 */
class ReminderCapabilityTest {

    @Test
    fun `a spoken time becomes a stored instant and an armed alarm`() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = RecordingScheduler()
        val capability = capabilityOf(dao, scheduler)

        val result = capability.execute(input("remind me to call mom tomorrow at 6 pm"))

        assertTrue(result is CapabilityResult.Ok)
        val row = dao.rows.values.single()
        assertEquals(ReminderStatus.PENDING, row.status)
        assertEquals("call mom tomorrow at 6 pm", row.text)
        assertTrue("the due instant sits ahead of now", row.dueAtMs > NOW)
        assertEquals(listOf(row.id to row.dueAtMs), scheduler.armed)
        assertEquals(1, scheduler.sweeps)
    }

    @Test
    fun `no readable time keeps the item without arming anything`() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = RecordingScheduler()
        val capability = capabilityOf(dao, scheduler)

        val result = capability.execute(input("remind me someday"))

        assertTrue(result is CapabilityResult.Ok)
        val row = dao.rows.values.single()
        assertEquals(Long.MAX_VALUE, row.dueAtMs)
        assertEquals("nothing was armed", 0, scheduler.armed.size)
        assertEquals("the sweep still guards it", 1, scheduler.sweeps)
        val event = dao.audit.events.single()
        assertTrue(event.detail!!.contains("No time"))
    }

    @Test
    fun `the todo slot stores a todo that never rings`() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = RecordingScheduler()
        val capability = capabilityOf(dao, scheduler)

        val result = capability.execute(
            input(
                "add milk to my shopping list",
                slots = mapOf(RulesTier.SLOT_KIND to RulesTier.KIND_TODO),
            ),
        )

        assertTrue(result is CapabilityResult.Ok)
        val row = dao.rows.values.single()
        assertEquals(ReminderKind.TODO, row.kind)
        assertEquals(Long.MAX_VALUE, row.dueAtMs)
        assertTrue((result as CapabilityResult.Ok).userMessage.contains("Added to your list"))
    }

    @Test
    fun `a missing kind defaults to the kind that can still ring`() = runBlocking {
        val dao = FakeReminderDao()
        val capability = capabilityOf(dao, RecordingScheduler())

        capability.execute(input("remind me at six"))

        assertEquals(ReminderKind.REMINDER, dao.rows.values.single().kind)
    }

    @Test
    fun `the audit entry names the cleaned title`() = runBlocking {
        val dao = FakeReminderDao()
        val capability = capabilityOf(dao, RecordingScheduler())

        capability.execute(input("remind me to call mom tomorrow at 6 pm"))

        val event = dao.audit.events.single()
        assertEquals(CapabilityId.TOOLS, event.capability)
        assertEquals("call mom tomorrow at 6 pm", event.subject)
    }

    private fun capabilityOf(dao: FakeReminderDao, scheduler: RecordingScheduler) =
        ReminderCapability(
            dao = dao,
            scheduler = scheduler,
            audit = AuditLog(dao.audit),
            clock = { NOW },
        )

    private fun input(
        text: String,
        slots: Map<String, String> = emptyMap(),
    ) = CapabilityInput(
        intent = StructuredIntent(
            capability = CapabilityId.TOOLS,
            rawText = text,
            slots = slots,
        ),
        origin = InteractionOrigin.VOICE,
    )

    private companion object {
        const val NOW = 1_789_000_000_000L
    }
}
