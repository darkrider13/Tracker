package com.myapp.app.data

import android.util.Xml
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * A small .xlsx reader (an .xlsx file is a zip of XML files), the other half of [XlsxWriter].
 * It also understands files that were opened and saved again in Excel / Google Sheets / LibreOffice
 * (shared strings, rich text, booleans, numbers, other XML prefixes).
 */
object XlsxReader {

    class ReadException(message: String) : Exception(message)

    /** [text] is the cell as text; [numeric] tells that the cell was a real number cell in the file. */
    class Cell(val text: String, val numeric: Boolean)

    /** [number] is the row number in Excel (1 = header row), [cells] is indexed by column (null = empty). */
    class Row(val number: Int, val cells: List<Cell?>)

    class Sheet(val name: String, val rows: List<Row>)

    private const val MAX_ENTRY_BYTES = 120L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 300L * 1024L * 1024L
    private const val MAX_ROW = 1_048_576
    private const val MAX_COL = 16_384

    fun read(bytes: ByteArray): List<Sheet> {
        try {
            val files = unzip(bytes)
            val workbook = files["xl/workbook.xml"] ?: throw ReadException("xl/workbook.xml not found")
            val relsData = files["xl/_rels/workbook.xml.rels"] ?: throw ReadException("workbook relationships not found")

            val rels = parseRels(relsData)               // id -> target path (lower case)
            val sheetRefs = parseWorkbook(workbook)       // (name, relationship id)

            val sstPath = rels.sharedStrings ?: "xl/sharedstrings.xml"
            val shared = files[sstPath]?.let { parseSharedStrings(it) } ?: emptyList()

            val sheets = ArrayList<Sheet>()
            for ((name, rid) in sheetRefs) {
                val path = rels.targets[rid] ?: continue
                val data = files[path] ?: continue
                sheets.add(Sheet(name, parseSheet(data, shared)))
            }
            if (sheets.isEmpty()) throw ReadException("no sheets found")
            return sheets
        } catch (e: XmlPullParserException) {
            throw ReadException(e.message ?: "bad XML")
        } catch (e: IOException) {
            throw ReadException(e.message ?: "bad file")
        }
    }

    // ---------------------------------------------------------------- zip

    /** Only the XML parts under xl/ are read (images and the like are skipped). Keys are lower case. */
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val map = HashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.replace('\\', '/').trimStart('/').lowercase()
                if (!(name.startsWith("xl/") && (name.endsWith(".xml") || name.endsWith(".rels")))) continue
                val out = ByteArrayOutputStream()
                val buf = ByteArray(32 * 1024)
                var size = 0L
                while (true) {
                    val n = zin.read(buf)
                    if (n < 0) break
                    size += n
                    total += n
                    if (size > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) throw ReadException("the file is too big inside")
                    out.write(buf, 0, n)
                }
                map[name] = out.toByteArray()
            }
        }
        return map
    }

    // ---------------------------------------------------------------- xml helpers

    private fun newParser(data: ByteArray): XmlPullParser {
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(data), null)
        return p
    }

    /** Tag / attribute name without its prefix ("x:sheet" -> "sheet", "r:id" -> "id"). */
    private fun local(name: String?): String = name?.substringAfter(':') ?: ""

    private fun attr(p: XmlPullParser, key: String): String? {
        for (i in 0 until p.attributeCount) {
            if (local(p.getAttributeName(i)) == key) return p.getAttributeValue(i)
        }
        return null
    }

    // ---------------------------------------------------------------- workbook + relationships

    private class Rels(val targets: Map<String, String>, val sharedStrings: String?)

    private fun parseRels(data: ByteArray): Rels {
        val p = newParser(data)
        val targets = HashMap<String, String>()
        var shared: String? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && local(p.name) == "Relationship") {
                val id = attr(p, "Id")
                val target = attr(p, "Target")
                val type = attr(p, "Type") ?: ""
                if (id != null && target != null) {
                    val path = resolveTarget(target)
                    targets[id] = path
                    if (type.endsWith("/sharedStrings")) shared = path
                }
            }
            ev = p.next()
        }
        return Rels(targets, shared)
    }

    /** "worksheets/sheet1.xml" -> "xl/worksheets/sheet1.xml", "/xl/worksheets/sheet1.xml" -> same (lower case). */
    private fun resolveTarget(target: String): String {
        val t = target.replace('\\', '/')
        val full = if (t.startsWith("/")) t.substring(1) else "xl/$t"
        val parts = ArrayList<String>()
        for (seg in full.split('/')) {
            if (seg.isEmpty() || seg == ".") continue
            if (seg == "..") {
                if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            } else {
                parts.add(seg)
            }
        }
        return parts.joinToString("/").lowercase()
    }

    private fun parseWorkbook(data: ByteArray): List<Pair<String, String>> {
        val p = newParser(data)
        val list = ArrayList<Pair<String, String>>()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && local(p.name) == "sheet") {
                val name = attr(p, "name")
                val rid = attr(p, "id")
                if (name != null && rid != null) list.add(Pair(name, rid))
            }
            ev = p.next()
        }
        return list
    }

    // ---------------------------------------------------------------- shared strings

    private fun parseSharedStrings(data: ByteArray): List<String> {
        val p = newParser(data)
        val list = ArrayList<String>()
        var sb: StringBuilder? = null
        var phDepth = 0
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (local(p.name)) {
                    "si" -> sb = StringBuilder()
                    "rPh" -> phDepth++
                    "t" -> {
                        val cur = sb
                        if (cur != null && phDepth == 0) cur.append(p.nextText())
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (local(p.name)) {
                    "rPh" -> if (phDepth > 0) phDepth--
                    "si" -> {
                        list.add(sb?.toString() ?: "")
                        sb = null
                    }
                }
            }
            ev = p.next()
        }
        return list
    }

    // ---------------------------------------------------------------- one sheet

    private fun numberCell(raw: String): Cell? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        return try {
            Cell(BigDecimal(v).stripTrailingZeros().toPlainString(), true)
        } catch (e: NumberFormatException) {
            Cell(v, false)
        }
    }

    /** "A" -> 0, "B" -> 1, "AA" -> 26 (the letters at the start of a cell reference such as "AB12"). */
    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            n = when (ch) {
                in 'A'..'Z' -> n * 26 + (ch - 'A' + 1)
                in 'a'..'z' -> n * 26 + (ch - 'a' + 1)
                else -> break
            }
        }
        return n - 1
    }

    private fun parseSheet(data: ByteArray, shared: List<String>): List<Row> {
        val p = newParser(data)
        val rows = ArrayList<Row>()

        var rowNo = 0
        var cells: HashMap<Int, Cell>? = null

        var inCell = false
        var colNo = -1
        var type = ""
        var value: String? = null
        var inlineText: StringBuilder? = null
        var phDepth = 0

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (local(p.name)) {
                    "row" -> {
                        rowNo = attr(p, "r")?.trim()?.toIntOrNull() ?: (rowNo + 1)
                        if (rowNo > MAX_ROW) throw ReadException("row number too big")
                        cells = HashMap()
                        colNo = -1
                    }
                    "c" -> {
                        inCell = true
                        type = attr(p, "t") ?: ""
                        val ref = attr(p, "r")
                        val fromRef = if (ref != null) colIndex(ref) else -1
                        colNo = if (fromRef >= 0) fromRef else colNo + 1
                        if (colNo >= MAX_COL) throw ReadException("column number too big")
                        value = null
                        inlineText = null
                        phDepth = 0
                    }
                    "v" -> if (inCell) value = p.nextText()
                    "is" -> if (inCell) inlineText = StringBuilder()
                    "rPh" -> phDepth++
                    "t" -> {
                        val cur = inlineText
                        if (inCell && cur != null && phDepth == 0) cur.append(p.nextText())
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (local(p.name)) {
                    "rPh" -> if (phDepth > 0) phDepth--
                    "c" -> {
                        val v = value
                        val cell: Cell? = when (type) {
                            "s" -> v?.trim()?.toIntOrNull()?.let { idx -> shared.getOrNull(idx) }?.let { Cell(it, false) }
                            "inlineStr" -> inlineText?.let { Cell(it.toString(), false) }
                            "str", "d" -> v?.let { Cell(it, false) }
                            "b" -> v?.let { Cell(if (it.trim() == "1") "true" else "false", false) }
                            "e" -> null
                            else -> v?.let { numberCell(it) }
                        }
                        if (cell != null && cell.text.isNotEmpty()) cells?.put(colNo, cell)
                        inCell = false
                    }
                    "row" -> {
                        val map = cells
                        if (map != null && map.isNotEmpty()) {
                            val maxCol = map.keys.maxOrNull() ?: 0
                            val list = ArrayList<Cell?>(maxCol + 1)
                            for (c in 0..maxCol) list.add(map[c])
                            rows.add(Row(rowNo, list))
                        }
                        cells = null
                    }
                }
            }
            ev = p.next()
        }
        return rows
    }
}

/**
 * The Excel backup read back: turns the sheets written by [ExcelBackup] into the same checked
 * [ImportResult.Full] the JSON import gives. Nothing is changed here - the caller shows the summary and
 * asks the user to confirm first.
 *
 * Checks: it is a Tracker backup, all 7 sheets and their key columns are there, every number / true-false
 * cell holds a valid value, ids are whole numbers, every payment belongs to a debt, no fund name is
 * repeated, at least one fund exists, a PIN / pattern lock has its code. Cells that Excel turned into a
 * date / time number are converted back to text.
 */
object ExcelImport {

    private class Fail(val error: ImportError, val reason: String = "") : Exception(reason)

    fun parse(bytes: ByteArray): ImportResult {
        val size = bytes.size.toLong()
        return try {
            build(bytes, size)
        } catch (f: Fail) {
            ImportResult.Failure(f.error, f.reason, size)
        } catch (oom: OutOfMemoryError) {
            ImportResult.Failure(ImportError.MEMORY, sizeBytes = size)
        }
    }

    // ---------------------------------------------------------------- table helpers

    private class Table(val sheet: String, all: List<XlsxReader.Row>) {
        val col = HashMap<String, Int>()
        val data: List<XlsxReader.Row> = if (all.size > 1) all.subList(1, all.size) else emptyList()

        init {
            all.firstOrNull()?.cells?.forEachIndexed { i, c ->
                val h = c?.text?.trim()?.lowercase()
                if (!h.isNullOrEmpty() && !col.containsKey(h)) col[h] = i
            }
        }

        fun need(vararg headers: String) {
            for (h in headers) {
                if (!col.containsKey(h.lowercase())) throw Fail(ImportError.EXCEL_COLUMN, "$sheet › $h")
            }
        }
    }

    private class RowView(val table: Table, val row: XlsxReader.Row) {
        fun cell(h: String): XlsxReader.Cell? {
            val i = table.col[h.lowercase()] ?: return null
            return row.cells.getOrNull(i)
        }

        fun text(h: String): String? = cell(h)?.text

        fun where(h: String): String = "${table.sheet} · row ${row.number} · $h"

        fun blank(cols: List<String>): Boolean = cols.all { text(it).isNullOrEmpty() }
    }

    private fun clip(s: String): String {
        val one = s.replace('\n', ' ').replace('\r', ' ')
        return if (one.length > 40) one.substring(0, 40) + "…" else one
    }

    private fun parseNumber(s: String, where: String): Double {
        val d = s.trim().toDoubleOrNull()
        if (d == null || d.isNaN() || d.isInfinite()) throw Fail(ImportError.EXCEL_NUMBER, "$where = ${clip(s)}")
        return d
    }

    private fun parseLong(s: String, where: String): Long {
        try {
            val bi = BigDecimal(s.trim()).toBigIntegerExact()
            if (bi.bitLength() > 63) throw Fail(ImportError.EXCEL_NUMBER, "$where = ${clip(s)}")
            return bi.toLong()
        } catch (e: NumberFormatException) {
            throw Fail(ImportError.EXCEL_NUMBER, "$where = ${clip(s)}")
        } catch (e: ArithmeticException) {
            throw Fail(ImportError.EXCEL_NUMBER, "$where = ${clip(s)}")
        }
    }

    private fun parseBool(s: String, where: String): Boolean {
        return when (s.trim().lowercase()) {
            "true", "1", "yes", "y" -> true
            "false", "0", "no", "n" -> false
            else -> throw Fail(ImportError.EXCEL_BOOL, "$where = ${clip(s)}")
        }
    }

    private fun numReq(r: RowView, h: String): Double {
        val s = r.text(h)?.trim()
        if (s.isNullOrEmpty()) throw Fail(ImportError.EXCEL_EMPTY, r.where(h))
        return parseNumber(s, r.where(h))
    }

    private fun numOpt(r: RowView, h: String): Double? {
        val s = r.text(h)?.trim()
        if (s.isNullOrEmpty()) return null
        return parseNumber(s, r.where(h))
    }

    private fun longReq(r: RowView, h: String): Long {
        val s = r.text(h)?.trim()
        if (s.isNullOrEmpty()) throw Fail(ImportError.EXCEL_EMPTY, r.where(h))
        return parseLong(s, r.where(h))
    }

    private fun longOpt(r: RowView, h: String): Long? {
        val s = r.text(h)?.trim()
        if (s.isNullOrEmpty()) return null
        return parseLong(s, r.where(h))
    }

    private fun boolOpt(r: RowView, h: String): Boolean? {
        val s = r.text(h)?.trim()
        if (s.isNullOrEmpty()) return null
        return parseBool(s, r.where(h))
    }

    /** Date text; a cell Excel turned into a date number is converted back to the app's ISO text. */
    private fun dateText(r: RowView, h: String): String? {
        val c = r.cell(h) ?: return null
        if (!c.numeric) return c.text
        val serial = c.text.toDoubleOrNull()
        if (serial == null || serial < 1.0 || serial > 2958465.0) {
            throw Fail(ImportError.EXCEL_VALUE, "${r.where(h)} = ${clip(c.text)}")
        }
        return JsDate.toIso(Math.round((serial - 25569.0) * 86400000.0))
    }

    /** "10:00" text; a cell Excel turned into a time number is converted back to "HH:mm". */
    private fun timeText(r: RowView, h: String): String? {
        val c = r.cell(h) ?: return null
        if (!c.numeric) return c.text
        val v = c.text.toDoubleOrNull() ?: throw Fail(ImportError.EXCEL_VALUE, "${r.where(h)} = ${clip(c.text)}")
        val frac = v - Math.floor(v)
        val minutes = Math.round(frac * 1440.0).toInt() % 1440
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
    }

    private fun jsonObject(text: String?, where: String): JSONObject? {
        if (text.isNullOrBlank()) return null
        try {
            return JSONObject(text)
        } catch (e: JSONException) {
            throw Fail(ImportError.EXCEL_JSON, where)
        }
    }

    private fun mergeExtra(target: JSONObject, extra: JSONObject?) {
        if (extra == null) return
        val it = extra.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (!target.has(k)) target.put(k, extra.opt(k))
        }
    }

    private fun JSONObject.putIf(key: String, value: Any?) {
        if (value != null) put(key, value)
    }

    // ---------------------------------------------------------------- the import

    private fun build(bytes: ByteArray, size: Long): ImportResult {
        val sheets = try {
            XlsxReader.read(bytes)
        } catch (e: XlsxReader.ReadException) {
            throw Fail(ImportError.ZIP, e.message ?: "")
        }

        val byName = HashMap<String, XlsxReader.Sheet>()
        for (s in sheets) {
            val key = s.name.trim().lowercase()
            if (!byName.containsKey(key)) byName[key] = s
        }

        fun table(name: String): Table {
            val s = byName[name.lowercase()] ?: throw Fail(ImportError.EXCEL_SHEET, name)
            return Table(name, s.rows)
        }

        val settingsTable = table(ExcelBackup.SHEET_SETTINGS)
        val fundsTable = table(ExcelBackup.SHEET_FUNDS)
        val txTable = table(ExcelBackup.SHEET_TRANSACTIONS)
        val debtsTable = table(ExcelBackup.SHEET_DEBTS)
        val paymentsTable = table(ExcelBackup.SHEET_PAYMENTS)
        val catsTable = table(ExcelBackup.SHEET_CATEGORIES)
        val notesTable = table(ExcelBackup.SHEET_NOTES)

        settingsTable.need("Key", "Value")
        fundsTable.need("Fund", "Balance")
        txTable.need("ID", "Fund", "Amount", "Date")
        debtsTable.need("ID", "Type", "Person", "Amount")
        paymentsTable.need("Debt ID", "Amount")
        catsTable.need("Name")
        notesTable.need("Kind", "Name", "Text")

        // ------------------------------------------------ Settings
        val settings = HashMap<String, String?>()
        for (row in settingsTable.data) {
            val r = RowView(settingsTable, row)
            val k = r.text("Key")?.trim()
            if (!k.isNullOrEmpty()) settings[k] = r.text("Value")
        }

        fun sText(k: String): String? = settings[k]?.takeIf { it.isNotEmpty() }

        fun sNum(k: String): Double? {
            val s = sText(k)?.trim() ?: return null
            return parseNumber(s, "${ExcelBackup.SHEET_SETTINGS} · $k")
        }

        fun sBool(k: String): Boolean? {
            val s = sText(k)?.trim() ?: return null
            return parseBool(s, "${ExcelBackup.SHEET_SETTINGS} · $k")
        }

        // not a Tracker backup (some other Excel file)
        if (sText(ExcelBackup.K_TYPE)?.trim() != Backup.TYPE) throw Fail(ImportError.INVALID_FORMAT)

        val data = JSONObject()
        sText(ExcelBackup.K_USERNAME)?.let { data.put("username", it) }
        sNum(ExcelBackup.K_SAVINGS_TARGET)?.let { data.put("savingsTarget", it) }
        sBool(ExcelBackup.K_ONBOARDING)?.let { data.put("onboardingComplete", it) }
        sText(ExcelBackup.K_ACTIVE_FUND)?.let { data.put("activeFund", it) }
        sText(ExcelBackup.K_AVATAR)?.let { data.put("profileAvatar", it) }
        sBool(ExcelBackup.K_BIOMETRIC)?.let { data.put("biometricLockEnabled", it) }

        val lockType = sText(ExcelBackup.K_LOCK_TYPE)?.trim()?.lowercase()
        if (lockType != null) {
            if (lockType != "none" && lockType != "biometric" && lockType != "pin" && lockType != "pattern") {
                throw Fail(ImportError.EXCEL_VALUE, "${ExcelBackup.SHEET_SETTINGS} · ${ExcelBackup.K_LOCK_TYPE} = ${clip(lockType)}")
            }
            data.put("lockType", lockType)
        }
        val pinHash = sText(ExcelBackup.K_LOCK_PIN)
        val patternHash = sText(ExcelBackup.K_LOCK_PATTERN)
        // a PIN / pattern lock without its code would lock the person out of their own app
        if (lockType == "pin" && pinHash == null) {
            throw Fail(ImportError.EXCEL_VALUE, "${ExcelBackup.SHEET_SETTINGS} · ${ExcelBackup.K_LOCK_PIN}")
        }
        if (lockType == "pattern" && patternHash == null) {
            throw Fail(ImportError.EXCEL_VALUE, "${ExcelBackup.SHEET_SETTINGS} · ${ExcelBackup.K_LOCK_PATTERN}")
        }
        data.put("lockPinHash", pinHash ?: JSONObject.NULL)
        data.put("lockPatternHash", patternHash ?: JSONObject.NULL)

        sText(ExcelBackup.K_RANGE_START)?.let { data.put("dashboardRangeStart", it) }
        sText(ExcelBackup.K_RANGE_END)?.let { data.put("dashboardRangeEnd", it) }

        val filters = JSONObject()
        filters.put("sort", sText(ExcelBackup.K_FILTER_SORT) ?: "newest")
        filters.put("time", sText(ExcelBackup.K_FILTER_TIME) ?: "month")
        filters.put("price", sText(ExcelBackup.K_FILTER_PRICE) ?: "all")
        sText(ExcelBackup.K_FILTER_DATE)?.let { filters.put("customDate", it) }
        sText(ExcelBackup.K_FILTER_MONTH)?.let { filters.put("customMonth", it) }
        mergeExtra(
            filters,
            jsonObject(sText(ExcelBackup.K_FILTER_EXTRA), "${ExcelBackup.SHEET_SETTINGS} · ${ExcelBackup.K_FILTER_EXTRA}"),
        )
        data.put("filters", filters)

        val orderText = sText(ExcelBackup.K_CATEGORY_ORDER)
        val order = if (orderText == null) {
            JSONArray()
        } else {
            try {
                JSONArray(orderText)
            } catch (e: JSONException) {
                throw Fail(ImportError.EXCEL_JSON, "${ExcelBackup.SHEET_SETTINGS} · ${ExcelBackup.K_CATEGORY_ORDER}")
            }
        }
        data.put("categoryOrder", order)

        val remindersObj = jsonObject(sText(ExcelBackup.K_REMINDERS), "${ExcelBackup.SHEET_SETTINGS} · ${ExcelBackup.K_REMINDERS}")
        val reminders: ReminderSettings? = remindersObj?.let { ReminderSettings.fromJson(it) }

        val topExtra = jsonObject(sText(ExcelBackup.K_EXTRA), "${ExcelBackup.SHEET_SETTINGS} · ${ExcelBackup.K_EXTRA}")

        val theme = sText(ExcelBackup.K_THEME)?.trim()?.lowercase()?.takeIf { it == "light" || it == "dark" }
        val language = sText(ExcelBackup.K_LANGUAGE)?.trim()?.lowercase()?.takeIf { it == "en" || it == "bn" }

        // ------------------------------------------------ Funds
        val funds = JSONObject()
        val fundMeta = JSONObject()
        val fundCols = listOf("Fund", "Balance", "Icon", "Color", "Type", "Extra")
        for (row in fundsTable.data) {
            val r = RowView(fundsTable, row)
            if (r.blank(fundCols)) continue
            val name = r.text("Fund")
            if (name.isNullOrEmpty()) throw Fail(ImportError.EXCEL_EMPTY, r.where("Fund"))
            if (funds.has(name)) throw Fail(ImportError.EXCEL_DUP, "${r.where("Fund")} = ${clip(name)}")
            funds.put(name, numOpt(r, "Balance") ?: 0.0)

            val icon = r.text("Icon")
            val color = r.text("Color")
            val type = r.text("Type")
            val extra = jsonObject(r.text("Extra"), r.where("Extra"))
            if (icon != null || color != null || type != null || extra != null) {
                val m = JSONObject()
                m.put("icon", icon ?: "")
                m.put("color", color ?: "")
                m.put("type", type ?: "")
                mergeExtra(m, extra)
                fundMeta.put(name, m)
            }
        }
        if (funds.length() == 0) throw Fail(ImportError.EXCEL_EMPTY, "${ExcelBackup.SHEET_FUNDS} · Fund")
        data.put("funds", funds)
        data.put("fundMeta", fundMeta)

        // ------------------------------------------------ Transactions
        val txCols = listOf(
            "ID", "Type", "Fund", "Category", "Description", "Amount", "Date", "Transfer ID", "Linked Fund",
            "Debt Note Kind", "Debt Note Person", "From Debt Payment", "Debt Entry ID", "Debt Payment Entry ID",
            "Debt Counter Tx", "History Hidden", "Extra",
        )
        val txArr = JSONArray()
        for (row in txTable.data) {
            val r = RowView(txTable, row)
            if (r.blank(txCols)) continue
            val o = JSONObject()
            o.put("id", longReq(r, "ID"))
            o.putIf("transferId", longOpt(r, "Transfer ID"))
            o.putIf("type", r.text("Type"))
            o.put("fund", r.text("Fund") ?: "")
            o.putIf("linkedFund", r.text("Linked Fund"))
            o.put("category", r.text("Category") ?: "")
            o.put("description", r.text("Description") ?: "")
            o.putIf("debtNoteKind", r.text("Debt Note Kind"))
            o.putIf("debtNotePerson", r.text("Debt Note Person"))
            o.put("amount", numReq(r, "Amount"))
            o.put("date", dateText(r, "Date") ?: "")
            o.putIf("fromDebtPayment", boolOpt(r, "From Debt Payment"))
            o.putIf("debtEntryId", longOpt(r, "Debt Entry ID"))
            o.putIf("debtPaymentEntryId", longOpt(r, "Debt Payment Entry ID"))
            o.putIf("debtCounterTx", boolOpt(r, "Debt Counter Tx"))
            o.putIf("historyHidden", boolOpt(r, "History Hidden"))
            mergeExtra(o, jsonObject(r.text("Extra"), r.where("Extra")))
            txArr.put(o)
        }
        data.put("transactions", txArr)

        // ------------------------------------------------ Debts
        val debtCols = listOf(
            "ID", "Type", "Person", "Amount", "Fund", "Counter Fund", "Note", "Due Date", "Reminder Time",
            "Due Reminder Fired", "Created Date", "Settled", "Settled Date", "Key Person", "Extra",
        )
        val debtArr = JSONArray()
        val debtById = HashMap<Long, JSONObject>()
        for (row in debtsTable.data) {
            val r = RowView(debtsTable, row)
            if (r.blank(debtCols)) continue
            val id = longReq(r, "ID")
            val type = r.text("Type")?.trim()?.lowercase() ?: "debt"
            if (type != "debt" && type != "receivable") {
                throw Fail(ImportError.EXCEL_VALUE, "${r.where("Type")} = ${clip(type)}")
            }
            val o = JSONObject()
            o.put("id", id)
            o.put("type", type)
            o.put("person", r.text("Person") ?: "")
            o.put("amount", numReq(r, "Amount"))
            o.put("fund", r.text("Fund") ?: JSONObject.NULL)
            o.put("counterFund", r.text("Counter Fund") ?: JSONObject.NULL)
            o.put("note", r.text("Note") ?: "")
            o.put("dueDate", dateText(r, "Due Date") ?: JSONObject.NULL)
            o.put("reminderTime", timeText(r, "Reminder Time") ?: JSONObject.NULL)
            o.put("dueReminderFired", boolOpt(r, "Due Reminder Fired") ?: false)
            o.put("createdDate", dateText(r, "Created Date") ?: "")
            o.put("settled", boolOpt(r, "Settled") ?: false)
            o.put("settledDate", dateText(r, "Settled Date") ?: JSONObject.NULL)
            o.put("payments", JSONArray())
            o.put("isKey", boolOpt(r, "Key Person") ?: false)
            mergeExtra(o, jsonObject(r.text("Extra"), r.where("Extra")))
            debtArr.put(o)
            if (!debtById.containsKey(id)) debtById[id] = o
        }

        // ------------------------------------------------ Debt payments (each one goes back to its debt)
        val paymentCols = listOf("Debt ID", "Amount", "Date", "Fund", "Internal", "From Fund", "To Fund", "Tx ID", "Tx IDs", "Extra")
        for (row in paymentsTable.data) {
            val r = RowView(paymentsTable, row)
            if (r.blank(paymentCols)) continue
            val debtId = longReq(r, "Debt ID")
            val debt = debtById[debtId] ?: throw Fail(ImportError.EXCEL_LINK, "${r.where("Debt ID")} = $debtId")
            val p = JSONObject()
            p.put("amount", numReq(r, "Amount"))
            p.put("date", dateText(r, "Date") ?: "")
            p.put("fund", r.text("Fund") ?: JSONObject.NULL)
            p.putIf("internal", boolOpt(r, "Internal"))
            p.putIf("fromFund", r.text("From Fund"))
            p.putIf("toFund", r.text("To Fund"))
            p.putIf("txId", longOpt(r, "Tx ID"))
            val idsText = r.text("Tx IDs")
            if (idsText != null) {
                val ids = JSONArray()
                for (part in idsText.split(',')) {
                    val s = part.trim()
                    if (s.isNotEmpty()) ids.put(parseLong(s, r.where("Tx IDs")))
                }
                p.put("txIds", ids)
            }
            mergeExtra(p, jsonObject(r.text("Extra"), r.where("Extra")))
            debt.getJSONArray("payments").put(p)
        }
        data.put("debts", debtArr)

        // ------------------------------------------------ Custom categories
        val catCols = listOf("Name", "Icon", "Extra")
        val cats = JSONArray()
        for (row in catsTable.data) {
            val r = RowView(catsTable, row)
            if (r.blank(catCols)) continue
            val name = r.text("Name")
            if (name.isNullOrEmpty()) throw Fail(ImportError.EXCEL_EMPTY, r.where("Name"))
            val o = JSONObject()
            o.put("name", name)
            o.put("icon", r.text("Icon") ?: "")
            mergeExtra(o, jsonObject(r.text("Extra"), r.where("Extra")))
            cats.put(o)
        }
        data.put("customCategories", cats)

        // ------------------------------------------------ Notes
        val noteCols = listOf("Kind", "Name", "Text")
        val fundNotes = JSONObject()
        val sectionNotes = JSONObject()
        for (row in notesTable.data) {
            val r = RowView(notesTable, row)
            if (r.blank(noteCols)) continue
            val kind = r.text("Kind")?.trim()?.lowercase()
            val name = r.text("Name")
            if (name.isNullOrEmpty()) throw Fail(ImportError.EXCEL_EMPTY, r.where("Name"))
            when (kind) {
                ExcelBackup.NOTE_FUND -> fundNotes.put(name, r.text("Text") ?: "")
                ExcelBackup.NOTE_SECTION -> sectionNotes.put(name, r.text("Text") ?: "")
                else -> throw Fail(ImportError.EXCEL_VALUE, "${r.where("Kind")} = ${clip(kind ?: "")}")
            }
        }
        data.put("fundNotes", fundNotes)
        data.put("logSectionNotes", sectionNotes)

        mergeExtra(data, topExtra)

        // ------------------------------------------------ same rules as the JSON import
        val txCount = txArr.length()
        val debtCount = debtArr.length()
        val fundCount = funds.length()

        val state: AppState = try {
            Backup.normalizeImportedJson(data)
            AppState.fromJson(data)
        } catch (e: Exception) {
            throw Fail(ImportError.CORRUPT, e.message ?: "")
        }

        return ImportResult.Full(
            state = state,
            reminders = reminders,
            theme = theme,
            language = language,
            transactionCount = txCount,
            debtCount = debtCount,
            fundCount = fundCount,
            sizeBytes = size,
        )
    }
}
