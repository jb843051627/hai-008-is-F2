package com.fc.v2.service.impl;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TIsHazCardMapper;
import com.fc.v2.model.auto.TIsHazCard;
import com.fc.v2.model.auto.TIsHazReview;
import com.fc.v2.service.ITIsHazCardService;
import com.fc.v2.service.haz.HazIdentTx;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 重大危险源档案卡（辨识单）Service业务层处理。
 *
 * 定级/选线/落库的事务过程在 {@link HazIdentTx} 单一事务里跑（代理生效、失败整单回滚）；
 * 本类只做翻条与"写过不改"的编辑白名单。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TIsHazCardServiceImpl extends ServiceImpl<TIsHazCardMapper, TIsHazCard> implements ITIsHazCardService {

    @Resource
    private HazIdentTx identTx;

    @Override
    public TIsHazCard selectTIsHazCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TIsHazCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TIsHazCard> selectTIsHazCardList(Wrapper<TIsHazCard> queryWrapper) {
        return this.baseMapper.selectList(queryWrapper);
    }

    @Override
    public TIsHazCard identify(TIsHazCard record, Date identDate) {
        return identTx.identify(record, identDate);
    }

    @Override
    public int updateTIsHazCard(TIsHazCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }
        TIsHazCard stored = this.baseMapper.selectOne(new QueryWrapper<TIsHazCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
        if (stored == null) {
            return 0;
        }

        // 写过不改：辨识日、原始储存量、级别、线代号、线启用日、所属底档一律以库内原单为准，
        // 入参带了也直接丢掉——事后倒查靠的就是这几栏纹丝不动。
        // 只许动活档：进展、版本次序、新增储存量、校验串、备注。
        stored.setStatus(record.getStatus());
        stored.setNodeNo(record.getNodeNo());
        stored.setFineAmt(record.getFineAmt());
        stored.setContent(record.getContent());
        stored.setRemark(record.getRemark());
        stored.setUpdateTime(new Date());
        return this.baseMapper.updateById(stored);
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

    @Override
    public TIsHazReview review(Long cardId, Date reviewAt) {
        return identTx.review(cardId, reviewAt);
    }

    @Override
    public List<TIsHazReview> listReviews(Long cardId) {
        return identTx.listReviews(cardId);
    }
}
