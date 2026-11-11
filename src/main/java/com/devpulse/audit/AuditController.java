package com.devpulse.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only view of the audit trail, so you can SEE the transaction behavior from outside. */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<AuditService.AuditEntry> recent(@RequestParam(defaultValue = "20") int limit) {
        return audit.findRecent(Math.max(1, Math.min(limit, 200)));
    }
}
