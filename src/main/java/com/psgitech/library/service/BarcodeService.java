package com.psgitech.library.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating Code 128 (Set B) barcodes in pure scalable vector graphics (SVG).
 * Conforms to ISO/IEC 15417 specification with quiet zones and centered human-readable text.
 */
public class BarcodeService {

    private static final String[] CODE128_PATTERNS = {
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
    };

    private static final int START_CODE_B = 104;
    private static final int STOP_CODE = 106;

    /**
     * Generates a complete SVG document containing the Code 128 Set B barcode.
     *
     * @param data        The alphanumeric string to encode (e.g. "PSG-BK1-C01")
     * @param moduleWidth Width of one narrowest bar module in pixels (e.g. 2)
     * @param barHeight   Height of the bars in pixels (e.g. 60)
     * @return SVG document string
     */
    public String generateBarcodeSvg(String data, int moduleWidth, int barHeight) {
        if (data == null || data.trim().isEmpty()) {
            throw new IllegalArgumentException("Barcode text cannot be empty.");
        }
        data = data.trim();

        // 1. Calculate symbol codes and checksum
        List<Integer> codes = new ArrayList<>();
        codes.add(START_CODE_B);
        int checksum = START_CODE_B;

        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            int codeVal = (int) c - 32;
            if (codeVal < 0 || codeVal > 95) {
                throw new IllegalArgumentException("Character '" + c + "' is not supported by Code 128 Set B.");
            }
            codes.add(codeVal);
            checksum += (codeVal * (i + 1));
        }

        checksum = checksum % 103;
        codes.add(checksum);
        codes.add(STOP_CODE);

        // 2. Measure total modules:
        // Start: 11, Data: len * 11, Checksum: 11, Stop: 13 modules
        int quietZoneModules = 10;
        int totalModules = (quietZoneModules * 2) + ((codes.size() - 1) * 11) + 13;
        int totalWidth = totalModules * moduleWidth;
        int textHeight = 22;
        int totalHeight = barHeight + textHeight + 16;

        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" ")
           .append("width=\"").append(totalWidth).append("\" ")
           .append("height=\"").append(totalHeight).append("\" ")
           .append("viewBox=\"0 0 ").append(totalWidth).append(" ").append(totalHeight).append("\">\n");

        // White background
        svg.append("  <rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n");

        // Render bars
        int currentX = quietZoneModules * moduleWidth;
        int topY = 10;

        for (int codeIndex = 0; codeIndex < codes.size(); codeIndex++) {
            int code = codes.get(codeIndex);
            String pattern = CODE128_PATTERNS[code];

            boolean isBar = true;
            for (int p = 0; p < pattern.length(); p++) {
                int widthInModules = Character.getNumericValue(pattern.charAt(p));
                int widthPx = widthInModules * moduleWidth;

                if (isBar) {
                    svg.append("  <rect x=\"").append(currentX)
                       .append("\" y=\"").append(topY)
                       .append("\" width=\"").append(widthPx)
                       .append("\" height=\"").append(barHeight)
                       .append("\" fill=\"#000000\"/>\n");
                }
                currentX += widthPx;
                isBar = !isBar;
            }
        }

        // Add human-readable text centered below barcode
        int textY = topY + barHeight + 16;
        int centerX = totalWidth / 2;
        svg.append("  <text x=\"").append(centerX)
           .append("\" y=\"").append(textY)
           .append("\" text-anchor=\"middle\" ")
           .append("font-family=\"'Times New Roman', Times, Courier, monospace\" ")
           .append("font-size=\"14\" font-weight=\"bold\" fill=\"#203B60\" letter-spacing=\"2\">")
           .append(escapeXml(data))
           .append("</text>\n");

        svg.append("</svg>");
        return svg.toString();
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

