package com.xinchan.voiceqa.observability;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {
    private final ObservabilityMetrics metrics;

    public ObservabilityController(ObservabilityMetrics metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return metrics.snapshot();
    }
}