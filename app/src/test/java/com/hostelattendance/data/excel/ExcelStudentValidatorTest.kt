package com.hostelattendance.data.excel

import com.hostelattendance.data.local.StudentEntity
import com.hostelattendance.data.local.WingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExcelStudentValidatorTest {

    private val wings = listOf(
        WingEntity(wingId = "w1", hostelId = "h1", name = "Wing A", normalizedName = "WING A", isActive = true, updatedAtMillis = 0),
        WingEntity(wingId = "w2", hostelId = "h1", name = "Wing B", normalizedName = "WING B", isActive = true, updatedAtMillis = 0),
    )

    private val existingStudents = listOf(
        StudentEntity(
            studentId = "s1",
            hostelId = "h1",
            rollNumber = "2401",
            normalizedRollNumber = "2401",
            currentWingId = "w1",
            currentRoomNumber = "101",
            studentName = "Old Name",
            branch = "CSE",
            year = "1",
            isActive = true,
            createdAtMillis = 0,
            updatedAtMillis = 0,
        ),
    )

    @Test
    fun `valid spreadsheet with new, existing, and error rows is analyzed accurately`() {
        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>
                    <row r="1">
                        <c r="A1" t="inlineStr"><is><t>Roll No.</t></is></c>
                        <c r="B1" t="inlineStr"><is><t>Wing</t></is></c>
                        <c r="C1" t="inlineStr"><is><t>Room No.</t></is></c>
                        <c r="D1" t="inlineStr"><is><t>Student Name</t></is></c>
                        <c r="E1" t="inlineStr"><is><t>Branch</t></is></c>
                        <c r="F1" t="inlineStr"><is><t>Year</t></is></c>
                    </row>
                    <row r="2">
                        <c r="A2" t="inlineStr"><is><t>2402</t></is></c>
                        <c r="B2" t="inlineStr"><is><t>Wing A</t></is></c>
                        <c r="C2" t="inlineStr"><is><t>102</t></is></c>
                        <c r="D2" t="inlineStr"><is><t>New Student</t></is></c>
                        <c r="E2" t="inlineStr"><is><t>IT</t></is></c>
                        <c r="F2" t="inlineStr"><is><t>2nd Year</t></is></c>
                    </row>
                    <row r="3">
                        <c r="A3" t="inlineStr"><is><t>2401</t></is></c>
                        <c r="B3" t="inlineStr"><is><t>Wing B</t></is></c>
                        <c r="C3" t="inlineStr"><is><t>201</t></is></c>
                        <c r="D3" t="inlineStr"><is><t>Old Name Updated</t></is></c>
                        <c r="E3" t="inlineStr"><is><t>CSE</t></is></c>
                        <c r="F3" t="inlineStr"><is><t>3rd Year</t></is></c>
                    </row>
                    <row r="4">
                        <c r="A4" t="inlineStr"><is><t>2403</t></is></c>
                        <c r="B4" t="inlineStr"><is><t>Unknown Wing</t></is></c>
                        <c r="C4" t="inlineStr"><is><t>301</t></is></c>
                        <c r="D4" t="inlineStr"><is><t>Failed Student</t></is></c>
                        <c r="E4" t="inlineStr"><is><t></t></is></c>
                        <c r="F4" t="inlineStr"><is><t></t></is></c>
                    </row>
                </sheetData>
            </worksheet>
        """.trimIndent()

        val xlsxStream = createMockXlsx(sheetXml)
        val validator = ExcelStudentValidator()
        val analysis = validator.validate(xlsxStream, wings, existingStudents)

        assertEquals(1, analysis.validNewRows.size)
        assertEquals("2402", analysis.validNewRows[0].student.rollNumber)
        assertEquals("Wing A", analysis.validNewRows[0].targetWing.name)

        assertEquals(1, analysis.existingRows.size)
        assertEquals("2401", analysis.existingRows[0].student.rollNumber)
        assertEquals("Wing B", analysis.existingRows[0].targetWing.name)

        assertEquals(1, analysis.errorRows.size)
        assertTrue(analysis.errorRows[0].message.contains("Unknown wing"))
        assertEquals(4, analysis.errorRows[0].rowNumber)

        assertTrue(analysis.canCommit)
    }

    @Test
    fun `rejects invalid header columns with clear error`() {
        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>
                    <row r="1">
                        <c r="A1" t="inlineStr"><is><t>Random Column</t></is></c>
                        <c r="B1" t="inlineStr"><is><t>Other</t></is></c>
                    </row>
                </sheetData>
            </worksheet>
        """.trimIndent()

        val xlsxStream = createMockXlsx(sheetXml)
        val validator = ExcelStudentValidator()
        val analysis = validator.validate(xlsxStream, wings, existingStudents)

        assertFalse(analysis.canCommit)
        assertEquals(1, analysis.errorRows.size)
        assertTrue(analysis.errorRows[0].message.contains("Invalid template headers"))
    }

    @Test
    fun `validates 5-column template format from corrected-student-details correctly`() {
        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>
                    <row r="1">
                        <c r="A1" t="inlineStr"><is><t>S.No.</t></is></c>
                        <c r="B1" t="inlineStr"><is><t>Name</t></is></c>
                        <c r="C1" t="inlineStr"><is><t>Room Number</t></is></c>
                        <c r="D1" t="inlineStr"><is><t>Branch</t></is></c>
                        <c r="E1" t="inlineStr"><is><t>Year</t></is></c>
                    </row>
                    <row r="2">
                        <c r="A2" t="inlineStr"><is><t>1</t></is></c>
                        <c r="B2" t="inlineStr"><is><t>KRISH KUMAR SINGH</t></is></c>
                        <c r="C2" t="inlineStr"><is><t>GF/01/01</t></is></c>
                        <c r="D2" t="inlineStr"><is><t>MECH</t></is></c>
                        <c r="E2" t="inlineStr"><is><t>1ST</t></is></c>
                    </row>
                </sheetData>
            </worksheet>
        """.trimIndent()

        val xlsxStream = createMockXlsx(sheetXml)
        val validator = ExcelStudentValidator()
        val analysis = validator.validate(xlsxStream, wings, existingStudents)

        assertTrue(analysis.canCommit)
        assertEquals(1, analysis.validNewRows.size)
        assertEquals("1", analysis.validNewRows[0].student.rollNumber)
        assertEquals("KRISH KUMAR SINGH", analysis.validNewRows[0].student.studentName)
        assertEquals("GF/01/01", analysis.validNewRows[0].student.roomNumber)
        assertEquals("GF/01", analysis.validNewRows[0].targetWing.name)
        assertEquals(0, analysis.errorRows.size)
    }

    @Test
    fun `validates standard user template (SNo, ROOM NUMBER, STUDENT NAME, BRANCH, YEAR) correctly`() {
        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>
                    <row r="1">
                        <c r="A1" t="inlineStr"><is><t>S.No.</t></is></c>
                        <c r="B1" t="inlineStr"><is><t>ROOM NUMBER</t></is></c>
                        <c r="C1" t="inlineStr"><is><t>STUDENT NAME</t></is></c>
                        <c r="D1" t="inlineStr"><is><t>BRANCH</t></is></c>
                        <c r="E1" t="inlineStr"><is><t>YEAR</t></is></c>
                    </row>
                    <row r="2">
                        <c r="A2" t="inlineStr"><is><t>1</t></is></c>
                        <c r="B2" t="inlineStr"><is><t>GF/01/01</t></is></c>
                        <c r="C2" t="inlineStr"><is><t>KRISH KUMAR SINGH</t></is></c>
                        <c r="D2" t="inlineStr"><is><t>MECH</t></is></c>
                        <c r="E2" t="inlineStr"><is><t>1ST</t></is></c>
                    </row>
                </sheetData>
            </worksheet>
        """.trimIndent()

        val xlsxStream = createMockXlsx(sheetXml)
        val validator = ExcelStudentValidator()
        val analysis = validator.validate(xlsxStream, wings, existingStudents)

        assertTrue(analysis.canCommit)
        assertEquals(1, analysis.validNewRows.size)
        assertEquals("1", analysis.validNewRows[0].student.rollNumber)
        assertEquals("GF/01/01", analysis.validNewRows[0].student.roomNumber)
        assertEquals("KRISH KUMAR SINGH", analysis.validNewRows[0].student.studentName)
        assertEquals("MECH", analysis.validNewRows[0].student.branch)
        assertEquals("1ST", analysis.validNewRows[0].student.year)
        assertEquals("GF/01", analysis.validNewRows[0].targetWing.name)
        assertEquals(0, analysis.errorRows.size)
    }

    @Test
    fun `validates CSV format with standard user template correctly`() {
        val csvData = """
            S.No.,ROOM NUMBER,STUDENT NAME,BRANCH,YEAR
            1,GF/01/01,KRISH KUMAR SINGH,MECH,1ST
            2,FF/02/07,GOPAL BARIHA,CIVIL,1ST
        """.trimIndent()

        val csvStream = ByteArrayInputStream(csvData.toByteArray(Charsets.UTF_8))
        val validator = ExcelStudentValidator()
        val analysis = validator.validate(csvStream, wings, existingStudents)

        assertTrue(analysis.canCommit)
        assertEquals(2, analysis.validNewRows.size)
        assertEquals("1", analysis.validNewRows[0].student.rollNumber)
        assertEquals("GF/01/01", analysis.validNewRows[0].student.roomNumber)
        assertEquals("KRISH KUMAR SINGH", analysis.validNewRows[0].student.studentName)
        assertEquals("GF/01", analysis.validNewRows[0].targetWing.name)

        assertEquals("2", analysis.validNewRows[1].student.rollNumber)
        assertEquals("FF/02/07", analysis.validNewRows[1].student.roomNumber)
        assertEquals("GOPAL BARIHA", analysis.validNewRows[1].student.studentName)
        assertEquals("FF/02", analysis.validNewRows[1].targetWing.name)
        assertEquals(0, analysis.errorRows.size)
    }

    private fun createMockXlsx(sheetXml: String): ByteArrayInputStream {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(sheetXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return ByteArrayInputStream(baos.toByteArray())
    }
}
