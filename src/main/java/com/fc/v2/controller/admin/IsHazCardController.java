package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TIsHazCard;
import com.fc.v2.model.auto.TIsHazReview;
import com.fc.v2.service.ITIsHazCardService;
import com.fc.v2.service.haz.DeadlineRuler;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 重大危险源档案卡（辨识单）Controller。
 *
 * 一罐一单一评定：按辨识那天现役的判定线定级，级别连同线代号、线启用日写死，事后倒查。
 * 补辨识传建成那年的日子（identDate，yyyy-MM-dd）；不传按今天。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "重大危险源档案卡")
@Controller
@RequestMapping("/IsHazCardController")
public class IsHazCardController extends BaseController {

    private final String prefix = "admin/isHazCard";

    @Autowired
    private ITIsHazCardService isHazCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("is:isHazCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "重大危险源辨识单集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("is:isHazCard:list")
    @ResponseBody
    public ResultTable list(TIsHazCard record) {
        QueryWrapper<TIsHazCard> queryWrapper = new QueryWrapper<TIsHazCard>()
                .eq("del_flag", 0)
                .orderByDesc("ident_date")
                .orderByDesc("id");
        startPage();
        List<TIsHazCard> list = isHazCardService.selectTIsHazCardList(queryWrapper);
        com.github.pagehelper.PageInfo<TIsHazCard> page =
                new com.github.pagehelper.PageInfo<>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "辨识单明细", notes = "级别、所依线代号与启用日、原始储存量一翻便知")
    @GetMapping("/detail")
    @RequiresPermissions("is:isHazCard:list")
    @ResponseBody
    public AjaxResult detail(Long id) {
        TIsHazCard card = isHazCardService.selectTIsHazCardById(id);
        if (card == null) {
            return AjaxResult.error("辨识单不存在");
        }
        return AjaxResult.successData(200, card).put("reviews", isHazCardService.listReviews(id));
    }

    @Log(title = "重大危险源辨识定级", action = "identify")
    @ApiOperation(value = "一罐一单按辨识那天现役判定线定级", notes = "identDate 空=今天；老罐子补辨识传建成那年的日子(yyyy-MM-dd)")
    @PostMapping("/identify")
    @RequiresPermissions("is:isHazCard:add")
    @ResponseBody
    public AjaxResult identify(TIsHazCard record,
                               @RequestParam(required = false) String identDate) {
        Date at;
        try {
            at = parseIdentDate(identDate);
        } catch (ParseException ex) {
            return AjaxResult.error("辨识日格式不对，应为 yyyy-MM-dd，退回重录");
        }
        try {
            TIsHazCard saved = isHazCardService.identify(record, at);
            String msg = gradeMsg(saved);
            return AjaxResult.success(msg).put("data", saved);
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @Log(title = "重大危险源辨识历史复核", action = "review")
    @ApiOperation(value = "凭原单代号调线在原辨识日重评", notes = "不许拿今天的标准改昨天的单；原结论与复核结论各留各的代号上下文")
    @PostMapping("/review")
    @RequiresPermissions("is:isHazCard:handle")
    @ResponseBody
    public AjaxResult review(Long id) {
        try {
            TIsHazReview rv = isHazCardService.review(id, new Date());
            return AjaxResult.success(rv.getConclusion()).put("data", rv);
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @Log(title = "重大危险源辨识单修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "辨识日/原始储存量/级别/线代号/线启用日写过不改，只许动进展等活档")
    @PostMapping("/edit")
    @RequiresPermissions("is:isHazCard:edit")
    @ResponseBody
    public AjaxResult editSave(TIsHazCard record) {
        return toAjax(isHazCardService.updateTIsHazCard(record));
    }

    @Log(title = "重大危险源辨识单删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("is:isHazCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(isHazCardService.deleteTIsHazCardByIds(ids));
    }

    /** 辨识日统一按 GMT+8 墙钟解析（yyyy-MM-dd），空串当今天；与全局 datetime 绑定分开走。 */
    private static Date parseIdentDate(String s) throws ParseException {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        fmt.setLenient(false);
        fmt.setTimeZone(DeadlineRuler.ZONE);
        return fmt.parse(s.trim());
    }

    /** 落单回执话术：定了几级、依的哪条线；无可用标准明说，不装成定级成功。 */
    private static String gradeMsg(TIsHazCard card) {
        if (card.getGradeLevel() == null) {
            return "辨识单已留档：辨识日无可用判定线，已明写“无可用标准”，未作定级";
        }
        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd");
        dayFmt.setTimeZone(DeadlineRuler.ZONE);
        return "辨识完成：" + card.getGradeLevel() + " 级，所依线 "
                + card.getRuleCode() + "（启用日 " + dayFmt.format(card.getRuleEffStart())
                + "），已写死不可改";
    }
}
