package com.hostelattendance.data.excel

import com.hostelattendance.data.local.StudentEntity
import com.hostelattendance.data.local.WingEntity
import com.hostelattendance.domain.StudentRegistrationRules
import java.io.InputStream
import java.util.Locale
import java.util.UUID

sealed interface ExcelRowValidation {
    data class Valid(val student: ExcelStudentRow, val targetWing: WingEntity) : ExcelRowValidation
    data class Existing(val student: ExcelStudentRow, val existingStudent: StudentEntity, val targetWing: WingEntity) : ExcelRowValidation
    data class Error(val rowNumber: Int, val message: String, val rollNumber: String?) : ExcelRowValidation
}

data class ExcelImportAnalysis(
    val validNewRows: List<ExcelRowValidation.Valid>,
    val existingRows: List<ExcelRowValidation.Existing>,
    val errorRows: List<ExcelRowValidation.Error>,
    val totalProcessedRows: Int,
) {
    val canCommit: Boolean get() = validNewRows.isNotEmpty() || existingRows.isNotEmpty()
}

class ExcelStudentValidator {

    /**
     * Validates spreadsheet rows against:
     * - Standard Template (5 columns): S.No. | ROOM NUMBER | STUDENT NAME | BRANCH | YEAR
     * - Flexible templates: handles both (Room Number, Student Name) and legacy (Name, Room Number)
     * Automatically extracts wing if Room Number is in Floor/Wing/Room format (e.g. GF/01/01 -> Wing GF/01).
     * Missing wings are dynamically provisioned for standard room-based imports.
     */
    fun validate(
        inputStream: InputStream,
        activeWings: List<WingEntity>,
        existingStudents: List<StudentEntity>,
        hostelId: String = "local-installation",
    ): ExcelImportAnalysis {
        val parser = ExcelStudentParser()
        val rawRows = parser.parse(inputStream)

        if (rawRows.isEmpty()) {
            return ExcelImportAnalysis(
                validNewRows = emptyList(),
                existingRows = emptyList(),
                errorRows = listOf(ExcelRowValidation.Error(1, "The spreadsheet is empty.", null)),
                totalProcessedRows = 0,
            )
        }

        val headerRow = rawRows.first()
        val headerMap = (0 until 8).mapNotNull { col -> headerRow[col]?.trim()?.takeIf { it.isNotEmpty() } }

        // Find column indices by header content
        var snoCol = -1
        var roomCol = -1
        var nameCol = -1
        var branchCol = -1
        var yearCol = -1
        var wingCol = -1

        for ((colIdx, cellVal) in headerRow) {
            val norm = cellVal.lowercase(Locale.ROOT)
                .replace(".", "")
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")

            when {
                norm.contains("sno") || norm.contains("roll") || norm.contains("serial") || norm == "sr" || norm == "id" -> {
                    if (snoCol == -1) snoCol = colIdx
                }
                norm.contains("room") -> {
                    if (roomCol == -1) roomCol = colIdx
                }
                norm.contains("name") || norm.contains("student") -> {
                    if (nameCol == -1) nameCol = colIdx
                }
                norm.contains("branch") || norm.contains("dept") || norm.contains("course") -> {
                    if (branchCol == -1) branchCol = colIdx
                }
                norm.contains("year") || norm.contains("sem") || norm.contains("yr") -> {
                    if (yearCol == -1) yearCol = colIdx
                }
                norm.contains("wing") -> {
                    if (wingCol == -1) wingCol = colIdx
                }
            }
        }

        val hasRecognizedHeaders = (snoCol != -1 || roomCol != -1) && nameCol != -1
        if (!hasRecognizedHeaders) {
            val foundHeaderNames = headerMap.joinToString(", ")
            return ExcelImportAnalysis(
                validNewRows = emptyList(),
                existingRows = emptyList(),
                errorRows = listOf(
                    ExcelRowValidation.Error(
                        rowNumber = 1,
                        message = "Invalid template headers. Expected columns: 'S.No. | ROOM NUMBER | STUDENT NAME | BRANCH | YEAR'. Found: [$foundHeaderNames]",
                        rollNumber = null,
                    ),
                ),
                totalProcessedRows = 0,
            )
        }

        if (snoCol == -1) snoCol = 0
        if (roomCol == -1) roomCol = 1
        if (branchCol == -1) branchCol = 3
        if (yearCol == -1) yearCol = 4

        val validNewList = mutableListOf<ExcelRowValidation.Valid>()
        val existingList = mutableListOf<ExcelRowValidation.Existing>()
        val errorList = mutableListOf<ExcelRowValidation.Error>()

        val seenRollsInFile = mutableMapOf<String, Int>()
        val wingsByNormalizedName = activeWings.associateBy { it.normalizedName }.toMutableMap()
        val existingStudentsByNormalizedRoll = existingStudents.associateBy { it.normalizedRollNumber }
        val now = System.currentTimeMillis()

        for (i in 1 until rawRows.size) {
            val excelRowNumber = i + 1
            val row = rawRows[i]

            val roll = if (snoCol >= 0) row[snoCol].orEmpty().trim() else ""
            val room = if (roomCol >= 0) row[roomCol].orEmpty().trim() else ""
            val name = if (nameCol >= 0) row[nameCol].orEmpty().trim() else ""
            val branch = if (branchCol >= 0) row[branchCol].orEmpty().trim().uppercase() else ""
            val year = if (yearCol >= 0) row[yearCol].orEmpty().trim().uppercase() else ""

            val wingName = if (wingCol >= 0 && !row[wingCol].isNullOrBlank()) {
                row[wingCol]!!.trim()
            } else {
                val parts = room.split('/')
                if (parts.size >= 2) {
                    "${parts[0].trim().uppercase()}/${parts[1].trim()}"
                } else if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                    parts[0].trim().uppercase()
                } else {
                    "Main"
                }
            }

            if (roll.isEmpty() && wingName.isEmpty() && room.isEmpty() && name.isEmpty() && branch.isEmpty() && year.isEmpty()) {
                continue
            }

            val missingFields = mutableListOf<String>()
            if (roll.isEmpty()) missingFields.add("S.No. / Roll No.")
            if (room.isEmpty()) missingFields.add("ROOM NUMBER")
            if (name.isEmpty()) missingFields.add("STUDENT NAME")

            if (missingFields.isNotEmpty()) {
                errorList.add(
                    ExcelRowValidation.Error(
                        rowNumber = excelRowNumber,
                        message = "Missing required cell(s): ${missingFields.joinToString(", ")}",
                        rollNumber = roll.ifEmpty { null },
                    ),
                )
                continue
            }

            val normalizedRoll = StudentRegistrationRules.normalizeRollNumber(roll)
            val normalizedWing = StudentRegistrationRules.normalizeWingName(wingName)

            if (seenRollsInFile.containsKey(normalizedRoll)) {
                val previousRow = seenRollsInFile[normalizedRoll]!!
                errorList.add(
                    ExcelRowValidation.Error(
                        rowNumber = excelRowNumber,
                        message = "Duplicate Roll/S.No. '$roll' within file (first seen at row $previousRow).",
                        rollNumber = roll,
                    ),
                )
                continue
            }
            seenRollsInFile[normalizedRoll] = excelRowNumber

            var targetWing = wingsByNormalizedName[normalizedWing]
            if (targetWing == null) {
                if (wingCol >= 0) {
                    val available = activeWings.map { it.name }.joinToString(", ")
                    errorList.add(
                        ExcelRowValidation.Error(
                            rowNumber = excelRowNumber,
                            message = "Unknown wing '$wingName'. Available wings in hostel: [$available]. Create the wing first if needed.",
                            rollNumber = roll,
                        ),
                    )
                    continue
                } else {
                    targetWing = WingEntity(
                        wingId = UUID.randomUUID().toString(),
                        hostelId = hostelId,
                        name = wingName,
                        normalizedName = normalizedWing,
                        updatedAtMillis = now,
                    )
                    wingsByNormalizedName[normalizedWing] = targetWing
                }
            }

            val studentRow = ExcelStudentRow(
                rowNumber = excelRowNumber,
                rollNumber = roll,
                wingName = targetWing.name,
                roomNumber = room,
                studentName = name,
                branch = branch,
                year = year,
            )

            val existingStudent = existingStudentsByNormalizedRoll[normalizedRoll]
            if (existingStudent != null) {
                existingList.add(
                    ExcelRowValidation.Existing(
                        student = studentRow,
                        existingStudent = existingStudent,
                        targetWing = targetWing,
                    ),
                )
            } else {
                validNewList.add(
                    ExcelRowValidation.Valid(
                        student = studentRow,
                        targetWing = targetWing,
                    ),
                )
            }
        }

        return ExcelImportAnalysis(
            validNewRows = validNewList,
            existingRows = existingList,
            errorRows = errorList,
            totalProcessedRows = validNewList.size + existingList.size + errorList.size,
        )
    }
}
