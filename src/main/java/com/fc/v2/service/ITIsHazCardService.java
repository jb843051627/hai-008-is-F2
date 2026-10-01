package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TIsHazCard;
import com.fc.v2.model.auto.TIsHazCardReview;
import com.fc.v2.service.haz.RosterScreen;

import java.util.Date;
import java.util.List;

/**
 * 重大危险源辨识单（档案卡）Service接口。
 *
 * 辨识三不：定级不看今天只看辨识基准日；落锤字段写过不改；复核另起结论不动原单。
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITIsHazCardService {

    /** 按主键查询（含无可用标准单） */
    TIsHazCard selectTIsHazCardById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TIsHazCard> selectTIsHazCardList(Wrapper<TIsHazCard> queryWrapper);

    /**
     * 一罐一评开具辨识单：按记录里的 identDate（补辨识取建成日）取现役线定级，
     * 级别连同线代号、线启用日写死落档；查不到线明写"无可用标准"；量不在理上抛错退回重录。
     */
    TIsHazCard identify(TIsHazCard record, Date now);

    /** 兼容旧入口：以落锤时刻为现在时 */
    int insertTIsHazCard(TIsHazCard record);

    /**
     * 历史复核：凭原单代号调线、按原辨识基准日重评，结论另存一行，绝不回改原单。
     * 原结论与复核结论各留各的代号上下文。
     */
    TIsHazCardReview reviewCard(Long cardId, Date reviewDate, Date now);

    /** 一张辨识单的历次复核结论（新的在前） */
    List<TIsHazCardReview> listReviews(Long cardId);

    /**
     * 重点监管名录一屏：按已落锤辨识单点算（辨识基准日 ≤ at），各档多少家、无可用标准多少单。
     * 园区自检屏与市局抽查屏调同一个本方法，保证一个口径。
     */
    RosterScreen rosterScreen(Date at);

    /** 修改保存（只许动非落锤字段；级别/代号/启用日/基准日一律不接改） */
    int updateTIsHazCard(TIsHazCard record);

    /** 批量删除 */
    int deleteTIsHazCardByIds(String ids);

    /** 按主键删除 */
    int deleteTIsHazCardById(Long id);
}
