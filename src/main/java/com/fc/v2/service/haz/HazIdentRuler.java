package com.fc.v2.service.haz;

import com.fc.v2.model.auto.TIsLimRule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 重大危险源辨识定级——全市场只此一把尺。
 *
 * 口径（写死，四季同一口径）：
 * 1. 用哪条线看辨识那天（墙钟零点），不看今天；时段半开 [effStart, effEnd)，
 *    交棒当天旧线已不含、新线已含，全市场只认一套。
 * 2. 同一时刻并行的多条线：顺位（priority）高的说了算；顺位并列，代号排后（字典序大）那条赢。
 * 3. 定级按原始储存量套三档界值：qty ≤ th1 记 1 级，≤ th2 记 2 级，≤ th3 记 3 级，
 *    超 th3 顶格记 4 级；恰好等于界值算这一档（含）。界值空 = 不设这档，顺到下一档继续推，
 *    不当断档、不报错。
 * 4. 储存量不在理上（空值、负数、超大怪值）一律不定级，退回重录。
 * 5. 该日查不到可用线：明写"无可用标准"，不许拿临近日期的线凑合。
 *
 * 全部走 GMT+8 墙钟瞬间比较，与 {@link DeadlineRuler} 同一时区基准。
 * 本类只管纯判定，不碰库；现评/历史两种取数口径在 Service 层区分。
 *
 * @author fuce
 * @date 2026-10-01
 */
public final class HazIdentRuler {

    /** 无可用标准时写进辨识单的原文，不许拿临近日期凑合 */
    public static final String NO_STANDARD = "无可用标准";

    /** 储存量合理上限（吨）：过了这个数视为超大怪值，不定级退回重录 */
    public static final BigDecimal MAX_QTY = new BigDecimal("1000000");

    /** 超三档界值后的顶格级别 */
    public static final int GRADE_TOP = 4;

    /** 不可定级（坏值 / 无可用线 / 线未设任何档） */
    public static final int GRADE_NONE = 0;

    /**
     * 顺位降序；并列按 ruleCode 字典序降序（代号排后的赢）。null 顺位按 0，null 代号按空串。
     */
    private static final Comparator<TIsLimRule> PRIORITY_THEN_CODE_DESC = new Comparator<TIsLimRule>() {
        @Override
        public int compare(TIsLimRule a, TIsLimRule b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    private HazIdentRuler() {
    }

    /** 储存量在不在理上：非空、非负、不超大，才可定级。 */
    public static boolean isQtySane(BigDecimal qty) {
        if (qty == null) {
            return false;
        }
        return qty.compareTo(BigDecimal.ZERO) >= 0 && qty.compareTo(MAX_QTY) <= 0;
    }

    /** 线在 at 瞬间是否有效：半开 [effStart, effEnd)；effEnd 空 = 仍现役。启用日缺失视为无效。 */
    public static boolean effectiveAt(TIsLimRule rule, Date at) {
        if (rule == null || at == null || rule.getEffStart() == null) {
            return false;
        }
        if (at.before(rule.getEffStart())) {
            return false;
        }
        return rule.getEffEnd() == null || at.before(rule.getEffEnd());
    }

    /**
     * 从候选线里挑辨识那天说了算的一条：先按生效时段过滤，再按顺位/代号决胜。
     * 调用方负责 del_flag、status（现评只给现役行）、适用企业类别的过滤。
     *
     * @return 胜出的线；无一在效返回 null（调用方写"无可用标准"）
     */
    public static TIsLimRule pickTop(List<TIsLimRule> candidates, Date at) {
        List<TIsLimRule> effective = effectiveOrdered(candidates, at);
        return effective.isEmpty() ? null : effective.get(0);
    }

    /**
     * 候选线中辨识那天在效的，按决胜序（顺位降序、代号降序）排好返回；无一在效给空表。
     */
    public static List<TIsLimRule> effectiveOrdered(List<TIsLimRule> candidates, Date at) {
        List<TIsLimRule> effective = new ArrayList<>();
        if (candidates == null || at == null) {
            return effective;
        }
        for (TIsLimRule r : candidates) {
            if (effectiveAt(r, at)) {
                effective.add(r);
            }
        }
        Collections.sort(effective, PRIORITY_THEN_CODE_DESC);
        return effective;
    }

    /**
     * 套三档界值定级。空界值 = 不设这档，顺到下一档；三档全空返回 {@link #GRADE_NONE}。
     * 恰好等于界值算这一档（compareTo &lt;= 0）。
     */
    public static int gradeOf(TIsLimRule rule, BigDecimal qty) {
        if (rule == null || qty == null) {
            return GRADE_NONE;
        }
        if (rule.getTh1Max() != null && qty.compareTo(rule.getTh1Max()) <= 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && qty.compareTo(rule.getTh2Max()) <= 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && qty.compareTo(rule.getTh3Max()) <= 0) {
            return 3;
        }
        boolean anyThreshold = rule.getTh1Max() != null
                || rule.getTh2Max() != null
                || rule.getTh3Max() != null;
        return anyThreshold ? GRADE_TOP : GRADE_NONE;
    }
}
