package com.fc.v2.service.haz;

import com.fc.v2.model.auto.TIsLimRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HazIdentRuler} 单测：分档压线、空界值顺延、交棒日半开、顺位/代号决胜、坏值退回。
 *
 * @author fuce
 * @date 2026-10-01
 */
class HazIdentRulerTest {

    private static Date t(String s) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        f.setTimeZone(DeadlineRuler.ZONE);
        return f.parse(s);
    }

    private static TIsLimRule rule(String code, Integer priority,
                                   String th1, String th2, String th3,
                                   String start, String end) {
        TIsLimRule r = new TIsLimRule();
        r.setRuleCode(code);
        r.setPriority(priority);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        try {
            r.setEffStart(start == null ? null : t(start));
            r.setEffEnd(end == null ? null : t(end));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return r;
    }

    // ---------- 储存量在不在理上 ----------

    @Test
    void saneQty_rejectsNullNegativeMonster() {
        assertFalse(HazIdentRuler.isQtySane(null));
        assertFalse(HazIdentRuler.isQtySane(new BigDecimal("-0.01")));
        assertFalse(HazIdentRuler.isQtySane(new BigDecimal("-100")));
        assertFalse(HazIdentRuler.isQtySane(HazIdentRuler.MAX_QTY.add(BigDecimal.ONE)));
        // 零与上限本身在理上
        assertTrue(HazIdentRuler.isQtySane(BigDecimal.ZERO));
        assertTrue(HazIdentRuler.isQtySane(HazIdentRuler.MAX_QTY));
    }

    // ---------- 三档定级：压线算这一档，超三档顶格 ----------

    @Test
    void gradeOf_threeBands_andBoundaryBelongsToBand() {
        TIsLimRule r = rule("X", 1, "50", "200", "500", "2025-01-01 00:00:00", null);
        assertEquals(1, HazIdentRuler.gradeOf(r, new BigDecimal("10")));
        // 恰好等于 50：算一级，不许顺到二级
        assertEquals(1, HazIdentRuler.gradeOf(r, new BigDecimal("50")));
        assertEquals(2, HazIdentRuler.gradeOf(r, new BigDecimal("50.01")));
        assertEquals(2, HazIdentRuler.gradeOf(r, new BigDecimal("200")));
        assertEquals(3, HazIdentRuler.gradeOf(r, new BigDecimal("500")));
        // 超三档顶格 4
        assertEquals(4, HazIdentRuler.gradeOf(r, new BigDecimal("500.01")));
        assertEquals(4, HazIdentRuler.gradeOf(r, new BigDecimal("9999")));
    }

    @Test
    void gradeOf_emptyFirstBand_skipsToSecond_notAnError() {
        // 早年间入册：一级那格空着 = 不设这档，顺到二级继续推
        TIsLimRule r = rule("OLD", 1, null, "200", "500", "1995-01-01 00:00:00", null);
        assertEquals(2, HazIdentRuler.gradeOf(r, new BigDecimal("60")));
        assertEquals(2, HazIdentRuler.gradeOf(r, new BigDecimal("200")));
        assertEquals(3, HazIdentRuler.gradeOf(r, new BigDecimal("300")));
        // 三档全空 = 线没设任何档，不可定级
        TIsLimRule empty = rule("E", 1, null, null, null, "1995-01-01 00:00:00", null);
        assertEquals(HazIdentRuler.GRADE_NONE, HazIdentRuler.gradeOf(empty, new BigDecimal("60")));
    }

    // ---------- 生效时段半开 [start, end)：交棒当天只认新线 ----------

    @Test
    void effectiveAt_halfOpen_handoffDayBelongsToNewRule() throws Exception {
        TIsLimRule oldRule = rule("OLD", 10, "100", "300", "500",
                "2015-01-01 00:00:00", "2025-01-01 00:00:00");
        TIsLimRule newRule = rule("NEW", 10, "50", "200", "500",
                "2025-01-01 00:00:00", null);

        Date lastMoment = t("2024-12-31 23:59:59");
        Date handoff = t("2025-01-01 00:00:00");
        assertTrue(HazIdentRuler.effectiveAt(oldRule, lastMoment));
        // 交棒那天旧线管不到（不含）、新线从结局算（含）
        assertFalse(HazIdentRuler.effectiveAt(oldRule, handoff));
        assertTrue(HazIdentRuler.effectiveAt(newRule, handoff));

        List<TIsLimRule> picked = HazIdentRuler.effectiveOrdered(
                Arrays.asList(oldRule, newRule), handoff);
        assertEquals(1, picked.size());
        assertEquals("NEW", picked.get(0).getRuleCode());
    }

    // ---------- 并行线：顺位高的赢；顺位并列，代号排后的赢 ----------

    @Test
    void pickTop_higherPriorityWins() throws Exception {
        TIsLimRule city = rule("SJ-X", 10, "100", "300", "500", "2022-01-01 00:00:00", null);
        TIsLimRule park = rule("YQ-X", 20, "55", "170", "380", "2022-01-01 00:00:00", null);
        TIsLimRule win = HazIdentRuler.pickTop(Arrays.asList(city, park), t("2023-06-01 00:00:00"));
        assertEquals("YQ-X", win.getRuleCode());
        // 给的顺序反过来也一样（确定性）
        TIsLimRule win2 = HazIdentRuler.pickTop(Arrays.asList(park, city), t("2023-06-01 00:00:00"));
        assertEquals("YQ-X", win2.getRuleCode());
    }

    @Test
    void pickTop_samePriority_codeLaterWins() throws Exception {
        TIsLimRule a = rule("YQ-2022A", 20, "60", "180", "400", "2022-06-01 00:00:00", null);
        TIsLimRule b = rule("YQ-2022B", 20, "55", "170", "380", "2022-06-01 00:00:00", null);
        TIsLimRule win = HazIdentRuler.pickTop(Arrays.asList(a, b), t("2023-01-01 00:00:00"));
        assertEquals("YQ-2022B", win.getRuleCode());
        // 55 在 B 是一级压线（55），在 A 落二级——赢的是 B，给 1 级
        assertEquals(1, HazIdentRuler.gradeOf(win, new BigDecimal("55")));
    }

    // ---------- 无可用线：明写，不凑合 ----------

    @Test
    void pickTop_noEffectiveRule_returnsNull() throws Exception {
        TIsLimRule future = rule("FUTURE", 10, "50", "200", "500",
                "2025-01-01 00:00:00", null);
        TIsLimRule past = rule("PAST", 10, "50", "200", "500",
                "2005-01-01 00:00:00", "2015-01-01 00:00:00");
        // 2000 年：老线还没启用，新线更没影——不许拿临近日期凑合
        assertNull(HazIdentRuler.pickTop(new ArrayList<>(Arrays.asList(future, past)),
                t("2000-06-01 00:00:00")));
        // 空候选
        assertNull(HazIdentRuler.pickTop(new ArrayList<TIsLimRule>(), t("2026-01-01 00:00:00")));
    }
}
