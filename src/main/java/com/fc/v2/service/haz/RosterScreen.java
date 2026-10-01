package com.fc.v2.service.haz;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

/**
 * 重点监管名录一屏：各档多少家、未达临界线多少、无可用标准多少单。
 *
 * 数全从已落锤辨识单（ident_date ≤ 屏日）点算，绝不现场套线重评——
 * 园区自检屏与市局抽查屏调同一个出处，一个口径；谁也不许拿今天的新线改昨天的数。
 *
 * @author fuce
 * @date 2026-10-01
 */
public class RosterScreen {

    /** 屏日（只点算辨识基准日落在此日及之前的单子） */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date screenDate;

    /** 已落锤辨识单总数 */
    private int totalCnt;
    /** 一级 */
    private int grade1Cnt;
    /** 二级 */
    private int grade2Cnt;
    /** 三级 */
    private int grade3Cnt;
    /** 入名录合计（一+二+三） */
    private int rosterCnt;
    /** 未达临界线、不入名录 */
    private int belowCnt;
    /** 无可用标准（缺线，待人工盯办，不许凑合定级） */
    private int noStandardCnt;

    /** 一屏话术 */
    private String message;

    public Date getScreenDate() {
        return screenDate;
    }

    public void setScreenDate(Date screenDate) {
        this.screenDate = screenDate;
    }

    public int getTotalCnt() {
        return totalCnt;
    }

    public void setTotalCnt(int totalCnt) {
        this.totalCnt = totalCnt;
    }

    public int getGrade1Cnt() {
        return grade1Cnt;
    }

    public void setGrade1Cnt(int grade1Cnt) {
        this.grade1Cnt = grade1Cnt;
    }

    public int getGrade2Cnt() {
        return grade2Cnt;
    }

    public void setGrade2Cnt(int grade2Cnt) {
        this.grade2Cnt = grade2Cnt;
    }

    public int getGrade3Cnt() {
        return grade3Cnt;
    }

    public void setGrade3Cnt(int grade3Cnt) {
        this.grade3Cnt = grade3Cnt;
    }

    public int getRosterCnt() {
        return rosterCnt;
    }

    public void setRosterCnt(int rosterCnt) {
        this.rosterCnt = rosterCnt;
    }

    public int getBelowCnt() {
        return belowCnt;
    }

    public void setBelowCnt(int belowCnt) {
        this.belowCnt = belowCnt;
    }

    public int getNoStandardCnt() {
        return noStandardCnt;
    }

    public void setNoStandardCnt(int noStandardCnt) {
        this.noStandardCnt = noStandardCnt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
