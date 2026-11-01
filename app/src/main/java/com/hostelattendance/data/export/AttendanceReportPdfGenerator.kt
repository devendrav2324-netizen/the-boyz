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
        // Collect dates in range (capped at 15 days)
        val clampedEnd = if (ChronoUnit.DAYS.between(startDate, endDate) > 14) {
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

        val dateHeaderFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.ENGLISH)
        val dateHeaders = dates.map { it.format(dateHeaderFormatter) }

        // Sort students matching student list standard: Room Number, Roll Number, Name
        val sortedStudents = students.sortedWith(
            compareBy(
                { it.currentRoomNumber },
                { it.rollNumber.toIntOrNull() ?: 999999 },
                { it.studentName },
            ),
        )

        // Pre-index records by (studentId, date) and fallback (rollNumber, date)
        val recordMap = HashMap<Pair<String, String>, AttendanceRecordEntity>(records.size)
        val rollMap = HashMap<Pair<String, String>, AttendanceRecordEntity>(records.size)
        for (r in records) {
            recordMap[Pair(r.studentId, r.localDate)] = r
            rollMap[Pair(r.rollNumberSnapshot, r.localDate)] = r
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
        val sNoWidth = 30f
        val roomWidth = 58f
        val nameWidth = 142f
        val branchWidth = 48f
        val yearWidth = 36f
        val fixedWidth = sNoWidth + roomWidth + nameWidth + branchWidth + yearWidth // 314 pt

        // Summary columns: Days Present (Individual student count of days present) | Days Absent
        val isSingleDay = dates.size == 1
        val totPWidth = if (isSingleDay) 45f else 56f
        val totAWidth = if (isSingleDay) 45f else 46f
        val summaryWidth = totPWidth + totAWidth // 90-102 pt

        val remainingForDates = contentWidth - fixedWidth - summaryWidth // 366 pt
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

        val headerRowHeight = 24f
        val dataRowHeight = 18f

        var currentY: Float
        var pageNumber = 1
        var studentIndex = 0
        val totalStudents = sortedStudents.size
        val timestamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())
        val dateRangeTitle = "${startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} to ${clampedEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} (${dates.size} Days)"

        while (studentIndex < totalStudents || totalStudents == 0) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            currentY = marginTop

            // Header titles
            val wingPart = if (!wingName.isNullOrBlank()) "   |   Wing: $wingName" else ""
            val filterPart = if (!filterDescription.isNullOrBlank()) "   |   Filter: $filterDescription" else ""
            val singleDayStat = if (isSingleDay && totalStudents > 0) {
                val dayPresents = records.count { it.status == "PRESENT" && it.localDate == dates.first().toString() }
                val dayRate = (dayPresents * 100) / totalStudents
                "   |   Day Attendance: $dayRate% ($dayPresents/$totalStudents Present)"
            } else {
                "   |   Summary: Individual Days Present"
            }
            canvas.drawText("CGITR BOYS HOSTEL - ATTENDANCE REGISTER", marginLeft, currentY + 12f, titlePaint)
            currentY += 18f

            canvas.drawText(
                "Date Range: $dateRangeTitle$singleDayStat$wingPart$filterPart   |   Residents: $totalStudents   |   Generated: $timestamp   |   Page $pageNumber",
                marginLeft,
                currentY + 6f,
                subtitlePaint,
            )
            currentY += 16f

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

            // Summary Headers
            val summaryHeaders = if (isSingleDay) {
                listOf(
                    Pair("Status", totPWidth),
                    Pair("Days P", totAWidth),
                )
            } else {
                listOf(
                    Pair("Days Present", totPWidth),
                    Pair("Days Absent", totAWidth),
                )
            }
            for ((header, w) in summaryHeaders) {
                canvas.drawText(header, xCursor + (w / 2f), currentY + 16f, dateHeaderTextPaint)
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
                    val record = recordMap[Pair(student.studentId, dateKey)]
                        ?: rollMap[Pair(student.rollNumber, dateKey)]

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

                if (isSingleDay) {
                    val statusStr = when {
                        pCount > 0 -> "P"
                        lCount > 0 -> "L"
                        else -> "A"
                    }
                    val statusPaint = when (statusStr) {
                        "P" -> presentPaint
                        "L" -> leavePaint
                        else -> absentPaint
                    }
                    canvas.drawText(statusStr, cellX + (totPWidth / 2f), currentY + 13f, statusPaint)
                    canvas.drawLine(cellX + totPWidth, currentY, cellX + totPWidth, currentY + dataRowHeight, gridPaint)
                    cellX += totPWidth

                    val pIndicator = if (pCount > 0) "1 Day" else "0 Days"
                    canvas.drawText(pIndicator, cellX + (totAWidth / 2f), currentY + 13f, centerCellTextPaint)
                    canvas.drawLine(cellX + totAWidth, currentY, cellX + totAWidth, currentY + dataRowHeight, gridPaint)
                    cellX += totAWidth
                } else {
                    // Summary: Days Present (Number of days individual student is present)
                    val daysPresentStr = "$pCount / $totalDaysInRange"
                    canvas.drawText(daysPresentStr, cellX + (totPWidth / 2f), currentY + 13f, centerCellTextPaint)
                    canvas.drawLine(cellX + totPWidth, currentY, cellX + totPWidth, currentY + dataRowHeight, gridPaint)
                    cellX += totPWidth

                    // Summary: Days Absent
                    canvas.drawText(effectiveAbsentCount.toString(), cellX + (totAWidth / 2f), currentY + 13f, centerCellTextPaint)
                    canvas.drawLine(cellX + totAWidth, currentY, cellX + totAWidth, currentY + dataRowHeight, gridPaint)
                    cellX += totAWidth
                }

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
