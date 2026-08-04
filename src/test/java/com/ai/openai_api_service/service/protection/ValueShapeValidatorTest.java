package com.ai.openai_api_service.service.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueShapeValidatorTest {

    private final ValueShapeValidator validator = new ValueShapeValidator();

    @Test
    void identifier_acceptsAlphanumericWithDigit_withinMaxLength() {
        assertTrue(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "1", 10, IdentifierCharacterSet.ALPHANUMERIC));
        assertTrue(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "ABC001", 10, IdentifierCharacterSet.ALPHANUMERIC));
        assertTrue(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "MF123456", 10, IdentifierCharacterSet.ALPHANUMERIC));
        assertTrue(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "SO10001", 10, IdentifierCharacterSet.ALPHANUMERIC));
        assertTrue(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "PO450001", 10, IdentifierCharacterSet.ALPHANUMERIC));
        assertTrue(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "A123456789", 10, IdentifierCharacterSet.ALPHANUMERIC));
        assertTrue(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "ITEM123", 15, IdentifierCharacterSet.ALPHANUMERIC));
        assertTrue(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "ABCDEFGHIJ12345", 15, IdentifierCharacterSet.ALPHANUMERIC));
    }

    @Test
    void identifier_rejectsTooLong_noDigit_badCharset_missingMetadata() {
        assertFalse(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "A1234567890", 10, IdentifierCharacterSet.ALPHANUMERIC));
        assertFalse(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "ABCDEFGHIJK123456", 15, IdentifierCharacterSet.ALPHANUMERIC));
        assertFalse(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "CUSTOMER", 10, IdentifierCharacterSet.ALPHANUMERIC));
        assertFalse(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "banana", 10, IdentifierCharacterSet.ALPHANUMERIC));
        assertFalse(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "ABC-001", 10, IdentifierCharacterSet.ALPHANUMERIC));
        assertFalse(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "ABC001", null, IdentifierCharacterSet.ALPHANUMERIC));
        assertFalse(validator.isValid(
                ValueShapeValidator.M3_IDENTIFIER, "ABC001", 10, null));
        assertFalse(validator.isValid(ValueShapeValidator.M3_IDENTIFIER, "ABC001"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedAliases_delegateToIdentifierSemantics() {
        assertTrue(validator.isValid(
                ValueShapeValidator.M3_ORDER_ID, "MF123456", 10, IdentifierCharacterSet.ALPHANUMERIC));
        assertTrue(validator.isValid(
                ValueShapeValidator.M3_PARTY_ID, "ABC001", 10, IdentifierCharacterSet.ALPHANUMERIC));
        assertFalse(validator.isValid(
                ValueShapeValidator.M3_ORDER_ID, "management", 10, IdentifierCharacterSet.ALPHANUMERIC));
    }

    @Test
    void siteCode_shortWithDigit() {
        assertTrue(validator.isValid(ValueShapeValidator.M3_SITE_CODE, "A01"));
        assertFalse(validator.isValid(ValueShapeValidator.M3_SITE_CODE, "management"));
    }

    @Test
    void personCode_letterStart() {
        assertTrue(validator.isValid(ValueShapeValidator.M3_PERSON_CODE, "MAHESHD"));
        assertFalse(validator.isValid(ValueShapeValidator.M3_PERSON_CODE, "12345"));
    }

    @Test
    void generic_requiresDigitOrAt() {
        assertTrue(validator.isValid(ValueShapeValidator.GENERIC_TOKEN, "P1001"));
        assertTrue(validator.isValid(ValueShapeValidator.GENERIC_TOKEN, "a@b.com"));
        assertFalse(validator.isValid(ValueShapeValidator.GENERIC_TOKEN, "banana"));
    }
}
