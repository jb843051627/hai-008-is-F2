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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 重大危险源辨识落库的事务执行器。
 *
 * 单开一个 Bean：让 {@link Transactional} 经代理生效（同类自调不生效），
 * 与 HazDispatchTx 同一套做法。定级口径全部在 {@link HazIdentRuler}，本类只编排取数与落库：
 *
 * 辨识（新建单）：
 * - 用哪条线看辨识那天（补辨识可传建成那年的日子），不看今天；
 * - 级别连同线代号、线启用日一起写进辨识单，写过不改（编辑路径也动不了这几栏）；
 * - 该日查不到现役线：级别留空、代号明写"无可用标准"，不许拿临近日期凑合；
 * - 储存量不在理上（空/负/超大怪值）：抛错退回，单子不落库。
 *
 * 历史复核：
 * - 凭原单代号调线、在原辨识日重评（让位行也允许调起，它只许在历史里用）；
 * - 原单写"无可用标准"（没有代号可凭）的，复核照写"无可用标准"；
 * - 原结论与复核结论各留各的代号上下文，原单一个字都不改。
 *
 * @author fuce
 * @date 2026-10-01
 */
@Component
public class HazIdentTx {

    @Resource
    private TIsHazCardMapper cardMapper;
    @Resource
    private TIsHazReviewMapper reviewMapper;
    @Resource
    private TIsEnterpriseMapper enterpriseMapper;
    @Resource
    private ITIsLimRuleService limRuleService;

    /**
     * 新建辨识单。identDate 为空按今天；补辨识传建成那年的日子。
     *
     * @throws IllegalArgumentException 储存量不在理上 / 底档缺失或已退役 / 单号重复
     */
    @Transactional(rollbackFor = Exception.class)
    public TIsHazCard identify(TIsHazCard card, Date identDate) {
        if (card == null) {
            throw new IllegalArgumentException("辨识单内容为空，退回重录");
        }

        // 1) 储存量先验在理上：空值、负数、超大怪值不定级，整单退回重录。
        BigDecimal qty = card.getQty();
        if (!HazIdentRuler.isQtySane(qty)) {
            throw new IllegalArgumentException("储存量不在理上（空值/负数/超大怪值），不定级，退回重录");
        }

        // 2) 底档要在、未退役；企业类别以底档为准，生产/储存各管各。
        TIsEnterprise site = enterpriseMapper.selectOne(new QueryWrapper<TIsEnterprise>()
                .eq("id", card.getSiteId()).eq("del_flag", 0));
        if (site == null) {
            throw new IllegalArgumentException("所属企业底档不存在，退回重录");
        }
        if (site.getStatus() != null && site.getStatus() == 1) {
            throw new IllegalArgumentException("所属企业底档已退役，不再辨识");
        }
        card.setSiteNo(site.getSiteNo());

        // 3) 单号去重（辨识单即档案卡号）。
        if (card.getBillNo() != null && !card.getBillNo().trim().isEmpty()) {
            Integer dup = cardMapper.selectCount(new QueryWrapper<TIsHazCard>()
                    .eq("bill_no", card.getBillNo()).eq("del_flag", 0));
            if (dup != null && dup > 0) {
                throw new IllegalArgumentException("辨识单号已存在：" + card.getBillNo());
            }
        }

        // 4) 用哪条线看辨识那天，不看今天。
        //    辨识日=今天及以后：现评，只认现役行，让位行不许参与；
        //    辨识日在过去（老罐子按建成那年补辨识）：查档，允许调起那年还现役、如今已让位的线；
        //    两种口径都仍按该日的半开生效时段筛选，临近日期的线一律不许凑合。
        Date today = DeadlineRuler.startOfDay(new Date());
        Date at = identDate != null ? DeadlineRuler.startOfDay(identDate) : today;
        card.setIdentDate(at);
        TIsLimRule rule = !at.before(today)
                ? limRuleService.currentRule(site.getSiteType(), at)
                : limRuleService.historicalTop(site.getSiteType(), at);
        if (rule == null) {
            // 查不到可用线明写，不许拿临近日期的凑合。
            card.setGradeLevel(null);
            card.setRuleCode(HazIdentRuler.NO_STANDARD);
            card.setRuleEffStart(null);
        } else {
            card.setGradeLevel(HazIdentRuler.gradeOf(rule, qty));
            card.setRuleCode(rule.getRuleCode());
            // 启用日随单抄存：日后线头改了日子，原单倒查仍按写死的这一天。
            card.setRuleEffStart(rule.getEffStart());
        }

        card.setDelFlag(0);
        card.setCreateBy(card.getBillNo());
        cardMapper.insert(card);
        return card;
    }

    /**
     * 历史复核：凭原单代号调线，在原辨识日重评。原单一字不改，复核上下文另立一行。
     *
     * @throws IllegalArgumentException 单子不存在 / 原单未留辨识日
     */
    @Transactional(rollbackFor = Exception.class)
    public TIsHazReview review(Long cardId, Date reviewAt) {
        TIsHazCard card = cardMapper.selectById(cardId);
        if (card == null || (card.getDelFlag() != null && card.getDelFlag() != 0)) {
            throw new IllegalArgumentException("辨识单不存在");
        }
        if (card.getIdentDate() == null) {
            throw new IllegalArgumentException("原单未留辨识日，无法按那天调线复核");
        }
        Date at = reviewAt != null ? reviewAt : new Date();

        TIsHazReview rv = new TIsHazReview();
        rv.setCardId(card.getId());
        rv.setBillNo(card.getBillNo());
        rv.setReviewAt(at);
        rv.setQty(card.getQty());
        rv.setIdentDate(card.getIdentDate());
        rv.setOrigRuleCode(card.getRuleCode());
        rv.setOrigRuleEffStart(card.getRuleEffStart());
        rv.setOrigGradeLevel(card.getGradeLevel());

        Integer reviewGrade;
        String origCode = card.getRuleCode();
        if (origCode == null || HazIdentRuler.NO_STANDARD.equals(origCode)) {
            // 原单就是"无可用标准"：没有代号可凭，不许另挑一条线充数，复核照写。
            rv.setReviewRuleCode(HazIdentRuler.NO_STANDARD);
            reviewGrade = null;
        } else {
            // 凭代号调线、在原辨识日重评；让位行允许调起。仍不许拿今天的标准。
            TIsLimRule rule = limRuleService.historicalRule(origCode, card.getIdentDate());
            if (rule == null) {
                rv.setReviewRuleCode(HazIdentRuler.NO_STANDARD);
                reviewGrade = null;
            } else {
                rv.setReviewRuleCode(rule.getRuleCode());
                rv.setReviewRuleEffStart(rule.getEffStart());
                reviewGrade = HazIdentRuler.gradeOf(rule, card.getQty());
            }
        }
        rv.setReviewGradeLevel(reviewGrade);

        boolean same = sameGrade(card.getGradeLevel(), reviewGrade);
        rv.setSameResult(same ? 1 : 0);
        rv.setConclusion(buildConclusion(card, rv, same));
        rv.setDelFlag(0);
        reviewMapper.insert(rv);
        return rv;
    }

    /** 某张辨识单的历次复核（新的在前）。 */
    public List<TIsHazReview> listReviews(Long cardId) {
        return reviewMapper.selectList(new QueryWrapper<TIsHazReview>()
                .eq("card_id", cardId).eq("del_flag", 0)
                .orderByDesc("review_at").orderByDesc("id"));
    }

    private static boolean sameGrade(Integer a, Integer b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String buildConclusion(TIsHazCard card, TIsHazReview rv, boolean same) {
        String orig = card.getGradeLevel() == null ? "无级别（" + HazIdentRuler.NO_STANDARD + "）" : card.getGradeLevel() + "级";
        String now = rv.getReviewGradeLevel() == null
                ? "无级别（" + HazIdentRuler.NO_STANDARD + "）"
                : rv.getReviewGradeLevel() + "级";
        String head = same ? "复核一致：" : "复核不一致：";
        return head + "原单" + orig + "（依 " + nv(card.getRuleCode()) + "）；复核" + now
                + "（依 " + nv(rv.getReviewRuleCode()) + "），均按原辨识日口径，未用今日标准改昨日单。";
    }

    private static String nv(String s) {
        return s == null ? HazIdentRuler.NO_STANDARD : s;
    }
}
