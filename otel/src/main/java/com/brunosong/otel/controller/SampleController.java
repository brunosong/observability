package com.brunosong.otel.controller;

import com.brunosong.otel.domain.Sample;
import com.brunosong.otel.service.SampleService;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    @GetMapping
    public List<Sample> list() {
        return sampleService.findAll();
    }

    @PostMapping
    public Sample create(@RequestParam String name) {
        return sampleService.create(name);
    }

    @GetMapping("/trace-id")
    public Map<String, String> traceId() {
        return Map.of("traceId", Span.current().getSpanContext().getTraceId());
    }
}
