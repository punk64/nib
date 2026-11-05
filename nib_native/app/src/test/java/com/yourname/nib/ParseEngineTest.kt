package com.yourname.nib

import com.yourname.nib.core.parse.parse
import com.yourname.nib.core.time.NibDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseEngineTest {
    private val today = NibDate(2026, 11, 4)

    private fun p(raw: String, base: NibDate = today) = parse(raw, base)

    @Test fun relativeToday() {
        val r = p("今天 买菜")
        assertEquals("2026-11-04", r.due)
        assertEquals("买菜", r.title)
    }

    @Test fun tomorrowWithTimeTagPriority() {
        val r = p("明天10点 交周报 #工作 !!")
        assertEquals("2026-11-05", r.due)
        assertEquals("10:00", r.time)
        assertEquals("交周报", r.title)
        assertEquals(listOf("工作"), r.tags)
        assertEquals(2, r.priority)
    }

    @Test fun dayAfterTomorrow() {
        assertEquals("2026-11-06", p("后天 交房租").due)
    }

    @Test fun tonightBelongsToToday() {
        val r = p("今晚 看电影")
        assertEquals("2026-11-04", r.due)
        assertEquals("看电影", r.title)
        assertNull(r.time)
    }

    @Test fun tomorrowMorning() {
        assertEquals("2026-11-05", p("明早 跑步").due)
    }

    @Test fun timeOnlyFallsOnToday() {
        val r = p("10点")
        assertEquals("2026-11-04", r.due)
        assertEquals("10:00", r.time)
        assertEquals("", r.title)
    }

    @Test fun dateOnlyWithoutTime() {
        val r = p("明天 开会")
        assertEquals("2026-11-05", r.due)
        assertNull(r.time)
        assertEquals("开会", r.title)
    }

    @Test fun weekdayNearestFuture() {
        assertEquals(
            "2026-11-04",
            p("周三 例会", NibDate(2026, 11, 2)).due,
        )
    }

    @Test fun sameWeekdayRollsOneWeek() {
        assertEquals("2026-11-11", p("周三 例会").due)
    }

    @Test fun nextWeekWeekday() {
        assertEquals(
            "2026-11-11",
            p("下周三 复盘", NibDate(2026, 11, 2)).due,
        )
    }

    @Test fun xingQiSanSpelling() {
        assertEquals(
            "2026-11-04",
            p("星期三 例会", NibDate(2026, 11, 2)).due,
        )
    }

    @Test fun pastDateWithoutYearRollsNextYear() {
        assertEquals("2027-03-18", p("3月18号 春游").due)
    }

    @Test fun fourDigitYearDoesNotRoll() {
        assertEquals("2027-03-18", p("2027年3月18号 出差").due)
    }

    @Test fun twoDigitYearGets2000() {
        assertEquals("2027-03-18", p("27年3月18号").due)
    }

    @Test fun nextYearPrefix() {
        assertEquals("2027-03-18", p("明年3月18号").due)
    }

    @Test fun yearAfterNextPrefix() {
        assertEquals("2028-01-01", p("后年1月1号").due)
    }

    @Test fun riAndHaoSuffixes() {
        assertEquals(
            "2026-03-18",
            p("3月18日 回程", NibDate(2026, 3, 1)).due,
        )
    }

    @Test fun monthEndOverflowNormalizes() {
        assertEquals(
            "2026-03-01",
            p("2月29号 复查", NibDate(2026, 1, 1)).due,
        )
    }

    @Test fun halfHour() {
        assertEquals("10:30", p("10点半 开会").time)
    }

    @Test fun hourAndMinute() {
        assertEquals("10:30", p("10点30分 开会").time)
    }

    @Test fun colonTime() {
        assertEquals("10:30", p("10:30 开会").time)
    }

    @Test fun afternoonHour() {
        val r = p("下午3点 面试")
        assertEquals("15:00", r.time)
        assertEquals("面试", r.title)
    }

    @Test fun eveningHour() {
        assertEquals("20:00", p("晚上8点 直播").time)
    }

    @Test fun morningHourNotShifted() {
        assertEquals("09:00", p("上午9点 站会").time)
    }

    @Test fun multipleTags() {
        val r = p("#工作 #Q3 任务")
        assertEquals(listOf("工作", "Q3"), r.tags)
        assertEquals("任务", r.title)
    }

    @Test fun lowPriority() {
        assertEquals(1, p("提醒!").priority)
    }

    @Test fun mediumPriority() {
        assertEquals(2, p("重要!!").priority)
    }

    @Test fun highPriority() {
        assertEquals(3, p("重要!!!").priority)
    }

    @Test fun exclamationMarksRemoved() {
        assertEquals("高 低", p("高!!! 低!").title)
    }

    @Test fun everyTwoWeeks() {
        val r = p("每2周 浇花")
        assertEquals("week", r.repeatFreq)
        assertEquals(2, r.repeatN)
        assertEquals("浇花", r.title)
    }

    @Test fun everyThreeWeeksOnce() {
        val r = p("每3周一次 健身")
        assertEquals("week", r.repeatFreq)
        assertEquals(3, r.repeatN)
    }

    @Test fun monthlyDefaultsToOne() {
        val r = p("每月 缴费")
        assertEquals("month", r.repeatFreq)
        assertEquals(1, r.repeatN)
    }

    @Test fun yearly() {
        assertEquals("year", p("每年 体检").repeatFreq)
    }

    @Test fun intervalCappedAt999() {
        assertEquals(999, p("每1000天 检查").repeatN)
    }

    @Test fun plainTextKeptVerbatim() {
        val r = p("投影仪密码 8842")
        assertEquals("投影仪密码 8842", r.title)
        assertNull(r.due)
        assertNull(r.time)
        assertTrue(r.tags.isEmpty())
        assertEquals(0, r.priority)
        assertEquals("none", r.repeatFreq)
    }

    @Test fun digitsInContentPreserved() {
        assertEquals("买牛奶 2瓶", p("买牛奶 2瓶").title)
    }

    @Test fun rawAlwaysPreserved() {
        val raw = "明天10点 交周报 #工作 !!"
        assertEquals(raw, p(raw).raw)
    }

    @Test fun pastRelativeDays() {
        assertEquals("2026-11-03", p("昨天 交话费").due)
        assertEquals("2026-11-02", p("前天 交话费").due)
        assertEquals("2026-11-01", p("大前天 交话费").due)
        assertEquals("2026-11-03", p("昨晚 写总结").due)
        assertEquals("2026-11-03", p("昨早 跑步").due)
    }

    @Test fun futureRelativeDays() {
        assertEquals("2026-11-06", p("后天 交房租").due)
        assertEquals("2026-11-07", p("大后天 体检").due)
        assertEquals("2026-11-04", p("今日 复盘").due)
        assertEquals("2026-11-05", p("明日 复盘").due)
    }

    @Test fun relativeWordsLeaveNoResidue() {
        assertEquals("体检", p("大后天 体检").title)
        assertEquals("交话费", p("大前天 交话费").title)
        assertEquals("写总结", p("昨晚 写总结").title)
    }

    @Test fun daysLaterArabicAndChinese() {
        assertEquals("2026-11-07", p("3天后 复查").due)
        assertEquals("2026-11-07", p("三天后 复查").due)
        assertEquals("2026-11-14", p("十天后 复查").due)
        assertEquals("2026-11-05", p("1天后 复查").due)
        assertEquals("复查", p("3天后 复查").title)
    }

    @Test fun pastRelativeDoesNotRollYear() {
        assertEquals("2026-11-03", p("昨天 10点 开会").due)
        assertEquals("10:00", p("昨天 10点 开会").time)
    }

    @Test fun morningPeriods() {
        assertEquals("08:00", p("早上8点 跑步").time)
        assertEquals("08:00", p("早晨8点 跑步").time)
        assertEquals("08:00", p("清晨8点 跑步").time)
        assertEquals("10:30", p("上午10点半 开会").time)
    }

    @Test fun dawnAndNoonPeriods() {
        assertEquals("02:00", p("凌晨2点 值班").time)
        assertEquals("00:00", p("凌晨12点 值班").time)
        assertEquals("12:00", p("中午12点 吃饭").time)
        assertEquals("13:00", p("中午1点 吃饭").time)
    }

    @Test fun afternoonAndEveningPeriods() {
        assertEquals("15:00", p("下午3点 开会").time)
        assertEquals("18:00", p("傍晚6点 散步").time)
        assertEquals("20:00", p("晚上8点 看电影").time)
        assertEquals("00:00", p("晚上12点 跨年").time)
        assertEquals("15:30", p("下午3:30 开会").time)
    }

    @Test fun explicitTwentyFourHourKept() {
        assertEquals("15:30", p("下午 15:30 开会").time)
        assertEquals("20:00", p("晚上 20点 看电影").time)
    }

    @Test fun periodWordsRemovedFromTitle() {
        assertEquals("开会", p("下午3点 开会").title)
        assertEquals("吃饭", p("中午 吃饭").title)
        assertNull(p("中午 吃饭").time)
    }
}
