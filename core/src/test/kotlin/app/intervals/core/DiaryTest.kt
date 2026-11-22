package app.intervals.core

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class DiaryTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val today = d("2026-11-22")
    private val trained = setOf(d("2026-11-17"), d("2026-10-30"), d("2026-11-21"), d("2026-09-02"))

    @Test
    fun newestFirstWithAHeadingPerOlderMonth() {
        val rows = diaryRows(trained, today, picked = today)
        assertEquals(
            listOf(DiaryDay(today), DiaryDay(d("2026-11-21")), DiaryDay(d("2026-11-17")),
                DiaryMonth(YearMonth.of(2026, 10), d("2026-10-30")), DiaryDay(d("2026-10-30")),
                DiaryMonth(YearMonth.of(2026, 9), d("2026-09-02")), DiaryDay(d("2026-09-02"))),
            rows,
        )
    }

    @Test
    fun pickedEmptyDayIsShownAndFilterKeepsOnlyMatches() {
        assertEquals(DiaryDay(d("2026-11-19")), diaryRows(trained, today, picked = d("2026-11-19"))[2])
        // filtering: only matching trained days and the picked one, no empty today
        val rows = diaryRows(trained, today, picked = d("2026-11-17"), matches = setOf(d("2026-11-17"), d("2026-09-02"), d("2026-11-20")))
        assertEquals(listOf(d("2026-11-17"), d("2026-09-02"), d("2026-09-02")), rows.map { it.day })
        assertEquals(DiaryMonth(YearMonth.of(2026, 9), d("2026-09-02")), rows[1])
    }
}
