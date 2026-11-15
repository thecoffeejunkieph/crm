package ph.thecoffeejunkie.crm.identity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Map;

final class QrCodes {

    private QrCodes() {
    }

    /** One path of horizontal runs; no image library needed. */
    static String svg(String text) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 240, 240,
                    Map.of(EncodeHintType.MARGIN, 1));
            StringBuilder d = new StringBuilder();
            for (int y = 0; y < m.getHeight(); y++) {
                for (int x = 0; x < m.getWidth(); x++) {
                    if (m.get(x, y)) {
                        int start = x;
                        while (x < m.getWidth() && m.get(x, y)) {
                            x++;
                        }
                        d.append('M').append(start).append(' ').append(y).append('h').append(x - start).append("v1h-")
                                .append(x - start).append('z');
                    }
                }
            }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + m.getWidth() + " " + m.getHeight()
                    + "\" width=\"240\" height=\"240\" shape-rendering=\"crispEdges\"><rect width=\"100%\" "
                    + "height=\"100%\" fill=\"#fff\"/><path d=\"" + d + "\"/></svg>";
        } catch (WriterException e) {
            throw new IllegalStateException(e);
        }
    }
}
