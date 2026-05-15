package com.brunosong.otelsample.controller;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return greet("world");
    }

    @WithSpan("greet")
    String greet(String name) {
        return "hello, " + name + " (from otel-sample-service)";
    }
}
