package org.oppia.app.parser

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.text.Spannable
import android.text.style.LeadingMarginSpan
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.google.common.truth.Truth.assertThat
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.oppia.app.R
import org.oppia.app.testing.HtmlParserTestActivity
import org.oppia.util.caching.CacheAssetsLocally
import org.oppia.util.gcsresource.DefaultResourceBucketName
import org.oppia.util.logging.EnableConsoleLog
import org.oppia.util.logging.EnableFileLog
import org.oppia.util.logging.GlobalLogLevel
import org.oppia.util.logging.LogLevel
import org.oppia.util.parser.BulletSpanWithRadius
import org.oppia.util.parser.DefaultGcsPrefix
import org.oppia.util.parser.GlideImageLoader
import org.oppia.util.parser.HtmlParser
import org.oppia.util.parser.ImageDownloadUrlTemplate
import org.oppia.util.parser.ImageLoader
import org.oppia.util.threading.BackgroundDispatcher
import org.oppia.util.threading.BlockingDispatcher
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

// TODO(#277): Add tests for UrlImageParser.
/** Tests for [HtmlParser]. */
@RunWith(AndroidJUnit4::class)
class HtmlParserTest {

  private val BULLET_RADIUS: Int = ApplicationProvider.getApplicationContext<Context>()
    .resources.getDimensionPixelSize(org.oppia.util.R.dimen.bullet_radius)
  private val GAP_WIDTH: Int = ApplicationProvider.getApplicationContext<Context>()
    .resources.getDimensionPixelSize(org.oppia.util.R.dimen.bullet_gap_width)
  private lateinit var launchedActivity: Activity
  @Inject
  lateinit var htmlParserFactory: HtmlParser.Factory
  @Inject
  @field:DefaultResourceBucketName
  lateinit var resourceBucketName: String

  @get:Rule
  var activityTestRule: ActivityTestRule<HtmlParserTestActivity> = ActivityTestRule(
    HtmlParserTestActivity::class.java, /* initialTouchMode= */ true, /* launchActivity= */ false
  )

  @Inject
  @field:TestDispatcher
  lateinit var testDispatcher: CoroutineDispatcher

  @Before
  fun setUp() {
    setUpTestApplicationComponent()
    Intents.init()
    val intent = Intent(Intent.ACTION_PICK)
    launchedActivity = activityTestRule.launchActivity(intent)
    DaggerHtmlParserTest_TestApplicationComponent.builder()
      .setApplication(ApplicationProvider.getApplicationContext())
      .build()
      .inject(this)
  }

  @After
  fun tearDown() {
    Intents.release()
  }

  private fun setUpTestApplicationComponent() {
    DaggerHtmlParserTest_TestApplicationComponent.builder()
      .setApplication(ApplicationProvider.getApplicationContext())
      .build()
      .inject(this)
  }

  @Test
  fun testHtmlContent_handleCustomOppiaTags_parsedHtmlDisplaysStyledText() {
    val textView =
      activityTestRule.activity.findViewById(R.id.test_html_content_text_view) as TextView
    val htmlParser = htmlParserFactory.create(
      resourceBucketName,
      /* entityType= */ "",
      /* entityId= */ "",
      /* imageCenterAlign= */ true
    )
    val htmlResult: Spannable = htmlParser.parseOppiaHtml(
      "\u003cp\u003e\"Let's try one last question,\" said Mr. Baker. \"Here's a pineapple cake cut into pieces.\"\u003c/p\u003e\u003coppia-noninteractive-image " +
        "alt-with-value=\"\u0026amp;quot;Pineapple" +
        " cake with 7/9 having cherries.\u0026amp;quot;\" caption-with-value=\"\u0026amp;quot;\u0026amp;quot;\" filepath-with-value=\"\u0026amp;quot;" +
        "pineapple_cake_height_479_width_480.png\u0026amp;quot;\"\u003e\u003c/" +
        "oppia-noninteractive-image\u003e\u003cp\u003e\u00a0\u003c/p\u003e\u003cp\u003e\u003cstrong\u003eQuestion 6\u003c/strong\u003e: What " +
        "fraction of the cake has big red cherries in the pineapple slices?\u003c/p\u003e",
      textView
    )
    assertThat(textView.text.toString()).isEqualTo(htmlResult.toString())
    onView(withId(R.id.test_html_content_text_view)).check(matches(isDisplayed()))
    onView(withId(R.id.test_html_content_text_view)).check(matches(withText(textView.text.toString())))
  }

  @Test
  fun testHtmlContent_nonCustomOppiaTags_notParsed() {
    val textView =
      activityTestRule.activity.findViewById(R.id.test_html_content_text_view) as TextView
    val htmlParser = htmlParserFactory.create(
      resourceBucketName,
      /* entityType= */ "",
      /* entityId= */ "",
      /* imageCenterAlign= */ true
    )
    val htmlResult: Spannable = htmlParser.parseOppiaHtml(
      "\u003cp\u003e\"Let's try one last question,\" said Mr. Baker. \"Here's a pineapple cake cut into pieces.\"\u003c/p\u003e\u003coppia--image " +
        "alt-with-value=\"\u0026amp;quot;Pineapple cake with 7/9 having cherries.\u0026amp;quot;\" caption-with-value=\"\u0026amp;quot;\u0026amp;quot;\"" +
        " filepath-value=\"\u0026amp;quot;pineapple_cake_height_479_width_480.png\u0026amp;quot;\"\u003e\u003c/oppia-noninteractive-image" +
        "\u003e\u003cp\u003e\u00a0\u003c/p\u003e\u003cp\u003e\u003cstrongQuestion 6\u003c/strong\u003e: What fraction of the cake has big " +
        "red cherries in the pineapple slices?\u003c/p\u003e",
      textView
    )
    // The two strings aren't equal because this HTML contains a Non-Oppia/Non-Html tag e.g. <image> tag and attributes "filepath-value" which isn't parsed.
    assertThat(textView.text.toString()).isNotEqualTo(htmlResult.toString())
    onView(withId(R.id.test_html_content_text_view)).check(matches(not(textView.text.toString())))
  }

  @Test
  fun testHtmlContent_doesNotStartWithOLorULTag_parsedHtmlDisplaysRegularText() {
    val textView =
      activityTestRule.activity.findViewById(R.id.test_html_content_text_view) as TextView
    val source = "<html>CITRUS FRUITS:<li>LEMON</li><li>LIME</li><li>ORANGE</li></html>"
    val htmlParser = htmlParserFactory.create(
      resourceBucketName,
      /* entityType= */ "",
      /* entityId= */ "",
      /* imageCenterAlign= */ true
    )
    val formattedHtml = htmlParser.parseOppiaHtml(source, textView).toString()

    assertThat(
      formattedHtml
    ).isEqualTo(
      "CITRUS FRUITS:LEMONLIMEORANGE"
    )
  }

  @Test
  fun testHtmlContent_handleNestedList_parsedHtmlDisplaysNestedList() {
    val textView =
      activityTestRule.activity.findViewById(R.id.test_html_content_with_ordered_list_text_view) as TextView
    val htmlParser = htmlParserFactory.create(
      resourceBucketName,
      /* entityType= */ "",
      /* entityId= */ "",
      /* imageCenterAlign= */ true
    )
    val htmlResult: Spannable = htmlParser.parseOppiaHtml(
      """<ul>
                <li>Item 1</li>
                <li>Item 2</li>
                <li>Item 3
                    <ol>
                        <li>Nested item 1</li>
                        <li>Lorem ipsum dolor sit amet, consectetur adipiscing elit. 
                        Nulla et tellus eu magna facilisis eleifend. Vestibulum faucibus pulvinar tincidunt. 
                        Nullam non mauris nisi.</li>
                    </ol>
                </li>
                <li>Item 4</li>
                <li>Item 5
                    <ul>
                        <li>Nested item 1</li>
                        <li>Nested item 2
                            <ol>
                                <li>Double nested item 1</li>
                                <li>Double nested item 2</li>
                            </ol>
                        </li>
                        <li>Nested item 3</li>
                    </ul>
                </li>
                <li>Item 6</li>
            </ul>
        """,
      textView
    )
    assertThat(textView.text.toString()).isEqualTo(htmlResult.toString())
    onView(withId(R.id.test_html_content_with_ordered_list_text_view)).check(matches(isDisplayed()))
    onView(withId(R.id.test_html_content_with_ordered_list_text_view)).check(
      matches(
        withText(
          textView.text.toString()
        )
      )
    )
  }

  @Test
  fun testHtmlContent_bulletSpanWithRadius_isAdded() {
    val textView =
      activityTestRule.activity.findViewById(R.id.test_html_content_text_view) as TextView
    val htmlParser = htmlParserFactory.create(
      resourceBucketName,
      /* entityType= */ "",
      /* entityId= */ "",
      /* imageCenterAlign= */ true
    )
    val htmlResult: Spannable = htmlParser.parseOppiaHtml(
      "<p>You should know the following before going on:<br></p>" +
        "<ul><li>The counting numbers (1, 2, 3, 4, 5 ….)<br></li>" +
        "<li>How to tell whether one counting number is bigger or smaller than another<br></li></ul>",
      textView
    )

    /* Reference: https://medium.com/androiddevelopers/spantastic-text-styling-with-spans-17b0c16b4568#e345 */
    val bulletSpans = htmlResult.getSpans<BulletSpanWithRadius>(
      0,
      htmlResult.length,
      BulletSpanWithRadius::class.java
    )
    assertThat(bulletSpans.size.toLong()).isEqualTo(2)

    val bulletSpan0 = bulletSpans[0] as BulletSpanWithRadius
    assertThat(bulletSpan0).isNotNull()

    val bulletSpan1 = bulletSpans[1] as BulletSpanWithRadius
    assertThat(bulletSpan1).isNotNull()

    assertThat(htmlResult.getSpanEnd(bulletSpan0)).isEqualTo(htmlResult.getSpanStart(bulletSpan1))
  }

  @Test
  fun testHtmlContent_numberedLeadingMarginSpan_isAdded() {
    val textView =
      activityTestRule.activity.findViewById(R.id.test_html_content_text_view) as TextView
    val htmlParser = htmlParserFactory.create(
      resourceBucketName,
      /* entityType= */ "",
      /* entityId= */ "",
      /* imageCenterAlign= */ true
    )
    val htmlResult: Spannable = htmlParser.parseOppiaHtml(
      "<p>You should know the following before going on:<br></p>" +
        "<ol><li>The counting numbers (1, 2, 3, 4, 5 ….)<br></li>" +
        "<li>How to tell whether one counting number is bigger or smaller than another<br></li></ol>",
      textView
    )

    /* Reference: https://medium.com/androiddevelopers/spantastic-text-styling-with-spans-17b0c16b4568#e345 */
    val bulletSpans = htmlResult.getSpans<LeadingMarginSpan.Standard>(
      0,
      htmlResult.length,
      LeadingMarginSpan.Standard::class.java
    )
    assertThat(bulletSpans.size.toLong()).isEqualTo(2)

    val bulletSpan0 = bulletSpans[0] as LeadingMarginSpan.Standard
    assertThat(bulletSpan0).isNotNull()

    val bulletSpan1 = bulletSpans[1] as LeadingMarginSpan.Standard
    assertThat(bulletSpan1).isNotNull()

    assertThat(htmlResult.getSpanEnd(bulletSpan0)).isEqualTo(htmlResult.getSpanStart(bulletSpan1))
  }

  @Test
  fun testHtmlContent_getLeadingMargin_isCorrect() {
    // Given a span with a certain gap width
    val span = BulletSpanWithRadius(ApplicationProvider.getApplicationContext<Context>(), GAP_WIDTH)

    // Check that the margin is set correctly
    val expectedMargin = (2 * BULLET_RADIUS + GAP_WIDTH)
    assertThat(expectedMargin.toLong()).isEqualTo(span.getLeadingMargin(true).toLong())
  }

  @Qualifier
  annotation class TestDispatcher

  // TODO(#89): Move this to a common test application component.
  @Module
  class TestModule {
    @Provides
    @Singleton
    fun provideContext(application: Application): Context {
      return application
    }

    @ExperimentalCoroutinesApi
    @Singleton
    @Provides
    @TestDispatcher
    fun provideTestDispatcher(): CoroutineDispatcher {
      return TestCoroutineDispatcher()
    }

    @Singleton
    @Provides
    @BackgroundDispatcher
    fun provideBackgroundDispatcher(@TestDispatcher testDispatcher: CoroutineDispatcher): CoroutineDispatcher {
      return testDispatcher
    }

    @Singleton
    @Provides
    @BlockingDispatcher
    fun provideBlockingDispatcher(@TestDispatcher testDispatcher: CoroutineDispatcher): CoroutineDispatcher {
      return testDispatcher
    }

    @Provides
    @CacheAssetsLocally
    fun provideCacheAssetsLocally(): Boolean = false

    @Provides
    @DefaultGcsPrefix
    @Singleton
    fun provideDefaultGcsPrefix(): String {
      return "https://storage.googleapis.com"
    }

    @Provides
    @DefaultResourceBucketName
    @Singleton
    fun provideDefaultGcsResource(): String {
      return "oppiaserver-resources"
    }

    @Provides
    @ImageDownloadUrlTemplate
    @Singleton
    fun provideImageDownloadUrlTemplate(): String {
      return "%s/%s/assets/image/%s"
    }

    // TODO(#59): Either isolate these to their own shared test module, or use the real logging
    // module in tests to avoid needing to specify these settings for tests.
    @EnableConsoleLog
    @Provides
    fun provideEnableConsoleLog(): Boolean = true

    @EnableFileLog
    @Provides
    fun provideEnableFileLog(): Boolean = false

    @GlobalLogLevel
    @Provides
    fun provideGlobalLogLevel(): LogLevel = LogLevel.VERBOSE
  }

  @Module
  abstract class ImageTestModule {
    @Binds
    abstract fun provideGlideImageLoader(impl: GlideImageLoader): ImageLoader
  }

  @Singleton
  @Component(modules = [TestModule::class, ImageTestModule::class])
  interface TestApplicationComponent {
    @Component.Builder
    interface Builder {
      @BindsInstance
      fun setApplication(application: Application): Builder

      fun build(): TestApplicationComponent
    }

    fun inject(htmlParserTest: HtmlParserTest)
  }
}
