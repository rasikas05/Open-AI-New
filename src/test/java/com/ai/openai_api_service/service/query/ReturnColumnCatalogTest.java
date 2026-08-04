package com.ai.openai_api_service.service.query;

import com.ai.openai_api_service.service.IntentApiCatalog;
import com.ai.openai_api_service.service.RequestedInformationResolver;
import com.ai.openai_api_service.service.api.ApiFieldCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReturnColumnCatalogTest {

    private final ReturnColumnCatalog catalog = new ReturnColumnCatalog(
            new IntentApiCatalog(),
            new ApiFieldCatalog()
    );

    @Test
    void getCustomerFinancial_paymentAndCurrency_matchesApiFieldCatalog() {
        List<String> columns = catalog.columnsFor(
                "GetCustomerFinancial",
                List.of(RequestedInformationResolver.PAYMENT, RequestedInformationResolver.CURRENCY)
        );

        assertEquals(Set.of("PYCD", "CUCD"), Set.copyOf(columns));
    }

    @Test
    void getCustomer_phone_matchesApiFieldCatalog() {
        assertEquals(
                List.of("PHNO"),
                catalog.columnsFor("GetCustomer", List.of(RequestedInformationResolver.PHONE))
        );
    }

    @Test
    void searchCustomerOrder_status_derivesFromApiFieldCatalog() {
        assertEquals(
                List.of("ORNO", "ORST"),
                catalog.columnsFor("SearchCustomerOrder", List.of(RequestedInformationResolver.STATUS))
        );
    }

    @Test
    void searchPurchaseOrder_buyer_derivesFromApiFieldCatalog() {
        assertEquals(
                List.of("PUNO", "BUYE"),
                catalog.columnsFor("SearchPurchaseOrder", List.of("BUYER"))
        );
    }

    @Test
    void searchManufacturingOrder_priority_derivesFromApiFieldCatalog() {
        assertEquals(
                List.of("MFNO", "PRIO"),
                catalog.columnsFor("SearchManufacturingOrder", List.of("PRIORITY"))
        );
    }

    @Test
    void searchCustomerOrder_orderTypeResponsibleLowestStatus() {
        assertEquals(
                List.of("ORNO", "ORTP"),
                catalog.columnsFor("SearchCustomerOrder", List.of("ORDER_TYPE"))
        );
        assertEquals(
                List.of("ORNO", "RESP"),
                catalog.columnsFor("SearchCustomerOrder", List.of("RESPONSIBLE"))
        );
        assertEquals(
                List.of("ORNO", "ORSL"),
                catalog.columnsFor("SearchCustomerOrder", List.of("LOWEST_STATUS"))
        );
    }

    @Test
    void searchPurchaseOrder_lowestStatusAndRequisitionBy() {
        assertEquals(
                List.of("PUNO", "PUSL"),
                catalog.columnsFor("SearchPurchaseOrder", List.of("LOWEST_STATUS"))
        );
        assertEquals(
                List.of("PUNO", "PURC"),
                catalog.columnsFor("SearchPurchaseOrder", List.of("REQUISITION_BY"))
        );
    }

    @Test
    void searchManufacturingOrder_plannedTimes() {
        assertEquals(
                List.of("MFNO", "MSTI"),
                catalog.columnsFor("SearchManufacturingOrder", List.of("PLANNED_START_TIME"))
        );
        assertEquals(
                List.of("MFNO", "MFTI"),
                catalog.columnsFor("SearchManufacturingOrder", List.of("PLANNED_FINISH_TIME"))
        );
    }

    @Test
    void searchDistributionOrder_coreColumns() {
        assertEquals(
                List.of("TRNR", "RIDT"),
                catalog.columnsFor("SearchDistributionOrder", List.of("RECEIVING_DATE"))
        );
        assertEquals(
                List.of("TRNR", "TRSL"),
                catalog.columnsFor("SearchDistributionOrder", List.of("LOWEST_STATUS"))
        );
        assertEquals(
                List.of("TRNR", "RESP"),
                catalog.columnsFor("SearchDistributionOrder", List.of("RESPONSIBLE"))
        );
    }
}
