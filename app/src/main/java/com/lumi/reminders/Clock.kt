package com.lumi.reminders

/**
 * Injectable instant. The capability, the processor, and the scheduler all need "now"; tests
 * need a fixed one. One fun interface keeps the wall clock out of the unit under test without
 * growing an abstraction around it.
 */
fun interface Clock {
    fun now(): Long
}
