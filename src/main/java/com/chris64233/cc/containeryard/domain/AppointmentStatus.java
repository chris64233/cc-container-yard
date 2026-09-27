package com.chris64233.cc.containeryard.domain;

public enum AppointmentStatus {
    /** 已提交待批准。 */
    PENDING,
    /** 已批准：闸口名额与（进场）堆位已预留，或（出场）前置移箱计划已生成。 */
    APPROVED,
    /** 车辆已到场确认完成。 */
    ARRIVED,
    /** 已取消，资源至多释放一次。 */
    CANCELLED
}
