package ph.thecoffeejunkie.crm.shared;

import jakarta.validation.Validator;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** CSV import and CSV/XLSX export for list screens (F-110). No spreadsheet library: one sheet, plain cells. */
public final class Sheets {

    public static final int MAX_ROWS = 5000;
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    // Excel runs a cell starting with these as a formula; numbers like +63917… or -5 are left alone.
    private static final Pattern FORMULA = Pattern.compile("^[=+\\-@\\t\\r].*", Pattern.DOTALL);
    private static final Pattern NUMBERISH = Pattern.compile("^[+-]?[0-9][0-9 ().-]*$");

    public record RowError(int row, String message) {
    }

    /** {@code row} is the spreadsheet row (header is row 1). Nothing is written when there are errors. */
    public record ImportResult(int rows, int created, boolean dryRun, List<RowError> errors) {
    }

    private Sheets() {
    }

    // ---- export ----

    /** {@code format} is csv or xlsx; the file downloads as {@code base-YYYY-MM-DD.ext}. */
    public static ResponseEntity<byte[]> export(String base, String format, List<String> header, List<List<?>> rows) {
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        if (!xlsx && !"csv".equalsIgnoreCase(format)) {
            throw invalid("format", "format must be csv or xlsx");
        }
        String name = base + "-" + LocalDate.now(ZoneId.of("Asia/Manila")) + (xlsx ? ".xlsx" : ".csv");
        return ResponseEntity.ok().contentType(xlsx ? XLSX : new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(xlsx ? xlsx(header, rows) : csv(header, rows));
    }

    /** UTF-8 with a BOM so Excel shows ₱ and ñ correctly. */
    static byte[] csv(List<String> header, List<List<?>> rows) {
        StringBuilder out = new StringBuilder("﻿");
        line(out, header);
        rows.forEach(r -> line(out, r));
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder out, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            out.append(i == 0 ? "" : ",").append(csvCell(cells.get(i)));
        }
        out.append("\r\n");
    }

    static String csvCell(Object v) {
        String s = text(v);
        if (!(v instanceof Number) && FORMULA.matcher(s).matches() && !NUMBERISH.matcher(s).matches()) {
            s = "'" + s;
        }
        return s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")
                ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    static byte[] xlsx(List<String> header, List<List<?>> rows) {
        StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        xlsxRow(sheet, header);
        rows.forEach(r -> xlsxRow(sheet, r));
        sheet.append("</sheetData></worksheet>");
        String rels = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
        String pkg = "http://schemas.openxmlformats.org/package/2006/relationships";
        String ct = "application/vnd.openxmlformats-officedocument.spreadsheetml";
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"" + ct + ".sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"" + ct + ".worksheet+xml\"/></Types>");
            entry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\""
                    + pkg + "\"><Relationship Id=\"rId1\" Type=\"" + rels + "/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"" + rels + "\">"
                    + "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\""
                    + pkg + "\"><Relationship Id=\"rId1\" Type=\"" + rels + "/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            entry(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build XLSX", e);
        }
        return bytes.toByteArray();
    }

    private static void xlsxRow(StringBuilder out, List<?> cells) {
        out.append("<row>");
        for (Object v : cells) {
            if (v instanceof Number) {
                out.append("<c t=\"n\"><v>").append(v instanceof BigDecimal b ? b.toPlainString() : v).append("</v></c>");
            } else {
                out.append("<c t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(text(v))).append("</t></is></c>");
            }
        }
        out.append("</row>");
    }

    private static void entry(ZipOutputStream zip, String name, String body) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(body.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String xml(String s) {
        return s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "").replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String text(Object v) {
        return v == null ? "" : v instanceof BigDecimal b ? b.toPlainString() : v.toString();
    }

    // ---- import ----

    /**
     * Validates every row, then creates all of them only when none failed and this is not a dry run. Callers run
     * this inside one transaction, so a failure while creating rolls the whole file back.
     *
     * @param toRequest builds the request from a header-keyed row; throws IllegalArgumentException for bad cells
     * @param check     extra rule (duplicates and the like): an error message, or null when the row is fine
     */
    public static <T> ImportResult importCsv(byte[] file, boolean dryRun, Validator validator,
                                            Function<Map<String, String>, T> toRequest, Function<T, String> check,
                                            Consumer<T> create) {
        List<Map<String, String>> records = records(file);
        List<RowError> errors = new ArrayList<>();
        List<T> ok = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            int row = i + 2;
            try {
                T req = toRequest.apply(records.get(i));
                var violations = validator.validate(req);
                if (!violations.isEmpty()) {
                    violations.forEach(v -> errors.add(new RowError(row, v.getPropertyPath() + " " + v.getMessage())));
                    continue;
                }
                String problem = check.apply(req);
                if (problem != null) {
                    errors.add(new RowError(row, problem));
                    continue;
                }
                ok.add(req);
            } catch (IllegalArgumentException e) {
                errors.add(new RowError(row, e.getMessage()));
            }
        }
        if (!dryRun && errors.isEmpty()) {
            ok.forEach(create);
        }
        return new ImportResult(records.size(), dryRun || !errors.isEmpty() ? 0 : ok.size(), dryRun, errors);
    }

    /** Rows keyed by lower-cased header; blank cells are absent. */
    static List<Map<String, String>> records(byte[] file) {
        List<List<String>> lines = parse(new String(file, StandardCharsets.UTF_8));
        if (lines.size() < 2) {
            throw invalid("file", "The file has no rows under the header.");
        }
        if (lines.size() - 1 > MAX_ROWS) {
            throw invalid("file", "Import at most " + MAX_ROWS + " rows at a time.");
        }
        List<String> header = lines.getFirst().stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        List<Map<String, String>> out = new ArrayList<>();
        for (List<String> line : lines.subList(1, lines.size())) {
            Map<String, String> r = new HashMap<>();
            for (int i = 0; i < Math.min(header.size(), line.size()); i++) {
                String v = unguard(line.get(i).trim());
                if (!v.isEmpty()) {
                    r.put(header.get(i), v);
                }
            }
            out.add(r);
        }
        return out;
    }

    /** RFC 4180: quoted fields with doubled quotes, CRLF or LF, a leading BOM; blank lines dropped. */
    static List<List<String>> parse(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> lines = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (c == ',') {
                cur.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                cur.add(cell.toString());
                cell.setLength(0);
                addLine(lines, cur);
                cur = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (quoted) {
            throw invalid("file", "The file has an unclosed quote.");
        }
        cur.add(cell.toString());
        addLine(lines, cur);
        return lines;
    }

    private static void addLine(List<List<String>> lines, List<String> cells) {
        if (cells.stream().anyMatch(s -> !s.isBlank())) {
            lines.add(cells);
        }
    }

    // Undo the export's formula guard so a file round-trips.
    private static String unguard(String v) {
        return v.length() > 1 && v.charAt(0) == '\'' && FORMULA.matcher(v.substring(1)).matches() ? v.substring(1) : v;
    }

    // ---- cell readers for toRequest ----

    public static <E extends Enum<E>> E enumOf(Class<E> type, String column, String v) {
        if (v == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, v.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(column + " must be one of " + Arrays.toString(type.getEnumConstants()));
        }
    }

    public static BigDecimal decimal(String column, String v) {
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(v.replace(",", "").replace("₱", "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(column + " must be a number");
        }
    }

    private static DomainException invalid(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message,
                Map.of("errors", List.of(Map.of("field", field, "message", message))));
    }
}
