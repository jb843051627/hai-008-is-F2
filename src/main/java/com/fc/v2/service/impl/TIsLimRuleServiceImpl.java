package com.fc.v2.service.impl;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIsLimRuleMapper;
import com.fc.v2.model.auto.TIsLimRule;
import com.fc.v2.service.ITIsLimRuleService;
import com.fc.v2.service.haz.GradeVerdict;
import com.fc.v2.service.haz.LimRuleRuler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 重大危险源辨识判定线 Service业务层处理（rule-book 形状）。
 *
 * 只管册子取数；取线决胜、空档顺推、等于界值上靠、脏值退回等口径全部在 {@link LimRuleRuler}，
 * 园区自检屏与市局抽查屏调同一个实现，天然一个口径。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TIsLimRuleServiceImpl implements ITIsLimRuleService {

    @Resource
    private TIsLimRuleMapper isLimRuleMapper;

    @Override
    public TIsLimRule selectTIsLimRuleById(Long id) {
        return this.isLimRuleMapper.selectById(id);
    }

    @Override
    public List<TIsLimRule> listBook() {
        // 现行排前、让位垫后；同情形内按代号、启用日，翻阅册子时历年版本成串。
        return this.isLimRuleMapper.selectList(new QueryWrapper<TIsLimRule>()
                .eq("del_flag", 0)
                .orderByAsc("rule_code", "eff_start"));
    }

    @Override
    public TIsLimRule addRule(TIsLimRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("判定线内容为空，拒登");
        }
        if (rule.getRuleCode() == null || rule.getRuleCode().trim().isEmpty()) {
            throw new IllegalArgumentException("判定线代号为空，拒登");
        }
        if (rule.getEffStart() == null) {
            throw new IllegalArgumentException("启用日为空，拒登");
        }
        rule.setEffStart(LimRuleRuler.startOfDay(rule.getEffStart()));
        if (rule.getEffEnd() != null) {
            rule.setEffEnd(LimRuleRuler.startOfDay(rule.getEffEnd()));
            if (!rule.getEffEnd().after(rule.getEffStart())) {
                throw new IllegalArgumentException("交棒日必须晚于启用日，拒登");
            }
        }
        if (rule.getStatus() == null) {
            rule.setStatus(LimRuleRuler.STATUS_ACTIVE);
        }
        rule.setDelFlag(0);

        // 同一代号、同一适用类别的各版本生效窗不许重叠——交棒日必须说得清。
        for (TIsLimRule old : listBook()) {
            if (!rule.getRuleCode().equals(old.getRuleCode())) {
                continue;
            }
            boolean sameType = (rule.getApplyType() == null && old.getApplyType() == null)
                    || (rule.getApplyType() != null && rule.getApplyType().equals(old.getApplyType()));
            if (!sameType) {
                continue;
            }
            if (windowsOverlap(rule.getEffStart(), rule.getEffEnd(),
                    old.getEffStart(), old.getEffEnd())) {
                throw new IllegalArgumentException("与既有版本[" + old.getRuleCode()
                        + " " + old.getEffStart() + "]生效窗重叠，交棒日说不清，拒登");
            }
        }
        this.isLimRuleMapper.insert(rule);
        return rule;
    }

    /** 半开区间 [aStart,aEnd) 与 [bStart,bEnd) 是否重叠；空止=仍现行、永远开放。 */
    private boolean windowsOverlap(Date aStart, Date aEnd, Date bStart, Date bEnd) {
        if (aStart == null || bStart == null) {
            return true;
        }
        boolean aBeforeB = aEnd != null && !aEnd.after(bStart);
        boolean bBeforeA = bEnd != null && !bEnd.after(aStart);
        return !aBeforeB && !bBeforeA;
    }

    @Override
    public TIsLimRule retireRule(Long id, Date handoverAt) {
        if (id == null || handoverAt == null) {
            throw new IllegalArgumentException("线ID与交棒日都不能为空");
        }
        TIsLimRule rule = this.isLimRuleMapper.selectById(id);
        if (rule == null || (rule.getDelFlag() != null && rule.getDelFlag() != 0)) {
            throw new IllegalArgumentException("判定线不存在：" + id);
        }
        if (rule.getStatus() != null && rule.getStatus() == LimRuleRuler.STATUS_RETIRED) {
            throw new IllegalArgumentException("该线已让位，不许重复翻旗");
        }
        Date day = LimRuleRuler.startOfDay(handoverAt);
        if (rule.getEffStart() != null && !day.after(rule.getEffStart())) {
            throw new IllegalArgumentException("交棒日必须晚于启用日");
        }
        rule.setEffEnd(day);
        rule.setStatus(LimRuleRuler.STATUS_RETIRED);
        this.isLimRuleMapper.updateById(rule);
        return rule;
    }

    @Override
    public TIsLimRule locate(String siteType, Date identDate, Date now) {
        if (identDate == null) {
            return null;
        }
        Date day = LimRuleRuler.startOfDay(identDate);
        Date clock = now == null ? new Date() : now;
        return LimRuleRuler.choose(listBook(), siteType, day, clock);
    }

    @Override
    public TIsLimRule locateByCode(String ruleCode, Date identDate) {
        if (ruleCode == null || identDate == null) {
            return null;
        }
        return LimRuleRuler.chooseByCode(listBook(), ruleCode, LimRuleRuler.startOfDay(identDate));
    }

    @Override
    public GradeVerdict evaluate(String siteType, BigDecimal qty, Date identDate, Date now) {
        if (identDate == null) {
            throw new IllegalArgumentException("辨识基准日为空，不予定级，退回重录");
        }
        Date day = LimRuleRuler.startOfDay(identDate);
        Date clock = now == null ? new Date() : now;
        return LimRuleRuler.verdict(listBook(), siteType, qty, day, clock);
    }

    @Override
    public GradeVerdict reevaluateByCode(String ruleCode, BigDecimal qty, Date identDate) {
        // 先拦脏值：复核也不许对怪值给结论。
        BigDecimal sane = LimRuleRuler.requireSaneQty(qty);
        if (ruleCode == null || ruleCode.trim().isEmpty() || identDate == null) {
            return GradeVerdict.noStandard();
        }
        TIsLimRule rule = locateByCode(ruleCode, identDate);
        if (rule == null) {
            // 原代号在原辨识日已无线可调——明写无可用标准，绝不拿今天的线顶替。
            return GradeVerdict.noStandard();
        }
        return GradeVerdict.graded(rule, LimRuleRuler.grade(rule, sane));
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TIsLimRule r = this.isLimRuleMapper.selectById(id);
        return r != null
                && (r.getDelFlag() == null || r.getDelFlag() == 0)
                && LimRuleRuler.covers(r, at);
    }

    @Override
    public int countActive(String siteType, Date at) {
        if (at == null) {
            return 0;
        }
        Date clock = new Date();
        List<TIsLimRule> hits = LimRuleRuler.effectiveList(
                listBook(), siteType, LimRuleRuler.startOfDay(at), clock);
        return hits.size();
    }
}
