package com.ai.openai_api_service.service.protection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden regression pack for Phase 2A detector coverage (grow toward 200–500 via 2C).
 */
class BusinessInformationDetectorCoverageTest {

    private BusinessInformationDetector detector;

    @BeforeEach
    void setUp() {
        detector = new BusinessInformationDetector(new FieldClassificationCatalog(), new ValueShapeValidator());
    }

    @ParameterizedTest(name = "{0} -> {1}={2}")
    @MethodSource("positiveCases")
    void detect_positive(String utterance, String expectedCode, String expectedValue) {
        List<DetectedSpan> spans = detector.detect(utterance);
        assertTrue(spans.stream().anyMatch(s -> expectedCode.equals(s.code())
                        && expectedValue.equals(utterance.substring(s.start(), s.end()))),
                () -> "expected " + expectedCode + "=" + expectedValue + " in " + spans
                        + " for utterance=[" + utterance + "]");
    }

    @ParameterizedTest(name = "negative: {0}")
    @MethodSource("negativeCases")
    void detect_negative(String utterance) {
        assertTrue(detector.detect(utterance).isEmpty(), () -> "unexpected spans for: " + utterance);
    }

    static Stream<Arguments> positiveCases() {
        return Stream.of(
                // CUNO
                Arguments.of("customer 45678", "CUNO", "45678"),
                Arguments.of("customer number 45678", "CUNO", "45678"),
                Arguments.of("customer id 45678", "CUNO", "45678"),
                Arguments.of("customer=45678", "CUNO", "45678"),
                Arguments.of("customer:45678", "CUNO", "45678"),
                Arguments.of("customer#45678", "CUNO", "45678"),
                Arguments.of("customer (45678)", "CUNO", "45678"),
                Arguments.of("customer is 45678", "CUNO", "45678"),
                Arguments.of("customer with 45678", "CUNO", "45678"),
                Arguments.of("customer named 45678", "CUNO", "45678"),
                Arguments.of("customer called 45678", "CUNO", "45678"),
                Arguments.of("customer with number 45678", "CUNO", "45678"),
                Arguments.of("customer having number 45678", "CUNO", "45678"),
                Arguments.of("customer identified by 45678", "CUNO", "45678"),
                Arguments.of("customer identified as 45678", "CUNO", "45678"),
                Arguments.of("customer code 45678", "CUNO", "45678"),
                Arguments.of("customer reference 45678", "CUNO", "45678"),
                Arguments.of("customer ref 45678", "CUNO", "45678"),
                Arguments.of("client 45678", "CUNO", "45678"),
                Arguments.of("client number 45678", "CUNO", "45678"),
                Arguments.of("client id 45678", "CUNO", "45678"),
                Arguments.of("account number 45678", "CUNO", "45678"),
                Arguments.of("account id 45678", "CUNO", "45678"),
                Arguments.of("cust 45678", "CUNO", "45678"),
                Arguments.of("cust#45678", "CUNO", "45678"),
                Arguments.of("cuno 45678", "CUNO", "45678"),
                Arguments.of("How can I edit customer 67890?", "CUNO", "67890"),
                Arguments.of("Show status for customer 1001", "CUNO", "1001"),
                Arguments.of("customer ABC001", "CUNO", "ABC001"),
                // SUNO
                Arguments.of("supplier SUP99", "SUNO", "SUP99"),
                Arguments.of("supplier number SUP99", "SUNO", "SUP99"),
                Arguments.of("supplier code SUP99", "SUNO", "SUP99"),
                Arguments.of("vendor V001", "SUNO", "V001"),
                Arguments.of("vendor number V001", "SUNO", "V001"),
                Arguments.of("supplier id SUP99", "SUNO", "SUP99"),
                Arguments.of("vendor id V001", "SUNO", "V001"),
                Arguments.of("vendor code V001", "SUNO", "V001"),
                Arguments.of("suno SUP99", "SUNO", "SUP99"),
                Arguments.of("Details for supplier SUP99", "SUNO", "SUP99"),
                Arguments.of("supplier=SUP99", "SUNO", "SUP99"),
                Arguments.of("supplier (SUP99)", "SUNO", "SUP99"),
                // ORNO
                Arguments.of("order 100001", "ORNO", "100001"),
                Arguments.of("order number 100001", "ORNO", "100001"),
                Arguments.of("customer order 100001", "ORNO", "100001"),
                Arguments.of("sales order 100001", "ORNO", "100001"),
                Arguments.of("sales order number 100001", "ORNO", "100001"),
                Arguments.of("order for 76948", "ORNO", "76948"),
                Arguments.of("so number 100001", "ORNO", "100001"),
                Arguments.of("so#100001", "ORNO", "100001"),
                Arguments.of("order=100001", "ORNO", "100001"),
                Arguments.of("What is the status of order 0012345678?", "ORNO", "0012345678"),
                // PUNO
                Arguments.of("purchase order 450001", "PUNO", "450001"),
                Arguments.of("purchase order number 450001", "PUNO", "450001"),
                Arguments.of("po number 450001", "PUNO", "450001"),
                Arguments.of("po 450001", "PUNO", "450001"),
                Arguments.of("po#450001", "PUNO", "450001"),
                Arguments.of("po no 450001", "PUNO", "450001"),
                Arguments.of("po id 450001", "PUNO", "450001"),
                Arguments.of("purchase order (450001)", "PUNO", "450001"),
                // MFNO
                Arguments.of("manufacturing order 900001", "MFNO", "900001"),
                Arguments.of("manufacturing order MF123456", "MFNO", "MF123456"),
                Arguments.of("mo number 900001", "MFNO", "900001"),
                Arguments.of("mfg order 900001", "MFNO", "900001"),
                Arguments.of("manufacturing order number 900001", "MFNO", "900001"),
                Arguments.of("order SO10001", "ORNO", "SO10001"),
                Arguments.of("purchase order PO450001", "PUNO", "PO450001"),
                // TRNR
                Arguments.of("distribution order 800001", "TRNR", "800001"),
                Arguments.of("transfer order 800002", "TRNR", "800002"),
                Arguments.of("do number 800001", "TRNR", "800001"),
                Arguments.of("distribution order number 800001", "TRNR", "800001"),
                // PRNO
                Arguments.of("product P1001", "PRNO", "P1001"),
                Arguments.of("product number P1001", "PRNO", "P1001"),
                Arguments.of("item number I2002", "PRNO", "I2002"),
                Arguments.of("product code P1001", "PRNO", "P1001"),
                Arguments.of("item code I2002", "PRNO", "I2002"),
                Arguments.of("material number M3003", "PRNO", "M3003"),
                Arguments.of("material code M3003", "PRNO", "M3003"),
                Arguments.of("sku SKU01", "PRNO", "SKU01"),
                // WHLO / FACI / DIVI
                Arguments.of("warehouse A01", "WHLO", "A01"),
                Arguments.of("warehouse code A01", "WHLO", "A01"),
                Arguments.of("warehouse id A01", "WHLO", "A01"),
                Arguments.of("Check stock at warehouse A01", "WHLO", "A01"),
                Arguments.of("facility F01", "FACI", "F01"),
                Arguments.of("facility code F01", "FACI", "F01"),
                Arguments.of("plant code F01", "FACI", "F01"),
                Arguments.of("plant F01", "FACI", "F01"),
                Arguments.of("division D01", "DIVI", "D01"),
                Arguments.of("division code D01", "DIVI", "D01"),
                // Person codes
                Arguments.of("salesperson is MAHESHD", "SMCD", "MAHESHD"),
                Arguments.of("sales representative MAHESHD", "SMCD", "MAHESHD"),
                Arguments.of("sales rep MAHESHD", "SMCD", "MAHESHD"),
                Arguments.of("responsible is ALICE01", "RESP", "ALICE01"),
                Arguments.of("buyer is BUYER1", "BUYE", "BUYER1"),
                // Multi / NL
                Arguments.of("How is customer 67890 linked with purchase order 450001?", "CUNO", "67890"),
                Arguments.of("How is customer 67890 linked with purchase order 450001?", "PUNO", "450001"),
                Arguments.of("Show customer 45678 order 1000234", "CUNO", "45678"),
                Arguments.of("Show customer 45678 order 1000234", "ORNO", "1000234"),
                Arguments.of("Please show customer 1001 order 0012345678 from warehouse A01 for supplier SUP99", "CUNO", "1001"),
                Arguments.of("Please show customer 1001 order 0012345678 from warehouse A01 for supplier SUP99", "ORNO", "0012345678"),
                Arguments.of("Please show customer 1001 order 0012345678 from warehouse A01 for supplier SUP99", "WHLO", "A01"),
                Arguments.of("Please show customer 1001 order 0012345678 from warehouse A01 for supplier SUP99", "SUNO", "SUP99")
        );
    }

    static Stream<Arguments> negativeCases() {
        return Stream.of(
                Arguments.of("customer service"),
                Arguments.of("customer hello"),
                Arguments.of("customer banana"),
                Arguments.of("customer ###"),
                Arguments.of("customer how"),
                Arguments.of("customer supplier"),
                Arguments.of("warehouse management"),
                Arguments.of("order management"),
                Arguments.of("account settings"),
                Arguments.of("customer, 45678"),
                Arguments.of("customer;45678"),
                Arguments.of("customer-45678"),
                Arguments.of("45678 this customer"),
                Arguments.of("registe 45678 this customer"),
                Arguments.of("material handling"),
                Arguments.of("How can I register a customer in infor m3")
        );
    }
}
