package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIsLimRuleMapper;
import com.fc.v2.model.auto.TIsLimRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 判定线 Service 测试：现评只认现役行且按企业类别各管各；历史凭代号调线允许让位行；坏值挡在门外。
 *
 * @author fuce
 * @date 2026-10-01
 */
@ExtendWith(MockitoExtension.class)
class TIsLimRuleServiceImplTest {

    @Mock
    private TIsLimRuleMapper mapper;

    @InjectMocks
    private TIsLimRuleServiceImpl service;

    private static Date t(String s) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return f.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TIsLimRule row(long id, String code, String siteType, int status,
                                  Integer priority, String th1, String start, String end) {
        TIsLimRule r = new TIsLimRule();
        r.setId(id);
        r.setRuleCode(code);
        r.setSiteType(siteType);
        r.setStatus(status);
        r.setPriority(priority);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(new BigDecimal("500"));
        r.setTh3Max(new BigDecimal("1000"));
        r.setEffStart(t(start));
        r.setEffEnd(end == null ? null : t(end));
        r.setDelFlag(0);
        return r;
    }

    /** 生产企业册子：旧线（让位）、新线（现役）；另有储存企业一条，不该串到生产。 */
    private List<TIsLimRule> all;

    @BeforeEach
    void seed() {
        all = new java.util.ArrayList<>(Arrays.asList(
                row(2, "SJ-2014", "生产企业", 1, 10, "100", "2015-01-01 00:00:00", "2025-01-01 00:00:00"),
                row(3, "SJ-2025", "生产企业", 0, 10, "50", "2025-01-01 00:00:00", null),
                row(6, "SJ-2025S", "储存企业", 0, 10, "80", "2025-01-01 00:00:00", null)));
        // 模拟库侧过滤：按 wrapper 片段里出现的列名配合绑定参数过滤（del_flag/status/site_type/rule_code）。
        org.mockito.Mockito.lenient().when(mapper.selectList(any(QueryWrapper.class))).thenAnswer(inv -> {
            QueryWrapper<?> w = inv.getArgument(0);
            String seg = w.getSqlSegment() == null ? "" : w.getSqlSegment();
            List<Object> vals = new ArrayList<>(w.getParamNameValuePairs().values());
            List<TIsLimRule> out = new ArrayList<>(all);
            if (seg.contains("del_flag")) {
                out.removeIf(r -> r.getDelFlag() == null || r.getDelFlag() != 0);
            }
            if (seg.contains("status")) {
                out.removeIf(r -> r.getStatus() == null || r.getStatus() != 0);
            }
            for (Object v : vals) {
                if (v instanceof String) {
                    String sv = (String) v;
                    if (seg.contains("rule_code")) {
                        out.removeIf(r -> !sv.equals(r.getRuleCode()));
                    } else if (seg.contains("site_type")) {
                        out.removeIf(r -> !sv.equals(r.getSiteType()));
                    }
                }
            }
            return out;
        });
        org.mockito.Mockito.lenient().when(mapper.selectById(2L)).thenReturn(all.get(0));
        org.mockito.Mockito.lenient().when(mapper.selectById(3L)).thenReturn(all.get(1));
    }

    @Test
    void currentRule_onlyActiveRow_andBySiteType() {
        // 2026 年辨识生产企业：只认现役 SJ-2025，让位的 SJ-2014 不许参与，储存线不串档
        TIsLimRule r = service.currentRule("生产企业", t("2026-03-01 00:00:00"));
        assertEquals("SJ-2025", r.getRuleCode());
        // 60 吨：旧线算二级（≤100），新线收紧后应顶到二级以上——这里压线 50 验新线生效
        assertEquals(1, service.gradeCurrent(new BigDecimal("50"), "生产企业", t("2026-03-01 00:00:00")));
        assertEquals(2, service.gradeCurrent(new BigDecimal("51"), "生产企业", t("2026-03-01 00:00:00")));

        TIsLimRule store = service.currentRule("储存企业", t("2026-03-01 00:00:00"));
        assertEquals("SJ-2025S", store.getRuleCode());
    }

    @Test
    void currentRule_handoffDay_oldRuleAlreadyOut() {
        // 现评口径：只查现役行。交棒那天旧线已不含、新线已含——全市场只认新线。
        TIsLimRule r = service.currentRule("生产企业", t("2025-01-01 00:00:00"));
        assertEquals("SJ-2025", r.getRuleCode());
        // 现役行在它启用之前查不到：现评不允许翻让位行凑合
        assertNull(service.currentRule("生产企业", t("2024-12-31 23:59:59")));
        assertNull(service.currentRule("生产企业", t("2000-06-01 00:00:00")));
    }

    @Test
    void historicalTop_supplementaryIdent_retiredRowAllowedAtItsDay() {
        // 老罐子补辨识：2018 年查档能调起那年现役、如今已让位的 SJ-2014
        TIsLimRule r = service.historicalTop("生产企业", t("2018-05-01 00:00:00"));
        assertEquals("SJ-2014", r.getRuleCode());
        // 交棒那天查档也只认新线（半开时段，与现评同一边界口径）
        TIsLimRule handoff = service.historicalTop("生产企业", t("2025-01-01 00:00:00"));
        assertEquals("SJ-2025", handoff.getRuleCode());
        // 2000 年：两线都没启用，查档照样查不到——不许拿临近日期凑合
        assertNull(service.historicalTop("生产企业", t("2000-06-01 00:00:00")));
        // 储存企业不串到生产的线
        assertEquals("SJ-2025S", service.historicalTop("储存企业", t("2026-03-01 00:00:00")).getRuleCode());
    }

    @Test
    void currentRule_noActiveAtThatDate_returnsNull_neverFallback() {
        // 2000 年补辨识：两条线都没启用，明写无可用标准，不许拿临近日期凑合
        assertNull(service.currentRule("生产企业", t("2000-06-01 00:00:00")));
        assertEquals(0, service.gradeCurrent(new BigDecimal("60"), "生产企业", t("2000-06-01 00:00:00")));
    }

    @Test
    void gradeCurrent_badQty_returnsZero() {
        assertEquals(0, service.gradeCurrent(null, "生产企业", t("2026-01-01 00:00:00")));
        assertEquals(0, service.gradeCurrent(new BigDecimal("-1"), "生产企业", t("2026-01-01 00:00:00")));
        assertEquals(0, service.gradeCurrent(new BigDecimal("99999999"), "生产企业", t("2026-01-01 00:00:00")));
    }

    @Test
    void historicalRule_retiredRowAllowed_butOnlyAtItsDay() {
        // 2026 年复核一张 2018 年的老单（凭 SJ-2014 调线）：让位行允许调起
        TIsLimRule r = service.historicalRule("SJ-2014", t("2018-05-01 00:00:00"));
        assertEquals("SJ-2014", r.getRuleCode());
        // 但在它交棒之后按这个代号调，仍然调不到——不许拿今天的现役线顶替老代号
        assertNull(service.historicalRule("SJ-2014", t("2026-05-01 00:00:00")));
        assertNull(service.historicalRule("SJ-2014", t("2000-05-01 00:00:00")));
    }

    @Test
    void usable_followsCurrentRuleMouth() {
        assertTrue(service.usable(3L, t("2026-01-01 00:00:00")));
        // 让位行不可用于现评（哪怕今天还在它…它已交棒）
        assertFalse(service.usable(2L, t("2018-01-01 00:00:00")));
        assertFalse(service.usable(2L, t("2026-01-01 00:00:00")));
        assertSame(3L, service.selectTIsLimRuleById(3L).getId());
    }
}
