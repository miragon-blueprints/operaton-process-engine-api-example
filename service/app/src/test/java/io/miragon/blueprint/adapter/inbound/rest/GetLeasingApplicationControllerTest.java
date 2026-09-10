package io.miragon.blueprint.adapter.inbound.rest;

import io.miragon.blueprint.application.port.inbound.GetLeasingApplicationQuery;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(GetLeasingApplicationController.class)
class GetLeasingApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetLeasingApplicationQuery query;

    @Test
    @DisplayName("returns the application with all of its fields mapped when it exists")
    void returnsTheApplicationWithAllOfItsFieldsMappedWhenItExists() throws Exception {

        // given: a fully-populated application (incl. order and contract) the query can find
        LeasingApplication application = testLeasingApplication()
            .orderId(new OrderId("ORDER-42"))
            .contractId(new ContractId("CONTRACT-7"))
            .build();
        given(query.byId(application.id()))
            .willReturn(new GetLeasingApplicationQuery.Result(application, "Gravel Explorer 900"));
        MockHttpServletRequestBuilder operation =
            get("/api/bike-leasing/{applicationId}", application.id().value().toString());

        // when: the request is performed
        MvcResult response = mockMvc.perform(operation).andReturn();

        // then: the response is 200 and every DTO field is mapped from the domain object
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        assertThat(response.getResponse().getContentAsString()).contains(
            application.id().value().toString(),
            application.customerName().value(),
            application.email().value(),
            application.bikeId().value(),
            "Gravel Explorer 900",
            "RECEIVED",
            "ORDER-42",
            "CONTRACT-7");
        then(query).should().byId(application.id());
        then(query).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("maps a not-yet-ordered application with null order and contract")
    void mapsANotYetOrderedApplicationWithNullOrderAndContract() throws Exception {

        // given: an application still without an order or contract (both nullable fields absent)
        LeasingApplication application = testLeasingApplication().orderId(null).contractId(null).build();
        given(query.byId(application.id())).willReturn(new GetLeasingApplicationQuery.Result(application, null));
        MockHttpServletRequestBuilder operation =
            get("/api/bike-leasing/{applicationId}", application.id().value().toString());

        // when: the request is performed
        MvcResult response = mockMvc.perform(operation).andReturn();

        // then: the response is 200 and the nullable fields are serialised as null
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        assertThat(response.getResponse().getContentAsString())
            .contains("\"orderId\":null", "\"contractId\":null", "\"bikeModel\":null");
    }

    @Test
    @DisplayName("returns 404 when the application does not exist")
    void returns404WhenTheApplicationDoesNotExist() throws Exception {

        // given: an unknown application id
        ApplicationId id = ApplicationId.of("123e4567-e89b-12d3-a456-426614174000");
        given(query.byId(id)).willReturn(null);
        MockHttpServletRequestBuilder operation = get("/api/bike-leasing/{applicationId}", id.value().toString());

        // when: the request is performed
        MvcResult response = mockMvc.perform(operation).andReturn();

        // then: the response is 404 Not Found
        assertThat(response.getResponse().getStatus()).isEqualTo(404);
    }
}
