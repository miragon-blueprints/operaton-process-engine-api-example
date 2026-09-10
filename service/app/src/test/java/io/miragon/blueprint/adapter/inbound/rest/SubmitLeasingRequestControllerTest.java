package io.miragon.blueprint.adapter.inbound.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.miragon.blueprint.application.port.inbound.SubmitLeasingRequestUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(SubmitLeasingRequestController.class)
class SubmitLeasingRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitLeasingRequestUseCase useCase;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("user submits a leasing request")
    void userSubmitsALeasingRequest() throws Exception {

        // given: valid input data & rest-operation
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("customerName", "John Doe");
        input.put("email", "john.doe@test.com");
        input.put("age", 35);
        input.put("monthlyNetIncome", 3500.0);
        input.put("bikeId", "BIKE-900");
        input.put("bikeModel", "Gravel Explorer 900");
        ApplicationId applicationId = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        SubmitLeasingRequestUseCase.Command expectedCommand =
            new SubmitLeasingRequestUseCase.Command(
                new CustomerName("John Doe"),
                new Email("john.doe@test.com"),
                35,
                3500.0,
                new BikeId("BIKE-900"),
                "Gravel Explorer 900");
        given(useCase.submit(any())).willReturn(applicationId);
        MockHttpServletRequestBuilder operation =
            post("/api/bike-leasing")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(input));

        // when: the request is performed
        MvcResult response = mockMvc.perform(operation).andReturn();

        // then: the use case is invoked with the mapped command and the id is returned
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        assertThat(response.getResponse().getContentAsString()).contains(applicationId.value().toString());
        then(useCase).should().submit(expectedCommand);
        then(useCase).shouldHaveNoMoreInteractions();
    }
}
