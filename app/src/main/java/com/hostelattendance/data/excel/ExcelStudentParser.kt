package com.hostelattendance.data.excel

import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList

data class ExcelStudentRow(
    val rowNumber: Int,
    val rollNumber: String,
    val wingName: String,
    val roomNumber: String,
    val studentName: String,
    val branch: String,
    val year: String,
)

data class ExcelValidationResult(
    val validRows: List<ExcelStudentRow>,
    val errors: List<String>,
    val warnings: List<String>,
    val duplicateRollsInFile: Set<String>,
)

class ExcelStudentParser {

    /**
     * Parses an .xlsx or .csv file input stream into a list of row column maps.
     * Supports standard OpenXML .xlsx files as well as plain comma-separated (.csv) files.
     */
    fun parse(inputStream: InputStream): List<Map<Int, String>> {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) {
            return emptyList()
        }

        // Check if file starts with ZIP magic header (0x50, 0x4B) -> .xlsx
        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        if (isZip) {
            try {
                return parseXlsx(bytes)
            } catch (_: Exception) {
                // If zip parsing fails, attempt parsing as CSV/text below
            }
        }

        return parseCsv(bytes)
    }

    private fun parseCsv(bytes: ByteArray): List<Map<Int, String>> {
        val text = String(bytes, Charsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n')
        val rows = mutableListOf<Map<Int, String>>()
        val lines = text.split('\n')
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val cells = parseCsvLine(line)
            val rowMap = mutableMapOf<Int, String>()
            for (i in cells.indices) {
                rowMap[i] = cells[i].trim()
            }
            if (rowMap.isNotEmpty()) {
                rows.add(rowMap)
            }
        }
        return rows
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private fun parseXlsx(bytes: ByteArray): List<Map<Int, String>> {
        val sharedStrings = mutableListOf<String>()
        var sheetBytes: ByteArray? = null

        ZipInputStream(bytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" -> {
                        sharedStrings.addAll(parseSharedStrings(zis.readBytes()))
                    }
                    entry.name == "xl/worksheets/sheet1.xml" || entry.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) -> {
                        if (sheetBytes == null) {
                            sheetBytes = zis.readBytes()
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }

        if (sheetBytes == null) {
            throw IllegalArgumentException("The selected file is not a valid Excel (.xlsx) spreadsheet or contains no worksheets.")
        }

        return parseSheetXml(sheetBytes!!, sharedStrings)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(bytes.inputStream())
        val siList = doc.getElementsByTagName("si")

        for (i in 0 until siList.length) {
            val si = siList.item(i) as Element
            val tNodes = si.getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until tNodes.length) {
                sb.append(tNodes.item(j).textContent)
            }
            strings.add(sb.toString())
        }
        return strings
    }

    private fun parseSheetXml(bytes: ByteArray, sharedStrings: List<String>): List<Map<Int, String>> {
        val rows = mutableListOf<Map<Int, String>>()
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(bytes.inputStream())
        val rowNodes: NodeList = doc.getElementsByTagName("row")

        for (i in 0 until rowNodes.length) {
            val rowElem = rowNodes.item(i) as Element
            val cNodes = rowElem.getElementsByTagName("c")
            val rowMap = mutableMapOf<Int, String>()

            for (j in 0 until cNodes.length) {
                val cElem = cNodes.item(j) as Element
                val cellRef = cElem.getAttribute("r")
                val colIndex = columnLetterToIndex(cellRef)
                val type = cElem.getAttribute("t")

                val value = when (type) {
                    "s" -> {
                        val vNode = cElem.getElementsByTagName("v").item(0)
                        val idx = vNode?.textContent?.toIntOrNull()
                        if (idx != null && idx in sharedStrings.indices) {
                            sharedStrings[idx]
                        } else ""
                    }
                    "inlineStr" -> {
                        val tNode = cElem.getElementsByTagName("t").item(0)
                        tNode?.textContent ?: ""
                    }
                    else -> {
                        val vNode = cElem.getElementsByTagName("v").item(0)
                        vNode?.textContent ?: ""
                    }
                }
                rowMap[colIndex] = value.trim()
            }
            if (rowMap.isNotEmpty()) {
                rows.add(rowMap)
            }
        }
        return rows
    }

    private fun columnLetterToIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        var index = 0
        for (char in letters) {
            index = index * 26 + (char - 'A' + 1)
        }
        return index - 1
    }
}
