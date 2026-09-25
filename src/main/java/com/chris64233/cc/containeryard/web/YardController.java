package com.chris64233.cc.containeryard.web;

import com.chris64233.cc.containeryard.service.YardService;
import com.chris64233.cc.containeryard.web.dto.Requests.CreateStackRequest;
import com.chris64233.cc.containeryard.web.dto.Requests.RegisterContainerRequest;
import com.chris64233.cc.containeryard.web.dto.Views.ContainerView;
import com.chris64233.cc.containeryard.web.dto.Views.StackView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class YardController {

    private final YardService yardService;

    public YardController(YardService yardService) {
        this.yardService = yardService;
    }

    @PostMapping("/stacks")
    @ResponseStatus(HttpStatus.CREATED)
    public StackView createStack(@Valid @RequestBody CreateStackRequest request) {
        return yardService.createStack(request.code(), request.maxTiers(), request.maxTotalWeight());
    }

    @PostMapping("/containers")
    @ResponseStatus(HttpStatus.CREATED)
    public ContainerView registerContainer(@Valid @RequestBody RegisterContainerRequest request) {
        return yardService.registerContainer(request.containerNo(), request.weight(), request.stackCode());
    }

    @GetMapping("/layout")
    public List<StackView> layout() {
        return yardService.layout();
    }
}
