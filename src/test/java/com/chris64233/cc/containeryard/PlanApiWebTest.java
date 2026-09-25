package com.chris64233.cc.containeryard;

import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.MoveEventRepository;
import com.chris64233.cc.containeryard.repo.MovePlanRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlanApiWebTest {

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

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        planRepository.deleteAll();
        containerRepository.deleteAll();
        stackRepository.deleteAll();

        yardService.createStack("S1", 3, 100);
        yardService.createStack("S2", 3, 100);
        yardService.placeContainer("C1", 10, "S1");
        yardService.placeContainer("C2", 20, "S1");
    }

    @Test
    void fullPlanLifecycleOverHttp() throws Exception {
        String body = "{\"steps\":[{\"containerNo\":\"C2\",\"targetStack\":\"S2\"}]}";

        mvc.perform(post("/api/plans/simulate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        Long planId = createPlan(body);

        mvc.perform(post("/api/plans/{id}/execute", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.events.length()").value(1));

        mvc.perform(post("/api/plans/{id}/execute", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.events.length()").value(1));

        mvc.perform(get("/api/yard/layout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].containers[0].containerNo").value("C2"));

        mvc.perform(get("/api/events").param("planId", planId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void invalidPlanReturns422AndIsNotSaved() throws Exception {
        mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steps\":[{\"containerNo\":\"C1\",\"targetStack\":\"S2\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.valid").value(false));

        mvc.perform(get("/api/plans/999")).andExpect(status().isNotFound());
    }

    @Test
    void stalePlanReturns409() throws Exception {
        String moveC2 = "{\"steps\":[{\"containerNo\":\"C2\",\"targetStack\":\"S2\"}]}";
        Long planA = createPlan(moveC2);
        Long planB = createPlan(moveC2);

        mvc.perform(post("/api/plans/{id}/execute", planB)).andExpect(status().isOk());
        mvc.perform(post("/api/plans/{id}/execute", planA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("STALE_REJECTED"));
    }

    private Long createPlan(String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }
}
