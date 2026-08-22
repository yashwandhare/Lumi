package com.lumi.reminders

import com.lumi.core.model.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins what the notification will read.
 *
 * The stored title is what the user sees when the reminder rings, so the scaffolding they
 * spoke around — "remind me to", "add … to my list" — must come off, and anything the rules do
 * not recognise must fall back to their words rather than a mangled guess.
 */
class ReminderTitleTest {

    @Test
    fun `the remind-me scaffolding comes off`() {
        assertEquals("call mom at six", ReminderTitle.clean("remind me to call mom at six", ReminderKind.REMINDER))
    }

    @Test
    fun `politeness is stripped before the rules run`() {
        assertEquals("stretch", ReminderTitle.clean("hey lumi remind me to stretch", ReminderKind.REMINDER))
        assertEquals(
            "the oven is on",
            ReminderTitle.clean("please remind me that the oven is on", ReminderKind.REMINDER),
        )
    }

    @Test
    fun `set a reminder names its subject`() {
        assertEquals(
            "dentist appointment",
            ReminderTitle.clean("set a reminder for dentist appointment", ReminderKind.REMINDER),
        )
    }

    @Test
    fun `a todo keeps the item not the list it landed on`() {
        assertEquals("milk", ReminderTitle.clean("add milk to my shopping list", ReminderKind.TODO))
        assertEquals("laundry", ReminderTitle.clean("put laundry on the checklist", ReminderKind.TODO))
    }

    @Test
    fun `an explicit todo task drops the create scaffolding`() {
        assertEquals(
            "water the plants",
            ReminderTitle.clean("create a todo to water the plants", ReminderKind.TODO),
        )
    }

    @Test
    fun `anything unmatched falls back to the user's own words`() {
        assertEquals(
            "check the mailbox",
            ReminderTitle.clean("check the mailbox", ReminderKind.REMINDER),
        )
    }
}
