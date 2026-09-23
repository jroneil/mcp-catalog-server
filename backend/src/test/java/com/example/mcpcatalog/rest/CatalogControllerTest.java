package com.example.mcpcatalog.rest;

import java.math.BigDecimal;
import java.util.List;

import com.example.mcpcatalog.catalog.application.CatalogPage;
import com.example.mcpcatalog.catalog.application.CatalogSearchCriteria;
import com.example.mcpcatalog.catalog.application.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogControllerTest {
    private CatalogService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CatalogService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CatalogController(service))
                .setControllerAdvice(new CatalogRestExceptionHandler()).build();
    }

    @Test
    void omittedInputsReachServiceWithoutControllerDefaults() throws Exception {
        when(service.search(any())).thenReturn(new CatalogPage(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/v1/catalog")).andExpect(status().isOk());
        verify(service).search(new CatalogSearchCriteria(null, null, null, null, null, null));
    }

    @Test
    void controllerPassesEvenInvalidCriteriaUnchangedToService() throws Exception {
        when(service.search(any())).thenReturn(new CatalogPage(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/v1/catalog").param("type", "unknown").param("active", "false")
                .param("maxPrice", "-1.000").param("text", "  network  ")
                .param("page", "-1").param("pageSize", "101")).andExpect(status().isOk());
        verify(service).search(new CatalogSearchCriteria("unknown", false, new BigDecimal("-1.000"),
                "  network  ", -1, 101));
    }

    @ParameterizedTest
    @ValueSource(strings = {"active=garbage", "maxPrice=NaN", "page=1.5", "pageSize=2147483648"})
    void malformedParametersFailBeforeServiceInvocation(String query) throws Exception {
        mvc.perform(get("/api/v1/catalog?" + query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed request parameter"))
                .andExpect(jsonPath("$.path").value("/api/v1/catalog"));
        verifyNoInteractions(service);
    }

    @Test
    void malformedIdentifierFailsBeforeServiceInvocation() throws Exception {
        mvc.perform(get("/api/v1/catalog/not-a-number")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request parameter"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/catalog", "/api/v1/catalog/16"})
    void unexpectedFailuresAreSanitized(String path) throws Exception {
        var failure = new IllegalStateException("SELECT password FROM secrets; jdbc:postgresql://internal java.sql.SQLException");
        when(service.search(any())).thenThrow(failure);
        when(service.getItem(any())).thenThrow(failure);
        mvc.perform(get(path).param("text", "secret-query"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(4)))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.path").value(path));
    }
}
