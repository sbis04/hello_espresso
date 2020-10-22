package com.souvikbiswas.helloespresso

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class ChangeTextBehaviorKtTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun changeText_sameActivity() {
        Espresso.onView(withId(R.id.editTextUserInput))
            .perform(ViewActions.typeText(STRING_TO_BE_TYPED), ViewActions.closeSoftKeyboard())

        Espresso.onView(withId(R.id.changeTextBt)).perform(ViewActions.click())

        Espresso.onView(withId(R.id.textToBeChanged))
            .check(ViewAssertions.matches(ViewMatchers.withText(STRING_TO_BE_TYPED)))
    }

    @Test
    fun changeText_newActivity() {
        Espresso.onView(withId(R.id.editTextUserInput)).perform(
            ViewActions.typeText(STRING_TO_BE_TYPED),
            ViewActions.closeSoftKeyboard()
        )
        Espresso.onView(withId(R.id.activityChangeTextBtn)).perform(ViewActions.click())


        Espresso.onView(withId(R.id.show_text_view))
            .check(ViewAssertions.matches(ViewMatchers.withText(STRING_TO_BE_TYPED)))
    }

    companion object {
        val STRING_TO_BE_TYPED = "Espresso"
    }
}