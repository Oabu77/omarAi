package com.darcloud.omarai.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface OmarApi {
    @GET("v1/health")
    suspend fun health(): ApiEnvelope<HealthData>

    @POST("v1/tasks/plan")
    suspend fun planTask(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: PlanTaskRequest,
    ): ApiEnvelope<PlanTaskData>

    @GET("v1/tasks/{id}")
    suspend fun getTask(@Path("id") id: String): ApiEnvelope<TaskDetailsData>

    @POST("v1/tasks/{id}/approve")
    suspend fun approveTask(
        @Path("id") id: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: ApproveTaskRequest,
    ): ApiEnvelope<ApproveTaskData>

    @POST("v1/tasks/{id}/cancel")
    suspend fun cancelTask(
        @Path("id") id: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CancelTaskRequest,
    ): ApiEnvelope<CancelTaskData>

    @POST("v1/reports/ai-output")
    suspend fun reportAiOutput(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: AiOutputReportRequest,
    ): ApiEnvelope<AiOutputReportData>

    @POST("v1/billing/google-play/verify")
    suspend fun verifyGooglePlayPurchase(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: BillingVerificationRequest,
    ): ApiEnvelope<BillingVerificationData>

    @GET("v1/integrations")
    suspend fun integrations(): ApiEnvelope<IntegrationListData>

    @HTTP(method = "DELETE", path = "v1/account", hasBody = true)
    suspend fun deleteAccount(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Header("X-Deletion-Confirmation") deletionConfirmation: String,
        @Body request: DeleteAccountRequest,
    ): ApiEnvelope<AccountDeletionData>
}
