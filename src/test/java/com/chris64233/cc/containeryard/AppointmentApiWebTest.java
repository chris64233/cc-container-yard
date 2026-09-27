package com.chris64233.cc.containeryard;

import com.chris64233.cc.containeryard.repo.AppointmentRepository;
import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.GateWindowRepository;
import com.chris64233.cc.containeryard.repo.MoveEventRepository;
import com.chris64233.cc.containeryard.repo.MovePlanRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
import com.chris64233.cc.containeryard.service.GateWindowService;
import com.chris64233.cc.containeryard.service.YardService;
import com.chris64233.cc.containeryard.service.dto.WindowRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentApiWebTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private YardService yardService;
    @Autowired
    private GateWindowService windowService;
    @Autowired
    private GateWindowRepository windowRepository;
    @Autowired
    private YardStackRepository stackRepository;
    @Autowired
    private ContainerRepository containerRepository;
    @Autowired
    private MovePlanRepository planRepository;
    @Autowired
    private MoveEventRepository eventRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;

    private Long windowId;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        planRepository.deleteAll();
        appointmentRepository.deleteAll();
        containerRepository.deleteAll();
        stackRepository.deleteAll();
        windowRepository.deleteAll();

        yardService.createStack("S1", 3, 1000);
        yardService.createStack("S2", 3, 1000);
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(1, ChronoUnit.HOURS);
        windowId = windowService.createWindow(new WindowRequest(start, end, 1)).id();
    }

    @Test
    void inboundAppointmentFullLifecycle() throws Exception {
        String body = "{\"appointmentNo\":\"A1\",\"containerNo\":\"NEW1\",\"direction\":\"INBOUND\","
                + "\"vehicleNo\":\"V1\",\"windowId\":" + windowId + ",\"targetStack\":\"S1\",\"weight\":40}";

        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(post("/api/appointments/A1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.version").isNumber());

        // 第二笔批准触发闸口容量不足：整笔失败 422，预约仍 PENDING
        String body2 = body.replace("\"A1\"", "\"A2\"").replace("\"NEW1\"", "\"NEW2\"");
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(body2))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/appointments/A2/approve"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("容量不足")));

        long version = fetchAppointment("A1").get("version").asLong();
        String arrival = "{\"appointmentNo\":\"A1\",\"arrivalNo\":\"IN-1\",\"expectedVersion\":"
                + version + ",\"at\":\"" + Instant.now() + "\"}";
        mvc.perform(post("/api/appointments/A1/arrivals")
                        .contentType(MediaType.APPLICATION_JSON).content(arrival))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ARRIVED"));

        // 到场后箱已落位 S1
        mvc.perform(get("/api/yard/layout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].containers[0].containerNo").value("NEW1"))
                .andExpect(jsonPath("$[0].reservedTiers").value(0));

        // 预约详情、闸口日程
        mvc.perform(get("/api/appointments/A1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"));
        mvc.perform(get("/api/gate/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].served").value(1));
        mvc.perform(get("/api/occupancy/windows/" + windowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("GATE_WINDOW"));
    }

    @Test
    void outboundAppointmentGeneratesPlanStepsAndRetrieves() throws Exception {
        yardService.placeContainer("C1", 10, "S1");
        yardService.placeContainer("C2", 20, "S1");
        yardService.placeContainer("C3", 30, "S1");

        String body = "{\"appointmentNo\":\"O1\",\"containerNo\":\"C2\",\"direction\":\"OUTBOUND\","
                + "\"vehicleNo\":\"V9\",\"windowId\":" + windowId + ",\"weight\":0}";
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/appointments/O1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.planId").isNumber());

        mvc.perform(get("/api/appointments/O1/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("OUTBOUND_APPOINTMENT"))
                .andExpect(jsonPath("$.targetContainerNo").value("C2"))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].containerNo").value("C3"));

        long version = fetchAppointment("O1").get("version").asLong();
        String arrival = "{\"appointmentNo\":\"O1\",\"arrivalNo\":\"OUT-1\",\"expectedVersion\":"
                + version + ",\"at\":\"" + Instant.now() + "\"}";
        mvc.perform(post("/api/appointments/O1/arrivals")
                        .contentType(MediaType.APPLICATION_JSON).content(arrival))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ARRIVED"));

        // C2 已提走出场
        mvc.perform(get("/api/yard/layout"))
                .andExpect(jsonPath("$[0].containers[*].containerNo").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("C2"))));
    }

    @Test
    void wrongVersionAndDuplicateBusinessNosBehaveByIdempotencyRules() throws Exception {
        String body = "{\"appointmentNo\":\"A1\",\"containerNo\":\"NEW1\",\"direction\":\"INBOUND\","
                + "\"vehicleNo\":\"V1\",\"windowId\":" + windowId + ",\"targetStack\":\"S1\",\"weight\":40}";
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/appointments/A1/approve")).andExpect(status().isOk());

        // 错误版本 -> 409
        String badVersion = "{\"appointmentNo\":\"A1\",\"arrivalNo\":\"IN-1\",\"expectedVersion\":999,"
                + "\"at\":\"" + Instant.now() + "\"}";
        mvc.perform(post("/api/appointments/A1/arrivals")
                        .contentType(MediaType.APPLICATION_JSON).content(badVersion))
                .andExpect(status().isConflict());

        // 一个箱不能重复申报有效预约
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"A1\"", "\"A9\"")))
                .andExpect(status().isConflict());

        // 取消释放资源
        mvc.perform(post("/api/appointments/A1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentNo\":\"A1\",\"cancelNo\":\"CAN-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        // 重复取消同业务号幂等
        mvc.perform(post("/api/appointments/A1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentNo\":\"A1\",\"cancelNo\":\"CAN-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        // 不同取消业务号 -> 409
        mvc.perform(post("/api/appointments/A1/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentNo\":\"A1\",\"cancelNo\":\"CAN-2\"}"))
                .andExpect(status().isConflict());
    }

    private JsonNode fetchAppointment(String no) throws Exception {
        String result = mvc.perform(get("/api/appointments/" + no))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(result);
    }
}
