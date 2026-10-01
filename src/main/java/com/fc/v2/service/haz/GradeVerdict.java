package com.fc.v2.service.haz;

import com.fc.v2.model.auto.TIsLimRule;

import java.math.BigDecimal;

/**
 * 一次定级的落锤结论——辨识单与复核结论都从这一个出口取数。
 *
 * 三态分明：
 * - 取到线且够档：grade=1/2/3，带所依线；
 * - 取到线但不够临界线：grade=0（不入名录，照样落单留痕），带所依线；
 * - 那天查不到可用线：noStandard=true，grade/线皆空——明写"无可用标准"，不许拿临近日期的线凑合。
 *
 * @author fuce
 * @date 2026-10-01
 */
public final class GradeVerdict {

    /** 所依判定线；无可用标准时为 null */
    private final TIsLimRule rule;
    /** 级别 1/2/3，0=未达临界线；无可用标准时为 null */
    private final Integer grade;
    /** 无可用标准 */
    private final boolean noStandard;

    private GradeVerdict(TIsLimRule rule, Integer grade, boolean noStandard) {
        this.rule = rule;
        this.grade = grade;
        this.noStandard = noStandard;
    }

    public static GradeVerdict graded(TIsLimRule rule, int grade) {
        if (rule == null) {
            throw new IllegalArgumentException("定级结论必须带所依线");
        }
        return new GradeVerdict(rule, grade, false);
    }

    /** 无可用标准：明写，不许凑合 */
    public static GradeVerdict noStandard() {
        return new GradeVerdict(null, null, true);
    }

    public TIsLimRule getRule() {
        return rule;
    }

    public Integer getGrade() {
        return grade;
    }

    public boolean isNoStandard() {
        return noStandard;
    }

    /** 落单时储存量与界值的比较结果（供留痕话术），不在理上的量由调用方先拦。 */
    public String describe(BigDecimal qty) {
        if (noStandard) {
            return "无可用标准";
        }
        if (grade != null && grade == 0) {
            return "储存量" + qty + "吨未达线[" + rule.getRuleCode() + "]临界线，不入名录";
        }
        return "储存量" + qty + "吨依线[" + rule.getRuleCode() + "]定为" + grade + "级";
    }
}
