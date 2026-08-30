package com.darcloud.omarai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.darcloud.omarai.ui.OmarTheme
import com.darcloud.omarai.ui.OnboardingScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingTruthUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fourPagesDiscloseLocalScopeBeforeOpeningTheApp() {
        var finished = false
        compose.setContent {
            OmarTheme {
                OnboardingScreen(onFinish = { finished = true })
            }
        }

        compose.onNodeWithText("Meet Omar AI").assertIsDisplayed()
        compose.onNodeWithText("Local plans and business records.").assertIsDisplayed()
        assertFalse(finished)

        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("What matters to you?").assertIsDisplayed()
        compose.onNodeWithText(
            "Optional preview only. These choices are not saved and do not enable unavailable integrations.",
        ).assertIsDisplayed()

        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Connect only what you need").assertIsDisplayed()
        compose.onNodeWithText(
            "Live phone calls, financial accounts, user messaging, and provider marketplace actions are not included in this v1.",
        ).assertIsDisplayed()

        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("You stay in control").assertIsDisplayed()
        compose.onNodeWithText("Camera").assertIsDisplayed()
        compose.onNodeWithText("Photos and files").assertIsDisplayed()
        assertFalse(finished)

        compose.onNodeWithText("Open Omar AI").performClick()
        compose.runOnIdle { assertTrue(finished) }
    }
}
