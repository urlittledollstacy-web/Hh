package app.hikari.media

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.hikari.core.model.MediaCharacter
import app.hikari.core.model.MediaStaff
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaCreditSectionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun castSection_rendersCharactersWithDuplicateIds() {
        val characters = listOf(
            MediaCharacter(7, "First Character", null, "MAIN", null, null),
            MediaCharacter(7, "Second Character", null, "SUPPORTING", null, null),
        )

        composeRule.setContent {
            MaterialTheme {
                CastSection(characters, onOpenCredit = {})
            }
        }

        composeRule.onNodeWithText("First Character").assertIsDisplayed()
        composeRule.onNodeWithText("Second Character").assertIsDisplayed()
    }

    @Test
    fun staffSection_rendersStaffWithDuplicateIds() {
        val staff = listOf(
            MediaStaff(11, "First Staff Member", null, listOf("Director")),
            MediaStaff(11, "Second Staff Member", null, listOf("Producer")),
        )

        composeRule.setContent {
            MaterialTheme {
                StaffSection(staff, onOpenCredit = {})
            }
        }

        composeRule.onNodeWithText("First Staff Member").assertIsDisplayed()
        composeRule.onNodeWithText("Second Staff Member").assertIsDisplayed()
    }
}
