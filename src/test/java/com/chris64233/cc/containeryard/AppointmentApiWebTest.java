package com.chris64233.cc.containeryard;

import com.chris64233.cc.containeryard.repo.AppointmentRepository;
import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.GateWindowRepository;
import com.chris64233.cc.containeryard.repo.MoveEventRepository;
import com.chris64233.cc.containeryard.repo.MovePlanRepository;
import com.chris64233.cc.containeryard.repo.SlotReservationRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
import com.chris64233.cc.containeryard.service.YardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

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
    private YardStackRepository stackRepository;
    @Autowired
    private ContainerRepository containerRepository;
    @Autowired
    private MovePlanRepository planRepository;
    @Autowired
    private MoveEventRepository eventRepository;
    @Autowired
    private GateWindowRepository windowRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private SlotReservationRepository slotRepository;

    @BeforeEach
    void setUp() {
        slotRepository.deleteAll();
        appointmentRepository.deleteAll();
        windowRepository.deleteAll();
        eventRepository.deleteAll();
        planRepository.deleteAll();
        containerRepository.deleteAll();
        stackRepository.deleteAll();

        yardService.createStack("S1", 3, 100);
        yardService.createStack("S2", 3, 100);
        yardService.placeContainer("C1", 10, "S1");
        yardService.placeContainer("C2", 20, "S1");
    }

    private Long createWindow(int capacity) throws Exception {
        String body = "{\"startAt\":\"" + Instant.now().minusSeconds(3600) + "\","
                + "\"endAt\":\"" + Instant.now().plusSeconds(3600) + "\","
                + "\"capacity\":" + capacity + "}";
        MvcResult result = mvc.perform(post("/api/gate/windows")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String inBookingJson(String no, String containerNo, long weight, Long windowId, String stack) {
        return "{\"appointmentNo\":\"" + no + "\",\"containerNo\":\"" + containerNo + "\","
                + "\"direction\":\"IN\",\"vehicleNo\":\"T-" + containerNo + "\","
                + "\"windowId\":" + windowId + ",\"targetStack\":\"" + stack + "\","
                + "\"weight\":" + weight + "}";
    }

    private String outBookingJson(String no, String containerNo, Long windowId, String stack) {
        return "{\"appointmentNo\":\"" + no + "\",\"containerNo\":\"" + containerNo + "\","
                + "\"direction\":\"OUT\",\"vehicleNo\":\"T-" + containerNo + "\","
                + "\"windowId\":" + windowId + ",\"targetStack\":\"" + stack + "\"}";
    }

    @Test
    void inboundAppointmentLifecycleOverHttp() throws Exception {
        Long windowId = createWindow(2);

        mvc.perform(get("/api/gate/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].capacity").value(2))
                .andExpect(jsonPath("$[0].remaining").value(2));

        String booking = inBookingJson("A-1", "C9", 40, windowId, "S1");
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.slot.stackCode").value("S1"))
                .andExpect(jsonPath("$.slot.tier").value(3));

        // 相同预约业务号重放返回原预约，不重复占用资源
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentNo").value("A-1"));

        mvc.perform(get("/api/gate/schedule"))
                .andExpect(jsonPath("$[0].bookedCount").value(1));

        mvc.perform(get("/api/yard/occupancy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reservedTiers").value(1))
                .andExpect(jsonPath("$[0].reservedSlots[0].containerNo").value("C9"));

        mvc.perform(get("/api/appointments/A-1/pre-moves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        String arrival = "{\"arrivalNo\":\"ARR-1\",\"planVersion\":0}";
        mvc.perform(post("/api/appointments/A-1/arrival")
                        .contentType(MediaType.APPLICATION_JSON).content(arrival))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CONFIRMED"))
                .andExpect(jsonPath("$.appointment.status").value("ARRIVED"));

        mvc.perform(post("/api/appointments/A-1/arrival")
                        .contentType(MediaType.APPLICATION_JSON).content(arrival))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CONFIRMED"));

        mvc.perform(get("/api/appointments/A-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"))
                .andExpect(jsonPath("$.arrivalNo").value("ARR-1"));
    }

    @Test
    void outboundAppointmentExposesPreMoveStepsOverHttp() throws Exception {
        Long windowId = createWindow(2);

        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(outBookingJson("A-1", "C1", windowId, "S1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planVersion").value(1))
                .andExpect(jsonPath("$.preMoveSteps[0].containerNo").value("C2"))
                .andExpect(jsonPath("$.preMoveSteps[0].toStack").value("S2"));

        mvc.perform(get("/api/appointments/A-1/pre-moves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromStack").value("S1"));

        mvc.perform(post("/api/appointments/A-1/arrival").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arrivalNo\":\"ARR-1\",\"planVersion\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("版本不一致")));

        mvc.perform(post("/api/appointments/A-1/arrival").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arrivalNo\":\"ARR-1\",\"planVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CONFIRMED"));
    }

    @Test
    void cancelReleasesCapacityAndIsIdempotentOverHttp() throws Exception {
        Long windowId = createWindow(1);
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(inBookingJson("A-1", "C9", 10, windowId, "S2")))
                .andExpect(status().isCreated());

        // 容量已满，第二笔预约 422
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(inBookingJson("A-2", "C10", 10, windowId, "S2")))
                .andExpect(status().isUnprocessableEntity());

        String cancel = "{\"cancelNo\":\"X-1\"}";
        mvc.perform(post("/api/appointments/A-1/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content(cancel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/appointments/A-1/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content(cancel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 容量与堆位已释放，可再次预约
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(inBookingJson("A-3", "C10", 10, windowId, "S2")))
                .andExpect(status().isCreated());
    }

    @Test
    void unknownAppointmentReturns404() throws Exception {
        mvc.perform(get("/api/appointments/NOPE")).andExpect(status().isNotFound());
        mvc.perform(post("/api/appointments/NOPE/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelNo\":\"X-1\"}"))
                .andExpect(status().isNotFound());
    }
}
