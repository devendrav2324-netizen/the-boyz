package com.hostelattendance.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.hostelattendance.data.local.StudentEntity
import com.hostelattendance.data.local.WingEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudentListPdfGenerator {

    /**
     * Generates a Landscape A4 PDF containing the complete student roster
     * matching the exact format of corrected-student-details.xlsx:
     * Columns: S.No. | Name | Room Number | Branch | Year
     * Then shares it via Android Intent.
     */
    fun generateAndSharePdf(
        context: Context,
        hostelId: String,
        students: List<StudentEntity>,
        wings: List<WingEntity>,
    ) {
        val wingMap = wings.associateBy { it.wingId }
        val activeStudents = students.filter { it.isActive }
        val sortedStudents = activeStudents.sortedWith(
            compareBy(
                { it.rollNumber.toIntOrNull() ?: 999999 },
                { it.currentRoomNumber },
                { it.studentName },
            ),
        )

        val doc = PdfDocument()

        // Landscape A4: 842 x 595 points
        val pageWidth = 842
        val pageHeight = 595
        val marginLeft = 40f
        val marginRight = 40f
        val marginTop = 40f
        val marginBottom = 40f
        val contentWidth = pageWidth - marginLeft - marginRight

        // 5 columns matching standard template: S.No. | ROOM NUMBER | STUDENT NAME | BRANCH | YEAR
        // Total = 762 pt
        val colWidths = floatArrayOf(62f, 160f, 280f, 130f, 130f)
        val colHeaders = arrayOf("S.No.", "ROOM NUMBER", "STUDENT NAME", "BRANCH", "YEAR")

        val titlePaint = Paint().apply {
            color = Color.rgb(24, 24, 32)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(90, 90, 105)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val headerBgPaint = Paint().apply {
            color = Color.rgb(232, 235, 243)
            style = Paint.Style.FILL
        }

        val altRowBgPaint = Paint().apply {
            color = Color.rgb(248, 249, 252)
            style = Paint.Style.FILL
        }

        val headerTextPaint = Paint().apply {
            color = Color.rgb(20, 25, 45)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val cellTextPaint = Paint().apply {
            color = Color.rgb(30, 30, 35)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val inactiveBadgePaint = Paint().apply {
            color = Color.rgb(180, 50, 50)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val gridPaint = Paint().apply {
            color = Color.rgb(205, 210, 225)
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
        }

        val headerRowHeight = 24f
        val dataRowHeight = 20f

        var currentY: Float
        var pageNumber = 1
        var studentIndex = 0
        val totalStudents = sortedStudents.size
        val timestamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())

        while (studentIndex < totalStudents || totalStudents == 0) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            currentY = marginTop

            // Title & Meta Info
            canvas.drawText("CGITR BOYS HOSTEL - REGISTERED STUDENTS ROSTER", marginLeft, currentY + 12f, titlePaint)
            currentY += 20f

            canvas.drawText(
                "Hostel ID: $hostelId   |   Total Active Residents: $totalStudents   |   Generated: $timestamp   |   Page $pageNumber",
                marginLeft,
                currentY + 6f,
                subtitlePaint,
            )
            currentY += 18f

            // Table Header Row
            canvas.drawRect(marginLeft, currentY, marginLeft + contentWidth, currentY + headerRowHeight, headerBgPaint)
            canvas.drawRect(marginLeft, currentY, marginLeft + contentWidth, currentY + headerRowHeight, gridPaint)

            var xCursor = marginLeft
            for (c in colHeaders.indices) {
                val colWidth = colWidths[c]
                canvas.drawText(colHeaders[c], xCursor + 6f, currentY + 16f, headerTextPaint)
                canvas.drawLine(xCursor + colWidth, currentY, xCursor + colWidth, currentY + headerRowHeight, gridPaint)
                xCursor += colWidth
            }
            currentY += headerRowHeight

            // Table Data Rows
            var rowCountOnPage = 0
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

                val values = arrayOf(
                    student.rollNumber,
                    student.currentRoomNumber,
                    student.studentName,
                    student.branch ?: "—",
                    student.year ?: "—",
                )

                var cellX = marginLeft
                for (c in values.indices) {
                    val w = colWidths[c]
                    val text = values[c]
                    val paintToUse = if (c == 2 && !student.isActive) inactiveBadgePaint else cellTextPaint

                    // Trim text if it exceeds cell width
                    val maxTextWidth = w - 12f
                    var displayText = text
                    while (displayText.isNotEmpty() && paintToUse.measureText(displayText) > maxTextWidth) {
                        displayText = displayText.dropLast(1)
                    }
                    if (displayText.length < text.length && displayText.isNotEmpty()) {
                        displayText = displayText.dropLast(1) + "…"
                    }

                    canvas.drawText(displayText, cellX + 6f, currentY + 14f, paintToUse)
                    canvas.drawLine(cellX + w, currentY, cellX + w, currentY + dataRowHeight, gridPaint)
                    cellX += w
                }

                currentY += dataRowHeight
                studentIndex++
                rowCountOnPage++
            }

            if (totalStudents == 0) {
                canvas.drawText("No registered students found.", marginLeft + 12f, currentY + 20f, cellTextPaint)
                break
            }

            doc.finishPage(page)
            pageNumber++
        }

        // Write to cache directory and trigger share intent
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val pdfFile = File(exportDir, "Hostel_Registered_Students.pdf")
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Student Sheet PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
