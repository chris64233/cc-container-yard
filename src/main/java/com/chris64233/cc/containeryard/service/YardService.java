package com.chris64233.cc.containeryard.service;

import com.chris64233.cc.containeryard.domain.Container;
import com.chris64233.cc.containeryard.domain.YardStack;
import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
import com.chris64233.cc.containeryard.web.dto.Views.ContainerView;
import com.chris64233.cc.containeryard.web.dto.Views.StackView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class YardService {

    private final YardStackRepository stackRepository;
    private final ContainerRepository containerRepository;

    public YardService(YardStackRepository stackRepository, ContainerRepository containerRepository) {
        this.stackRepository = stackRepository;
        this.containerRepository = containerRepository;
    }

    @Transactional
    public StackView createStack(String code, int maxTiers, long maxTotalWeight) {
        if (stackRepository.findByCode(code).isPresent()) {
            throw new PlanValidationException("堆栈 " + code + " 已存在");
        }
        YardStack stack = stackRepository.save(new YardStack(code, maxTiers, maxTotalWeight));
        return toView(stack, List.of());
    }

    @Transactional
    public ContainerView registerContainer(String containerNo, long weight, String stackCode) {
        if (containerRepository.findByContainerNo(containerNo).isPresent()) {
            throw new PlanValidationException("集装箱 " + containerNo + " 已存在");
        }
        YardStack stack = stackRepository.findByCode(stackCode)
                .orElseThrow(() -> new NotFoundException("堆栈 " + stackCode + " 不存在"));
        List<Container> existing = containerRepository.findByStack_CodeOrderByTierAsc(stackCode);
        if (existing.size() >= stack.getMaxTiers()) {
            throw new PlanValidationException("堆栈 " + stackCode + " 已达最大层数");
        }
        if (!stack.canAccept(weight)) {
            throw new PlanValidationException("堆栈 " + stackCode + " 超过最大总重量");
        }
        stack.accept(weight);
        Container container = containerRepository.save(
                new Container(containerNo, weight, stack, existing.size() + 1));
        return toView(container);
    }

    @Transactional(readOnly = true)
    public List<StackView> layout() {
        List<YardStack> stacks = stackRepository.findAllByOrderByCodeAsc();
        Map<String, List<Container>> byStack = containerRepository
                .findByStack_CodeInOrderByStack_CodeAscTierAsc(
                        stacks.stream().map(YardStack::getCode).toList())
                .stream()
                .collect(Collectors.groupingBy(c -> c.getStack().getCode()));
        return stacks.stream()
                .map(s -> toView(s, byStack.getOrDefault(s.getCode(), List.of())))
                .toList();
    }

    static StackView toView(YardStack stack, List<Container> containers) {
        return new StackView(stack.getCode(), stack.getMaxTiers(), stack.getMaxTotalWeight(),
                stack.getCurrentTotalWeight(), stack.getVersion(),
                containers.stream().map(YardService::toView).toList());
    }

    static ContainerView toView(Container container) {
        return new ContainerView(container.getContainerNo(), container.getWeight(),
                container.getStack().getCode(), container.getTier());
    }
}
