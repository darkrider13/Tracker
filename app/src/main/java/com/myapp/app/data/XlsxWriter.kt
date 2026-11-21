package com.myapp.app.data

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A tiny .xlsx writer (an .xlsx file is a zip of XML files), so the app needs no extra library.
 * One header row (bold, frozen) + data rows per sheet. A cell is a String, a Number or null (empty).
 */
object XlsxWriter {

    /** Excel's own limit for the text of one cell. */
    const val MAX_CELL_CHARS = 32767

    class Sheet(
        val name: String,
        val headers: List<String>,
        val rows: List<List<Any?>>,
        /** Column widths in characters; missing entries use 18. */
        val widths: List<Int> = emptyList(),
    )

    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    fun build(sheets: List<Sheet>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            fun put(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            // [Content_Types].xml
            val types = StringBuilder()
            types.append(XML_HEAD)
            types.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            types.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            types.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            types.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
            sheets.forEachIndexed { i, _ ->
                types.append("<Override PartName=\"/xl/worksheets/sheet${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
            }
            types.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
            types.append("</Types>")
            put("[Content_Types].xml", types.toString())

            // _rels/.rels
            put(
                "_rels/.rels",
                XML_HEAD + "<Relationships xmlns=\"$NS_PKG_REL\">" +
                    "<Relationship Id=\"rId1\" Type=\"$NS_REL/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>",
            )

            // xl/workbook.xml
            val wb = StringBuilder()
            wb.append(XML_HEAD)
            wb.append("<workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\"><sheets>")
            sheets.forEachIndexed { i, s ->
                wb.append("<sheet name=\"").append(esc(s.name)).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>")
            }
            wb.append("</sheets></workbook>")
            put("xl/workbook.xml", wb.toString())

            // xl/_rels/workbook.xml.rels
            val rels = StringBuilder()
            rels.append(XML_HEAD)
            rels.append("<Relationships xmlns=\"$NS_PKG_REL\">")
            sheets.forEachIndexed { i, _ ->
                rels.append("<Relationship Id=\"rId${i + 1}\" Type=\"$NS_REL/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>")
            }
            rels.append("<Relationship Id=\"rId${sheets.size + 1}\" Type=\"$NS_REL/styles\" Target=\"styles.xml\"/>")
            rels.append("</Relationships>")
            put("xl/_rels/workbook.xml.rels", rels.toString())

            // xl/styles.xml  (style 0 = normal, style 1 = bold header)
            put(
                "xl/styles.xml",
                XML_HEAD + "<styleSheet xmlns=\"$NS_MAIN\">" +
                    "<fonts count=\"2\">" +
                    "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                    "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                    "</fonts>" +
                    "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
                    "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                    "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                    "<cellXfs count=\"4\">" +
                    "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                    "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
                    "<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                    "<xf numFmtId=\"1\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>" +
                    "</cellXfs>" +
                    "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
                    "</styleSheet>",
            )

            // xl/worksheets/sheetN.xml
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
        return bos.toByteArray()
    }

    private fun sheetXml(s: Sheet): String {
        val sb = StringBuilder()
        sb.append(XML_HEAD)
        sb.append("<worksheet xmlns=\"$NS_MAIN\">")
        val lastCol = XlsxWriter.colName(maxOf(s.headers.size, 1) - 1)
        sb.append("<dimension ref=\"A1:").append(lastCol).append(s.rows.size + 1).append("\"/>")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\">")
        sb.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        sb.append("</sheetView></sheetViews>")
        sb.append("<cols>")
        for (c in s.headers.indices) {
            val w = s.widths.getOrNull(c) ?: 18
            sb.append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"$w\" customWidth=\"1\"/>")
        }
        sb.append("</cols>")
        sb.append("<sheetData>")

        sb.append("<row r=\"1\">")
        s.headers.forEachIndexed { c, h -> appendString(sb, ref(c, 1), h, 1) }
        sb.append("</row>")

        s.rows.forEachIndexed { r, row ->
            val rowNo = r + 2
            sb.append("<row r=\"").append(rowNo).append("\">")
            row.forEachIndexed { c, v ->
                if (v == null) return@forEachIndexed
                val cellRef = ref(c, rowNo)
                if (v is String) {
                    // an empty text is the same as an empty cell
                    if (v.isNotEmpty()) appendString(sb, cellRef, v, 0)
                    return@forEachIndexed
                }
                if (v is Int || v is Long) {
                    appendNumber(sb, cellRef, v.toString(), 3)
                } else if (v is Number) {
                    appendNumber(sb, cellRef, plainNumber(v.toDouble()), 2)
                } else {
                    appendString(sb, cellRef, v.toString(), 0)
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun appendString(sb: StringBuilder, ref: String, text: String, style: Int) {
        if (text.length > MAX_CELL_CHARS) {
            throw IllegalStateException("a text is too long for one Excel cell (${text.length} characters)")
        }
        sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"")
        if (style != 0) sb.append(" s=\"").append(style).append("\"")
        sb.append("><is><t xml:space=\"preserve\">").append(esc(text)).append("</t></is></c>")
    }

    private fun appendNumber(sb: StringBuilder, ref: String, value: String, style: Int) {
        // explicit type + number style, so every viewer (Excel, Sheets, phone viewers) shows the number
        sb.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\" t=\"n\"><v>").append(value).append("</v></c>")
    }

    /** 12.5 -> "12.5", 100.0 -> "100", 1.0E12 -> "1000000000000" (never scientific notation). */
    private fun plainNumber(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "0"
        val bd = BigDecimal.valueOf(d).stripTrailingZeros()
        return bd.toPlainString()
    }

    /** Column 0 -> "A", 25 -> "Z", 26 -> "AA" */
    internal fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    private fun ref(col: Int, row: Int): String = colName(col) + row

    /** XML-escapes [s] and drops the characters XML 1.0 cannot hold. */
    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            val code = ch.code
            val ok = code == 0x9 || code == 0xA || code == 0xD ||
                (code in 0x20..0xD7FF) || (code in 0xD800..0xDFFF) || (code in 0xE000..0xFFFD)
            if (!ok) continue
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
