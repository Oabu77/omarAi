package com.darcloud.omarai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OmarDao {
    @Query("SELECT * FROM customers ORDER BY createdAtEpochMs DESC")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM leads ORDER BY createdAtEpochMs DESC")
    fun observeLeads(): Flow<List<LeadEntity>>

    @Query("SELECT * FROM jobs ORDER BY createdAtEpochMs DESC")
    fun observeJobs(): Flow<List<JobEntity>>

    @Query("SELECT * FROM invoices ORDER BY createdAtEpochMs DESC")
    fun observeInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM tasks ORDER BY updatedAtEpochMs DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAtEpochMs ASC")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTask(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(value: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(value: LeadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(value: JobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(value: InvoiceEntity)

    @Query("UPDATE leads SET status = :status WHERE id = :id")
    suspend fun updateLeadStatus(id: String, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(value: TaskEntity)

    @Update
    suspend fun updateTask(value: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditEvent(value: AuditEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(value: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutputReport(value: AiOutputReportEntity)

    @Query("SELECT * FROM customers ORDER BY createdAtEpochMs DESC")
    suspend fun allCustomers(): List<CustomerEntity>

    @Query("SELECT * FROM leads ORDER BY createdAtEpochMs DESC")
    suspend fun allLeads(): List<LeadEntity>

    @Query("SELECT * FROM jobs ORDER BY createdAtEpochMs DESC")
    suspend fun allJobs(): List<JobEntity>

    @Query("SELECT * FROM invoices ORDER BY createdAtEpochMs DESC")
    suspend fun allInvoices(): List<InvoiceEntity>

    @Query("SELECT * FROM tasks ORDER BY updatedAtEpochMs DESC")
    suspend fun allTasks(): List<TaskEntity>

    @Query("SELECT * FROM audit_events ORDER BY createdAtEpochMs DESC")
    suspend fun allAuditEvents(): List<AuditEventEntity>

    @Query("SELECT * FROM chat_messages ORDER BY createdAtEpochMs ASC")
    suspend fun allMessages(): List<ChatMessageEntity>

    @Query("SELECT * FROM ai_output_reports ORDER BY createdAtEpochMs DESC")
    suspend fun allOutputReports(): List<AiOutputReportEntity>
}
