package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TIsHazCard;
import com.fc.v2.service.ITIsHazCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.beans.PropertyEditorSupport;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 重大危险源辨识单（档案卡）Controller。
 *
 * 一罐一评、落锤写死；另提供重点监管名录屏（园区自检屏与市局抽查屏同打这一口）。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "重大危险源辨识单")
@Controller
@RequestMapping("/IsHazCardController")
public class IsHazCardController extends BaseController {

    private final String prefix = "admin/isHazCard";

    @Autowired
    private ITIsHazCardService isHazCardService;

    /** 表单上来的辨识基准日是 yyyy-MM-dd 纯日，注册到 java.util.Date，避免绑定成报错串。 */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Date.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null || text.trim().isEmpty()) {
                    setValue(null);
                    return;
                }
                try {
                    String t = text.trim();
                    setValue(new SimpleDateFormat(t.length() > 10 ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd").parse(t));
                } catch (java.text.ParseException e) {
                    setValue(null);
                }
            }
        });
    }

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("is:isHazCard:view")
    public String view(ModelMap model) {
        model.put("roster", isHazCardService.rosterScreen(new Date()));
        return prefix + "/list";
    }

    @Log(title = "重大危险源辨识单集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("is:isHazCard:list")
    @ResponseBody
    public ResultTable list(TIsHazCard record) {
        QueryWrapper<TIsHazCard> queryWrapper = new QueryWrapper<TIsHazCard>();
        if (record.getGradeLevel() != null) {
            queryWrapper.eq("grade_level", record.getGradeLevel());
        }
        if (record.getNoStandard() != null) {
            queryWrapper.eq("no_standard", record.getNoStandard());
        }
        if (record.getSiteNo() != null && !record.getSiteNo().isEmpty()) {
            queryWrapper.eq("site_no", record.getSiteNo());
        }
        startPage();
        com.github.pagehelper.PageInfo<TIsHazCard> page =
                new com.github.pagehelper.PageInfo<TIsHazCard>(
                        isHazCardService.selectTIsHazCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "重大危险源辨识单", action = "identify")
    @ApiOperation(value = "一罐一评开辨识单",
            notes = "按辨识基准日取现役线定级，级别+线代号+启用日写死；无可用标准明写；脏值退回重录")
    @PostMapping("/identify")
    @RequiresPermissions("is:isHazCard:add")
    @ResponseBody
    public AjaxResult identify(TIsHazCard record) {
        try {
            TIsHazCard saved = isHazCardService.identify(record, new Date());
            String msg = saved.getNoStandard() != null && saved.getNoStandard() == 1
                    ? "辨识基准日无可用标准，已明写落单"
                    : "已依线[" + saved.getRuleCode() + "]定为" + saved.getGradeLevel() + "级，落锤写死";
            return AjaxResult.success(msg).put("data", saved);
        } catch (RuntimeException ex) {
            // 空值/负数/超大怪值/底档缺失等：不定级，退回重录。
            return AjaxResult.error(ex.getMessage());
        }
    }

    @Log(title = "重大危险源辨识单新增", action = "add")
    @ApiOperation(value = "新增（兼容旧入口，等同 identify）", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("is:isHazCard:add")
    @ResponseBody
    public AjaxResult add(TIsHazCard record) {
        return identify(record);
    }

    @Log(title = "重大危险源辨识单历史复核", action = "review")
    @ApiOperation(value = "凭原单代号调线重评", notes = "原结论与复核结论各留各的代号上下文，原单不改")
    @PostMapping("/review")
    @RequiresPermissions("is:isHazCard:edit")
    @ResponseBody
    public AjaxResult review(Long id, String reviewDate) {
        try {
            Date d = reviewDate == null || reviewDate.isEmpty()
                    ? new Date()
                    : new SimpleDateFormat("yyyy-MM-dd").parse(reviewDate);
            return AjaxResult.success("复核结论已另存，原单未改")
                    .put("data", isHazCardService.reviewCard(id, d, new Date()));
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        } catch (java.text.ParseException ex) {
            return AjaxResult.error("复核日期格式不对，应为 yyyy-MM-dd");
        }
    }

    @ApiOperation(value = "一张单的历次复核结论", notes = "各留各的代号上下文")
    @GetMapping("/reviews")
    @RequiresPermissions("is:isHazCard:list")
    @ResponseBody
    public AjaxResult reviews(Long id) {
        return AjaxResult.successData(200, isHazCardService.listReviews(id));
    }

    @ApiOperation(value = "重点监管名录屏",
            notes = "园区自检屏与市局抽查屏同一个出处；只点算已落锤单，不现场套线")
    @GetMapping("/roster")
    @RequiresPermissions("is:isHazCard:list")
    @ResponseBody
    public AjaxResult roster(String date) {
        try {
            Date d = date == null || date.isEmpty()
                    ? new Date()
                    : new SimpleDateFormat("yyyy-MM-dd").parse(date);
            return AjaxResult.successData(200, isHazCardService.rosterScreen(d));
        } catch (java.text.ParseException ex) {
            return AjaxResult.error("日期格式不对，应为 yyyy-MM-dd");
        }
    }

    @Log(title = "重大危险源档案卡修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "落锤字段不接改，只许动状态/备注等")
    @PostMapping("/edit")
    @RequiresPermissions("is:isHazCard:edit")
    @ResponseBody
    public AjaxResult editSave(TIsHazCard record) {
        return toAjax(isHazCardService.updateTIsHazCard(record));
    }

    @Log(title = "重大危险源档案卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("is:isHazCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(isHazCardService.deleteTIsHazCardByIds(ids));
    }
}
