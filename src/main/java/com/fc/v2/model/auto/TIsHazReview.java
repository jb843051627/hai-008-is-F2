package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 重大危险源辨识历史复核对象 t_is_haz_review
 *
 * 凭原单代号调线、在原辨识日重评；不许拿今天的标准改昨天的单。
 * 原结论上下文（orig*）与复核结论上下文（review*）各留各的。
 *
 * @author fuce
 * @date 2026-10-01
 */
@TableName("t_is_haz_review")
@ApiModel(value = "TIsHazReview", description = "重大危险源辨识历史复核")
public class TIsHazReview implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所复核辨识单 */
    @TableField("card_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "所复核辨识单")
    private Long cardId;

    /** 辨识单号(抄存) */
    @TableField("bill_no")
    @ApiModelProperty(value = "辨识单号(抄存)")
    private String billNo;

    /** 复核那天 */
    @TableField("review_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "复核那天")
    private Date reviewAt;

    /** 复核所据原始储存量(抄自原单) */
    @TableField("qty")
    @ApiModelProperty(value = "复核所据原始储存量(抄自原单)")
    private BigDecimal qty;

    /** 原辨识那天(复核就在这天重评,不取今天) */
    @TableField("ident_date")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "原辨识那天(复核就在这天重评,不取今天)")
    private Date identDate;

    /** 原单所依线代号 */
    @TableField("orig_rule_code")
    @ApiModelProperty(value = "原单所依线代号")
    private String origRuleCode;

    /** 原单所依线启用日 */
    @TableField("orig_rule_eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "原单所依线启用日")
    private Date origRuleEffStart;

    /** 原单级别 */
    @TableField("orig_grade_level")
    @ApiModelProperty(value = "原单级别")
    private Integer origGradeLevel;

    /** 复核调起的线代号(查不到写"无可用标准") */
    @TableField("review_rule_code")
    @ApiModelProperty(value = "复核调起的线代号(查不到写无可用标准)")
    private String reviewRuleCode;

    /** 复核所调线启用日 */
    @TableField("review_rule_eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "复核所调线启用日")
    private Date reviewRuleEffStart;

    /** 复核级别(无可用标准为空) */
    @TableField("review_grade_level")
    @ApiModelProperty(value = "复核级别(无可用标准为空)")
    private Integer reviewGradeLevel;

    /** 复核结论与原结论是否一致 0不一致 1一致 */
    @TableField("same_result")
    @ApiModelProperty(value = "复核结论与原结论是否一致 0不一致 1一致")
    private Integer sameResult;

    /** 复核结论文话 */
    @TableField("conclusion")
    @ApiModelProperty(value = "复核结论文话")
    private String conclusion;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Date getReviewAt() {
        return reviewAt;
    }

    public void setReviewAt(Date reviewAt) {
        this.reviewAt = reviewAt;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public Date getIdentDate() {
        return identDate;
    }

    public void setIdentDate(Date identDate) {
        this.identDate = identDate;
    }

    public String getOrigRuleCode() {
        return origRuleCode;
    }

    public void setOrigRuleCode(String origRuleCode) {
        this.origRuleCode = origRuleCode;
    }

    public Date getOrigRuleEffStart() {
        return origRuleEffStart;
    }

    public void setOrigRuleEffStart(Date origRuleEffStart) {
        this.origRuleEffStart = origRuleEffStart;
    }

    public Integer getOrigGradeLevel() {
        return origGradeLevel;
    }

    public void setOrigGradeLevel(Integer origGradeLevel) {
        this.origGradeLevel = origGradeLevel;
    }

    public String getReviewRuleCode() {
        return reviewRuleCode;
    }

    public void setReviewRuleCode(String reviewRuleCode) {
        this.reviewRuleCode = reviewRuleCode;
    }

    public Date getReviewRuleEffStart() {
        return reviewRuleEffStart;
    }

    public void setReviewRuleEffStart(Date reviewRuleEffStart) {
        this.reviewRuleEffStart = reviewRuleEffStart;
    }

    public Integer getReviewGradeLevel() {
        return reviewGradeLevel;
    }

    public void setReviewGradeLevel(Integer reviewGradeLevel) {
        this.reviewGradeLevel = reviewGradeLevel;
    }

    public Integer getSameResult() {
        return sameResult;
    }

    public void setSameResult(Integer sameResult) {
        this.sameResult = sameResult;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
