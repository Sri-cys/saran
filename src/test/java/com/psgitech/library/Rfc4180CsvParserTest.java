package com.psgitech.library;

import com.psgitech.library.util.Rfc4180Parser;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class Rfc4180CsvParserTest {

    @Test
    public void testBasicCsvParsing() throws IOException {
        String csv = "Title,Author,Category\r\nAlgorithms,Cormen,CS\r\nNetworks,Tanenbaum,CS\r\n";
        List<List<String>> rows = Rfc4180Parser.parse(csv);

        assertEquals(3, rows.size());
        assertEquals("Title", rows.get(0).get(0));
        assertEquals("Author", rows.get(0).get(1));
        assertEquals("Category", rows.get(0).get(2));

        assertEquals("Algorithms", rows.get(1).get(0));
        assertEquals("Cormen", rows.get(1).get(1));
        assertEquals("CS", rows.get(1).get(2));
    }

    @Test
    public void testQuotedFieldsWithCommas() throws IOException {
        String csv = "\"Introduction, to Algorithms\",\"Thomas H. Cormen, Charles E. Leiserson\",Computer Science\r\n";
        List<List<String>> rows = Rfc4180Parser.parse(csv);

        assertEquals(1, rows.size());
        assertEquals("Introduction, to Algorithms", rows.get(0).get(0));
        assertEquals("Thomas H. Cormen, Charles E. Leiserson", rows.get(0).get(1));
        assertEquals("Computer Science", rows.get(0).get(2));
    }

    @Test
    public void testQuotedFieldsWithNewlinesAndEscapedQuotes() throws IOException {
        String csv = "\"Line 1\r\nLine 2\",\"He said \"\"Hello world\"\" to me\",Tech\r\n";
        List<List<String>> rows = Rfc4180Parser.parse(csv);

        assertEquals(1, rows.size());
        assertEquals("Line 1\r\nLine 2", rows.get(0).get(0));
        assertEquals("He said \"Hello world\" to me", rows.get(0).get(1));
        assertEquals("Tech", rows.get(0).get(2));
    }

    @Test
    public void testUtf8BomStripping() throws IOException {
        String csvWithBom = "\uFEFFTitle,Author\r\nDatabase,Elmasri\r\n";
        List<List<String>> rows = Rfc4180Parser.parse(csvWithBom);

        assertEquals(2, rows.size());
        assertEquals("Title", rows.get(0).get(0));
        assertFalse("BOM must be stripped", rows.get(0).get(0).startsWith("\uFEFF"));
    }

    @Test
    public void testFormulaInjectionSanitization() {
        assertEquals("'=cmd|' /C calc'!A0", Rfc4180Parser.sanitizeFormula("=cmd|' /C calc'!A0"));
        assertEquals("'+12345", Rfc4180Parser.sanitizeFormula("+12345"));
        assertEquals("'-9876", Rfc4180Parser.sanitizeFormula("-9876"));
        assertEquals("'@SUM(A1:A10)", Rfc4180Parser.sanitizeFormula("@SUM(A1:A10)"));
        assertEquals("Safe Text", Rfc4180Parser.sanitizeFormula("Safe Text"));
        assertEquals("", Rfc4180Parser.sanitizeFormula(""));
        assertEquals("", Rfc4180Parser.sanitizeFormula(null));
    }

    @Test
    public void testToCsvLineSerialization() {
        List<String> fields = List.of("=1+1", "Cormen, Thomas", "Normal");
        String line = Rfc4180Parser.toCsvLine(fields);

        // '=1+1' should be sanitized to ''=1+1', and 'Cormen, Thomas' quoted
        assertTrue(line.contains("'=1+1"));
        assertTrue(line.contains("\"Cormen, Thomas\""));
        assertTrue(line.endsWith("\r\n"));
    }
}
