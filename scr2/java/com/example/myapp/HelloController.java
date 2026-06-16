package com.example.myapp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/")
    public Map<String, String> index() {
        return Map.of("message", "Spring Boot is running!");
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello, Spring Boot!";
    }
}
