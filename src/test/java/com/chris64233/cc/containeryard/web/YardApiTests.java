package com.chris64233.cc.containeryard.web;

import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.MoveEventRepository;
import com.chris64233.cc.containeryard.repo.MovePlanRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class YardApiTests {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MovePlanRepository planRepository;
    @Autowired
    private MoveEventRepository eventRepository;
    @Autowired
    private ContainerRepository containerRepository;
    @Autowired
    private YardStackRepository stackRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        eventRepository.deleteAll();
        planRepository.deleteAll();
        containerRepository.deleteAll();
        stackRepository.deleteAll();
    }

    @Test
    void fullFlowViaApi() throws Exception {
        mvc.perform(post("/api/stacks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"A\",\"maxTiers\":3,\"maxTotalWeight\":1000}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/stacks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"B\",\"maxTiers\":3,\"maxTotalWeight\":1000}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/containers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"containerNo\":\"C1\",\"weight\":100,\"stackCode\":\"A\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/containers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"containerNo\":\"C2\",\"weight\":200,\"stackCode\":\"A\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/layout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("A"))
                .andExpect(jsonPath("$[0].containers.length()").value(2))
                .andExpect(jsonPath("$[0].currentTotalWeight").value(300));

        mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steps\":[{\"containerNo\":\"C1\",\"targetStackCode\":\"B\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PLAN_VALIDATION_FAILED"));
        assertThat(planRepository.count()).isZero();

        MvcResult created = mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steps\":[{\"containerNo\":\"C2\",\"targetStackCode\":\"B\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.steps[0].fromStackCode").value("A"))
                .andExpect(jsonPath("$.snapshots.length()").value(2))
                .andReturn();
        String body = created.getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(post("/api/plans/" + id + "/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.events.length()").value(1));

        mvc.perform(post("/api/plans/" + id + "/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.idempotentReplay").value(true))
                .andExpect(jsonPath("$.events.length()").value(1));

        mvc.perform(get("/api/plans/" + id + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].containerNo").value("C2"))
                .andExpect(jsonPath("$[0].fromStackCode").value("A"))
                .andExpect(jsonPath("$[0].toStackCode").value("B"));

        mvc.perform(get("/api/layout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentTotalWeight").value(100))
                .andExpect(jsonPath("$[1].currentTotalWeight").value(200));
    }
}
