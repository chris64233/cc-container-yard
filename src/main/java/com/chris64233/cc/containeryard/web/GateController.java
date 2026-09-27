package com.chris64233.cc.containeryard.web;

import com.chris64233.cc.containeryard.service.GateWindowService;
import com.chris64233.cc.containeryard.service.dto.WindowRequest;
import com.chris64233.cc.containeryard.service.dto.WindowView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/gate")
public class GateController {

    private final GateWindowService windowService;

    public GateController(GateWindowService windowService) {
        this.windowService = windowService;
    }

    @PostMapping("/windows")
    @ResponseStatus(HttpStatus.CREATED)
    public WindowView createWindow(@Valid @RequestBody WindowRequest request) {
        return windowService.createWindow(request);
    }

    @GetMapping("/schedule")
    public List<WindowView> schedule(@RequestParam(required = false) Instant from,
                                     @RequestParam(required = false) Instant to) {
        return windowService.schedule(from, to);
    }
}
