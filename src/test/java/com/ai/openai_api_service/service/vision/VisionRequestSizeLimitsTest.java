package com.ai.openai_api_service.service.vision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisionRequestSizeLimitsTest {

    @Test
    void encodedCharsForDefaultFourMib() {
        assertEquals(5_592_408L, VisionRequestSizeLimits.maxEncodedBase64Chars(4_194_304L));
    }

    @Test
    void totalBodySingleImageIncludesOverhead() {
        assertEquals(
                5_854_552L,
                VisionRequestSizeLimits.maxHttpBodyBytes(1, 4_194_304L, 262_144L)
        );
    }

    @Test
    void totalBodyThreeImagesMatchesProductionFormula() {
        // 3 * 5592408 + 262144 = 17039368
        assertEquals(
                17_039_368L,
                VisionRequestSizeLimits.maxHttpBodyBytes(3, 4_194_304L, 262_144L)
        );
    }

    @Test
    void deprecatedSingleImageOverloadStillWorks() {
        assertEquals(
                5_854_552L,
                VisionRequestSizeLimits.maxHttpBodyBytes(4_194_304L, 262_144L)
        );
    }

    @Test
    void encodedCharsRoundsUpByThreeByteGroups() {
        assertEquals(4L, VisionRequestSizeLimits.maxEncodedBase64Chars(1L));
        assertEquals(4L, VisionRequestSizeLimits.maxEncodedBase64Chars(2L));
        assertEquals(4L, VisionRequestSizeLimits.maxEncodedBase64Chars(3L));
        assertEquals(8L, VisionRequestSizeLimits.maxEncodedBase64Chars(4L));
    }
}
