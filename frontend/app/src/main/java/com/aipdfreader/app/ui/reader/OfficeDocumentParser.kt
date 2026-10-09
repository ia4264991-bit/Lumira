package com.aipdfreader.app.ui.reader

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Small, offline OOXML reader for the read-only DOCX and PPTX preview. */
internal object OfficeDocumentParser {
    private const val MAX_ENTRIES = 512
    private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    private const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    fun parseWord(input: InputStream): WordDocumentPreview {
        val entries = readEntries(input)
        val document = parseXml(entries["word/document.xml"] ?: error("Word document content is missing."))
        val body = document.getElementsByTagNameNS("*", "body").item(0)
            ?: error("Word document content is invalid.")
        val relationships = relationships(entries["word/_rels/document.xml.rels"], "word/document.xml")
        val blocks = mutableListOf<WordDocumentBlock>()

        body.childElements().forEach { child ->
            when (child.local()) {
                "p" -> {
                    val paragraph = wordParagraph(child)
                    val images = embeddedImages(child, relationships, entries)
                    if (paragraph.runs.any { it.text.isNotBlank() } || paragraph.headingLevel != null || paragraph.bullet) {
                        blocks += paragraph
                    }
                    blocks += images
                }
                "tbl" -> {
                    val rows = child.descendants("tr").map { row ->
                        row.descendants("tc").map { cell ->
                            cell.descendants("p").joinToString("\n") { paragraphText(it) }
                        }
                    }.filter { row -> row.any(String::isNotBlank) }
                    if (rows.isNotEmpty()) blocks += WordTableBlock(rows)
                    child.descendants("blip").mapNotNull { blip ->
                        imageForRelationship(blip.attribute("embed"), relationships, entries)
                    }.forEach { blocks += WordImageBlock(it) }
                }
            }
        }
        if (blocks.isEmpty()) error("No readable paragraphs, tables, or images were found in this Word document.")
        return WordDocumentPreview(blocks)
    }

    fun parsePresentation(input: InputStream): PowerPointDocumentPreview {
        val entries = readEntries(input)
        val presentation = parseXml(entries["ppt/presentation.xml"] ?: error("PowerPoint presentation content is missing."))
        val size = presentation.getElementsByTagNameNS("*", "sldSz").item(0) as? Element
        val widthEmu = size?.attribute("cx")?.toLongOrNull()?.coerceAtLeast(1L) ?: 12_192_000L
        val heightEmu = size?.attribute("cy")?.toLongOrNull()?.coerceAtLeast(1L) ?: 6_858_000L
        val presentationRelationships = relationships(entries["ppt/_rels/presentation.xml.rels"], "ppt/presentation.xml")
        val orderedSlides = presentation.getElementsByTagNameNS("*", "sldId").let { nodes ->
            (0 until nodes.length).mapNotNull { index ->
                val relId = (nodes.item(index) as? Element)?.attribute("id", REL_NS)
                relId?.let(presentationRelationships::get)
            }
        }.ifEmpty {
            entries.keys.filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
                .sortedBy { it.substringAfter("slide").substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE }
        }

        val slides = orderedSlides.mapNotNull { slidePath ->
            val xml = entries[slidePath] ?: return@mapNotNull null
            val slide = parseXml(xml)
            val relPath = relationshipPart(slidePath)
            val slideRelationships = relationships(entries[relPath], slidePath)
            val shapeTree = slide.getElementsByTagNameNS("*", "spTree").item(0) ?: slide.documentElement
            val shapeNodes = shapeTree.childElements().filter { it.local() in setOf("sp", "pic", "graphicFrame") }
            val elements = mutableListOf<PowerPointElement>()
            shapeNodes.forEachIndexed { index, shape ->
                val bounds = shapeBounds(shape, widthEmu, heightEmu, index, shapeNodes.size)
                val image = shape.descendants("blip").firstOrNull()?.let { blip ->
                    imageForRelationship(blip.attribute("embed"), slideRelationships, entries)
                }
                if (image != null) {
                    elements += PowerPointImageElement(image, bounds.x, bounds.y, bounds.width, bounds.height)
                }
                val text = shape.descendants("p").map { paragraphText(it) }
                    .filter(String::isNotBlank).joinToString("\n")
                if (text.isNotBlank()) {
                    val runProperties = shape.descendants("rPr").firstOrNull()
                    val fontSize = runProperties?.attribute("sz")?.toFloatOrNull()?.div(100f)
                    val bold = runProperties?.attribute("b") in setOf("1", "true")
                    elements += PowerPointTextElement(text, bounds.x, bounds.y, bounds.width, bounds.height,
                        fontSize?.coerceIn(6f, 96f) ?: 20f, bold)
                }
            }
            if (elements.isEmpty()) null else PowerPointSlidePreview(elements)
        }
        if (slides.isEmpty()) error("No readable slides were found in this PowerPoint file.")
        return PowerPointDocumentPreview(widthEmu, heightEmu, slides)
    }

    private fun wordParagraph(element: Element): WordParagraphBlock {
        val style = element.descendants("pStyle").firstOrNull()?.attribute("val").orEmpty()
        val headingLevel = when {
            style.equals("Title", ignoreCase = true) -> 0
            style.equals("Subtitle", ignoreCase = true) -> -1
            style.startsWith("Heading", ignoreCase = true) -> style.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 6)
            else -> null
        }
        val bullet = element.descendants("numPr").isNotEmpty()
        val runs = element.descendants("r").mapNotNull { run ->
            val text = buildString {
                run.childElements().forEach { child ->
                    when (child.local()) {
                        "t" -> append(child.textContent)
                        "tab" -> append('\t')
                        "br", "cr" -> append('\n')
                    }
                }
            }
            if (text.isEmpty()) null else {
                val properties = run.childElements().firstOrNull { it.local() == "rPr" }
                WordTextRun(text,
                    bold = properties?.descendants("b")?.isNotEmpty() == true,
                    italic = properties?.descendants("i")?.isNotEmpty() == true,
                    underline = properties?.descendants("u")?.firstOrNull()?.attribute("val")?.let { it != "none" } == true)
            }
        }
        return WordParagraphBlock(runs, headingLevel, bullet)
    }

    private fun paragraphText(element: Element): String = element.descendants("t")
        .joinToString("") { it.textContent }
        .replace('\u00A0', ' ')
        .trim()

    private fun embeddedImages(node: Element, relationships: Map<String, String>, entries: Map<String, ByteArray>): List<WordImageBlock> =
        node.descendants("blip").mapNotNull { blip ->
            imageForRelationship(blip.attribute("embed"), relationships, entries)?.let(::WordImageBlock)
        }

    private fun imageForRelationship(
        relationshipId: String?,
        relationships: Map<String, String>,
        entries: Map<String, ByteArray>
    ): OfficeImage? {
        val path = relationshipId?.let(relationships::get) ?: return null
        val bytes = entries[path]?.takeIf { it.isNotEmpty() && it.size <= MAX_IMAGE_BYTES } ?: return null
        return OfficeImage(bytes, mimeType(path))
    }

    private data class NormalizedBounds(val x: Float, val y: Float, val width: Float, val height: Float)

    private fun shapeBounds(shape: Element, slideWidth: Long, slideHeight: Long, index: Int, count: Int): NormalizedBounds {
        val transform = shape.descendants("xfrm").firstOrNull()
        val offset = transform?.childElements()?.firstOrNull { it.local() == "off" }
        val extent = transform?.childElements()?.firstOrNull { it.local() == "ext" }
        val x = offset?.attribute("x")?.toLongOrNull()
        val y = offset?.attribute("y")?.toLongOrNull()
        val cx = extent?.attribute("cx")?.toLongOrNull()
        val cy = extent?.attribute("cy")?.toLongOrNull()
        if (x == null || y == null || cx == null || cy == null) {
            val rowHeight = 1f / count.coerceAtLeast(1)
            return NormalizedBounds(0.05f, index * rowHeight, 0.9f, rowHeight.coerceAtLeast(0.08f))
        }
        val left = (x.toDouble() / slideWidth).toFloat().coerceIn(0f, 1f)
        val top = (y.toDouble() / slideHeight).toFloat().coerceIn(0f, 1f)
        val right = ((x.toDouble() + cx.toDouble()) / slideWidth).toFloat().coerceIn(left, 1f)
        val bottom = ((y.toDouble() + cy.toDouble()) / slideHeight).toFloat().coerceIn(top, 1f)
        return NormalizedBounds(left, top, (right - left).coerceAtLeast(0.01f), (bottom - top).coerceAtLeast(0.01f))
    }

    private fun relationships(bytes: ByteArray?, sourcePart: String): Map<String, String> {
        if (bytes == null) return emptyMap()
        val root = parseXml(bytes).documentElement
        val basePath = sourcePart.substringBeforeLast('/')
        return root.descendants("Relationship").mapNotNull { relationship ->
            if (relationship.attribute("TargetMode").equals("External", ignoreCase = true)) return@mapNotNull null
            val id = relationship.attribute("Id")
            val target = relationship.attribute("Target")
            if (id.isBlank() || target.isBlank()) null else {
                val resolved = normalizePartPath(basePath, target)
                id to resolved
            }
        }.toMap()
    }

    private fun normalizePartPath(basePath: String, target: String): String {
        val path = if (target.startsWith('/')) target.drop(1) else "$basePath/$target"
        val stack = mutableListOf<String>()
        path.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack += segment
            }
        }
        return stack.joinToString("/")
    }

    private fun relationshipPart(part: String): String {
        val directory = part.substringBeforeLast('/', "")
        val file = part.substringAfterLast('/')
        return if (directory.isEmpty()) "_rels/$file.rels" else "$directory/_rels/$file.rels"
    }

    private fun readEntries(input: InputStream): Map<String, ByteArray> = input.use { source ->
        val entries = LinkedHashMap<String, ByteArray>()
        val zip = ZipInputStream(source.buffered())
        val buffer = ByteArray(8192)
        var entry = zip.nextEntry
        var entryCount = 0
        var totalBytes = 0
        while (entry != null) {
            entryCount++
            if (entryCount > MAX_ENTRIES) error("This Office file contains too many parts to preview safely.")
            if (!entry.isDirectory) {
                val name = entry.name.replace('\\', '/')
                val shouldKeep = name.endsWith(".xml", true) || name.endsWith(".rels", true) ||
                    ((name.startsWith("word/media/") || name.startsWith("ppt/media/")) &&
                        name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "gif", "bmp", "webp"))
                val output = if (shouldKeep) ByteArrayOutputStream() else null
                var partBytes = 0
                while (true) {
                    val read = zip.read(buffer)
                    if (read <= 0) break
                    partBytes += read
                    totalBytes += read
                    if (partBytes > MAX_ENTRY_BYTES || totalBytes > MAX_ARCHIVE_BYTES) {
                        error("This Office file is too large to preview safely.")
                    }
                    output?.write(buffer, 0, read)
                }
                if (output != null) {
                    if (entries.put(name, output.toByteArray()) != null) error("This Office file contains duplicate parts.")
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        entries
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        val xml = bytes.toString(Charsets.UTF_8)
        if (Regex("<!\\s*DOCTYPE", RegexOption.IGNORE_CASE).containsMatchIn(xml)) {
            error("This Office file contains unsupported external XML declarations.")
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        return builder.parse(ByteArrayInputStream(bytes))
    }

    private fun mimeType(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    private fun Node.childElements(): List<Element> = buildList {
        var child = firstChild
        while (child != null) {
            if (child is Element) add(child)
            child = child.nextSibling
        }
    }

    private fun Node.descendants(localName: String): List<Element> {
        val root = this as? Element ?: (this as? Document)?.documentElement ?: ownerDocument?.documentElement
        val nodes = root?.getElementsByTagNameNS("*", localName)
            ?: return emptyList()
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun Element.local(): String = localName ?: tagName.substringAfter(':')

    private fun Element.attribute(name: String, namespace: String? = null): String {
        val value = if (namespace == null) getAttribute(name) else getAttributeNS(namespace, name)
        if (value.isNotBlank()) return value
        val attributes = attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            val local = attribute.localName ?: attribute.nodeName.substringAfter(':')
            if (local == name) return attribute.nodeValue.orEmpty()
        }
        return ""
    }
}

internal data class OfficeImage(val bytes: ByteArray, val mimeType: String)

internal sealed interface WordDocumentBlock
internal data class WordParagraphBlock(
    val runs: List<WordTextRun>,
    val headingLevel: Int?,
    val bullet: Boolean
) : WordDocumentBlock
internal data class WordTableBlock(val rows: List<List<String>>) : WordDocumentBlock
internal data class WordImageBlock(val image: OfficeImage) : WordDocumentBlock
internal data class WordTextRun(val text: String, val bold: Boolean, val italic: Boolean, val underline: Boolean)
internal data class WordDocumentPreview(val blocks: List<WordDocumentBlock>)

internal sealed interface PowerPointElement {
    val x: Float
    val y: Float
    val width: Float
    val height: Float
}
internal data class PowerPointTextElement(
    val text: String,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float,
    val fontSizePoints: Float,
    val bold: Boolean
) : PowerPointElement
internal data class PowerPointImageElement(
    val image: OfficeImage,
    override val x: Float,
    override val y: Float,
    override val width: Float,
    override val height: Float
) : PowerPointElement
internal data class PowerPointSlidePreview(val elements: List<PowerPointElement>)
internal data class PowerPointDocumentPreview(
    val widthEmu: Long,
    val heightEmu: Long,
    val slides: List<PowerPointSlidePreview>
)
