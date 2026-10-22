package com.routedispatch.plan.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routedispatch.common.ConflictException;
import com.routedispatch.plan.PlanService;
import com.routedispatch.plan.PlanStatus;
import com.routedispatch.plan.api.PlanViews.PlanSummaryView;
import com.routedispatch.security.SecurityProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: routing, validation, error mapping and role rules, with the services mocked. */
@WebMvcTest(PlanController.class)
@ImportAutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)
@ComponentScan(basePackages = "com.routedispatch.security", useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*SecurityConfiguration"))
class PlanControllerSliceTest {

    @TestConfiguration
    static class Clocks {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean PlanService planService;
    @MockitoBean PlanQueryService queries;

    static final SimpleGrantedAuthority DISPATCHER = new SimpleGrantedAuthority("ROLE_DISPATCHER");
    static final SimpleGrantedAuthority DRIVER = new SimpleGrantedAuthority("ROLE_DRIVER");

    @Test
    void anonymousCallsAreUnauthorized() throws Exception {
        mvc.perform(get("/api/plans")).andExpect(status().isUnauthorized());
    }

    @Test
    void driversCannotUseDispatcherEndpoints() throws Exception {
        mvc.perform(get("/api/plans").with(jwt().authorities(DRIVER))).andExpect(status().isForbidden());
    }

    @Test
    void dispatcherListsPlans() throws Exception {
        when(queries.list()).thenReturn(List.of(new PlanSummaryView(3, "Monday", LocalDate.of(2026, 10, 26),
                PlanStatus.PLANNED, 2, 200, "Kinzie Corridor Depot", Instant.EPOCH)));

        mvc.perform(get("/api/plans").with(jwt().authorities(DISPATCHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Monday"))
                .andExpect(jsonPath("$[0].stops").value(200));
    }

    @Test
    void createPlanValidatesTheBody() throws Exception {
        mvc.perform(post("/api/plans").with(jwt().authorities(DISPATCHER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"depotId\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void conflictsBecome409ProblemDetails() throws Exception {
        doThrow(new ConflictException("Plan 5 is dispatched")).when(planService).optimize(anyLong());

        mvc.perform(post("/api/plans/5/optimize").with(jwt().authorities(DISPATCHER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Plan 5 is dispatched"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void timeScaleOutsideTheAllowedRangeIsRejected() throws Exception {
        mvc.perform(post("/api/plans/5/dispatch").with(jwt().authorities(DISPATCHER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"timeScale\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(post("/api/plans").with(jwt().authorities(DISPATCHER))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
