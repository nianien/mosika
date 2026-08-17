package com.nianien.mosika.ui.web.controller;

import com.nianien.mosika.ui.web.common.ApiResponse;
import com.nianien.mosika.ui.web.service.RuleSuiteManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统级操作：显式刷新 RuleSuite 等。
 *
 * @author skyfalling {@literal <skyfalling@live.com>}
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final RuleSuiteManager suiteManager;

    @PostMapping("/refresh")
    public ApiResponse<Void> refresh() {
        suiteManager.refresh();
        return ApiResponse.ok();
    }
}
