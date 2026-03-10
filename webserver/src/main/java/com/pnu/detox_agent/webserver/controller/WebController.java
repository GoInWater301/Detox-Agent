package com.pnu.detox_agent.webserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

@Controller
public class WebController {

    @GetMapping("/")
    public Mono<String> index() {
        return Mono.just("redirect:/login");
    }

    @GetMapping("/login")
    public Mono<String> login() {
        return Mono.just("login");
    }

    @GetMapping("/register")
    public Mono<String> register() {
        return Mono.just("register");
    }

    @GetMapping("/dashboard")
    public Mono<String> dashboard() {
        return Mono.just("dashboard");
    }

    @GetMapping("/review")
    public Mono<String> review() {
        return Mono.just("review");
    }

    @GetMapping("/blocklist")
    public Mono<String> blocklist() {
        return Mono.just("blocklist");
    }
}
