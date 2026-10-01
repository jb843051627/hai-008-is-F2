package com.fc.v2.service;

import com.fc.v2.model.auto.TIsLimRule;
import com.fc.v2.service.haz.GradeVerdict;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 重大危险源辨识判定线 Service接口（rule-book 形状：册子只读，取线/定级全走 LimRuleRuler）。
 *
 * 定级尺度全市场只认系统册子现役那条线：个人记的、墙上贴的老表都不作数。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITIsLimRuleService {

    /** 按主键查一条（含让位行，供历史调阅） */
    TIsLimRule selectTIsLimRuleById(Long id);

    /** 整本册子：现行、让位都在（换标准不删行），只剔删除行，按代号、启用日排好。 */
    List<TIsLimRule> listBook();

    /**
     * 换标准只添行：登一条新线（新代号专号或同代号新版本）。
     * 同一代号的版本生效窗不许与既有版本重叠，否则交棒日说不清——抛错拒登。
     */
    TIsLimRule addRule(TIsLimRule rule);

    /**
     * 交棒翻旗：把某条线在交棒日零点让位（eff_end 落交棒日、status=1），只许查历史。
     * 旧行原样留着，绝不删除。
     */
    TIsLimRule retireRule(Long id, java.util.Date handoverAt);

    /**
     * 现评/补辨识取线：按企业类别、辨识基准日取唯一生效线，顺位/代号决胜口径与各处一致。
     * 查不到返回 null（调用方明写"无可用标准"，不许凑合）。
     */
    TIsLimRule locate(String siteType, Date identDate, Date now);

    /**
     * 历史复核专用：凭代号调阅该代号在辨识基准日生效的那一版。
     * 查不到返回 null——不回退到邻近日、也不回退到别的代号。
     */
    TIsLimRule locateByCode(String ruleCode, Date identDate);

    /**
     * 完整定级：取线 + 套三档。
     * 储存量不在理上（空/负/超大怪值）抛 IllegalArgumentException，由入口退回重录；
     * 无可用线返回 noStandard 结论。
     */
    GradeVerdict evaluate(String siteType, BigDecimal qty, Date identDate, Date now);

    /**
     * 凭代号调线重评（历史复核）：只按代号+原辨识基准日取线，拿不到线即"无可用标准"，
     * 不许顺手换成今天的现行线。
     */
    GradeVerdict reevaluateByCode(String ruleCode, BigDecimal qty, Date identDate);

    /** 某条线在 at 时刻是否在册生效（不区分现行/让位；批量导入旁路复用，口径须与尺子一致） */
    boolean usable(Long id, Date at);

    /** at 时刻某企业类别并行可用的线有几条（自检/抽查屏角标，同口径） */
    int countActive(String siteType, Date at);
}
