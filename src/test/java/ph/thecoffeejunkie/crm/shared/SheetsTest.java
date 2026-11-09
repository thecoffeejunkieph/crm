package ph.thecoffeejunkie.crm.shared;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SheetsTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    record Req(@NotBlank String name, java.math.BigDecimal price) {
    }

    @Test
    void parsesQuotesCrlfAndBom() {
        var rows = Sheets.parse("﻿a,b\r\n\"x, \"\"y\"\"\",2\r\n\r\n\"multi\nline\",3");
        assertThat(rows).containsExactly(List.of("a", "b"), List.of("x, \"y\"", "2"), List.of("multi\nline", "3"));
    }

    @Test
    void exportRoundTripsThroughImportIncludingFormulaGuard() {
        List<List<?>> data = List.of(Arrays.asList("=HYPERLINK(\"x\")", "+639171234567", null, -5));
        String csv = new String(Sheets.csv(List.of("a", "phone", "c", "n"), data), StandardCharsets.UTF_8);
        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\")\"").contains(",+639171234567,").contains(",-5");
        var rec = Sheets.records(csv.getBytes(StandardCharsets.UTF_8)).getFirst();
        assertThat(rec).containsEntry("a", "=HYPERLINK(\"x\")").containsEntry("phone", "+639171234567")
                .doesNotContainKey("c");
    }

    @Test
    void dryRunAndAllOrNothing() {
        List<Req> created = new ArrayList<>();
        byte[] good = "Name,Price\nA,1\nB,\"1,200.50\"".getBytes(StandardCharsets.UTF_8);
        var dry = Sheets.importCsv(good, true, VALIDATOR, Sheets_req(), r -> null, created::add);
        assertThat(dry.errors()).isEmpty();
        assertThat(dry.created()).isZero();
        assertThat(created).isEmpty();

        byte[] bad = "name,price\nA,1\n,2\nC,abc".getBytes(StandardCharsets.UTF_8);
        var res = Sheets.importCsv(bad, false, VALIDATOR, Sheets_req(), r -> null, created::add);
        assertThat(res.errors()).extracting(Sheets.RowError::row).containsExactly(3, 4);
        assertThat(created).isEmpty();

        var real = Sheets.importCsv(good, false, VALIDATOR, Sheets_req(), r -> null, created::add);
        assertThat(real.created()).isEqualTo(2);
        assertThat(created).extracting(Req::price).map(Object::toString).containsExactly("1", "1200.50");
    }

    private static java.util.function.Function<java.util.Map<String, String>, Req> Sheets_req() {
        return r -> new Req(r.get("name"), Sheets.decimal("price", r.get("price")));
    }

    @Test
    void rejectsEmptyFileAndBadFormat() {
        assertThatThrownBy(() -> Sheets.records("name\n".getBytes())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Sheets.export("x", "pdf", List.of(), List.of())).isInstanceOf(DomainException.class);
    }

    @Test
    void xlsxIsAZipWithEscapedInlineStrings() throws Exception {
        byte[] x = Sheets.xlsx(List.of("name", "price"), List.of(List.of("A & <B>", new java.math.BigDecimal("12.50"))));
        List<String> names = new ArrayList<>();
        String sheet = null;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(x))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                names.add(e.getName());
                if (e.getName().endsWith("sheet1.xml")) {
                    sheet = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertThat(names).contains("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml");
        assertThat(sheet).contains("A &amp; &lt;B&gt;").contains("<c t=\"n\"><v>12.50</v></c>");
    }
}
