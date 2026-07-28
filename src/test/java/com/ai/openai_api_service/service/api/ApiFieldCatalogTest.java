package com.ai.openai_api_service.service.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiFieldCatalogTest {

    private final ApiFieldCatalog catalog = new ApiFieldCatalog();

    @Test
    void ppsSearchHead_buyerColumns() {
        assertEquals(
                List.of("PUNO", "BUYE"),
                catalog.columnsFor(M3ApiKey.of("PPS200MI", "SearchHead"), "BUYER")
        );
    }

    @Test
    void pmsSearchMo_referenceOrderNumberColumns() {
        assertEquals(
                List.of("MFNO", "RORN"),
                catalog.columnsFor(M3ApiKey.of("PMS100MI", "SearchMO"), "REFERENCE_ORDER_NUMBER")
        );
    }

    @Test
    void mmsSearchHead_receivingDateColumns() {
        assertEquals(
                List.of("TRNR", "RIDT"),
                catalog.columnsFor(M3ApiKey.of("MMS100MI", "SearchHead"), "RECEIVING_DATE")
        );
    }

    @Test
    void oisSearchHead_netOrderValueColumns() {
        assertEquals(
                List.of("ORNO", "NTAM"),
                catalog.columnsFor(M3ApiKey.of("OIS100MI", "SearchHead"), "NET_ORDER_VALUE")
        );
    }

    @Test
    void getFinancial_totalDueInvoicesColumns() {
        assertEquals(
                List.of("TDIN"),
                catalog.columnsFor(M3ApiKey.of("CRS610MI", "GetFinancial"), "TOTAL_DUE_INVOICES")
        );
    }

    @Test
    void everySearchApiHasEntry() {
        assertNotNull(catalog.entryFor(M3ApiKey.of("PPS200MI", "SearchHead")));
        assertNotNull(catalog.entryFor(M3ApiKey.of("PMS100MI", "SearchMO")));
        assertNotNull(catalog.entryFor(M3ApiKey.of("MMS100MI", "SearchHead")));
    }
}
