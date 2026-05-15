package com.brunosong.otel.service;

import com.brunosong.otel.domain.Sample;
import com.brunosong.otel.domain.SampleRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SampleService {

    private final SampleRepository sampleRepository;

    @WithSpan("SampleService.create")
    @Transactional
    public Sample create(@SpanAttribute("sample.name") String name) {
        Span.current().setAttribute("sample.name.length", name.length());
        log.debug("creating sample name={}", name);
        return sampleRepository.save(new Sample(name));
    }

    @WithSpan("SampleService.findAll")
    @Transactional(readOnly = true)
    public List<Sample> findAll() {
        return sampleRepository.findAll();
    }
}
