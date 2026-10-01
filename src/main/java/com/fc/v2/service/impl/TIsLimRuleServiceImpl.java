package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIsLimRuleMapper;
import com.fc.v2.model.auto.TIsLimRule;
import com.fc.v2.service.ITIsLimRuleService;
import com.fc.v2.service.haz.HazIdentRuler;

/**
 * 重大危险源辨识判定线 Service业务层处理（rule-eval 形状）。
 *
 * 选线/套档的口径全部在 {@link HazIdentRuler} 一把尺上，本类只管取数与现评/历史的区分：
 * 现评只取 status=0 现役行；历史凭代号调线，允许 status=1 让位行（只许查历史）。
 *
 * 时段比较不把 java.util.Date 直接丢给 JDBC（serverTimezone 与本机时区不对称会整体偏移，
 * 交棒当天这类边界会错档——既有教训），一律先取数再在内存按 GMT+8 墙钟瞬间比较。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TIsLimRuleServiceImpl implements ITIsLimRuleService {

    @javax.annotation.Resource
    private TIsLimRuleMapper isLimRuleMapper;

    @Override
    public TIsLimRule selectTIsLimRuleById(Long id) {
        return this.isLimRuleMapper.selectById(id);
    }

    @Override
    public TIsLimRule currentRule(String siteType, Date at) {
        if (at == null) {
            return null;
        }
        List<TIsLimRule> active = loadRows(siteType, true);
        return HazIdentRuler.pickTop(active, at);
    }

    @Override
    public TIsLimRule historicalTop(String siteType, Date at) {
        if (at == null) {
            return null;
        }
        // 补辨识查档：让位行也算——辨识那年它还是现役。生效时段半开区间自然把它挡在交棒之后。
        List<TIsLimRule> all = loadRows(siteType, false);
        return HazIdentRuler.pickTop(all, at);
    }

    @Override
    public TIsLimRule historicalRule(String ruleCode, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || at == null) {
            return null;
        }
        // 历史调线不限定 status：换标准不删行，让位行只许查历史，此处正是它唯一的用途。
        List<TIsLimRule> sameCode = this.isLimRuleMapper.selectList(new QueryWrapper<TIsLimRule>()
                .eq("del_flag", 0)
                .eq("rule_code", ruleCode));
        return HazIdentRuler.pickTop(sameCode, at);
    }

    @Override
    public List<TIsLimRule> listAvailable(String siteType, Date at) {
        if (at == null) {
            return new ArrayList<>();
        }
        // 返回口径：在效现役线按决胜序排好，头一条即说了算的。
        List<TIsLimRule> rows = loadRows(siteType, true);
        return HazIdentRuler.effectiveOrdered(rows, at);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TIsLimRule r = this.isLimRuleMapper.selectById(id);
        if (r == null || r.getDelFlag() == null || r.getDelFlag() != 0) {
            return false;
        }
        if (r.getStatus() == null || r.getStatus() != 0) {
            return false;
        }
        return HazIdentRuler.effectiveAt(r, at);
    }

    @Override
    public int countAvailable(String siteType, Date at) {
        return listAvailable(siteType, at).size();
    }

    @Override
    public int gradeCurrent(BigDecimal qty, String siteType, Date at) {
        if (!HazIdentRuler.isQtySane(qty) || at == null) {
            return HazIdentRuler.GRADE_NONE;
        }
        TIsLimRule rule = currentRule(siteType, at);
        if (rule == null) {
            return HazIdentRuler.GRADE_NONE;
        }
        return HazIdentRuler.gradeOf(rule, qty);
    }

    @Override
    public int gradeByCode(String ruleCode, BigDecimal qty, Date at) {
        if (!HazIdentRuler.isQtySane(qty) || at == null) {
            return HazIdentRuler.GRADE_NONE;
        }
        TIsLimRule rule = historicalRule(ruleCode, at);
        if (rule == null) {
            return HazIdentRuler.GRADE_NONE;
        }
        return HazIdentRuler.gradeOf(rule, qty);
    }

    /**
     * 取册子行。activeOnly=true 只取现役行（现评用）；false 连让位行一起（历史调线另行按代号取）。
     */
    private List<TIsLimRule> loadRows(String siteType, boolean activeOnly) {
        QueryWrapper<TIsLimRule> qw = new QueryWrapper<TIsLimRule>().eq("del_flag", 0);
        if (siteType != null && !siteType.trim().isEmpty()) {
            qw.eq("site_type", siteType);
        }
        if (activeOnly) {
            qw.eq("status", 0);
        }
        List<TIsLimRule> rows = this.isLimRuleMapper.selectList(qw);
        return rows == null ? Collections.<TIsLimRule>emptyList() : rows;
    }
}
