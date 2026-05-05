package com.daliborpovolny.shiftwatcher

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM escalation_contacts ORDER BY priority ASC")
    fun getAllEscalationContacts(): Flow<List<EscalationContact>>

    @Query("SELECT * FROM escalation_contacts ORDER BY priority ASC")
    fun getAllEscalationContactsSync(): List<EscalationContact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEscalationContact(escalationContact: EscalationContact)

    @Delete
    suspend fun deleteEscalationContact(escalationContact: EscalationContact)

    @Update
    suspend fun updateEscalationContact(escalationContact: EscalationContact)


    @Query("SELECT * FROM info_contacts ORDER BY name ASC")
    fun getAllInfoContacts(): Flow<List<InfoContact>>

    @Query("SELECT * FROM info_contacts ORDER BY name ASC")
    fun getAllInfoContactsSync(): List<InfoContact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInfoContact(infoContact: InfoContact)

    @Delete
    suspend fun deleteInfoContact(infoContact: InfoContact)

    @Update
    suspend fun updateInfoContact(infoContact: InfoContact)

    @Query("SELECT value FROM user_settings WHERE `key` = :key")
    suspend fun getUserSetting(key: String): String?

    @Query("SELECT value FROM user_settings WHERE `key` = :key")
    fun getUserSettingFlow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserSetting(setting: UserSetting)
}