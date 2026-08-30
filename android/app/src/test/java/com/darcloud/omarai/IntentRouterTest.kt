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
}
