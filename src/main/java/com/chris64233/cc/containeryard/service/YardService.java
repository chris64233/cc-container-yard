package com.chris64233.cc.containeryard.service;

import com.chris64233.cc.containeryard.domain.Container;
import com.chris64233.cc.containeryard.domain.YardStack;
import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
import com.chris64233.cc.containeryard.service.dto.StackView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class YardService {

    private final YardStackRepository stackRepository;
    private final ContainerRepository containerRepository;

    public YardService(YardStackRepository stackRepository, ContainerRepository containerRepository) {
        this.stackRepository = stackRepository;
        this.containerRepository = containerRepository;
    }

    @Transactional
    public StackView createStack(String code, int maxTiers, long maxWeight) {
        stackRepository.findByCode(code).ifPresent(s -> {
            throw new DuplicateException("堆栈已存在: " + code);
        });
        YardStack stack = stackRepository.save(new YardStack(code, maxTiers, maxWeight));
        return toView(stack, List.of());
    }

    @Transactional
    public StackView placeContainer(String containerNo, long weight, String stackCode) {
        containerRepository.findByContainerNo(containerNo).ifPresent(c -> {
            throw new DuplicateException("箱号已存在: " + containerNo);
        });
        YardStack stack = stackRepository.findByCode(stackCode)
                .orElseThrow(() -> new NotFoundException("堆栈不存在: " + stackCode));
        if (!stack.canAccept(weight)) {
            throw new IllegalStateException("堆栈 " + stackCode + " 层数或重量超限，无法入场");
        }
        Container container = new Container(containerNo, weight);
        stack.addTop(weight);
        container.placeOn(stack, stack.getCurrentTiers());
        containerRepository.save(container);
        return toView(stack, containerRepository.findByStackCodeOrderByTier(stackCode));
    }

    @Transactional(readOnly = true)
    public List<StackView> layout() {
        return stackRepository.findAllByOrderByCode().stream()
                .map(s -> toView(s, containerRepository.findByStackCodeOrderByTier(s.getCode())))
                .toList();
    }

    private StackView toView(YardStack stack, List<Container> containers) {
        return new StackView(stack.getCode(), stack.getMaxTiers(), stack.getMaxWeight(),
                stack.getCurrentTiers(), stack.getCurrentWeight(), stack.getVersion(),
                containers.stream()
                        .map(c -> new StackView.ContainerView(c.getContainerNo(), c.getWeight(), c.getTier()))
                        .toList());
    }
}
