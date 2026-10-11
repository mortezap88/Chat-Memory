package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSettings
import com.example.data.ChatMessage
import com.example.ui.screens.AiResponsePageView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("StreamChat", appName)
  }

  @Test
  fun `test AiResponsePageView with response does not crash`() {
    composeTestRule.setContent {
      AiResponsePageView(
        message = ChatMessage(
          userText = "Hi",
          aiResponse = "Here is the response",
          isStreaming = false
        ),
        settings = AppSettings(messagingStreamlining = false),
        onRetry = {}
      )
    }
  }

  @Test
  fun `test AiResponsePageView while streaming does not crash`() {
    composeTestRule.setContent {
      AiResponsePageView(
        message = ChatMessage(
          userText = "Tell me a joke",
          aiResponse = "",
          isStreaming = true,
          modelUsed = "gemini-3.8-flash"
        ),
        settings = AppSettings(messagingStreamlining = false),
        onRetry = {}
      )
    }
  }

  @Test
  fun `test AiResponsePageView streaming with partial tokens does not crash`() {
    composeTestRule.setContent {
      AiResponsePageView(
        message = ChatMessage(
          userText = "Explain quantum computing",
          aiResponse = "Quantum computing is",
          isStreaming = true,
          modelUsed = "gemini-3.8-flash"
        ),
        settings = AppSettings(messagingStreamlining = false),
        onRetry = {}
      )
    }
  }

  @Test
  fun `test AiResponsePageView in streamlined mode does not crash`() {
    composeTestRule.setContent {
      AiResponsePageView(
        message = ChatMessage(
          userText = "Hello",
          aiResponse = "Hello there! How can I help you today?",
          isStreaming = false
        ),
        settings = AppSettings(messagingStreamlining = true),
        onRetry = {}
      )
    }
  }
}
