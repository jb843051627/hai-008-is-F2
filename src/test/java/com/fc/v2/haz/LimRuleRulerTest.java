package com.fc.v2.haz;

import com.fc.v2.model.auto.TIsLimRule;
import com.fc.v2.service.haz.GradeVerdict;
import com.fc.v2.service.haz.LimRuleRuler;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定级尺子 {@link LimRuleRuler} 的纯单测：界值含本数、空档顺推、交棒半开区间、
 * 顺位/代号决胜、现行让位分流、脏值退回、无可用标准。
 *
 * @author fuce
 * @date 2026-10-01
 */
class LimRuleRulerTest {

    private static final SimpleDateFormat F = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static {
        F.setTimeZone(LimRuleRuler.ZONE);
    }

    private static Date ts(String s) {
        try {
            return F.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Date day(String s) {
        return ts(s + " 00:00:00");
    }

    private static TIsLimRule rule(String code, String type,
                                   String th1, String th2, String th3,
                                   String start, String end, int priority, int status) {
        TIsLimRule r = new TIsLimRule();
        r.setRuleCode(code);
        r.setApplyType(type);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        r.setEffStart(ts(start));
        r.setEffEnd(end == null ? null : ts(end));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    // ---------- 套档：门槛含本数、空档顺推 ----------

    @Test
    void grade_boundaryCountBelongsToUpperTier() {
        // 界值是入门门槛（含本数）：踩着 30 算三级、踩着 100 算二级、踩着 300 算一级。
        TIsLimRule r = rule("R", "生产企业", "300", "100", "30",
                "2025-01-01 00:00:00", null, 10, 0);
        assertEquals(3, LimRuleRuler.grade(r, new BigDecimal("30")));
        assertEquals(2, LimRuleRuler.grade(r, new BigDecimal("100")));
        assertEquals(1, LimRuleRuler.grade(r, new BigDecimal("300")));
    }

    @Test
    void grade_justBelowThresholdDropsTier() {
        TIsLimRule r = rule("R", "生产企业", "300", "100", "30",
                "2025-01-01 00:00:00", null, 10, 0);
        assertEquals(0, LimRuleRuler.grade(r, new BigDecimal("29.99")));
        assertEquals(3, LimRuleRuler.grade(r, new BigDecimal("99.99")));
        assertEquals(2, LimRuleRuler.grade(r, new BigDecimal("299.99")));
        assertEquals(1, LimRuleRuler.grade(r, new BigDecimal("100000")));
    }

    @Test
    void grade_emptyTierIsSkipped_notAnError() {
        // 早年老线：一级那格空着——不设一级档，顺到二级继续推。
        TIsLimRule old = rule("SC1990", "生产企业", null, "300", "100",
                "1990-01-01 00:00:00", null, 10, 0);
        assertEquals(3, LimRuleRuler.grade(old, new BigDecimal("100")));
        assertEquals(2, LimRuleRuler.grade(old, new BigDecimal("300")));
        // 量再大，没设一级档就是定不上一级——空档是"不设"，不是断档报错。
        assertEquals(2, LimRuleRuler.grade(old, new BigDecimal("9999")));
        // 二级也空：继续顺到三级。
        TIsLimRule only3 = rule("X", null, null, null, "50",
                "2025-01-01 00:00:00", null, 10, 0);
        assertEquals(3, LimRuleRuler.grade(only3, new BigDecimal("50")));
        assertEquals(0, LimRuleRuler.grade(only3, new BigDecimal("49")));
    }

    // ---------- 脏值：空、负、超大怪值，不定级，退回重录 ----------

    @Test
    void grade_insaneQtyRejected() {
        TIsLimRule r = rule("R", "生产企业", "300", "100", "30",
                "2025-01-01 00:00:00", null, 10, 0);
        assertThrows(IllegalArgumentException.class,
                () -> LimRuleRuler.grade(r, null));
        assertThrows(IllegalArgumentException.class,
                () -> LimRuleRuler.grade(r, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> LimRuleRuler.grade(r, new BigDecimal("100000000")));
        // 零是合法存量（空罐），定 0 级而不是报错。
        assertEquals(0, LimRuleRuler.grade(r, BigDecimal.ZERO));
    }

    // ---------- 交棒日：[起,止) 半开，全市场一套 ----------

    @Test
    void choose_handoverDay_newLineTakesOverAtMidnight() {
        TIsLimRule oldLine = rule("SC2009-P", "生产企业", "500", "200", "50",
                "2009-06-01 00:00:00", "2025-01-01 00:00:00", 10, 1);
        TIsLimRule newLine = rule("SC2025-P", "生产企业", "300", "100", "30",
                "2025-01-01 00:00:00", null, 10, 0);
        List<TIsLimRule> book = Arrays.asList(oldLine, newLine);

        // 交棒前一天（2024-12-31）补辨识（作业在今天 2026）：历史日允许让位行，仍认旧线。
        Date now = day("2026-10-01");
        Date oldDay = day("2024-12-31");
        assertEquals("SC2009-P",
                LimRuleRuler.choose(book, "生产企业", oldDay, now).getRuleCode());

        // 交棒当天零点起，全市只认新线（补辨识也一样，旧线窗不含交棒日）。
        Date newDay = day("2025-01-01");
        assertEquals("SC2025-P",
                LimRuleRuler.choose(book, "生产企业", newDay, now).getRuleCode());

        // 旧线的窗不含交棒日：covers 边界复核。
        assertFalse(LimRuleRuler.covers(oldLine, ts("2025-01-01 00:00:00")));
        assertTrue(LimRuleRuler.covers(oldLine, ts("2024-12-31 23:59:59")));
        assertTrue(LimRuleRuler.covers(newLine, ts("2025-01-01 00:00:00")));
    }

    @Test
    void choose_todayEvaluationIgnoresRetiredLines() {
        TIsLimRule retired = rule("SC2009-P", "生产企业", "500", "200", "50",
                "2009-06-01 00:00:00", null, 10, 1);
        TIsLimRule active = rule("SC2025-P", "生产企业", "300", "100", "30",
                "2025-01-01 00:00:00", null, 10, 0);
        List<TIsLimRule> book = Arrays.asList(retired, active);

        Date today = day("2026-10-01");
        // 让位行即使窗口盖着今天，也不许参与现评。
        TIsLimRule picked = LimRuleRuler.choose(book, "生产企业", today, today);
        assertEquals("SC2025-P", picked.getRuleCode());

        // 只有让位行时，现评 = 查不到线，不许凑合。
        assertNull(LimRuleRuler.choose(new ArrayList<TIsLimRule>(Arrays.asList(retired)),
                "生产企业", today, today));
    }

    @Test
    void choose_historicalBackfillCanUseRetiredLines() {
        TIsLimRule retired = rule("SC1990-P", "生产企业", null, "300", "100",
                "1990-01-01 00:00:00", "2009-06-01 00:00:00", 10, 1);
        Date buildDay = day("2000-05-01");
        // 老罐子今年补辨识：辨识基准日是建成日（历史日），让位行可调。
        TIsLimRule picked = LimRuleRuler.choose(
                new ArrayList<TIsLimRule>(Arrays.asList(retired)), "生产企业", buildDay, day("2026-10-01"));
        assertEquals("SC1990-P", picked.getRuleCode());
    }

    // ---------- 顺位高的说了算；并列代号排后者赢 ----------

    @Test
    void choose_higherPriorityWins() {
        TIsLimRule city = rule("SC2025-S", "储存企业", "600", "300", "60",
                "2025-01-01 00:00:00", null, 10, 0);
        TIsLimRule park = rule("PZ2025-S", "储存企业", "500", "250", "50",
                "2025-01-01 00:00:00", null, 20, 0);
        Date d = day("2026-10-01");
        assertEquals("PZ2025-S",
                LimRuleRuler.choose(Arrays.asList(city, park), "储存企业", d, d).getRuleCode());
    }

    @Test
    void choose_equalPriority_laterCodeWins_sameAllYear() {
        TIsLimRule a = rule("SC2025-A", "生产企业", "400", "200", "40",
                "2025-01-01 00:00:00", null, 10, 0);
        TIsLimRule b = rule("SC2025-B", "生产企业", "300", "100", "30",
                "2025-01-01 00:00:00", null, 10, 0);
        // 春、冬各对一次：四季同一口径，不随日期变。
        for (String d : new String[]{"2026-03-01", "2026-12-31"}) {
            assertEquals("SC2025-B",
                    LimRuleRuler.choose(Arrays.asList(a, b), "生产企业", day(d), day(d)).getRuleCode());
        }
    }

    // ---------- 企业类别各管各 ----------

    @Test
    void choose_siteTypeSegregatedAndWildcard() {
        TIsLimRule prod = rule("P", "生产企业", "300", "100", "30",
                "2025-01-01 00:00:00", null, 10, 0);
        TIsLimRule any = rule("A", null, "800", "400", "80",
                "2025-01-01 00:00:00", null, 5, 0);
        Date d = day("2026-10-01");
        assertEquals("P", LimRuleRuler.choose(Arrays.asList(prod, any), "生产企业", d, d).getRuleCode());
        // 储存企业不套生产线，只吃通吃线。
        assertEquals("A", LimRuleRuler.choose(Arrays.asList(prod, any), "储存企业", d, d).getRuleCode());
    }

    @Test
    void choose_noUsableLine_returnsNull_notNearbyDateSubstitute() {
        TIsLimRule line = rule("SC2009-P", "生产企业", "500", "200", "50",
                "2009-06-01 00:00:00", "2025-01-01 00:00:00", 10, 1);
        // 2009 年之前无线；不许拿临近日期的线凑合。
        assertNull(LimRuleRuler.choose(new ArrayList<TIsLimRule>(Arrays.asList(line)),
                "生产企业", day("2008-01-01"), day("2008-01-01")));
        GradeVerdict v = LimRuleRuler.verdict(
                new ArrayList<TIsLimRule>(Arrays.asList(line)),
                "生产企业", new BigDecimal("100"), day("2008-01-01"), day("2008-01-01"));
        assertTrue(v.isNoStandard());
        assertNull(v.getRule());
        assertNull(v.getGrade());
    }

    // ---------- 凭代号调线（复核） ----------

    @Test
    void chooseByCode_picksThatCodeOnly() {
        TIsLimRule oldP = rule("SCX", "生产企业", "500", "200", "50",
                "2009-06-01 00:00:00", "2025-01-01 00:00:00", 10, 1);
        TIsLimRule newP = rule("SCX", "生产企业", "300", "100", "30",
                "2025-01-01 00:00:00", null, 10, 0);
        List<TIsLimRule> book = Arrays.asList(oldP, newP);
        assertEquals("500", LimRuleRuler.chooseByCode(book, "SCX", day("2024-12-31")).getTh1Max().toPlainString());
        assertEquals("300", LimRuleRuler.chooseByCode(book, "SCX", day("2025-06-01")).getTh1Max().toPlainString());
        // 别的代号查无：null，不许顶替。
        assertNull(LimRuleRuler.chooseByCode(book, "NOPE", day("2025-06-01")));
    }

    @Test
    void verdict_fullShape() {
        TIsLimRule line = rule("SC2025-P", "生产企业", "300", "100", "30",
                "2025-01-01 00:00:00", null, 10, 0);
        List<TIsLimRule> book = new ArrayList<TIsLimRule>(Arrays.asList(line));
        Date d = day("2026-10-01");
        GradeVerdict v1 = LimRuleRuler.verdict(book, "生产企业", new BigDecimal("120"), d, d);
        assertEquals(2, v1.getGrade());
        assertEquals("SC2025-P", v1.getRule().getRuleCode());

        GradeVerdict v0 = LimRuleRuler.verdict(book, "生产企业", new BigDecimal("10"), d, d);
        assertEquals(0, v0.getGrade());
        assertFalse(v0.isNoStandard());
    }
}
