package com.fc.v2.haz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIsHazCardMapper;
import com.fc.v2.model.auto.TIsHazCard;
import com.fc.v2.model.auto.TIsHazCardReview;
import com.fc.v2.model.auto.TIsLimRule;
import com.fc.v2.service.ITIsHazCardService;
import com.fc.v2.service.ITIsLimRuleService;
import com.fc.v2.service.haz.RosterScreen;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 辨识单开具/落锤/历史复核/名录屏的 H2 集成测试（真 SQL 落库、事务回滚）。
 *
 * @author fuce
 * @date 2026-10-01
 */
@SpringBootTest(classes = HazITConfig.class)
@Transactional
class HazCardIdentifyTest {

    private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static {
        DAY.setTimeZone(com.fc.v2.service.haz.LimRuleRuler.ZONE);
        TS.setTimeZone(com.fc.v2.service.haz.LimRuleRuler.ZONE);
    }

    @Autowired
    private ITIsHazCardService cardService;
    @Autowired
    private ITIsLimRuleService limRuleService;
    @Autowired
    private TIsHazCardMapper cardMapper;

    private static Date day(String s) {
        try {
            return DAY.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Date ts(String s) {
        try {
            return TS.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 固定"今天"，保证可复现。 */
    private static final Date NOW = ts("2026-10-01 10:00:00");

    private TIsHazCard newCard(int siteId, String qty, String identDate) {
        TIsHazCard c = new TIsHazCard();
        c.setSiteId(siteId);
        c.setQty(qty == null ? null : new BigDecimal(qty));
        c.setIdentDate(identDate == null ? null : day(identDate));
        return c;
    }

    @Test
    void identify_currentProduction_freezeGradeAndLineContext() {
        TIsHazCard c = cardService.identify(newCard(0, "120.00", "2026-10-01"), NOW);
        assertNotNull(c.getBillNo());
        assertEquals(Integer.valueOf(2), c.getGradeLevel());          // 120 ∈ [100,300)
        assertEquals("SC2025-P", c.getRuleCode());
        assertEquals(ts("2025-01-01 00:00:00"), c.getRuleEffStart());
        assertEquals(Integer.valueOf(0), c.getNoStandard());
        assertEquals("EN00", c.getSiteNo());
        // 校验串把落锤上下文串死。
        assertTrue(c.getContent().contains("SC2025-P") && c.getContent().contains("120.00"));
    }

    @Test
    void identify_storage_parkLineWinsByPriority() {
        // 260 吨：园区加严线（二级 250）定二级；市局线（二级 300）会定三级——顺位高的说了算。
        TIsHazCard c = cardService.identify(newCard(1, "260.00", "2026-10-01"), NOW);
        assertEquals("PZ2025-S", c.getRuleCode());
        assertEquals(Integer.valueOf(2), c.getGradeLevel());
    }

    @Test
    void identify_exactlyOnThreshold_countsUpperTier() {
        TIsHazCard c = cardService.identify(newCard(0, "30.00", "2026-10-01"), NOW);
        assertEquals(Integer.valueOf(3), c.getGradeLevel());
    }

    @Test
    void identify_belowAllThresholds_grade0StillRecorded() {
        TIsHazCard c = cardService.identify(newCard(0, "10.00", "2026-10-01"), NOW);
        assertEquals(Integer.valueOf(0), c.getGradeLevel());
        assertEquals("SC2025-P", c.getRuleCode());
    }

    @Test
    void identify_dirtyQty_rejectedNoRow() {
        int before = cardMapper.selectCount(new QueryWrapper<TIsHazCard>().eq("del_flag", 0));
        assertThrows(IllegalArgumentException.class,
                () -> cardService.identify(newCard(0, null, "2026-10-01"), NOW));
        assertThrows(IllegalArgumentException.class,
                () -> cardService.identify(newCard(0, "-5.00", "2026-10-01"), NOW));
        assertThrows(IllegalArgumentException.class,
                () -> cardService.identify(newCard(0, "999999999", "2026-10-01"), NOW));
        int after = cardMapper.selectCount(new QueryWrapper<TIsHazCard>().eq("del_flag", 0));
        assertEquals(before, after); // 脏值半行单都不落
    }

    @Test
    void identify_noUsableLine_writtenExplicitly() {
        // 2008 年：册子最早一条 1990 年才启用，更早无可用标准（且不许拿临近日期凑合）。
        TIsHazCard c = cardService.identify(newCard(0, "500.00", "1988-05-01"), NOW);
        assertEquals(Integer.valueOf(1), c.getNoStandard());
        assertNull(c.getGradeLevel());
        assertNull(c.getRuleCode());
        assertNull(c.getRuleEffStart());
        assertTrue(c.getContent().endsWith("|NA|-|-"));
    }

    @Test
    void identify_backfillOldTank_usesLineEffectiveAtBuildYear() {
        // 老罐子 2000 年建成，今年补辨识：取 1990 年册（一级空、二级 300、三级 100）。
        TIsHazCard big = cardService.identify(newCard(0, "350.00", "2000-05-01"), NOW);
        assertEquals("SC1990-P", big.getRuleCode());
        assertEquals(Integer.valueOf(2), big.getGradeLevel()); // 空一级顺到二级
        TIsHazCard mid = cardService.identify(newCard(0, "150.00", "2000-05-01"), NOW);
        assertEquals(Integer.valueOf(3), mid.getGradeLevel());
    }

    @Test
    void identify_handoverDay_oldLineProcess_newLineOutcome() {
        // 40 吨：旧线三级门槛 50，够不上、不入名录（0）；新线门槛收紧到 30，定为三级。
        // 这正是去年换标准后按新线抽查、几家该入名录却漏了的场景。
        TIsHazCard oldCard = cardService.identify(newCard(0, "40.00", "2024-12-31"), NOW);
        assertEquals("SC2009-P", oldCard.getRuleCode());
        assertEquals(Integer.valueOf(0), oldCard.getGradeLevel());
        // 交棒日 2025-01-01 起只认新线：同 40 吨进三级。
        TIsHazCard newCard = cardService.identify(newCard(0, "40.00", "2025-01-01"), NOW);
        assertEquals("SC2025-P", newCard.getRuleCode());
        assertEquals(Integer.valueOf(3), newCard.getGradeLevel());
    }

    @Test
    void frozenFields_neverOverwritten() {
        TIsHazCard c = cardService.identify(newCard(0, "120.00", "2026-10-01"), NOW);
        TIsHazCard tamper = new TIsHazCard();
        tamper.setId(c.getId());
        tamper.setGradeLevel(1);              // 想改级别
        tamper.setRuleCode("SC2009-P");       // 想换老线
        tamper.setQty(new BigDecimal("999")); // 想改存量
        tamper.setStatus(2);
        cardService.updateTIsHazCard(tamper);

        TIsHazCard reloaded = cardService.selectTIsHazCardById(c.getId());
        assertEquals(Integer.valueOf(2), reloaded.getGradeLevel());
        assertEquals("SC2025-P", reloaded.getRuleCode());
        assertEquals(new BigDecimal("120.00"), reloaded.getQty());
        assertEquals(Integer.valueOf(2), reloaded.getStatus()); // 非落锤字段可动
    }

    @Test
    void review_reEvaluatesByCode_keepsBothContextsAndNeverTouchesOriginal() {
        TIsHazCard c = cardService.identify(newCard(0, "120.00", "2024-12-31"), NOW);
        assertEquals("SC2009-P", c.getRuleCode());
        Integer origGrade = c.getGradeLevel();

        TIsHazCardReview r = cardService.reviewCard(c.getId(), day("2026-10-01"), NOW);
        // 凭原代号调旧线、按原辨识日重评：结论一致，两套上下文各留各的。
        assertEquals("SC2009-P", r.getOrigRuleCode());
        assertEquals("SC2009-P", r.getReviewRuleCode());
        assertEquals(ts("2009-06-01 00:00:00"), r.getReviewRuleEffStart());
        assertEquals(origGrade, r.getReviewGradeLevel());
        assertEquals(Integer.valueOf(1), r.getConsistent());
        assertEquals(day("2024-12-31"), r.getIdentDate()); // 没有拿今天的标准重取线
        assertEquals(Integer.valueOf(0), r.getNoStandard());

        // 原单原样。
        TIsHazCard reloaded = cardService.selectTIsHazCardById(c.getId());
        assertEquals(origGrade, reloaded.getGradeLevel());
        assertEquals("SC2009-P", reloaded.getRuleCode());

        // 可多次复核，各留各的。
        cardService.reviewCard(c.getId(), day("2026-10-02"), NOW);
        assertEquals(2, cardService.listReviews(c.getId()).size());
    }

    @Test
    void review_noStandardCard_staysNoStandard() {
        TIsHazCard c = cardService.identify(newCard(0, "500.00", "1988-05-01"), NOW);
        TIsHazCardReview r = cardService.reviewCard(c.getId(), NOW, NOW);
        assertEquals(Integer.valueOf(1), r.getNoStandard());
        assertNull(r.getReviewRuleCode());
    }

    @Test
    void rosterScreen_countsFrozenCardsAndSameSourceForBothScreens() {
        cardService.identify(newCard(0, "350.00", "2026-09-01"), NOW);   // 一级
        cardService.identify(newCard(0, "120.00", "2026-09-02"), NOW);   // 二级
        cardService.identify(newCard(0, "50.00", "2026-09-03"), NOW);    // 三级
        cardService.identify(newCard(0, "10.00", "2026-09-04"), NOW);    // 未达
        cardService.identify(newCard(0, "500.00", "1988-05-01"), NOW);   // 无可用标准

        // 自检屏与抽查屏：同一方法、同一库数，口径天然一致。
        RosterScreen park = cardService.rosterScreen(NOW);
        RosterScreen bureau = cardService.rosterScreen(day("2026-10-01"));
        for (RosterScreen s : new RosterScreen[]{park, bureau}) {
            assertEquals(5, s.getTotalCnt());
            assertEquals(1, s.getGrade1Cnt());
            assertEquals(1, s.getGrade2Cnt());
            assertEquals(1, s.getGrade3Cnt());
            assertEquals(3, s.getRosterCnt());
            assertEquals(1, s.getBelowCnt());
            assertEquals(1, s.getNoStandardCnt());
        }
        // 屏日再往前挪，只点算更早落锤的单子（含 1988 无标准单）。
        RosterScreen earlier = cardService.rosterScreen(day("2026-09-02"));
        assertEquals(3, earlier.getTotalCnt());
        assertEquals(1, earlier.getNoStandardCnt());
    }

    @Test
    void locateAndCountActive_shareRulerSemantics() {
        assertEquals("PZ2025-S",
                limRuleService.locate("储存企业", day("2026-10-01"), NOW).getRuleCode());
        assertEquals(2, limRuleService.countActive("储存企业", day("2026-10-01")));
        assertEquals(1, limRuleService.countActive("生产企业", day("2026-10-01")));
        // 历史日：1990 年之前册子无线（SC1990 1990 才启用），不许拿临近日期凑合。
        assertNull(limRuleService.locate("生产企业", day("1988-01-01"), NOW));
        assertNotNull(limRuleService.locate("生产企业", day("2000-05-01"), NOW));
    }

    // ---------- 判定线册子：只添行、翻旗，永不删旧行 ----------

    @Test
    void ruleBook_addNewVersionAndRetireOldLine() {
        // 2030 再收紧：登一条 2030 起的新生产企业线。
        TIsLimRule next = new TIsLimRule();
        next.setRuleCode("SC2030-P");
        next.setRuleName("2030生产企业判定线");
        next.setRuleSource(1);
        next.setApplyType("生产企业");
        next.setTh1Max(new BigDecimal("200"));
        next.setTh2Max(new BigDecimal("80"));
        next.setTh3Max(new BigDecimal("20"));
        next.setEffStart(day("2030-01-01"));
        next.setPriority(10);
        TIsLimRule saved = limRuleService.addRule(next);
        assertEquals(Integer.valueOf(0), saved.getStatus());

        // 现评日仍在 2026，不受 2030 线影响。
        assertEquals("SC2025-P", limRuleService.locate("生产企业", day("2026-10-01"), NOW).getRuleCode());

        // 同代号版本窗重叠拒登（交棒日说不清）。
        TIsLimRule dup = new TIsLimRule();
        dup.setRuleCode("SC2025-P");
        dup.setApplyType("生产企业");
        dup.setTh1Max(new BigDecimal("100"));
        dup.setTh2Max(new BigDecimal("50"));
        dup.setTh3Max(new BigDecimal("10"));
        dup.setEffStart(day("2026-01-01"));
        assertThrows(IllegalArgumentException.class, () -> limRuleService.addRule(dup));

        // 把现行 SC2025-P 在 2030 交棒翻旗：旧行保留，只许查历史。
        TIsLimRule active2025 = limRuleService.locate("生产企业", day("2026-10-01"), NOW);
        TIsLimRule retired = limRuleService.retireRule(active2025.getId(), day("2030-01-01"));
        assertEquals(Integer.valueOf(1), retired.getStatus());
        assertEquals(day("2030-01-01"), retired.getEffEnd());
        // 已让位不许重复翻旗。
        assertThrows(IllegalArgumentException.class,
                () -> limRuleService.retireRule(active2025.getId(), day("2031-01-01")));
    }
}
