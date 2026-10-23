import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** То же, что PdfExtractor.LineStripper в приложении, — строки в JSONL для теста анализатора. */
public class PdfBoxDump {
    static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    public static void main(String[] a) throws Exception {
        Pattern bold = Pattern.compile("(?i)bold|demi|black|heavy|semibold");
        Pattern italic = Pattern.compile("(?i)italic|oblique");
        try (PDDocument doc = PDDocument.load(new File(a[0]));
             Writer w = new OutputStreamWriter(new FileOutputStream(a[1]), "UTF-8")) {
            List<String> lines = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            List<TextPosition> pos = new ArrayList<>();
            Runnable flush = () -> {
                String raw = text.toString();
                String t = (raw.length() > 0 && Character.isWhitespace(raw.charAt(0)) && !raw.isBlank() ? "• " : "") + raw.trim();
                if (!t.isEmpty() && !pos.isEmpty()) {
                    Map<Float, Integer> counts = new HashMap<>();
                    for (TextPosition p : pos) counts.merge(((int) (p.getFontSizeInPt() * 2)) / 2f, 1, Integer::sum);
                    float size = Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
                    TextPosition f = pos.get(0);
                    String font = f.getFont() == null ? "" : f.getFont().getName();
                    boolean b = bold.matcher(font).find() || (f.getFont() != null && f.getFont().getFontDescriptor() != null && f.getFont().getFontDescriptor().getFontWeight() >= 600);
                    lines.add(String.format(Locale.ROOT, "{\"t\":\"%s\",\"s\":%.1f,\"b\":%b,\"i\":%b,\"x\":%.1f,\"y\":%.1f}",
                        esc(t), size, b, italic.matcher(font).find(), f.getXDirAdj(), f.getYDirAdj() - f.getHeightDir()));
                }
                text.setLength(0);
                pos.clear();
            };
            PDFTextStripper s = new PDFTextStripper() {
                @Override protected void writeString(String str, List<TextPosition> ps) { text.append(str); pos.addAll(ps); }
                @Override protected void writeWordSeparator() { text.append(' '); }
                @Override protected void writeLineSeparator() { flush.run(); }
                @Override protected void writePageEnd() { flush.run(); }
            };
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                PDRectangle box = doc.getPage(i).getMediaBox();
                lines.clear();
                s.setStartPage(i + 1); s.setEndPage(i + 1);
                s.getText(doc);
                w.write(String.format(Locale.ROOT, "{\"p\":%d,\"w\":%.1f,\"h\":%.1f,\"lines\":[%s]}\n", i, box.getWidth(), box.getHeight(), String.join(",", lines)));
            }
        }
    }
}
