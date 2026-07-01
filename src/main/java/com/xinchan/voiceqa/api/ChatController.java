package com.xinchan.voiceqa.api;

import com.xinchan.voiceqa.routing.RouterService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final RouterService routerService;

    public ChatController(RouterService routerService) {
        this.routerService = routerService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return routerService.route(request);
    }
}