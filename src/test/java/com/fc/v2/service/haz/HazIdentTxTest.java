package com.fc.v2.service.haz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIsEnterpriseMapper;
import com.fc.v2.mapper.auto.TIsHazCardMapper;
import com.fc.v2.mapper.auto.TIsHazReviewMapper;
import com.fc.v2.model.auto.TIsEnterprise;
import com.fc.v2.model.auto.TIsHazCard;
import com.fc.v2.model.auto.TIsHazReview;
import com.fc.v2.model.auto.TIsLimRule;
import com.fc.v2.service.ITIsLimRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HazIdentTx} 测试：定级写死、坏值退回不落库、无可用标准明写、历史复核不拿今日标准改昨日单。
 *
 * @author fuce
 * @date 2026-10-01
 */
@ExtendWith(MockitoExtension.class)
class HazIdentTxTest {

    @Mock
    private TIsHazCardMapper cardMapper;
    @Mock
    private TIsHazReviewMapper reviewMapper;
    @Mock
    private TIsEnterpriseMapper enterpriseMapper;
    @Mock
    private ITIsLimRuleService limRuleService;

    @InjectMocks
    private HazIdentTx tx;

    private static Date t(String s) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return f.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TIsEnterprise site(String type) {
        TIsEnterprise s = new TIsEnterprise();
        s.setId(0L);
        s.setSiteNo("EN00");
        s.setSiteType(type);
        s.setStatus(0);
        return s;
    }

    private static TIsLimRule rule(String code, String effStart, String th1, String th2, String th3) {
        TIsLimRule r = new TIsLimRule();
        r.setRuleCode(code);
        r.setEffStart(t(effStart));
        r.setTh1Max(new BigDecimal(th1));
        r.setTh2Max(new BigDecimal(th2));
        r.setTh3Max(new BigDecimal(th3));
        return r;
    }

    @BeforeEach
    void noDup() {
        org.mockito.Mockito.lenient().when(cardMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);
        org.mockito.Mockito.lenient().when(enterpriseMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(site("生产企业"));
    }

    @Test
    void identify_todayGradesByActiveRule_andWritesContext() {
        // 今天辨识：走现评，只认现役新线 50/200/500，60 吨二级
        Date today = DeadlineRuler.startOfDay(new Date());
        when(limRuleService.currentRule("生产企业", today))
                .thenReturn(rule("SJ-2025", "2025-01-01 00:00:00", "50", "200", "500"));

        TIsHazCard card = new TIsHazCard();
        card.setBillNo("BS-2026-001");
        card.setSiteId(0);
        card.setQty(new BigDecimal("60"));

        TIsHazCard saved = tx.identify(card, null);
        assertEquals(2, saved.getGradeLevel());
        assertEquals("SJ-2025", saved.getRuleCode());
        assertEquals(t("2025-01-01 00:00:00"), saved.getRuleEffStart());
        assertEquals(today, saved.getIdentDate());
        assertEquals(new BigDecimal("60"), saved.getQty());
        verify(cardMapper).insert(any(TIsHazCard.class));
    }

    @Test
    void identify_supplementary_oldTankUsesRuleEffectiveAtBuildYear() {
        // 老罐子 2018 年建成、今年补辨识：走查档，调起那年现役如今已让位的 SJ-2014（100/300/500）。
        // 80 吨在旧线是一级；若错用 2025 新线（50/200/500）会变二级——必须落一级。
        Date identDay = DeadlineRuler.startOfDay(t("2018-05-01 00:00:00"));
        when(limRuleService.historicalTop("生产企业", identDay))
                .thenReturn(rule("SJ-2014", "2015-01-01 00:00:00", "100", "300", "500"));

        TIsHazCard card = new TIsHazCard();
        card.setBillNo("BS-2018-001");
        card.setSiteId(0);
        card.setQty(new BigDecimal("80"));

        TIsHazCard saved = tx.identify(card, t("2018-05-01 08:00:00"));
        assertEquals(1, saved.getGradeLevel());
        assertEquals("SJ-2014", saved.getRuleCode());
        assertEquals(t("2015-01-01 00:00:00"), saved.getRuleEffStart());
        // 补辨识不许顺手翻现役线
        verify(limRuleService, never()).currentRule(any(), any());
    }

    @Test
    void identify_badQty_rejected_nothingInserted() {
        TIsHazCard neg = new TIsHazCard();
        neg.setBillNo("BS-X1");
        neg.setSiteId(0);
        neg.setQty(new BigDecimal("-5"));
        assertThrows(IllegalArgumentException.class, () -> tx.identify(neg, t("2026-03-01 00:00:00")));

        TIsHazCard monster = new TIsHazCard();
        monster.setBillNo("BS-X2");
        monster.setSiteId(0);
        monster.setQty(new BigDecimal("99999999"));
        assertThrows(IllegalArgumentException.class, () -> tx.identify(monster, t("2026-03-01 00:00:00")));

        TIsHazCard empty = new TIsHazCard();
        empty.setBillNo("BS-X3");
        empty.setSiteId(0);
        assertThrows(IllegalArgumentException.class, () -> tx.identify(empty, t("2026-03-01 00:00:00")));

        verify(cardMapper, never()).insert(any());
        // 坏值在选线之前就挡回，根本不该去翻册子
        verify(limRuleService, never()).currentRule(any(), any());
    }

    @Test
    void identify_noUsableRule_writesNoStandard_stillFiled() {
        // 老罐子 2000 年建成补辨识：那年没有可用线，查档也查不到，明写"无可用标准"
        Date identDay = DeadlineRuler.startOfDay(t("2000-06-01 00:00:00"));
        when(limRuleService.historicalTop("生产企业", identDay)).thenReturn(null);

        TIsHazCard card = new TIsHazCard();
        card.setBillNo("BS-OLD-001");
        card.setSiteId(0);
        card.setQty(new BigDecimal("120"));

        TIsHazCard saved = tx.identify(card, t("2000-06-01 08:00:00"));
        assertNull(saved.getGradeLevel());
        assertEquals(HazIdentRuler.NO_STANDARD, saved.getRuleCode());
        assertNull(saved.getRuleEffStart());
        verify(cardMapper).insert(any(TIsHazCard.class));
    }

    @Test
    void review_replaysByOriginalCodeAtIdentDate_contextsKeptApart() {
        // 原单：2018 年按 SJ-2014（100/300/500）定的一级，80 吨。
        // 若错拿 2026 年现役新线 50/200/500，80 吨会变二级——复核必须仍给一级。
        TIsHazCard old = new TIsHazCard();
        old.setId(77L);
        old.setBillNo("BS-2018-009");
        old.setQty(new BigDecimal("80"));
        old.setIdentDate(DeadlineRuler.startOfDay(t("2018-05-01 00:00:00")));
        old.setRuleCode("SJ-2014");
        old.setRuleEffStart(t("2015-01-01 00:00:00"));
        old.setGradeLevel(1);
        old.setDelFlag(0);
        when(cardMapper.selectById(77L)).thenReturn(old);
        when(limRuleService.historicalRule("SJ-2014", old.getIdentDate()))
                .thenReturn(rule("SJ-2014", "2015-01-01 00:00:00", "100", "300", "500"));

        TIsHazReview rv = tx.review(77L, t("2026-10-01 09:00:00"));
        assertEquals("SJ-2014", rv.getReviewRuleCode());
        assertEquals(Integer.valueOf(1), rv.getReviewGradeLevel());
        assertEquals(Integer.valueOf(1), rv.getOrigGradeLevel());
        assertEquals(1, rv.getSameResult());
        // 原结论上下文原样抄存
        assertEquals("SJ-2014", rv.getOrigRuleCode());
        assertEquals(t("2015-01-01 00:00:00"), rv.getOrigRuleEffStart());
        // 复核只插复核行，绝不回写原单
        verify(cardMapper, never()).updateById(any());
        verify(reviewMapper).insert(any(TIsHazReview.class));
    }

    @Test
    void review_drift_isRecorded_notSilentlyOverwritten() {
        // 册子被人动过（数据异常）：同代号调起的线如今把 80 吨判成二级——
        // 复核结论照实记二级、same=0，原单一级纹丝不动，留给倒查。
        TIsHazCard old = new TIsHazCard();
        old.setId(78L);
        old.setBillNo("BS-2018-010");
        old.setQty(new BigDecimal("80"));
        old.setIdentDate(DeadlineRuler.startOfDay(t("2018-05-01 00:00:00")));
        old.setRuleCode("SJ-2014");
        old.setRuleEffStart(t("2015-01-01 00:00:00"));
        old.setGradeLevel(1);
        old.setDelFlag(0);
        when(cardMapper.selectById(78L)).thenReturn(old);
        when(limRuleService.historicalRule("SJ-2014", old.getIdentDate()))
                .thenReturn(rule("SJ-2014", "2015-01-01 00:00:00", "50", "200", "500"));

        TIsHazReview rv = tx.review(78L, t("2026-10-01 09:00:00"));
        assertEquals(Integer.valueOf(2), rv.getReviewGradeLevel());
        assertEquals(0, rv.getSameResult());
        assertEquals(Integer.valueOf(1), old.getGradeLevel());
        verify(cardMapper, never()).updateById(any());
    }

    @Test
    void review_originalNoStandard_reviewAlsoNoStandard() {
        TIsHazCard old = new TIsHazCard();
        old.setId(79L);
        old.setBillNo("BS-OLD-002");
        old.setQty(new BigDecimal("120"));
        old.setIdentDate(DeadlineRuler.startOfDay(t("2000-06-01 00:00:00")));
        old.setRuleCode(HazIdentRuler.NO_STANDARD);
        old.setGradeLevel(null);
        old.setDelFlag(0);
        when(cardMapper.selectById(79L)).thenReturn(old);

        TIsHazReview rv = tx.review(79L, t("2026-10-01 09:00:00"));
        assertEquals(HazIdentRuler.NO_STANDARD, rv.getReviewRuleCode());
        assertNull(rv.getReviewGradeLevel());
        assertEquals(1, rv.getSameResult());
        // 没有代号可凭，不许另挑一条线充数
        verify(limRuleService, never()).historicalRule(any(), any());
    }

    @Test
    void review_missingCard_throws() {
        when(cardMapper.selectById(404L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> tx.review(404L, new Date()));
    }

    @Test
    void identify_retiredSite_rejected() {
        TIsEnterprise retired = site("生产企业");
        retired.setStatus(1);
        when(enterpriseMapper.selectOne(any(QueryWrapper.class))).thenReturn(retired);

        TIsHazCard card = new TIsHazCard();
        card.setBillNo("BS-R1");
        card.setSiteId(1);
        card.setQty(new BigDecimal("60"));
        assertThrows(IllegalArgumentException.class, () -> tx.identify(card, t("2026-03-01 00:00:00")));
        verify(cardMapper, never()).insert(any());
    }
}
