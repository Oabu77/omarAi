package com.darcloud.omarai

import com.darcloud.omarai.data.local.IntentRouter
import com.darcloud.omarai.data.local.ProductCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRouterTest {
    @Test fun liveCallRequestsAreExplicitlyDeferred() {
        val route = IntentRouter.route("Answer my calls and transfer a customer", hasPhoto = false)
        assertFalse(route.supportedInV1)
        assertEquals(ProductCapability.COMING_LATER, route.capability)
        assertEquals("Receptionist Agent", route.agent)
    }

    @Test fun photoRoutesToEstimatingWithSupportedDisclaimerFlow() {
        val route = IntentRouter.route("What is in this image?", hasPhoto = true)
        assertTrue(route.supportedInV1)
        assertEquals(ProductCapability.ESTIMATING, route.capability)
    }

    @Test fun companyPlanWinsOverGenericBusinessRoute() {
        val route = IntentRouter.route("Create a business plan for a landscaping company", false)
        assertEquals(ProductCapability.COMPANY_BUILDER, route.capability)
    }

    @Test fun everyLiveIntegrationCategoryFailsClosedAsComingLater() {
        val cases = listOf(
            "Please answer my call" to "Receptionist Agent",
            "Connect my bank account" to "Finance Agent",
            "Send a message to the group" to "Communication Agent",
            "Find me a plumber nearby" to "Marketplace Agent",
        )
        cases.forEach { (request, expectedAgent) ->
            val route = IntentRouter.route(request, hasPhoto = false)
            assertEquals(expectedAgent, route.agent)
            assertEquals(ProductCapability.COMING_LATER, route.capability)
            assertFalse(route.supportedInV1)
            assertTrue(route.explanation.contains("no external action was taken"))
        }
    }

    @Test fun unavailableExternalActionWinsOverPhotoAndBusinessKeywords() {
        val route = IntentRouter.route(
            "Use this photo to find me a plumber for my business",
            hasPhoto = true,
        )
        assertEquals("Marketplace Agent", route.agent)
        assertEquals(ProductCapability.COMING_LATER, route.capability)
        assertFalse(route.supportedInV1)
    }

    @Test fun supportedRoutesRemainDeterministicAndPlanningOnly() {
        val cases = listOf(
            Triple("Prepare a materials estimate", false, ProductCapability.ESTIMATING),
            Triple("Start a company for landscaping", false, ProductCapability.COMPANY_BUILDER),
            Triple("Organize this customer lead", false, ProductCapability.BUSINESS),
            Triple("Help me outline tomorrow", false, ProductCapability.GENERAL_ASSISTANT),
        )
        cases.forEach { (request, hasPhoto, expectedCapability) ->
            val first = IntentRouter.route(request, hasPhoto)
            val second = IntentRouter.route(request, hasPhoto)
            assertEquals(first, second)
            assertEquals(expectedCapability, first.capability)
            assertTrue(first.supportedInV1)
        }
    }
}
