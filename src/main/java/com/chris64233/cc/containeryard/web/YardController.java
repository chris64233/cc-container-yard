package com.chris64233.cc.containeryard.web;

import com.chris64233.cc.containeryard.service.YardService;
import com.chris64233.cc.containeryard.service.dto.StackView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    @GetMapping("/yard/layout")
    public List<StackView> layout() {
        return yardService.layout();
    }

    @PostMapping("/stacks")
    @ResponseStatus(HttpStatus.CREATED)
    public StackView createStack(@Valid @RequestBody CreateStackRequest request) {
        return yardService.createStack(request.code(), request.maxTiers(), request.maxWeight());
    }

    @PostMapping("/containers")
    @ResponseStatus(HttpStatus.CREATED)
    public StackView placeContainer(@Valid @RequestBody PlaceContainerRequest request) {
        return yardService.placeContainer(request.containerNo(), request.weight(), request.stackCode());
    }

    public record CreateStackRequest(@NotBlank String code,
                                     @Min(1) int maxTiers,
                                     @Min(1) long maxWeight) {
    }

    public record PlaceContainerRequest(@NotBlank String containerNo,
                                        @Min(1) long weight,
                                        @NotBlank String stackCode) {
    }
}
