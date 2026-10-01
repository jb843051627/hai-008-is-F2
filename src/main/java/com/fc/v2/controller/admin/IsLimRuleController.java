package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TIsLimRule;
import com.fc.v2.service.ITIsLimRuleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.beans.PropertyEditorSupport;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 重大危险源辨识判定线册子 Controller。
 *
 * 只添行、翻旗，永不删旧行；让位行留着服务历史复核。
 *
 * @author fuce
 * @date 2026-10-01
 */
@Api(value = "重大危险源辨识判定线册子")
@Controller
@RequestMapping("/IsLimRuleController")
public class IsLimRuleController extends BaseController {

    private final String prefix = "admin/isLimRule";

    @Autowired
    private ITIsLimRuleService limRuleService;

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

    @ApiOperation(value = "册子分页跳转", notes = "现行让位都翻得到")
    @GetMapping("/view")
    @RequiresPermissions("is:isLimRule:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "判定线册子查询", action = "list")
    @ApiOperation(value = "册子全量", notes = "换标准不删旧行，现行让位都在")
    @GetMapping("/list")
    @RequiresPermissions("is:isLimRule:list")
    @ResponseBody
    public ResultTable list() {
        startPage();
        com.github.pagehelper.PageInfo<TIsLimRule> page =
                new com.github.pagehelper.PageInfo<TIsLimRule>(limRuleService.listBook());
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "判定线册子添行", action = "add")
    @ApiOperation(value = "登记一条判定线", notes = "换标准只添行；同代号版本生效窗重叠拒登")
    @PostMapping("/add")
    @RequiresPermissions("is:isLimRule:add")
    @ResponseBody
    public AjaxResult add(TIsLimRule rule) {
        try {
            TIsLimRule saved = limRuleService.addRule(rule);
            return AjaxResult.success("判定线[" + saved.getRuleCode() + "]已入册").put("data", saved);
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @Log(title = "判定线交棒让位", action = "retire")
    @ApiOperation(value = "交棒翻旗", notes = "交棒日零点起旧线只许查历史；旧行保留不删")
    @PostMapping("/retire")
    @RequiresPermissions("is:isLimRule:add")
    @ResponseBody
    public AjaxResult retire(Long id, Date handoverDate) {
        try {
            TIsLimRule r = limRuleService.retireRule(id, handoverDate);
            return AjaxResult.success("线[" + r.getRuleCode() + "]已于交棒日让位，旧行保留可查历史")
                    .put("data", r);
        } catch (RuntimeException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }
}
