package com.leadrat.aisdk.configure;

import com.leadrat.aisdk.security.PasswordStore;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ai-sdk")
public class ConfigureUiController {

    private final PasswordStore passwordStore;

    public ConfigureUiController(PasswordStore passwordStore) {
        this.passwordStore = passwordStore;
    }

    @ModelAttribute("setupCompleted")
    public boolean setupCompleted() {
        return passwordStore.isSetupCompleted();
    }

    @GetMapping
    public String index() {
        return passwordStore.isSetupCompleted() ? "redirect:/ai-sdk/auth" : "redirect:/ai-sdk/setup";
    }

    @GetMapping("/setup")
    public String setup() {
        return passwordStore.isSetupCompleted() ? "redirect:/ai-sdk/auth" : "ai-sdk/setup";
    }

    @GetMapping("/auth")
    public String auth() {
        return passwordStore.isSetupCompleted() ? "ai-sdk/auth" : "redirect:/ai-sdk/setup";
    }

    @GetMapping("/settings")
    public String settings() {
        return "ai-sdk/settings";
    }

    @GetMapping("/configure")
    public String configure() {
        return "ai-sdk/configure";
    }

    @GetMapping("/meetings")
    public String meetings() {
        return "ai-sdk/meetings";
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
