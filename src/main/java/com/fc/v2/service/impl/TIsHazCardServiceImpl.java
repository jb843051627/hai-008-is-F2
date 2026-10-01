package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TIsEnterpriseMapper;
import com.fc.v2.mapper.auto.TIsHazCardMapper;
import com.fc.v2.mapper.auto.TIsHazCardReviewMapper;
import com.fc.v2.model.auto.TIsEnterprise;
import com.fc.v2.model.auto.TIsHazCard;
import com.fc.v2.model.auto.TIsHazCardReview;
import com.fc.v2.model.auto.TIsLimRule;
import com.fc.v2.service.ITIsHazCardService;
import com.fc.v2.service.ITIsLimRuleService;
import com.fc.v2.service.haz.GradeVerdict;
import com.fc.v2.service.haz.LimRuleRuler;
import com.fc.v2.service.haz.RosterScreen;
import com.fc.v2.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 重大危险源辨识单（档案卡）Service业务层处理。
 *
 * 一罐一评：定级只走 {@link LimRuleRuler}（全市场唯一一把尺），级别+线代号+线启用日落锤写死；
 * 开单后这些字段一律不接改，再评只准添新版单。历史复核另起结论行，原单永不回改。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TIsHazCardServiceImpl extends ServiceImpl<TIsHazCardMapper, TIsHazCard>
        implements ITIsHazCardService {

    @Resource
    private TIsEnterpriseMapper isEnterpriseMapper;
    @Resource
    private TIsHazCardReviewMapper reviewMapper;
    @Resource
    private ITIsLimRuleService limRuleService;

    @Override
    public TIsHazCard selectTIsHazCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TIsHazCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TIsHazCard> selectTIsHazCardList(Wrapper<TIsHazCard> queryWrapper) {
        QueryWrapper<TIsHazCard> wrapper;
        if (queryWrapper instanceof QueryWrapper) {
            wrapper = (QueryWrapper<TIsHazCard>) queryWrapper;
        } else {
            wrapper = new QueryWrapper<TIsHazCard>();
        }
        wrapper.eq("del_flag", 0).orderByDesc("ident_date", "create_time");
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TIsHazCard identify(TIsHazCard record, Date now) {
        if (record == null) {
            throw new IllegalArgumentException("辨识单内容为空，退回重录");
        }
        Date clock = now == null ? new Date() : now;

        TIsEnterprise site = isEnterpriseMapper.selectOne(new QueryWrapper<TIsEnterprise>()
                .eq("id", record.getSiteId()).eq("del_flag", 0));
        if (site == null) {
            throw new IllegalArgumentException("所属企业底档不存在，退回重录");
        }

        // 辨识基准日：现评不传=今天；老罐子补辨识必须传建成年当日，取线只认这天。
        Date identDate = record.getIdentDate() == null ? clock : record.getIdentDate();
        if (LimRuleRuler.startOfDay(identDate).after(clock)) {
            throw new IllegalArgumentException("辨识基准日不能晚于今天，不许预开辨识单");
        }
        record.setIdentDate(LimRuleRuler.startOfDay(identDate));

        // 量在不在理上（空/负/超大怪值）尺子先拦，抛错即退回重录，半行单都不落。
        BigDecimal qty = LimRuleRuler.requireSaneQty(record.getQty());
        record.setQty(qty);

        GradeVerdict verdict =
                limRuleService.evaluate(site.getSiteType(), qty, record.getIdentDate(), clock);

        record.setSiteNo(site.getSiteNo());
        if (verdict.isNoStandard()) {
            // 查不到可用线：明写，不许拿临近日期的线凑合。
            record.setNoStandard(1);
            record.setGradeLevel(null);
            record.setRuleCode(null);
            record.setRuleEffStart(null);
        } else {
            TIsLimRule rule = verdict.getRule();
            record.setNoStandard(0);
            record.setGradeLevel(verdict.getGrade());
            record.setRuleCode(rule.getRuleCode());
            record.setRuleEffStart(rule.getEffStart());
        }

        if (StringUtils.isEmpty(record.getBillNo())) {
            record.setBillNo(nextBillNo(clock));
        } else {
            Integer dup = this.baseMapper.selectCount(new QueryWrapper<TIsHazCard>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dup != null && dup > 0) {
                throw new IllegalArgumentException("辨识单号已存在：" + record.getBillNo());
            }
        }
        if (record.getNodeNo() == null) {
            Integer cnt = this.baseMapper.selectCount(new QueryWrapper<TIsHazCard>()
                    .eq("site_id", site.getId()).eq("del_flag", 0));
            record.setNodeNo((cnt == null ? 0 : cnt) + 1);
        }
        record.setContent(buildChecksum(record));
        if (record.getStatus() == null) {
            record.setStatus(0);
        }
        record.setDelFlag(0);
        this.baseMapper.insert(record);
        return record;
    }

    @Override
    public int insertTIsHazCard(TIsHazCard record) {
        return identify(record, new Date()) == null ? 0 : 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TIsHazCardReview reviewCard(Long cardId, Date reviewDate, Date now) {
        TIsHazCard card = selectTIsHazCardById(cardId);
        if (card == null) {
            throw new IllegalArgumentException("辨识单不存在：" + cardId);
        }
        Date clock = now == null ? new Date() : now;
        Date rDate = reviewDate == null ? clock : reviewDate;

        TIsHazCardReview review = new TIsHazCardReview();
        review.setCardId(card.getId());
        review.setBillNo(card.getBillNo());
        review.setReviewDate(LimRuleRuler.startOfDay(rDate));
        // 存量与基准日均抄原单，复核不重新采录、不改成今天。
        review.setQty(card.getQty());
        review.setIdentDate(card.getIdentDate());
        review.setOrigGradeLevel(card.getGradeLevel());
        review.setOrigRuleCode(card.getRuleCode());
        review.setOrigRuleEffStart(card.getRuleEffStart());

        GradeVerdict verdict;
        if (card.getNoStandard() != null && card.getNoStandard() == 1) {
            // 原单本就无可用标准：复核仍凭代号（无）调线，结论依旧无可用标准。
            verdict = GradeVerdict.noStandard();
        } else {
            // 凭原单代号调线、按原辨识基准日重评，绝不拿今天的线顶替。
            verdict = limRuleService.reevaluateByCode(
                    card.getRuleCode(), card.getQty(), card.getIdentDate());
        }

        if (verdict.isNoStandard()) {
            review.setNoStandard(1);
        } else {
            review.setNoStandard(0);
            review.setReviewGradeLevel(verdict.getGrade());
            review.setReviewRuleCode(verdict.getRule().getRuleCode());
            review.setReviewRuleEffStart(verdict.getRule().getEffStart());
        }

        Integer origGrade = card.getNoStandard() != null && card.getNoStandard() == 1
                ? null : card.getGradeLevel();
        Integer reviewGrade = review.getReviewGradeLevel();
        review.setConsistent(Objects.equals(origGrade, reviewGrade)
                && Objects.equals(card.getRuleCode(), review.getReviewRuleCode()) ? 1 : 0);

        review.setReviewNo(nextReviewNo(rDate));
        review.setDelFlag(0);
        review.setCreateTime(clock);
        reviewMapper.insert(review);
        return review;
    }

    @Override
    public List<TIsHazCardReview> listReviews(Long cardId) {
        return reviewMapper.selectList(new QueryWrapper<TIsHazCardReview>()
                .eq("card_id", cardId)
                .eq("del_flag", 0)
                .orderByDesc("review_date", "create_time"));
    }

    @Override
    public RosterScreen rosterScreen(Date at) {
        Date clock = at == null ? new Date() : at;
        // 只点算辨识基准日 ≤ 屏日的已落锤单；绑 java.sql.Date（纯日精度，跨时区不偏移）。
        Date dayStart = LimRuleRuler.startOfDay(clock);
        java.sql.Date dayBound = new java.sql.Date(dayStart.getTime());
        String day = new SimpleDateFormat("yyyy-MM-dd") {{
            setTimeZone(LimRuleRuler.ZONE);
        }}.format(clock);
        List<TIsHazCard> cards = this.baseMapper.selectList(new QueryWrapper<TIsHazCard>()
                .eq("del_flag", 0)
                .le("ident_date", dayBound));

        RosterScreen screen = new RosterScreen();
        screen.setScreenDate(LimRuleRuler.startOfDay(clock));
        for (TIsHazCard c : cards) {
            screen.setTotalCnt(screen.getTotalCnt() + 1);
            if (c.getNoStandard() != null && c.getNoStandard() == 1) {
                screen.setNoStandardCnt(screen.getNoStandardCnt() + 1);
                continue;
            }
            Integer g = c.getGradeLevel();
            if (g == null) {
                screen.setNoStandardCnt(screen.getNoStandardCnt() + 1);
            } else if (g == 1) {
                screen.setGrade1Cnt(screen.getGrade1Cnt() + 1);
            } else if (g == 2) {
                screen.setGrade2Cnt(screen.getGrade2Cnt() + 1);
            } else if (g == 3) {
                screen.setGrade3Cnt(screen.getGrade3Cnt() + 1);
            } else {
                screen.setBelowCnt(screen.getBelowCnt() + 1);
            }
        }
        screen.setRosterCnt(screen.getGrade1Cnt() + screen.getGrade2Cnt() + screen.getGrade3Cnt());
        screen.setMessage("截至" + day + "：入名录" + screen.getRosterCnt() + "家（一级"
                + screen.getGrade1Cnt() + "/二级" + screen.getGrade2Cnt() + "/三级"
                + screen.getGrade3Cnt() + "），未达临界线" + screen.getBelowCnt()
                + "单，无可用标准" + screen.getNoStandardCnt() + "单");
        return screen;
    }

    @Override
    public int updateTIsHazCard(TIsHazCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TIsHazCard old = selectTIsHazCardById(record.getId());
        if (old == null) {
            return 0;
        }
        // 落锤字段（基准日/存量/级别/线代号/线启用日/无标准旗）一律不接改——要改请另开新版辨识。
        UpdateWrapper<TIsHazCard> uw = new UpdateWrapper<TIsHazCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0)
                .set("update_time", new Date());
        if (record.getStatus() != null) {
            uw.set("status", record.getStatus());
        }
        if (record.getFineAmt() != null) {
            uw.set("fine_amt", record.getFineAmt());
        }
        if (record.getRemark() != null) {
            uw.set("remark", record.getRemark());
        }
        return this.baseMapper.update(null, uw);
    }

    @Override
    public int deleteTIsHazCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTIsHazCardById(Long id) {
        return this.baseMapper.deleteById(id);
    }

    /** 辨识单号：BS+年月日+当日序。 */
    private String nextBillNo(Date at) {
        String prefix = "BS" + new SimpleDateFormat("yyyyMMdd").format(at);
        int seq = this.baseMapper.selectCount(new QueryWrapper<TIsHazCard>()
                .likeRight("bill_no", prefix));
        return prefix + String.format("%04d", seq + 1);
    }

    /** 复核结论号：FH+年月日+当日序。 */
    private String nextReviewNo(Date at) {
        String prefix = "FH" + new SimpleDateFormat("yyyyMMdd").format(at);
        int seq = reviewMapper.selectCount(new QueryWrapper<TIsHazCardReview>()
                .likeRight("review_no", prefix));
        return prefix + String.format("%04d", seq + 1);
    }

    /** 倒查校验串：把落锤上下文串死，单被越库改过一眼对不上。 */
    private String buildChecksum(TIsHazCard r) {
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd");
        day.setTimeZone(LimRuleRuler.ZONE);
        SimpleDateFormat ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ts.setTimeZone(LimRuleRuler.ZONE);
        String grade = r.getNoStandard() != null && r.getNoStandard() == 1
                ? "NA" : String.valueOf(r.getGradeLevel());
        String code = r.getRuleCode() == null ? "-" : r.getRuleCode();
        String start = r.getRuleEffStart() == null ? "-" : ts.format(r.getRuleEffStart());
        return r.getBillNo() + "|" + r.getSiteNo() + "|" + r.getQty().toPlainString()
                + "|" + day.format(r.getIdentDate()) + "|" + grade + "|" + code + "|" + start;
    }
}
