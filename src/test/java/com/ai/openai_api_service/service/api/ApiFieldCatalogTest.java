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
    void ppsSearchHead_lowestStatusAndRequisitionBy() {
        assertEquals(
                List.of("PUNO", "PUSL"),
                catalog.columnsFor(M3ApiKey.of("PPS200MI", "SearchHead"), "LOWEST_STATUS")
        );
        assertEquals(
                List.of("PUNO", "PURC"),
                catalog.columnsFor(M3ApiKey.of("PPS200MI", "SearchHead"), "REQUISITION_BY")
        );
        assertEquals(
                List.of("PUNO", "WHLO"),
                catalog.columnsFor(M3ApiKey.of("PPS200MI", "SearchHead"), "WAREHOUSE")
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
    void pmsSearchMo_plannedTimesAndPriority() {
        assertEquals(
                List.of("MFNO", "MSTI"),
                catalog.columnsFor(M3ApiKey.of("PMS100MI", "SearchMO"), "PLANNED_START_TIME")
        );
        assertEquals(
                List.of("MFNO", "MFTI"),
                catalog.columnsFor(M3ApiKey.of("PMS100MI", "SearchMO"), "PLANNED_FINISH_TIME")
        );
        assertEquals(
                List.of("MFNO", "PRIO"),
                catalog.columnsFor(M3ApiKey.of("PMS100MI", "SearchMO"), "PRIORITY")
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
    void mmsSearchHead_coreReturnColumns() {
        M3ApiKey key = M3ApiKey.of("MMS100MI", "SearchHead");
        assertEquals(List.of("TRNR", "FACI"), catalog.columnsFor(key, "FACILITY"));
        assertEquals(List.of("TRNR"), catalog.columnsFor(key, "DISTRIBUTION_ORDER_NUMBER"));
        assertEquals(List.of("TRNR", "TRTP"), catalog.columnsFor(key, "ORDER_TYPE"));
        assertEquals(List.of("TRNR", "RESP"), catalog.columnsFor(key, "RESPONSIBLE"));
        assertEquals(List.of("TRNR", "TRSL"), catalog.columnsFor(key, "LOWEST_STATUS"));
        assertEquals(List.of("TRNR", "TRSH"), catalog.columnsFor(key, "HIGHEST_STATUS"));
        assertEquals(List.of("TRNR", "WHLO"), catalog.columnsFor(key, "WAREHOUSE"));
    }

    @Test
    void oisSearchHead_netOrderValueColumns() {
        assertEquals(
                List.of("ORNO", "NTAM"),
                catalog.columnsFor(M3ApiKey.of("OIS100MI", "SearchHead"), "NET_ORDER_VALUE")
        );
    }

    @Test
    void oisSearchHead_orderTypeResponsibleLowestStatus() {
        M3ApiKey key = M3ApiKey.of("OIS100MI", "SearchHead");
        assertEquals(List.of("ORNO", "ORTP"), catalog.columnsFor(key, "ORDER_TYPE"));
        assertEquals(List.of("ORNO", "RESP"), catalog.columnsFor(key, "RESPONSIBLE"));
        assertEquals(List.of("ORNO", "ORSL"), catalog.columnsFor(key, "LOWEST_STATUS"));
        assertEquals(List.of("ORNO", "ORST"), catalog.columnsFor(key, "STATUS"));
        assertEquals(List.of("ORNO", "ADID"), catalog.columnsFor(key, "ADDRESS_ID"));
    }

    @Test
    void getBasicData_countryAndCustomerType() {
        M3ApiKey key = M3ApiKey.of("CRS610MI", "GetBasicData");
        assertEquals(List.of("CSCD"), catalog.columnsFor(key, "COUNTRY"));
        assertEquals(List.of("CUTP"), catalog.columnsFor(key, "CUSTOMER_TYPE"));
        assertEquals(List.of("PHNO"), catalog.columnsFor(key, "PHONE"));
        assertEquals(List.of("MAIL"), catalog.columnsFor(key, "EMAIL"));
        assertEquals(List.of("CUCD"), catalog.columnsFor(key, "CURRENCY"));
        assertEquals(
                List.of("CUA1", "CUA2", "CUA3", "CUA4", "TOWN", "PONO"),
                catalog.columnsFor(key, "ADDRESS")
        );
    }

    @Test
    void getFinancial_liveColumnIds() {
        M3ApiKey key = M3ApiKey.of("CRS610MI", "GetFinancial");
        assertEquals(List.of("TDIN"), catalog.columnsFor(key, "TOTAL_DUE_INVOICES"));
        assertEquals(List.of("CRLM"), catalog.columnsFor(key, "CREDIT_LIMIT"));
        assertEquals(List.of("PYCD"), catalog.columnsFor(key, "PAYMENT"));
        assertEquals(List.of("TECD"), catalog.columnsFor(key, "PAYMENT_TERMS"));
        assertEquals(List.of("TOIN"), catalog.columnsFor(key, "OUTSTANDING_INVOICES"));
        assertEquals(List.of("TDIN"), catalog.columnsFor(key, "OVERDUE_INVOICES"));
        assertEquals(List.of("INCO", "INSN", "INLI"), catalog.columnsFor(key, "INSURANCE"));
        assertEquals(List.of("CUNO", "CRLM"), catalog.columnsFor(key, "BASIC"));
    }

    @Test
    void everySearchApiHasEntry() {
        assertNotNull(catalog.entryFor(M3ApiKey.of("PPS200MI", "SearchHead")));
        assertNotNull(catalog.entryFor(M3ApiKey.of("PMS100MI", "SearchMO")));
        assertNotNull(catalog.entryFor(M3ApiKey.of("MMS100MI", "SearchHead")));
    }
}
