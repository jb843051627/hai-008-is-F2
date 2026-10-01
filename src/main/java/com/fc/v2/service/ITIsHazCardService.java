package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TIsHazCard;
import com.fc.v2.model.auto.TIsHazReview;

import java.util.Date;
import java.util.List;

/**
 * 重大危险源档案卡（辨识单）Service接口。
 *
 * 辨识定级走判定线册子，级别/线代号/线启用日/辨识日落单后写死，编辑路径动不了这几栏。
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITIsHazCardService {

    /** 按主键查询 */
    TIsHazCard selectTIsHazCardById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TIsHazCard> selectTIsHazCardList(Wrapper<TIsHazCard> queryWrapper);

    /**
     * 新建辨识单：按 identDate（空=今天；补辨识传建成那年的日子）选现役判定线定级，
     * 级别连同线代号、线启用日写死。无可用线写"无可用标准"；坏储存量抛错退回。
     */
    TIsHazCard identify(TIsHazCard record, Date identDate);

    /**
     * 修改保存：只许动进展/备注等活档；辨识日、级别、线代号、线启用日、原始储存量写过不改，
     * 入参里带了也一律忽略。
     */
    int updateTIsHazCard(TIsHazCard record);

    /** 批量删除 */
    int deleteTIsHazCardByIds(String ids);

    /** 按主键删除 */
    int deleteTIsHazCardById(Long id);

    /** 历史复核：凭原单代号在原辨识日调线重评，原结论/复核结论各留上下文，原单不改 */
    TIsHazReview review(Long cardId, Date reviewAt);

    /** 某张辨识单的历次复核 */
    List<TIsHazReview> listReviews(Long cardId);
}
