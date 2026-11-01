package com.hostelattendance.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WingDao {
    @Query(
        """
        SELECT * FROM wings
        WHERE hostelId = :hostelId AND isActive = 1
        ORDER BY name
        """,
    )
    fun observeActiveWings(hostelId: String): Flow<List<WingEntity>>

    @Query(
        """
        SELECT * FROM wings
        WHERE hostelId = :hostelId AND isActive = 1
        ORDER BY name
        """,
    )
    suspend fun getAllActiveWings(hostelId: String): List<WingEntity>

    @Query(
        """
        SELECT * FROM wings
        WHERE hostelId = :hostelId AND normalizedName = :normalizedName
        LIMIT 1
        """,
    )
    suspend fun findByNormalizedName(hostelId: String, normalizedName: String): WingEntity?

    @Query("SELECT * FROM wings WHERE wingId = :wingId AND hostelId = :hostelId AND isActive = 1 LIMIT 1")
    suspend fun findActiveWing(hostelId: String, wingId: String): WingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wing: WingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWings(wings: List<WingEntity>)

    @Query("SELECT * FROM wings")
    suspend fun getAllWings(): List<WingEntity>

    @Query("SELECT COUNT(*) FROM wings")
    suspend fun getWingCount(): Int
}
