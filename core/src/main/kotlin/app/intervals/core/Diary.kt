package app.intervals.core

import java.time.LocalDate
import java.time.YearMonth

/** One row of the Journal diary (mockup intervals-journal-month-diary.html, option 2). */
sealed interface DiaryRow { val day: LocalDate }

/** The heading before an older month's days; [day] is its newest day in the diary. */
data class DiaryMonth(val month: YearMonth, override val day: LocalDate) : DiaryRow

data class DiaryDay(override val day: LocalDate) : DiaryRow

/**
 * The diary, newest first: the trained days (only those in [matches] while filtering), plus today when not filtering
 * and the day picked on the calendar. Every month after the first starts with its heading.
 */
fun diaryRows(trained: Set<LocalDate>, today: LocalDate, picked: LocalDate, matches: Set<LocalDate>? = null): List<DiaryRow> {
    val days = ((if (matches == null) trained + today else trained intersect matches) + picked).filter { it <= today }.sortedDescending()
    return buildList {
        days.forEachIndexed { i, d ->
            if (i > 0 && YearMonth.from(d) != YearMonth.from(days[i - 1])) add(DiaryMonth(YearMonth.from(d), d))
            add(DiaryDay(d))
        }
    }
}
