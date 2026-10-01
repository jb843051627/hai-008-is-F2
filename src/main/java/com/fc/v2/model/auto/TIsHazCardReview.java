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
 * 辨识单历史复核结论对象 t_is_haz_card_review
 *
 * @author fuce
 * @date 2026-10-01
 */
@TableName("t_is_haz_card_review")
@ApiModel(value = "TIsHazCardReview", description = "辨识单历史复核结论")
public class TIsHazCardReview implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 复核结论号 */
    @TableField("review_no")
    @ApiModelProperty(value = "复核结论号")
    private String reviewNo;

    /** 被复核辨识单 */
    @TableField("card_id")
    @ApiModelProperty(value = "被复核辨识单")
    private Long cardId;

    /** 被复核辨识单号（抄存） */
    @TableField("bill_no")
    @ApiModelProperty(value = "被复核辨识单号（抄存）")
    private String billNo;

    /** 复核作业日 */
    @TableField("review_date")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "复核作业日")
    private Date reviewDate;

    /** 原始储存量抄存（取自原单，不重新采录） */
    @TableField("qty")
    @ApiModelProperty(value = "原始储存量抄存（取自原单，不重新采录）")
    private BigDecimal qty;

    /** 辨识基准日抄存（取线仍按这天） */
    @TableField("ident_date")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @ApiModelProperty(value = "辨识基准日抄存（取线仍按这天）")
    private Date identDate;

    /** 原单级别 */
    @TableField("orig_grade_level")
    @ApiModelProperty(value = "原单级别")
    private Integer origGradeLevel;

    /** 原单所依线代号 */
    @TableField("orig_rule_code")
    @ApiModelProperty(value = "原单所依线代号")
    private String origRuleCode;

    /** 原单所依线启用日 */
    @TableField("orig_rule_eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "原单所依线启用日")
    private Date origRuleEffStart;

    /** 复核级别（凭代号调线重评） */
    @TableField("review_grade_level")
    @ApiModelProperty(value = "复核级别（凭代号调线重评）")
    private Integer reviewGradeLevel;

    /** 复核对上的线代号（一般与原单同；缺线时为空） */
    @TableField("review_rule_code")
    @ApiModelProperty(value = "复核对上的线代号（一般与原单同；缺线时为空）")
    private String reviewRuleCode;

    /** 复核对上的线启用日 */
    @TableField("review_rule_eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "复核对上的线启用日")
    private Date reviewRuleEffStart;

    /** 复核时无可用标准 0否 1是 */
    @TableField("no_standard")
    @ApiModelProperty(value = "复核时无可用标准 0否 1是")
    private Integer noStandard;

    /** 复核与原结论是否一致 1一致 0不一致 */
    @TableField("consistent")
    @ApiModelProperty(value = "复核与原结论是否一致 1一致 0不一致")
    private Integer consistent;

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

    public String getReviewNo() {
        return reviewNo;
    }

    public void setReviewNo(String reviewNo) {
        this.reviewNo = reviewNo;
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

    public Date getReviewDate() {
        return reviewDate;
    }

    public void setReviewDate(Date reviewDate) {
        this.reviewDate = reviewDate;
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

    public Integer getOrigGradeLevel() {
        return origGradeLevel;
    }

    public void setOrigGradeLevel(Integer origGradeLevel) {
        this.origGradeLevel = origGradeLevel;
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

    public Integer getReviewGradeLevel() {
        return reviewGradeLevel;
    }

    public void setReviewGradeLevel(Integer reviewGradeLevel) {
        this.reviewGradeLevel = reviewGradeLevel;
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

    public Integer getNoStandard() {
        return noStandard;
    }

    public void setNoStandard(Integer noStandard) {
        this.noStandard = noStandard;
    }

    public Integer getConsistent() {
        return consistent;
    }

    public void setConsistent(Integer consistent) {
        this.consistent = consistent;
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
