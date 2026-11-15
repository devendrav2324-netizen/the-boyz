package com.hostelattendance.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.hostelattendance.data.local.AttendanceRecordEntity
import com.hostelattendance.data.local.StudentEntity
import com.hostelattendance.data.local.WingEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

class AttendanceReportPdfGenerator {

    /**
     * Generates a Landscape A4 PDF containing the attendance register in Excel-style format:
     * Columns: S.No. | Name | Room Number | Branch | Year | [Date 1] | [Date 2] ... | Tot P | Tot A | %
     * Formatted with dates like "23/08", "24/08", etc.
     */
    fun generateAndSharePdf(
        context: Context,
        hostelId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        students: List<StudentEntity>,
        records: List<AttendanceRecordEntity>,
        wingName: String? = null,
        filterDescription: String? = null,
    ) {
        // Collect dates in range (capped at 15 days only for multi-student hostel registers; single student reports get full month)
        val isSingleStudentReport = students.size == 1
        val clampedEnd = if (!isSingleStudentReport && ChronoUnit.DAYS.between(startDate, endDate) > 14) {
            startDate.plusDays(14)
        } else if (endDate.isBefore(startDate)) {
            startDate
        } else {
            endDate
        }

        val dates = mutableListOf<LocalDate>()
        var curr = startDate
        while (!curr.isAfter(clampedEnd)) {
            dates.add(curr)
            curr = curr.plusDays(1)
        }

        val dateHeaderFormatter = if (dates.size > 15) {
            DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
        } else {
            DateTimeFormatter.ofPattern("dd/MM", Locale.ENGLISH)
        }
        val dateHeaders = dates.map { it.format(dateHeaderFormatter) }

        // Sort students matching student list standard: Room Number, Roll Number, Name
        val sortedStudents = students.sortedWith(
            compareBy<StudentEntity> { it.currentRoomNumber }
                .thenComparator { s1, s2 ->
                    com.hostelattendance.domain.NaturalRollNumberComparator.compare(s1.rollNumber, s2.rollNumber)
                }
                .thenBy { it.studentName }
        )

        // Pre-index records by (studentId, date), fallback (rollNumber, date), and date directly for single-student reports
        val recordMap = HashMap<Pair<String, String>, AttendanceRecordEntity>(records.size)
        val rollMap = HashMap<Pair<String, String>, AttendanceRecordEntity>(records.size)
        val dateMap = HashMap<String, AttendanceRecordEntity>(records.size)
        for (r in records) {
            recordMap[Pair(r.studentId, r.localDate)] = r
            val rRoll = r.rollNumberSnapshot.trim()
            if (rRoll.isNotBlank() && rRoll != "—" && rRoll != "N/A" && rRoll != "1") {
                rollMap[Pair(rRoll, r.localDate)] = r
            }
            dateMap[r.localDate] = r
        }

        val doc = PdfDocument()

        // Landscape A4: 842 x 595 points
        val pageWidth = 842
        val pageHeight = 595
        val marginLeft = 36f
        val marginRight = 36f
        val marginTop = 36f
        val marginBottom = 36f
        val contentWidth = pageWidth - marginLeft - marginRight // 770 pt

        // Fixed columns: S.No. | Room Number | Student Name | Branch | Year
        val sNoWidth = 28f
        val roomWidth = 52f
        val nameWidth = 132f
        val branchWidth = 46f
        val yearWidth = 34f
        val fixedWidth = sNoWidth + roomWidth + nameWidth + branchWidth + yearWidth // 292 pt

        // Summary columns: Total Present | Total Absent | Total Leave
        val isSingleDay = dates.size == 1
        val totPWidth = 54f
        val totAWidth = 52f
        val totLWidth = 50f
        val summaryWidth = totPWidth + totAWidth + totLWidth // 156 pt

        val remainingForDates = contentWidth - fixedWidth - summaryWidth // 322 pt
        val numDays = dates.size.coerceAtLeast(1)
        val dateColWidth = remainingForDates / numDays

        // Paints
        val titlePaint = Paint().apply {
            color = Color.rgb(20, 24, 38)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(85, 90, 105)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val headerBgPaint = Paint().apply {
            color = Color.rgb(232, 236, 245)
            style = Paint.Style.FILL
        }

        val altRowBgPaint = Paint().apply {
            color = Color.rgb(248, 249, 252)
            style = Paint.Style.FILL
        }

        val headerTextPaint = Paint().apply {
            color = Color.rgb(20, 25, 45)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val dateHeaderTextPaint = Paint().apply {
            color = Color.rgb(20, 25, 45)
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val summaryHeaderTextPaint = Paint().apply {
            color = Color.rgb(20, 25, 45)
            textSize = 7f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val cellTextPaint = Paint().apply {
            color = Color.rgb(30, 30, 35)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val centerCellTextPaint = Paint().apply {
            color = Color.rgb(30, 30, 35)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val presentPaint = Paint().apply {
            color = Color.rgb(25, 135, 84) // Forest Green
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val absentPaint = Paint().apply {
            color = Color.rgb(220, 53, 69) // Crimson Red
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val leavePaint = Paint().apply {
            color = Color.rgb(217, 119, 6) // Warm Amber
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val unrecordedPaint = Paint().apply {
            color = Color.rgb(170, 170, 175) // Neutral Gray
            textSize = 9f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val gridPaint = Paint().apply {
            color = Color.rgb(205, 210, 225)
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
        }

        val kpiBoxBgPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }

        val kpiBoxBorderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
        }

        val kpiLabelPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val kpiPresentPaint = Paint().apply {
            color = Color.rgb(22, 101, 52)
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val kpiAbsentPaint = Paint().apply {
            color = Color.rgb(185, 28, 28)
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val kpiLeavePaint = Paint().apply {
            color = Color.rgb(180, 83, 9)
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val kpiUnmarkedPaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val kpiDividerPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 0.6f
            style = Paint.Style.STROKE
        }

        val headerRowHeight = 24f
        val dataRowHeight = 18f

        var currentY: Float
        var pageNumber = 1
        var studentIndex = 0
        val totalStudents = sortedStudents.size
        val timestamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
        val dateRangeTitle = "${startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} to ${clampedEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} (${dates.size} Days)"

        // Pre-compute daily totals for hostel top headings (Total Present, Absent, Leave, Unmarked)
        data class DayTotals(
            val date: LocalDate,
            val present: Int,
            val absent: Int,
            val leave: Int,
            val unmarked: Int,
        )

        val dailyTotals = dates.map { date ->
            val dateKey = date.toString()
            var p = 0
            var a = 0
            var l = 0
            for (student in sortedStudents) {
                val record = if (isSingleStudentReport) {
                    dateMap[dateKey]
                        ?: recordMap[Pair(student.studentId, dateKey)]
                        ?: (if (student.rollNumber != "—" && student.rollNumber != "N/A") rollMap[Pair(student.rollNumber, dateKey)] else null)
                } else {
                    recordMap[Pair(student.studentId, dateKey)]
                        ?: (if (student.rollNumber != "—" && student.rollNumber != "N/A") rollMap[Pair(student.rollNumber, dateKey)] else null)
                }
                when (record?.status) {
                    "PRESENT" -> p++
                    "ABSENT" -> a++
                    "LEAVE" -> l++
                }
            }
            val u = (totalStudents - p - a - l).coerceAtLeast(0)
            DayTotals(
                date = date,
                present = p,
                absent = a,
                leave = l,
                unmarked = u,
            )
        }

        val totalPresentSum = dailyTotals.sumOf { it.present }
        val totalAbsentSum = dailyTotals.sumOf { it.absent }
        val totalLeaveSum = dailyTotals.sumOf { it.leave }
        val totalUnmarkedSum = dailyTotals.sumOf { it.unmarked }

        while (studentIndex < totalStudents || totalStudents == 0) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            currentY = marginTop

            // Header titles
            val wingPart = if (!wingName.isNullOrBlank()) "   |   Wing: $wingName" else ""
            val filterPart = if (!filterDescription.isNullOrBlank()) "   |   Filter: $filterDescription" else ""
            val kpiHeaderSummary = if (isSingleDay) {
                val p = dailyTotals.firstOrNull()?.present ?: 0
                val a = dailyTotals.firstOrNull()?.absent ?: 0
                val l = dailyTotals.firstOrNull()?.leave ?: 0
                val u = dailyTotals.firstOrNull()?.unmarked ?: 0
                "   |   Total Present: $p   |   Total Absent: $a   |   Total Leave: $l   |   Total Unmarked: $u"
            } else {
                "   |   Range Totals -> Present: $totalPresentSum   |   Absent: $totalAbsentSum   |   Leave: $totalLeaveSum   |   Unmarked: $totalUnmarkedSum"
            }

            canvas.drawText("CGITR BOYS HOSTEL - ATTENDANCE REGISTER", marginLeft, currentY + 12f, titlePaint)
            currentY += 18f

            canvas.drawText(
                "Date Range: $dateRangeTitle$kpiHeaderSummary$wingPart$filterPart   |   Residents: $totalStudents   |   Generated: $timestamp   |   Page $pageNumber",
                marginLeft,
                currentY + 6f,
                subtitlePaint,
            )
            currentY += 15f

            // Top Headings Summary Section (Total Present, Absent, Leave, Unmarked)
            // If date range is selected, heading size expands dynamically to show breakdown for each day
            if (dates.size == 1 && dailyTotals.isNotEmpty()) {
                val dt = dailyTotals.first()
                val kpiCardHeight = 22f
                canvas.drawRoundRect(marginLeft, currentY, marginLeft + contentWidth, currentY + kpiCardHeight, 4f, 4f, kpiBoxBgPaint)
                canvas.drawRoundRect(marginLeft, currentY, marginLeft + contentWidth, currentY + kpiCardHeight, 4f, 4f, kpiBoxBorderPaint)

                val textY = currentY + 14.5f
                var curX = marginLeft + 10f

                canvas.drawText("DATE: ${dt.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", curX, textY, kpiLabelPaint)
                curX += 95f
                canvas.drawText("TOTAL RESIDENTS: $totalStudents", curX, textY, kpiLabelPaint)
                curX += 120f
                canvas.drawText("TOTAL PRESENT: ${dt.present}", curX, textY, kpiPresentPaint)
                curX += 115f
                canvas.drawText("TOTAL ABSENT: ${dt.absent}", curX, textY, kpiAbsentPaint)
                curX += 110f
                canvas.drawText("TOTAL LEAVE: ${dt.leave}", curX, textY, kpiLeavePaint)
                curX += 105f
                canvas.drawText("TOTAL UNMARKED: ${dt.unmarked}", curX, textY, kpiUnmarkedPaint)

                currentY += kpiCardHeight + 7f
            } else if (dates.size > 1 && dailyTotals.isNotEmpty()) {
                val numRows = (dailyTotals.size + 1) / 2
                val rowLineHeight = 13.5f
                val headerBlockHeight = 18f
                val kpiCardHeight = headerBlockHeight + (numRows * rowLineHeight) + 5f

                canvas.drawRoundRect(marginLeft, currentY, marginLeft + contentWidth, currentY + kpiCardHeight, 4f, 4f, kpiBoxBgPaint)
                canvas.drawRoundRect(marginLeft, currentY, marginLeft + contentWidth, currentY + kpiCardHeight, 4f, 4f, kpiBoxBorderPaint)

                // Top overall summary line
                val topTextY = currentY + 12.5f
                var topX = marginLeft + 10f
                canvas.drawText("HOSTEL RANGE TOTALS ($totalStudents Residents):", topX, topTextY, kpiLabelPaint)
                topX += 190f
                canvas.drawText("TOTAL PRESENT: $totalPresentSum", topX, topTextY, kpiPresentPaint)
                topX += 130f
                canvas.drawText("TOTAL ABSENT: $totalAbsentSum", topX, topTextY, kpiAbsentPaint)
                topX += 125f
                canvas.drawText("TOTAL LEAVE: $totalLeaveSum", topX, topTextY, kpiLeavePaint)
                topX += 120f
                canvas.drawText("TOTAL UNMARKED: $totalUnmarkedSum", topX, topTextY, kpiUnmarkedPaint)

                canvas.drawLine(marginLeft, currentY + headerBlockHeight, marginLeft + contentWidth, currentY + headerBlockHeight, kpiDividerPaint)

                // Multi-day breakdown (2 columns across landscape width)
                var dayRowY = currentY + headerBlockHeight + 10.5f
                val colWidth = contentWidth / 2f
                for (row in 0 until numRows) {
                    val i1 = row * 2
                    val i2 = row * 2 + 1

                    // Column 1
                    if (i1 < dailyTotals.size) {
                        val dt = dailyTotals[i1]
                        val x1 = marginLeft + 10f
                        val fullDateStr = dt.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        canvas.drawText("• $fullDateStr :", x1, dayRowY, kpiLabelPaint)
                        canvas.drawText("Present: ${dt.present}", x1 + 80f, dayRowY, kpiPresentPaint)
                        canvas.drawText("Absent: ${dt.absent}", x1 + 150f, dayRowY, kpiAbsentPaint)
                        canvas.drawText("Leave: ${dt.leave}", x1 + 215f, dayRowY, kpiLeavePaint)
                        canvas.drawText("Unmarked: ${dt.unmarked}", x1 + 275f, dayRowY, kpiUnmarkedPaint)
                    }

                    // Column 2
                    if (i2 < dailyTotals.size) {
                        val dt = dailyTotals[i2]
                        val x2 = marginLeft + colWidth + 10f
                        val fullDateStr = dt.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        canvas.drawText("• $fullDateStr :", x2, dayRowY, kpiLabelPaint)
                        canvas.drawText("Present: ${dt.present}", x2 + 80f, dayRowY, kpiPresentPaint)
                        canvas.drawText("Absent: ${dt.absent}", x2 + 150f, dayRowY, kpiAbsentPaint)
                        canvas.drawText("Leave: ${dt.leave}", x2 + 215f, dayRowY, kpiLeavePaint)
                        canvas.drawText("Unmarked: ${dt.unmarked}", x2 + 275f, dayRowY, kpiUnmarkedPaint)
                    }

                    dayRowY += rowLineHeight
                }

                currentY += kpiCardHeight + 7f
            }

            // Draw Header Row
            canvas.drawRect(marginLeft, currentY, marginLeft + contentWidth, currentY + headerRowHeight, headerBgPaint)
            canvas.drawRect(marginLeft, currentY, marginLeft + contentWidth, currentY + headerRowHeight, gridPaint)

            var xCursor = marginLeft

            // Fixed Headers: S.No. | Room Number | Student Name | Branch | Year
            val fixedHeaders = listOf(
                Pair("S.No.", sNoWidth),
                Pair("Room No.", roomWidth),
                Pair("Student Name", nameWidth),
                Pair("Branch", branchWidth),
                Pair("Year", yearWidth),
            )
            for ((header, w) in fixedHeaders) {
                canvas.drawText(header, xCursor + 5f, currentY + 16f, headerTextPaint)
                canvas.drawLine(xCursor + w, currentY, xCursor + w, currentY + headerRowHeight, gridPaint)
                xCursor += w
            }

            // Date Headers
            for (dh in dateHeaders) {
                canvas.drawText(dh, xCursor + (dateColWidth / 2f), currentY + 16f, dateHeaderTextPaint)
                canvas.drawLine(xCursor + dateColWidth, currentY, xCursor + dateColWidth, currentY + headerRowHeight, gridPaint)
                xCursor += dateColWidth
            }

            // Summary Headers: Total Present | Total Absent | Total Leave
            val summaryHeaders = listOf(
                Pair("Total Present", totPWidth),
                Pair("Total Absent", totAWidth),
                Pair("Total Leave", totLWidth),
            )
            for ((header, w) in summaryHeaders) {
                canvas.drawText(header, xCursor + (w / 2f), currentY + 16f, summaryHeaderTextPaint)
                canvas.drawLine(xCursor + w, currentY, xCursor + w, currentY + headerRowHeight, gridPaint)
                xCursor += w
            }

            currentY += headerRowHeight

            // Table Data Rows
            while (studentIndex < totalStudents) {
                val student = sortedStudents[studentIndex]

                if (currentY + dataRowHeight > pageHeight - marginBottom) {
                    break // Next page
                }

                // Alternating row background
                if (studentIndex % 2 == 1) {
                    canvas.drawRect(marginLeft, currentY, marginLeft + contentWidth, currentY + dataRowHeight, altRowBgPaint)
                }
                canvas.drawRect(marginLeft, currentY, marginLeft + contentWidth, currentY + dataRowHeight, gridPaint)

                var cellX = marginLeft

                // 1. S.No.
                val sNo = (studentIndex + 1).toString()
                canvas.drawText(sNo, cellX + 4f, currentY + 13f, cellTextPaint)
                canvas.drawLine(cellX + sNoWidth, currentY, cellX + sNoWidth, currentY + dataRowHeight, gridPaint)
                cellX += sNoWidth

                // 2. Room Number
                canvas.drawText(student.currentRoomNumber, cellX + 4f, currentY + 13f, cellTextPaint)
                canvas.drawLine(cellX + roomWidth, currentY, cellX + roomWidth, currentY + dataRowHeight, gridPaint)
                cellX += roomWidth

                // 3. Student Name
                var displayName = student.studentName + if (!student.isActive) " (Inact)" else ""
                val maxNameWidth = nameWidth - 8f
                while (displayName.isNotEmpty() && cellTextPaint.measureText(displayName) > maxNameWidth) {
                    displayName = displayName.dropLast(1)
                }
                if (displayName.length < student.studentName.length && displayName.isNotEmpty()) {
                    displayName = displayName.dropLast(1) + "…"
                }
                canvas.drawText(displayName, cellX + 4f, currentY + 13f, cellTextPaint)
                canvas.drawLine(cellX + nameWidth, currentY, cellX + nameWidth, currentY + dataRowHeight, gridPaint)
                cellX += nameWidth

                // 4. Branch
                canvas.drawText(student.branch ?: "—", cellX + 4f, currentY + 13f, cellTextPaint)
                canvas.drawLine(cellX + branchWidth, currentY, cellX + branchWidth, currentY + dataRowHeight, gridPaint)
                cellX += branchWidth

                // 5. Year
                canvas.drawText(student.year ?: "—", cellX + 4f, currentY + 13f, cellTextPaint)
                canvas.drawLine(cellX + yearWidth, currentY, cellX + yearWidth, currentY + dataRowHeight, gridPaint)
                cellX += yearWidth

                // Date Columns
                var pCount = 0
                var aCount = 0
                var lCount = 0

                for (date in dates) {
                    val dateKey = date.toString()
                    val record = if (isSingleStudentReport) {
                        dateMap[dateKey]
                            ?: recordMap[Pair(student.studentId, dateKey)]
                            ?: (if (student.rollNumber != "—" && student.rollNumber != "N/A") rollMap[Pair(student.rollNumber, dateKey)] else null)
                    } else {
                        recordMap[Pair(student.studentId, dateKey)]
                            ?: (if (student.rollNumber != "—" && student.rollNumber != "N/A") rollMap[Pair(student.rollNumber, dateKey)] else null)
                    }

                    val (char, paint) = when (record?.status) {
                        "PRESENT" -> {
                            pCount++
                            Pair("P", presentPaint)
                        }
                        "ABSENT" -> {
                            aCount++
                            Pair("A", absentPaint)
                        }
                        "LEAVE" -> {
                            lCount++
                            Pair("L", leavePaint)
                        }
                        else -> Pair("—", unrecordedPaint)
                    }

                    canvas.drawText(char, cellX + (dateColWidth / 2f), currentY + 13f, paint)
                    canvas.drawLine(cellX + dateColWidth, currentY, cellX + dateColWidth, currentY + dataRowHeight, gridPaint)
                    cellX += dateColWidth
                }

                val totalDaysInRange = dates.size.coerceAtLeast(1)
                val effectiveAbsentCount = (totalDaysInRange - pCount - lCount).coerceAtLeast(0)

                // 1. Total Present
                canvas.drawText(pCount.toString(), cellX + (totPWidth / 2f), currentY + 13f, presentPaint)
                canvas.drawLine(cellX + totPWidth, currentY, cellX + totPWidth, currentY + dataRowHeight, gridPaint)
                cellX += totPWidth

                // 2. Total Absent
                canvas.drawText(effectiveAbsentCount.toString(), cellX + (totAWidth / 2f), currentY + 13f, absentPaint)
                canvas.drawLine(cellX + totAWidth, currentY, cellX + totAWidth, currentY + dataRowHeight, gridPaint)
                cellX += totAWidth

                // 3. Total Leave
                canvas.drawText(lCount.toString(), cellX + (totLWidth / 2f), currentY + 13f, leavePaint)
                canvas.drawLine(cellX + totLWidth, currentY, cellX + totLWidth, currentY + dataRowHeight, gridPaint)
                cellX += totLWidth

                currentY += dataRowHeight
                studentIndex++
            }

            if (totalStudents == 0) {
                canvas.drawText("No residents matching criteria.", marginLeft + 12f, currentY + 20f, cellTextPaint)
                break
            }

            doc.finishPage(page)
            pageNumber++
        }

        // Write to cache directory and trigger share intent
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val pdfFile = File(exportDir, "Hostel_Attendance_Register.pdf")
        FileOutputStream(pdfFile).use { out ->
            doc.writeTo(out)
        }
        doc.close()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile,
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Attendance Register ($dateRangeTitle)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Attendance Register PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
