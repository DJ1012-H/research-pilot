package com.dj1012h.researchpilot.controller;

import com.dj1012h.researchpilot.dto.response.SystemStatusResponse;
import com.dj1012h.researchpilot.service.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final SystemStatusService systemStatusService;

    public SystemStatusController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/status")
    public SystemStatusResponse status() {
        return systemStatusService.check();
    }
}
