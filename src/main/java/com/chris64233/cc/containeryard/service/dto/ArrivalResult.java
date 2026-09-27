package com.chris64233.cc.containeryard.service.dto;

/**
 * 到场确认结果。
 * <ul>
 *   <li>{@code ARRIVED}：按（可能重新规划后的）计划完成提/落箱；</li>
 *   <li>{@code NEEDS_MANUAL}：旧出场计划已陈旧且重新规划失败，预约保留为 APPROVED 等待人工处理。</li>
 * </ul>
 */
public record ArrivalResult(String outcome, boolean replanned, String message,
                            AppointmentView appointment) {

    public static final String OUTCOME_ARRIVED = "ARRIVED";
    public static final String OUTCOME_NEEDS_MANUAL = "NEEDS_MANUAL";
}
