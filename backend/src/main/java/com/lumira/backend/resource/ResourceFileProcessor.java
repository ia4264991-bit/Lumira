package com.lumira.backend.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Synchronous, bounded parsing for the B3 format allow-list. */
@Component
public class ResourceFileProcessor {
    private static final int MAX_EXTRACTED_CHARS = 8_000_000;
    private static final int MAX_STRUCTURAL_ITEMS = 200_000;
    private static final int MAX_IMAGE_PIXELS = 20_000_000;
    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "csv", "text/csv", "txt", "text/plain", "png", "image/png",
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "webp", "image/webp");

    private final ObjectMapper mapper;
    private final long maxUploadBytes;
    private final int maxPdfPages;
    private final ThreadLocal<Map<ArrayNode, Integer>> extractedCharacters = new ThreadLocal<>();

    public ResourceFileProcessor(ObjectMapper mapper,
            @Value("${lumira.resource.max-upload-bytes:20971520}") long maxUploadBytes,
            @Value("${lumira.resource.max-pdf-pages:500}") int maxPdfPages) {
        this.mapper = mapper;
        this.maxUploadBytes = maxUploadBytes;
        this.maxPdfPages = maxPdfPages;
        // Bound OOXML expansion and object counts before parsing untrusted ZIPs.
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(40L * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(MAX_EXTRACTED_CHARS);
    }

    public ProcessedContent process(String filename, String declaredMime, byte[] bytes) throws ProcessingFailure {
        extractedCharacters.set(new IdentityHashMap<>());
        try {
            validate(filename, declaredMime, bytes);
            String extension = extension(filename);
            return switch (extension) {
                case "pdf" -> processPdf(bytes);
                case "docx" -> processDocx(bytes);
                case "pptx" -> processPptx(bytes);
                case "xlsx" -> processXlsx(bytes);
                case "csv" -> processCsv(bytes);
                case "txt" -> processText(bytes);
                case "png", "jpg", "jpeg", "webp" -> processImage(bytes, extension);
                default -> throw new ProcessingFailure("This file type is not supported yet.");
            };
        } catch (ProcessingFailure ex) {
            throw ex;
        } catch (Exception ex) {
            String message = ex.getMessage();
            if (message != null && (message.toLowerCase(Locale.ROOT).contains("password")
                    || message.toLowerCase(Locale.ROOT).contains("encrypt"))) {
                throw new ProcessingFailure("Password-protected or encrypted files are not supported.");
            }
            throw new ProcessingFailure("The file is malformed or could not be safely processed.");
        } finally {
            extractedCharacters.remove();
        }
    }

    private void validate(String filename, String mime, byte[] bytes) throws ProcessingFailure {
        if (bytes == null || bytes.length == 0) throw new ProcessingFailure("The uploaded file is empty.");
        if (bytes.length > maxUploadBytes) throw new ProcessingFailure("The file exceeds the configured upload-size limit.");
        String ext = extension(filename);
        String expectedMime = MIME_BY_EXTENSION.get(ext);
        if (expectedMime == null) throw new ProcessingFailure("This file type is not supported yet.");
        if (mime == null || !expectedMime.equalsIgnoreCase(mime.split(";", 2)[0].strip())) {
            throw new ProcessingFailure("The declared MIME type does not match the supported file extension.");
        }
        if (!signatureMatches(ext, bytes)) {
            throw new ProcessingFailure("The file content signature does not match its declared type.");
        }
    }

    private boolean signatureMatches(String ext, byte[] b) throws ProcessingFailure {
        return switch (ext) {
            case "pdf" -> starts(b, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "png" -> starts(b, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
            case "jpg", "jpeg" -> b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255;
            case "webp" -> b.length >= 12 && ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 4).equals("WEBP");
            case "docx", "pptx", "xlsx" -> officeSignature(ext, b);
            case "csv", "txt" -> !looksLikeBinaryFile(b) && validText(b);
            default -> false;
        };
    }

    private boolean officeSignature(String ext, byte[] bytes) throws ProcessingFailure {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            if (bytes.length >= 8 && (bytes[0] & 255) == 0xD0 && (bytes[1] & 255) == 0xCF) {
                throw new ProcessingFailure("Encrypted or legacy binary Office files are not supported; upload DOCX, PPTX, or XLSX.");
            }
            return false;
        }
        boolean contentTypes = false;
        boolean requiredPart = false;
        int entries = 0;
        long totalUncompressed = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] drain = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 20_000) throw new ProcessingFailure("The Office file contains too many package entries.");
                String name = entry.getName();
                if (name.equals("[Content_Types].xml")) contentTypes = true;
                if ((ext.equals("docx") && name.equals("word/document.xml"))
                        || (ext.equals("pptx") && name.equals("ppt/presentation.xml"))
                        || (ext.equals("xlsx") && name.equals("xl/workbook.xml"))) requiredPart = true;
                long read = 0;
                int n;
                while ((n = zip.read(drain)) != -1) {
                    read += n;
                    totalUncompressed += n;
                    if (read > 40L * 1024 * 1024) throw new ProcessingFailure("An Office package entry exceeds the safe processing limit.");
                    if (totalUncompressed > 100L * 1024 * 1024) throw new ProcessingFailure("The Office package expands beyond the safe processing limit.");
                }
                zip.closeEntry();
            }
        } catch (IOException ex) {
            return false;
        }
        return contentTypes && requiredPart;
    }

    private boolean validText(byte[] bytes) {
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            long controls = value.codePoints().filter(cp -> Character.isISOControl(cp) && cp != '\n' && cp != '\r' && cp != '\t').count();
            return !value.isBlank() && controls <= Math.max(1, value.length() / 100);
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private boolean looksLikeBinaryFile(byte[] bytes) {
        return starts(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))
                || starts(bytes, new byte[]{'P', 'K', 3, 4})
                || starts(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G'})
                || (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255)
                || (bytes.length >= 12 && ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WEBP"));
    }

    private ProcessedContent processPdf(byte[] bytes) throws Exception {
        PDDocument doc = Loader.loadPDF(bytes);
        try (doc) {
            if (doc.isEncrypted()) throw new ProcessingFailure("Password-protected or encrypted PDF files are not supported.");
            if (doc.getNumberOfPages() > maxPdfPages) throw new ProcessingFailure("The PDF exceeds the configured page-processing limit.");
            ArrayNode chunks = mapper.createArrayNode();
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                var pageSize = doc.getPage(i).getMediaBox();
                double pagePixels = pageSize.getWidth() * 1.25d * pageSize.getHeight() * 1.25d;
                if (pagePixels > MAX_IMAGE_PIXELS) throw new ProcessingFailure("A PDF page exceeds the safe render-size limit.");
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String text = stripper.getText(doc).strip();
                if (text.isBlank()) {
                    BufferedImage page = renderer.renderImage(i, 1.25f);
                    OcrResult ocr = ocr(page);
                    text = ocr.text();
                    if (!text.isBlank()) addChunk(chunks, text, mapper.createObjectNode().put("type", "page").put("page", i + 1), mapper.createObjectNode().put("ocrConfidence", ocr.confidence()));
                } else {
                    addChunk(chunks, text, mapper.createObjectNode().put("type", "page").put("page", i + 1), mapper.createObjectNode());
                    var pageResources = doc.getPage(i).getResources();
                    if (pageResources != null) for (var name : pageResources.getXObjectNames()) {
                        var object = pageResources.getXObject(name);
                        if (object instanceof PDImageXObject embedded) {
                            if ((long) embedded.getWidth() * embedded.getHeight() > MAX_IMAGE_PIXELS) continue;
                            try {
                                BufferedImage image = embedded.getImage();
                                OcrResult imageText = ocr(image);
                                if (!imageText.text().isBlank()) addChunk(chunks, imageText.text(), mapper.createObjectNode()
                                        .put("type", "embeddedImage").put("page", i + 1).put("object", name.getName()),
                                        mapper.createObjectNode().put("ocrConfidence", imageText.confidence()));
                            } catch (Exception ignored) { /* partial processing is permitted by the spec */ }
                        }
                    }
                }
            }
            return new ProcessedContent(chunks, mapper.createObjectNode().put("pages", doc.getNumberOfPages()));
        }
    }

    private ProcessedContent processDocx(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            ArrayNode chunks = mapper.createArrayNode();
            List<String> hierarchy = new ArrayList<>();
            int position = 0;
            for (IBodyElement body : doc.getBodyElements()) {
                if (body instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText().strip();
                    if (text.isBlank()) continue;
                    String style = paragraph.getStyle();
                    int level = headingLevel(style);
                    if (level > 0) {
                        while (hierarchy.size() >= level) hierarchy.remove(hierarchy.size() - 1);
                        while (hierarchy.size() < level - 1) hierarchy.add("");
                        hierarchy.add(text);
                    }
                    ObjectNode location = mapper.createObjectNode().put("type", "paragraph").put("paragraph", ++position);
                    var heads = location.putArray("headingHierarchy");
                    hierarchy.stream().filter(s -> !s.isBlank()).forEach(heads::add);
                    addChunk(chunks, text, location, mapper.createObjectNode());
                    for (XWPFRun run : paragraph.getRuns()) {
                        for (XWPFPicture picture : run.getEmbeddedPictures()) {
                            try {
                                BufferedImage image = boundedImage(picture.getPictureData().getData());
                                OcrResult imageText = ocr(image);
                                if (!imageText.text().isBlank()) addChunk(chunks, imageText.text(), mapper.createObjectNode()
                                        .put("type", "embeddedImage").put("paragraph", position),
                                        mapper.createObjectNode().put("ocrConfidence", imageText.confidence()));
                            } catch (Exception ignored) { /* retain valid text from the rest of the document */ }
                        }
                    }
                } else if (body instanceof XWPFTable table) {
                    int row = 0;
                    for (XWPFTableRow tableRow : table.getRows()) {
                        int col = 0;
                        for (XWPFTableCell cell : tableRow.getTableCells()) {
                            addChunk(chunks, cell.getText(), mapper.createObjectNode().put("type", "tableCell")
                                    .put("table", position + 1).put("row", row + 1).put("column", ++col), mapper.createObjectNode());
                        }
                        row++;
                    }
                    position++;
                }
                checkItemLimit(chunks.size());
            }
            return new ProcessedContent(chunks, mapper.createObjectNode().put("paragraphsAndTables", position));
        }
    }

    private ProcessedContent processPptx(byte[] bytes) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            List<XSLFSlide> slides = ppt.getSlides();
            if (slides.size() > 2000) throw new ProcessingFailure("The presentation exceeds the safe slide-processing limit.");
            ArrayNode chunks = mapper.createArrayNode();
            for (int i = 0; i < slides.size(); i++) {
                StringBuilder text = new StringBuilder();
                for (XSLFShape shape : slides.get(i).getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) text.append(textShape.getText()).append('\n');
                    else if (shape instanceof org.apache.poi.xslf.usermodel.XSLFPictureShape picture) {
                        try {
                            BufferedImage image = boundedImage(picture.getPictureData().getData());
                            OcrResult imageText = ocr(image);
                            if (!imageText.text().isBlank()) addChunk(chunks, imageText.text(), mapper.createObjectNode()
                                    .put("type", "embeddedImage").put("slide", i + 1),
                                    mapper.createObjectNode().put("ocrConfidence", imageText.confidence()));
                        } catch (Exception ignored) { /* partial processing is allowed */ }
                    }
                }
                if (slides.get(i).getNotes() != null) {
                    StringBuilder notes = new StringBuilder();
                    for (List<XSLFTextParagraph> paragraphGroup : slides.get(i).getNotes().getTextParagraphs()) {
                        for (XSLFTextParagraph paragraph : paragraphGroup) {
                            String paragraphText = paragraph.getText();
                            if (paragraphText != null && !paragraphText.isBlank()) {
                                notes.append(paragraphText).append('\n');
                            }
                        }
                    }
                    addChunk(chunks, notes.toString(), mapper.createObjectNode().put("type", "speakerNotes").put("slide", i + 1), mapper.createObjectNode());
                }
                addChunk(chunks, text.toString(), mapper.createObjectNode().put("type", "slide").put("slide", i + 1), mapper.createObjectNode());
                checkItemLimit(chunks.size());
            }
            return new ProcessedContent(chunks, mapper.createObjectNode().put("slides", slides.size()));
        }
    }

    private ProcessedContent processXlsx(byte[] bytes) throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = book.getCreationHelper().createFormulaEvaluator();
            ArrayNode chunks = mapper.createArrayNode();
            int cells = 0;
            for (int s = 0; s < book.getNumberOfSheets(); s++) {
                var sheet = book.getSheetAt(s);
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell, evaluator);
                        String formula = cell.getCellType().name().equals("FORMULA") ? cell.getCellFormula() : null;
                        if (value.isBlank() && formula == null) continue;
                        ObjectNode metadata = mapper.createObjectNode();
                        if (formula != null) metadata.put("formula", formula);
                        addChunk(chunks, value, mapper.createObjectNode().put("type", "cell").put("sheet", sheet.getSheetName())
                                .put("row", row.getRowNum() + 1).put("column", cell.getColumnIndex() + 1)
                                .put("cell", cell.getAddress().formatAsString()), metadata);
                        if (++cells > MAX_STRUCTURAL_ITEMS) throw new ProcessingFailure("The spreadsheet exceeds the safe cell-processing limit.");
                    }
                }
            }
            return new ProcessedContent(chunks, mapper.createObjectNode().put("sheets", book.getNumberOfSheets()).put("cells", cells));
        }
    }

    private ProcessedContent processCsv(byte[] bytes) throws Exception {
        String csv = decodeUtf8(bytes);
        char delimiter = detectDelimiter(csv);
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setIgnoreEmptyLines(false).build();
        try (CSVParser parser = CSVParser.parse(csv, format)) {
            Iterator<org.apache.commons.csv.CSVRecord> rows = parser.iterator();
            if (!rows.hasNext()) throw new ProcessingFailure("The CSV file contains no rows.");
            var first = rows.next();
            List<String> headers = new ArrayList<>();
            boolean header = first.size() > 0 && java.util.stream.IntStream.range(0, first.size()).mapToObj(first::get).allMatch(v -> !v.isBlank())
                    && java.util.stream.IntStream.range(0, first.size()).mapToObj(first::get)
                    .map(v -> v.strip().toLowerCase(Locale.ROOT)).distinct().count() == first.size();
            if (header) first.forEach(headers::add);
            ArrayNode chunks = mapper.createArrayNode();
            long rowNumber = header ? 1 : 0;
            if (!header) appendCsvRow(chunks, first, ++rowNumber, headers);
            while (rows.hasNext()) {
                appendCsvRow(chunks, rows.next(), ++rowNumber, headers);
                checkItemLimit(chunks.size());
            }
            ObjectNode metadata = mapper.createObjectNode().put("delimiter", String.valueOf(delimiter))
                    .put("encoding", "UTF-8").put("hasHeader", header).put("rows", rowNumber);
            metadata.set("columns", mapper.valueToTree(headers));
            return new ProcessedContent(chunks, metadata);
        }
    }

    private void appendCsvRow(ArrayNode chunks, org.apache.commons.csv.CSVRecord row, long rowNumber, List<String> headers) throws ProcessingFailure {
        ObjectNode value = mapper.createObjectNode();
        for (int c = 0; c < row.size(); c++) value.put(c < headers.size() ? headers.get(c) : "column" + (c + 1), row.get(c));
        ObjectNode location = mapper.createObjectNode().put("type", "row").put("row", rowNumber);
        addChunk(chunks, value.toString(), location, value);
    }

    private ProcessedContent processText(byte[] bytes) throws Exception {
        String text = decodeUtf8(bytes);
        if (text.length() > MAX_EXTRACTED_CHARS) throw new ProcessingFailure("The text file exceeds the safe extraction limit.");
        ArrayNode chunks = mapper.createArrayNode();
        java.util.regex.Matcher lineBreaks = java.util.regex.Pattern.compile("\\R").matcher(text);
        int start = 0;
        int line = 1;
        while (lineBreaks.find()) {
            appendTextLine(chunks, text.substring(start, lineBreaks.start()), line++, start, lineBreaks.start());
            start = lineBreaks.end();
        }
        appendTextLine(chunks, text.substring(start), line, start, text.length());
        return new ProcessedContent(chunks, mapper.createObjectNode().put("encoding", "UTF-8").put("lines", line));
    }

    private void appendTextLine(ArrayNode chunks, String value, int line, int start, int end) throws ProcessingFailure {
        if (!value.isBlank()) addChunk(chunks, value, mapper.createObjectNode().put("type", "line")
                .put("line", line).put("startOffset", start).put("endOffset", end), mapper.createObjectNode());
    }

    private ProcessedContent processImage(byte[] bytes, String ext) throws Exception {
        BufferedImage image = boundedImage(bytes);
        OcrResult result = ocr(image);
        ArrayNode chunks = mapper.createArrayNode();
        if (!result.text().isBlank()) addChunk(chunks, result.text(), mapper.createObjectNode().put("type", "image")
                .put("width", image.getWidth()).put("height", image.getHeight()), mapper.createObjectNode().put("ocrConfidence", result.confidence()));
        ObjectNode metadata = mapper.createObjectNode().put("format", ext.equals("jpg") ? "jpeg" : ext)
                .put("width", image.getWidth()).put("height", image.getHeight());
        if (result.confidence() >= 0) metadata.put("ocrConfidence", result.confidence());
        return new ProcessedContent(chunks, metadata);
    }

    private BufferedImage boundedImage(byte[] bytes) throws IOException, ProcessingFailure {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new ProcessingFailure("The image file is malformed or unsupported.");
            Iterator<ImageReader> candidates = ImageIO.getImageReaders(input);
            if (!candidates.hasNext()) throw new ProcessingFailure("The image file is malformed or unsupported.");
            ImageReader reader = candidates.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_IMAGE_PIXELS)
                    throw new ProcessingFailure("The image dimensions exceed the safe processing limit.");
                BufferedImage image = reader.read(0);
                if (image == null) throw new ProcessingFailure("The image file is malformed or unsupported.");
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private OcrResult ocr(BufferedImage image) throws Exception {
        Tesseract tesseract = new Tesseract();
        try {
            tesseract.setDatapath(net.sourceforge.tess4j.util.LoadLibs.extractTessResources("tessdata").getAbsolutePath());
        } catch (Exception ignored) {
            // Tess4J may already have a configured/bundled tessdata directory.
        }
        tesseract.setLanguage("eng");
        List<net.sourceforge.tess4j.Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
        StringBuilder text = new StringBuilder();
        double confidence = 0;
        int confidentWords = 0;
        for (var word : words) {
            if (!word.getText().isBlank()) text.append(word.getText()).append(' ');
            if (word.getConfidence() >= 0) { confidence += word.getConfidence(); confidentWords++; }
        }
        return new OcrResult(text.toString().strip(), confidentWords == 0 ? -1 : confidence / confidentWords);
    }

    private void addChunk(ArrayNode chunks, String text, JsonNode location, JsonNode metadata) throws ProcessingFailure {
        if (text == null || text.isBlank()) return;
        if (chunks.size() >= MAX_STRUCTURAL_ITEMS) throw new ProcessingFailure("The file exceeds the safe extraction-item limit.");
        Map<ArrayNode, Integer> collected = extractedCharacters.get();
        int existing = collected == null ? 0 : collected.getOrDefault(chunks, 0);
        if ((long) existing + text.length() > MAX_EXTRACTED_CHARS) throw new ProcessingFailure("Extracted text exceeds the safe processing limit.");
        if (collected != null) collected.put(chunks, existing + text.length());
        ObjectNode chunk = mapper.createObjectNode().put("text", text);
        chunk.set("location", location);
        chunk.set("metadata", metadata);
        chunks.add(chunk);
    }

    private void checkItemLimit(int size) throws ProcessingFailure {
        if (size > MAX_STRUCTURAL_ITEMS) throw new ProcessingFailure("The file exceeds the safe extraction-item limit.");
    }
    private String decodeUtf8(byte[] bytes) throws ProcessingFailure {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException ex) { throw new ProcessingFailure("Only valid UTF-8 text is supported for CSV and TXT files."); }
    }
    private char detectDelimiter(String csv) {
        String first = csv.lines().findFirst().orElse("");
        char best = ',';
        int max = -1;
        for (char candidate : new char[]{',', ';', '\t', '|'}) {
            int count = (int) first.chars().filter(c -> c == candidate).count();
            if (count > max) { max = count; best = candidate; }
        }
        return best;
    }
    private int headingLevel(String style) {
        if (style == null || !style.toLowerCase(Locale.ROOT).startsWith("heading")) return 0;
        try { return Integer.parseInt(style.replaceAll("[^0-9]", "")); } catch (NumberFormatException ignored) { return 1; }
    }
    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private boolean starts(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }
    private String ascii(byte[] bytes, int start, int size) { return new String(bytes, start, size, StandardCharsets.US_ASCII); }

    public record ProcessedContent(ArrayNode chunks, ObjectNode metadata) { }
    private record OcrResult(String text, double confidence) { }
    public static class ProcessingFailure extends Exception {
        public ProcessingFailure(String message) { super(message); }
    }
}
