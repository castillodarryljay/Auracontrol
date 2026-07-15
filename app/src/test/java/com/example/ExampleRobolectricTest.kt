package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.service.GestureService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AuraControl", appName)
  }

  @Test
  fun `test gesture service creation`() {
    val controller = Robolectric.buildService(GestureService::class.java)
    val service = controller.create().startCommand(0, 0).get()
    assertEquals(true, GestureService.isServiceRunning)
    controller.destroy()
  }
}
