package com.leadrat.aisdk.configure;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ai-sdk")
public class ConfigureUiController {

    @GetMapping
    public String index() {
        return "redirect:/ai-sdk/setup";
    }

    @GetMapping("/setup")
    public String setup() {
        return "ai-sdk/setup";
    }

    @GetMapping("/auth")
    public String auth() {
        return "ai-sdk/auth";
    }

    @GetMapping("/configure")
    public String configure() {
        return "ai-sdk/configure";
    }

    @GetMapping("/console")
    public String console() {
        return "ai-sdk/console";
    }

    @GetMapping("/embed")
    public String embed() {
        return "ai-sdk/embed";
    }
}
