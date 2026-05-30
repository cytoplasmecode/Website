package com.cytoplasmecode.plantwatering.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface PlantDao {

    @Query("SELECT * FROM plants ORDER BY nextWateringMillis ASC")
    fun getAllPlants(): LiveData<List<Plant>>

    @Query("SELECT * FROM plants WHERE nextWateringMillis <= :nowMillis")
    suspend fun getDuePlants(nowMillis: Long): List<Plant>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plant: Plant): Long

    @Update
    suspend fun update(plant: Plant)

    @Delete
    suspend fun delete(plant: Plant)
}
