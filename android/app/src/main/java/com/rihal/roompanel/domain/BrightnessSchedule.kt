package com.rihal.roompanel.domain

import java.time.ZonedDateTime

object BrightnessSchedule {
    const val FULL = 1.0f
    const val DIMMED = 0.08f

    /**
     * Full brightness during working hours, or whenever the room is in use or about to be
     * (an evening meeting still needs a readable door); dimmed otherwise to save the screen.
     */
    fun brightnessFor(now: ZonedDateTime, status: RoomStatus, hours: WorkingHours): Float {
        if (status !is RoomStatus.Free) return FULL
        val time = now.toLocalTime()
        val open = now.dayOfWeek in hours.days && !time.isBefore(hours.start) && time.isBefore(hours.end)
        return if (open) FULL else DIMMED
    }
}
