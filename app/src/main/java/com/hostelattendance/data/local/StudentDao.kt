package com.hostelattendance.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query(
        """
        SELECT COUNT(*) FROM students
        INNER JOIN wings ON wings.wingId = students.currentWingId
        WHERE students.hostelId = :hostelId AND students.currentWingId = :wingId
          AND students.isActive = 1 AND wings.isActive = 1
        """,
    )
    fun observeActiveStudentCount(hostelId: String, wingId: String): Flow<Int>

    @Query(
        """
        SELECT * FROM students
        WHERE hostelId = :hostelId AND currentWingId = :wingId AND isActive = 1
        ORDER BY normalizedRollNumber
        """,
    )
    fun observeActiveStudents(hostelId: String, wingId: String): Flow<List<StudentEntity>>

    @Query(
        """
        SELECT * FROM students
        WHERE hostelId = :hostelId
        ORDER BY isActive DESC, normalizedRollNumber ASC
        """,
    )
    fun observeAllStudents(hostelId: String): Flow<List<StudentEntity>>

    @Query(
        """
        SELECT * FROM students
        WHERE hostelId = :hostelId
        ORDER BY isActive DESC, normalizedRollNumber ASC
        """,
    )
    suspend fun getAllStudents(hostelId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE studentId = :studentId LIMIT 1")
    suspend fun findById(studentId: String): StudentEntity?

    @Query(
        """
        SELECT * FROM students
        WHERE hostelId = :hostelId AND normalizedRollNumber = :normalizedRollNumber
        LIMIT 1
        """,
    )
    suspend fun findByNormalizedRollNumber(
        hostelId: String,
        normalizedRollNumber: String,
    ): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(student: StudentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<StudentEntity>)

    @Query(
        """
        UPDATE students
        SET currentRoomNumber = :roomNumber, currentWingId = :wingId, branch = :branch, year = :year, updatedAtMillis = :updatedAtMillis
        WHERE studentId = :studentId
        """,
    )
    suspend fun updateStudent(
        studentId: String,
        roomNumber: String,
        wingId: String,
        branch: String?,
        year: String?,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE students
        SET studentName = :studentName, currentRoomNumber = :roomNumber, currentWingId = :wingId, branch = :branch, year = :year, updatedAtMillis = :updatedAtMillis
        WHERE studentId = :studentId
        """,
    )
    suspend fun updateStudentWithFullName(
        studentId: String,
        studentName: String,
        roomNumber: String,
        wingId: String,
        branch: String?,
        year: String?,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE students
        SET isActive = :isActive, updatedAtMillis = :updatedAtMillis
        WHERE studentId = :studentId
        """,
    )
    suspend fun setStudentActive(
        studentId: String,
        isActive: Boolean,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        SELECT students.*, wings.name AS currentWingName
        FROM students
        INNER JOIN wings ON wings.wingId = students.currentWingId
        WHERE students.hostelId = :hostelId
          AND students.currentWingId = :wingId
          AND students.isActive = 1
          AND wings.isActive = 1
        ORDER BY students.normalizedRollNumber
        """,
    )
    suspend fun findActiveStudentsWithWingName(
        hostelId: String,
        wingId: String,
    ): List<StudentWithWingName>
}

data class StudentWithWingName(
    @androidx.room.Embedded val student: StudentEntity,
    val currentWingName: String,
)
