package ph.thecoffeejunkie.crm.sales;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Extracted PDF text, for asserting on rendered documents. */
public final class PdfText {

    private PdfText() {
    }

    public static String of(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
