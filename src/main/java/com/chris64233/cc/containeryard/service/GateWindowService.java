package com.chris64233.cc.containeryard.service;

import com.chris64233.cc.containeryard.domain.GateWindow;
import com.chris64233.cc.containeryard.repo.GateWindowRepository;
import com.chris64233.cc.containeryard.service.dto.WindowRequest;
import com.chris64233.cc.containeryard.service.dto.WindowView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class GateWindowService {

    private final GateWindowRepository windowRepository;

    public GateWindowService(GateWindowRepository windowRepository) {
        this.windowRepository = windowRepository;
    }

    @Transactional
    public WindowView createWindow(WindowRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new IllegalStateException("时间窗结束时间必须晚于开始时间");
        }
        List<GateWindow> overlapping = windowRepository.findOverlapping(request.startAt(), request.endAt());
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException("时间窗与已有时段重叠: "
                    + overlapping.get(0).getStartAt() + " ~ " + overlapping.get(0).getEndAt());
        }
        GateWindow window = windowRepository.save(
                new GateWindow(request.startAt(), request.endAt(), request.capacity()));
        return WindowView.from(window);
    }

    @Transactional(readOnly = true)
    public List<WindowView> schedule(Instant from, Instant to) {
        List<GateWindow> windows = (from == null || to == null)
                ? windowRepository.findAllByOrderByStartAtAsc()
                : windowRepository.findOverlapping(from, to);
        return windows.stream().map(WindowView::from).toList();
    }
}
