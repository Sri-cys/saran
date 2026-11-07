package com.psgitech.library;

import com.psgitech.library.service.BarcodeService;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class BarcodeServiceTest {

    private BarcodeService barcodeService;

    @Before
    public void setUp() {
        this.barcodeService = new BarcodeService();
    }

    @Test
    public void testValidDataGeneratesSvgWithRectanglesAndText() {
        String testData = "PSG-BK1-C01";
        String svg = barcodeService.generateBarcodeSvg(testData, 2, 60);

        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.endsWith("</svg>"));
        assertTrue(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""));
        assertTrue(svg.contains("<rect"));
        assertTrue(svg.contains(testData)); // human readable text included
    }

    @Test
    public void testNullOrEmptyDataThrowsException() {
        try {
            barcodeService.generateBarcodeSvg(null, 2, 60);
            fail("Expected exception for null data");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cannot be empty"));
        }

        try {
            barcodeService.generateBarcodeSvg("   ", 2, 60);
            fail("Expected exception for whitespace data");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cannot be empty"));
        }
    }

    @Test
    public void testUnsupportedCharactersThrowException() {
        try {
            // Character with ASCII < 32 (e.g. newline or control char)
            barcodeService.generateBarcodeSvg("TEST\nCODE", 2, 60);
            fail("Expected exception for newline in Code 128 Set B");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not supported"));
        }
    }
}
