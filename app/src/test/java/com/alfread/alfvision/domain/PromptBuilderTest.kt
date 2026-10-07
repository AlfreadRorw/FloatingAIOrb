package com.alfread.alfvision.domain

import com.alfread.alfvision.core.ResponseStyle
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    @Test fun stepStyleIsExplicit() {
        assertTrue(PromptBuilder.styleInstruction(ResponseStyle.STEP_BY_STEP).contains("step-by-step"))
    }

    @Test fun quickActionFindErrorIsUseful() {
        assertTrue(PromptBuilder.quickAction("Find Error").contains("errors", ignoreCase = true))
    }
}
