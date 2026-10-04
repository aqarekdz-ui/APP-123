package com.dani.assistant.core.money

/** حساب الأشهر المستحقة للمصاريف الثابتة (منطق صافي، يتجرّب بـ JUnit). الشهر = سنة*12+شهر(0..11). */
object RecurrenceMath {
    const val MAX_CATCHUP = 5

    /** الأشهر اللي لازم تتسجل: من (last+1) لـ cur، بحد أقصى 6 أشهر، وشهر cur ما يتسجلش قبل يومو. */
    fun dueMonths(last: Int, cur: Int, today: Int, day: Int): List<Int> {
        val out = ArrayList<Int>()
        var idx = maxOf(last + 1, cur - MAX_CATCHUP)
        while (idx <= cur) {
            if (idx == cur && today < day) break
            out.add(idx)
            idx++
        }
        return out
    }

    /** مصروف ثابت جديد ما يسجلش بأثر رجعي: إذا اليوم فات هذا الشهر يبدأ من الجاي. */
    fun initialLast(cur: Int, today: Int, day: Int): Int = if (today >= day) cur else cur - 1
}
