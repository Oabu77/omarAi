package com.darcloud.omarai

import com.darcloud.omarai.data.api.ApiEnvelope
import com.darcloud.omarai.data.api.AccountDeletionData
import com.darcloud.omarai.data.api.AiOutputReportRequest
import com.darcloud.omarai.data.api.ApproveTaskRequest
import com.darcloud.omarai.data.api.BillingVerificationData
import com.darcloud.omarai.data.api.CancelTaskRequest
import com.darcloud.omarai.data.api.DeleteAccountRequest
import com.darcloud.omarai.data.api.PlanTaskData
import com.darcloud.omarai.data.api.PlanTaskRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiContractTest {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Test fun planRequestUsesBackendFieldNamesAndOmitsAbsentAttachments() {
        val json = moshi.adapter(PlanTaskRequest::class.java).toJson(
            PlanTaskRequest("Prepare a business plan", "en-US", "conversation-123", null),
        )
        assertTrue(json.contains("\"text\":\"Prepare a business plan\""))
        assertTrue(json.contains("\"locale\":\"en-US\""))
        assertTrue(json.contains("\"conversationId\":\"conversation-123\""))
        assertFalse(json.contains("requestText"))
        assertFalse(json.contains("clientRequestId"))
        assertFalse(json.contains("attachments"))
    }

    @Test fun explicitApprovalAndDeletionBodiesMatchBackend() {
        assertEquals("{\"approved\":true}", moshi.adapter(ApproveTaskRequest::class.java).toJson(ApproveTaskRequest()))
        assertEquals("{\"confirm\":true}", moshi.adapter(DeleteAccountRequest::class.java).toJson(DeleteAccountRequest()))
        assertEquals("{\"reason\":\"user_requested\"}", moshi.adapter(CancelTaskRequest::class.java).toJson(CancelTaskRequest()))
    }

    @Test fun moderationReportUsesPreparedStateAndTypedCategory() {
        val json = moshi.adapter(AiOutputReportRequest::class.java).toJson(
            AiOutputReportRequest(
                taskId = "task-1",
                provider = "android-user-report",
                output = "User flagged Android output for moderation.",
                category = "Incorrect",
            ),
        )
        assertTrue(json.contains("\"reportedState\":\"prepared\""))
        assertTrue(json.contains("\"category\":\"Incorrect\""))
        assertFalse(json.contains("\"reportedState\":\"completed\""))
    }

    @Test fun planEnvelopeAndReceiptDeserialize() {
        val type = Types.newParameterizedType(ApiEnvelope::class.java, PlanTaskData::class.java)
        val adapter = moshi.adapter<ApiEnvelope<PlanTaskData>>(type)
        val value = adapter.fromJson(
            """{
              "ok":true,
              "data":{
                "conversationId":"c1","messageId":"m1","reply":"Draft prepared; nothing sent.",
                "plan":{"title":"Plan","intent":"business","steps":[{"ordinal":0,"agent":"Business Agent","action":"Prepare draft","requiresApproval":false,"externalAction":false}]},
                "taskReceipt":{"taskId":"t1","status":"planned","verificationState":"not_executed","approvalRequired":false,"cancellable":true,"createdAt":"2026-08-30T00:00:00Z","providerEvidence":{"state":"PROVIDER_VERIFIED","provider":"workers-ai","referenceId":null}},
                "provider":{"name":"workers-ai","model":"model","inferenceVerified":true,"externalActionsExecuted":false}
              },
              "requestId":"r1"
            }""".trimIndent(),
        )!!
        assertTrue(value.ok)
        assertEquals("r1", value.requestId)
        assertEquals("t1", value.data!!.taskReceipt.taskId)
        assertFalse(value.data!!.provider.externalActionsExecuted)
    }

    @Test fun billingEnvelopeRequiresNestedEntitlementAndEvidence() {
        val type = Types.newParameterizedType(ApiEnvelope::class.java, BillingVerificationData::class.java)
        val adapter = moshi.adapter<ApiEnvelope<BillingVerificationData>>(type)
        val value = adapter.fromJson(
            """{"ok":true,"data":{"entitlement":{"key":"pro","state":"active","productId":"omar_ai_pro","provider":"google-play","verifiedAt":"2026-08-30T00:00:00Z","expiresAt":null,"mode":"test","grantsAccess":true},"providerEvidence":{"state":"PROVIDER_VERIFIED","provider":"google-play","referenceId":"order-1"}},"requestId":"r2"}""",
        )!!
        assertEquals("pro", value.data!!.entitlement.key)
        assertTrue(value.data!!.entitlement.grantsAccess)
        assertEquals("order-1", value.data!!.providerEvidence.referenceId)
    }

    @Test fun accountDeletionEnvelopeUsesDeletionId() {
        val type = Types.newParameterizedType(ApiEnvelope::class.java, AccountDeletionData::class.java)
        val adapter = moshi.adapter<ApiEnvelope<AccountDeletionData>>(type)
        val value = adapter.fromJson(
            """{"ok":true,"data":{"deletionId":"delete-1","status":"COMPLETED","identityProviderAccount":"not_configured","applicationData":"deleted","completedAt":"2026-08-30T00:00:00Z"},"requestId":"request-1"}""",
        )!!
        assertEquals("delete-1", value.data!!.deletionId)
        assertEquals("request-1", value.requestId)
    }

    @Test fun attachmentsSendMetadataOnlyAndNeverAUriOrRawBytesField() {
        val json = moshi.adapter(PlanTaskRequest::class.java).toJson(
            PlanTaskRequest(
                text = "Prepare a local estimate",
                locale = "en-US",
                conversationId = "conversation-9",
                attachments = listOf(
                    com.darcloud.omarai.data.api.AttachmentMetadata(
                        name = "demo.jpg",
                        mimeType = "image/jpeg",
                        source = "PHOTO_PICKER",
                    ),
                ),
            ),
        )
        assertTrue(json.contains("\"name\":\"demo.jpg\""))
        assertTrue(json.contains("\"mimeType\":\"image/jpeg\""))
        assertTrue(json.contains("\"source\":\"PHOTO_PICKER\""))
        assertFalse(json.contains("content://"))
        assertFalse(json.contains("file://"))
        assertFalse(json.contains("base64"))
        assertFalse(json.contains("bytes"))
    }
}
