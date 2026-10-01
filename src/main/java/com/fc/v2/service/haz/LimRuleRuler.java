package com.fc.v2.service.haz;

import com.fc.v2.model.auto.TIsLimRule;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * 重大危险源定级口径——全市场唯一一把尺（园区自检屏、市局抽查屏、历史复核都从这里取数）。
 *
 * 取线只看"辨识基准日"那天，不看今天：
 * - 生效窗半开区间 [effStart, effEnd)：交棒日零点起认新线，交棒日前一天仍认旧线；
 * - 适用企业类别各管各（生产企业/储存企业），applyType 空的线两类通吃；
 * - 现行（status=0）线才能参与现评；已让位（status=1）的行只许服务历史补辨识，不许参与现评；
 * - 同一时刻并行多条，顺位（priority）高的先说；顺位并列，代号（ruleCode）排后那条赢，四季同一口径。
 *
 * 定级按三档"入门界值"套：qty &gt;= th1 一级、&gt;= th2 二级、&gt;= th3 三级；
 * 踩着界值算界值上那一档；某档界值为空 = 不设这档，顺到下一档继续推，不当断档、不报错；
 * 三档都够不上 = 0（未达临界线，不入名录，但照样落单留痕）。
 *
 * 统一走 GMT+8 墙钟字符串比较：JDBC serverTimezone 与本机时区不对称时，直接拿 Date 串库会整体偏移，
 * 切换当日的边界会错档（与本包既有教训一致）。
 *
 * @author fuce
 * @date 2026-10-01
 */
public final class LimRuleRuler {

    /** 全区统一业务时区 */
    public static final TimeZone ZONE = TimeZone.getTimeZone("GMT+8");

    /** 一级 */
    public static final int GRADE_1 = 1;
    /** 二级 */
    public static final int GRADE_2 = 2;
    /** 三级 */
    public static final int GRADE_3 = 3;
    /** 未达临界线（不入名录） */
    public static final int GRADE_BELOW = 0;

    /** 现行 */
    public static final int STATUS_ACTIVE = 0;
    /** 已让位（只许查历史） */
    public static final int STATUS_RETIRED = 1;

    /**
     * 储存量理上的上限（吨）：过了它就是超大怪值，退回重录。
     * 刻度卡在 decimal(12,2) 容得下、业务上也够荒谬的一亿吨以内。
     */
    public static final BigDecimal SANITY_MAX_QTY = new BigDecimal("99999999.99");

    /** 顺位降序；并列按代号降序（"代号排后那条赢"，字典序靠后） */
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

    private LimRuleRuler() {
    }

    private static SimpleDateFormat tsFmt() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        f.setTimeZone(ZONE);
        return f;
    }

    private static SimpleDateFormat dayFmt() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
        f.setTimeZone(ZONE);
        return f;
    }

    /**
     * 辨识基准日那天本线是否在生效窗内：[effStart, effEnd)，两端皆可空（空起=远古有效，空止=仍现行）。
     * 走墙钟字符串比较，避开 JDBC 时区偏移。
     */
    public static boolean covers(TIsLimRule rule, Date identDate) {
        if (rule == null || identDate == null) {
            return false;
        }
        String t = tsFmt().format(identDate);
        if (rule.getEffStart() != null
                && tsFmt().format(rule.getEffStart()).compareTo(t) > 0) {
            return false;
        }
        if (rule.getEffEnd() != null
                && tsFmt().format(rule.getEffEnd()).compareTo(t) <= 0) {
            return false;
        }
        return true;
    }

    /** 企业类别对不对得上：线类别空=两类通吃；否则逐字一致。 */
    public static boolean typeMatches(TIsLimRule rule, String siteType) {
        String apply = rule.getApplyType();
        return apply == null || apply.trim().isEmpty() || apply.equals(siteType);
    }

    /** 是不是同一墙钟日（GMT+8）。 */
    public static boolean sameWallDay(Date a, Date b) {
        if (a == null || b == null) {
            return false;
        }
        return dayFmt().format(a).equals(dayFmt().format(b));
    }

    /** 某墙钟日零点（GMT+8）。辨识基准日一律压到零点再与生效窗比。 */
    public static Date startOfDay(Date d) {
        Calendar c = Calendar.getInstance(ZONE);
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /**
     * 辨识基准日在册可用、且按顺位/代号排过序的线清单（现评只收现行行，历史日允许让位行）。
     * 取线决胜与看板角标都从这一个口径出，不许多处各筛各的。
     */
    public static List<TIsLimRule> effectiveList(List<TIsLimRule> rules, String siteType,
                                                 Date identDate, Date now) {
        List<TIsLimRule> hits = new ArrayList<TIsLimRule>();
        if (rules == null || identDate == null) {
            return hits;
        }
        boolean current = sameWallDay(identDate, now == null ? new Date() : now);
        for (TIsLimRule r : rules) {
            if (r == null || (r.getDelFlag() != null && r.getDelFlag() != 0)) {
                continue;
            }
            if (!covers(r, identDate) || !typeMatches(r, siteType)) {
                continue;
            }
            // 现评只认现行线；历史补辨识可调让位行（窗口本身已把范围限死在当年）。
            if (current && r.getStatus() != null && r.getStatus() == STATUS_RETIRED) {
                continue;
            }
            hits.add(r);
        }
        Collections.sort(hits, PRIORITY_THEN_CODE_DESC);
        return hits;
    }

    /**
     * 从册子里挑出辨识基准日唯一说了算的那条线；查不到返回 null（调用方明写"无可用标准"）。
     *
     * @param rules     册子（已剔删除行）
     * @param siteType  被辨识企业类别（生产企业/储存企业）
     * @param identDate 辨识基准日（取线只认这天）
     * @param now       落锤时刻：用来分"现评/历史"——已让位行只许服务历史补辨识
     */
    public static TIsLimRule choose(List<TIsLimRule> rules, String siteType, Date identDate, Date now) {
        List<TIsLimRule> hits = effectiveList(rules, siteType, identDate, now);
        return hits.isEmpty() ? null : hits.get(0);
    }

    /**
     * 凭代号调阅某条线在辨识基准日生效的那一版（历史复核专用）。
     * 同代号多版时窗口不应重叠；万一重叠，取启用日最晚的一版。查不到返回 null，不许拿邻近日凑合。
     */
    public static TIsLimRule chooseByCode(List<TIsLimRule> rules, String ruleCode, Date identDate) {
        if (rules == null || ruleCode == null || identDate == null) {
            return null;
        }
        TIsLimRule hit = null;
        for (TIsLimRule r : rules) {
            if (r == null || !ruleCode.equals(r.getRuleCode())) {
                continue;
            }
            if (r.getDelFlag() != null && r.getDelFlag() != 0) {
                continue;
            }
            if (!covers(r, identDate)) {
                continue;
            }
            if (hit == null
                    || (r.getEffStart() != null && hit.getEffStart() != null
                        && r.getEffStart().after(hit.getEffStart()))) {
                hit = r;
            }
        }
        return hit;
    }

    /**
     * 储存量在不在理上：空值、负数、超大怪值都不许定级，抛回由调用方退回重录。
     * 返回规整为 2 位小数的量。
     */
    public static BigDecimal requireSaneQty(BigDecimal qty) {
        if (qty == null) {
            throw new IllegalArgumentException("储存量为空，不予定级，退回重录");
        }
        if (qty.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("储存量为负数(" + qty + ")，不予定级，退回重录");
        }
        if (qty.compareTo(SANITY_MAX_QTY) > 0) {
            throw new IllegalArgumentException("储存量超出常理上限(" + qty + ")，疑似超大怪值，退回重录");
        }
        return qty.setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 按一条线的三档界值定级：1/2/3 入名录各档；0=未达临界线。
     * 界值是"入门门槛（含本数）"，踩着界值算界值上那一档；某档为空=不设这档，顺推下一档。
     */
    public static int grade(TIsLimRule rule, BigDecimal qty) {
        if (rule == null) {
            throw new IllegalArgumentException("无判定线不能定级");
        }
        BigDecimal q = requireSaneQty(qty);
        if (rule.getTh1Max() != null && q.compareTo(rule.getTh1Max()) >= 0) {
            return GRADE_1;
        }
        if (rule.getTh2Max() != null && q.compareTo(rule.getTh2Max()) >= 0) {
            return GRADE_2;
        }
        if (rule.getTh3Max() != null && q.compareTo(rule.getTh3Max()) >= 0) {
            return GRADE_3;
        }
        return GRADE_BELOW;
    }

    /**
     * 完整定级：取线 + 套档。量不在理上抛 IllegalArgumentException（退回重录，不落单）；
     * 那天查不到线返回 {@link GradeVerdict#noStandard()}（明写"无可用标准"，照样落单留痕）。
     */
    public static GradeVerdict verdict(List<TIsLimRule> rules, String siteType,
                                       BigDecimal qty, Date identDate, Date now) {
        BigDecimal q = requireSaneQty(qty);
        Date day = startOfDay(identDate == null ? (now == null ? new Date() : now) : identDate);
        TIsLimRule rule = choose(rules, siteType, day, now == null ? new Date() : now);
        if (rule == null) {
            return GradeVerdict.noStandard();
        }
        return GradeVerdict.graded(rule, grade(rule, q));
    }
}
