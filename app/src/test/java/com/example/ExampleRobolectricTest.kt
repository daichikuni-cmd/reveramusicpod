package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ReveraMusicPod", appName)
  }

  @Test
  fun `music launcher view model tab switching and home intent`() {
    val application = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.MusicLauncherViewModel(application)

    // Initial tab is PLAY
    assertEquals(com.example.ui.MusicTab.PLAY, viewModel.currentTab.value)

    // Switch to STORAGE
    viewModel.selectTab(com.example.ui.MusicTab.STORAGE)
    assertEquals(com.example.ui.MusicTab.STORAGE, viewModel.currentTab.value)

    // Home intent while resumed resets to PLAY
    viewModel.onHomeIntentReceived(isActivityResumed = true)
    assertEquals(com.example.ui.MusicTab.PLAY, viewModel.currentTab.value)
  }
}
