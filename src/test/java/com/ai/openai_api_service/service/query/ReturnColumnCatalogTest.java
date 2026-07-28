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

        assertEquals(Set.of("TEPY", "CUCD"), Set.copyOf(columns));
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
}
