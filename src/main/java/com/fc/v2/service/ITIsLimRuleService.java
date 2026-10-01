package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TIsLimRule;

/**
 * 重大危险源辨识判定线 Service接口（rule-eval 形状：规则求值，无增删改）。
 *
 * 两种口径严格分开：
 * 现评 {@link #currentRule} —— 只认 status=0 现役行，让位行不许参与；
 * 历史 {@link #historicalRule} —— 凭原单代号调线，允许让位行（只许查历史）。
 * 两者用哪条线都只看辨识那天，不看今天；该日无在效线一律 null，明写"无可用标准"。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITIsLimRuleService {

    /** 按主键查询规则（含让位行，后台维护用） */
    TIsLimRule selectTIsLimRuleById(Long id);

    /**
     * 现评选线：at 时刻、该企业类别下现役行（status=0）中按顺位/代号决胜的一条。
     * 无一在效返回 null（调用方写"无可用标准"，不许拿临近日期凑合）。
     */
    TIsLimRule currentRule(String siteType, Date at);

    /**
     * 历史调线：凭代号定位 at 时刻在效的那条；允许让位行（换标准不删行，让位行只许查历史）。
     * 同代号多版本在效（异常数据）按同一决胜序取一条；无一在效返回 null。
     * 原单写"无可用标准"（无代号）的复核不经过这里，复核结论照写"无可用标准"。
     */
    TIsLimRule historicalRule(String ruleCode, Date at);

    /**
     * 补辨识查档：辨识那天在过去（老罐子按建成那年补），取该企业类别下 at 时刻在效、
     * 顺位最高的一条，允许让位行——那年它就是现役线。无一在效返回 null，明写"无可用标准"。
     * 今天的现评不许走这里（今天的活走 {@link #currentRule}，只认现役行）。
     */
    TIsLimRule historicalTop(String siteType, Date at);

    /** at 时刻现役可用线（顺位降序、代号降序）；siteType 为空表示不分类别 */
    List<TIsLimRule> listAvailable(String siteType, Date at);

    /** 单条线在 at 时刻是否现役可用（del_flag=0 且 status=0 且在效），口径与现评选线一致 */
    boolean usable(Long id, Date at);

    /** at 时刻现役可用线条数（列表页角标 / 看板） */
    int countAvailable(String siteType, Date at);

    /**
     * 现评定级：现评选线后套三档界值。坏值 / 无可用线 / 线未设任何档返回 0。
     */
    int gradeCurrent(BigDecimal qty, String siteType, Date at);

    /**
     * 历史定级：凭代号调线后套档。坏值 / 该代号在 at 无在效版本返回 0。
     */
    int gradeByCode(String ruleCode, BigDecimal qty, Date at);
}
