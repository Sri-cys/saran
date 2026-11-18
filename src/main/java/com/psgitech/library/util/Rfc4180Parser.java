package com.psgitech.library.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, strictly RFC 4180 compliant CSV parser and serializer.
 * Handles UTF-8 BOM, quoted cells with commas and newlines, escaped quotes,
 * and formula injection sanitization. Zero external library dependencies.
 */
public class Rfc4180Parser {

    private static final char BOM = '\uFEFF';

    /**
     * Parses a CSV string into a list of rows, each represented as a list of field values.
     */
    public static List<List<String>> parse(String input) throws IOException {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }

        // Strip UTF-8 BOM if present
        String content = input;
        if (content.charAt(0) == BOM) {
            content = content.substring(1);
        }

        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();

        boolean inQuotes = false;
        int len = content.length();

        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && content.charAt(i + 1) == '"') {
                        // Escaped quote: "" -> "
                        currentField.append('"');
                        i++;
                    } else {
                        // End of quoted block
                        inQuotes = false;
                    }
                } else {
                    currentField.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    currentRow.add(currentField.toString().trim());
                    currentField.setLength(0);
                } else if (c == '\r') {
                    if (i + 1 < len && content.charAt(i + 1) == '\n') {
                        i++;
                    }
                    currentRow.add(currentField.toString().trim());
                    currentField.setLength(0);
                    if (!currentRow.isEmpty() && !(currentRow.size() == 1 && currentRow.get(0).isEmpty())) {
                        rows.add(new ArrayList<>(currentRow));
                    }
                    currentRow.clear();
                } else if (c == '\n') {
                    currentRow.add(currentField.toString().trim());
                    currentField.setLength(0);
                    if (!currentRow.isEmpty() && !(currentRow.size() == 1 && currentRow.get(0).isEmpty())) {
                        rows.add(new ArrayList<>(currentRow));
                    }
                    currentRow.clear();
                } else {
                    currentField.append(c);
                }
            }
        }

        // Add remaining field and row
        if (currentField.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentField.toString().trim());
            if (!currentRow.isEmpty() && !(currentRow.size() == 1 && currentRow.get(0).isEmpty())) {
                rows.add(new ArrayList<>(currentRow));
            }
        }

        return rows;
    }

    /**
     * Escapes formula injection characters (=, +, -, @) to prevent spreadsheet code execution.
     */
    public static String sanitizeFormula(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return "'" + value;
        }
        return value;
    }

    /**
     * Serializes a row into an RFC 4180 CSV line with formula sanitization.
     */
    public static String toCsvLine(List<?> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            Object val = fields.get(i);
            String str = val != null ? val.toString() : "";
            str = sanitizeFormula(str);

            boolean needsQuotes = str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r");
            if (needsQuotes) {
                sb.append('"');
                sb.append(str.replace("\"", "\"\""));
                sb.append('"');
            } else {
                sb.append(str);
            }
        }
        sb.append("\r\n");
        return sb.toString();
    }
}
