package com.ai.openai_api_service.service.protection;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Metadata-driven value-shape checks. Entity knowledge (maxLength, characterSet) stays on the catalog;
 * this class maps shape keys / character-set enums to validation rules.
 */
@Component
public class ValueShapeValidator {

    public static final String M3_IDENTIFIER = "M3_IDENTIFIER";

    /**
     * @deprecated Use {@link #M3_IDENTIFIER} with catalog maxLength + characterSet.
     *             Kept as a deprecated alias that delegates to identifier validation.
     */
    @Deprecated
    public static final String M3_PARTY_ID = "M3_PARTY_ID";

    /**
     * @deprecated Use {@link #M3_IDENTIFIER} with catalog maxLength + characterSet.
     *             Kept as a deprecated alias that delegates to identifier validation
     *             (no longer digits-only).
     */
    @Deprecated
    public static final String M3_ORDER_ID = "M3_ORDER_ID";

    public static final String M3_SITE_CODE = "M3_SITE_CODE";
    public static final String M3_PERSON_CODE = "M3_PERSON_CODE";
    public static final String GENERIC_TOKEN = "GENERIC_TOKEN";

    private static final Pattern ALPHANUMERIC_CHARS = Pattern.compile("^[A-Z0-9]+$");
    /** M3 site/warehouse codes are short (typically ≤5); rejects phrases like {@code management}. */
    private static final Pattern SITE_CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._\\-]{0,4}$");
    private static final Pattern PERSON_CODE = Pattern.compile("^[A-Za-z][A-Za-z0-9._\\-]{1,19}$");
    private static final Pattern GENERIC = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9@._+\\-]{0,63}$");

    /**
     * Legacy two-arg API. Non-identifier shapes still work; identifier shapes require
     * {@link #isValid(String, String, Integer, IdentifierCharacterSet)}.
     */
    public boolean isValid(String valueShapeKey, String value) {
        return isValid(valueShapeKey, value, null, null);
    }

    public boolean isValid(
            String valueShapeKey,
            String value,
            Integer maxLength,
            IdentifierCharacterSet characterSet
    ) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String key = valueShapeKey == null || valueShapeKey.isBlank()
                ? GENERIC_TOKEN
                : valueShapeKey.trim().toUpperCase(Locale.ROOT);

        return switch (key) {
            case M3_IDENTIFIER, M3_PARTY_ID, M3_ORDER_ID ->
                    matchesIdentifier(value, maxLength, characterSet);
            case M3_SITE_CODE -> matchesWithDigit(SITE_CODE, value);
            case M3_PERSON_CODE -> PERSON_CODE.matcher(value).matches();
            default -> matchesGeneric(value);
        };
    }

    /**
     * Catalog-driven M3 identifier: upper bound length, character set, at least one digit.
     * Missing {@code maxLength} or {@code characterSet} → invalid (no silent defaults).
     */
    private static boolean matchesIdentifier(
            String value,
            Integer maxLength,
            IdentifierCharacterSet characterSet
    ) {
        if (maxLength == null || maxLength < 1 || characterSet == null) {
            return false;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        int len = upper.length();
        if (len < 1 || len > maxLength) {
            return false;
        }
        Pattern charset = charsetPattern(characterSet);
        if (charset == null || !charset.matcher(upper).matches()) {
            return false;
        }
        return containsDigit(upper);
    }

    private static Pattern charsetPattern(IdentifierCharacterSet characterSet) {
        if (characterSet == null) {
            return null;
        }
        return switch (characterSet) {
            case ALPHANUMERIC -> ALPHANUMERIC_CHARS;
        };
    }

    /** Alphanumeric identifier that contains at least one digit (rejects plain English words). */
    private static boolean matchesWithDigit(Pattern pattern, String value) {
        if (!pattern.matcher(value).matches()) {
            return false;
        }
        return containsDigit(value);
    }

    /**
     * Generic tokens must look like identifiers: contain a digit or {@code @}
     * (emails / numeric amounts / coded products), not plain English words.
     */
    private static boolean matchesGeneric(String value) {
        if (!GENERIC.matcher(value).matches()) {
            return false;
        }
        if (value.indexOf('@') >= 0) {
            return true;
        }
        return containsDigit(value);
    }

    private static boolean containsDigit(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
