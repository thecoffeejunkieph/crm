package ph.thecoffeejunkie.crm.util;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;

/**
 * Company logo, rasterized once from the source SVG to PNG.
 * PNG is used everywhere it's actually embedded (PDF and email) because OpenPDF/Flying Saucer
 * has no SVG decoder and most email clients (Outlook included) don't render inline SVG either.
 */
@Component
public class LogoAsset {

    private static final String LOGO_CLASSPATH = "static/brandmark-dark.svg";
    private static final float RASTER_WIDTH = 480f;

    private final byte[] pngBytes;
    private final String dataUri;

    public LogoAsset() {
        this.pngBytes = rasterize();
        this.dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
    }

    public byte[] pngBytes() {
        return pngBytes;
    }

    public String dataUri() {
        return dataUri;
    }

    private byte[] rasterize() {
        try (InputStream in = new ClassPathResource(LOGO_CLASSPATH).getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, RASTER_WIDTH);
            transcoder.transcode(new TranscoderInput(in), new TranscoderOutput(out));

            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rasterize logo SVG at " + LOGO_CLASSPATH, e);
        }
    }
}
