package com.lumi.data.chat

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lumi.core.model.MessageRole
import com.lumi.data.local.LumiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the two rules that made "history doesn't work" a bug rather than a missing feature: a chat row
 * appears only when there is a message in it, and its title comes from that message.
 */
@RunWith(AndroidJUnit4::class)
class ChatRepositoryTest {

    private lateinit var database: LumiDatabase
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LumiDatabase::class.java,
        ).build()
        repository = ChatRepository(database.chatDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun openingTheAppWithoutSendingAnythingLeavesNoEmptyConversation() = runTest {
        // The whole reason chats are created lazily: an eager insert filled the drawer with blanks
        // every time the app was opened and closed.
        assertEquals(emptyList<Long>(), repository.observeChats().first().map { it.id })
    }

    @Test
    fun theFirstMessageCreatesTheConversationAndTitlesItFromThatMessage() = runTest {
        val chatId = repository.appendUserMessage(null, "how do I mass rename files", 1_000L)

        val chats = repository.observeChats().first()
        assertEquals(1, chats.size)
        assertEquals(chatId, chats.single().id)
        assertEquals("how do I mass rename files", chats.single().title)
    }

    @Test
    fun aLongFirstMessageIsCutOnAWordBoundaryRatherThanMidWord() = runTest {
        val long = "explain in detail how the linux kernel schedules processes across multiple cores"
        repository.appendUserMessage(null, long, 1_000L)

        val title = repository.observeChats().first().single().title
        assertTrue("should be shortened", title.length < long.length)
        assertTrue("should be marked as cut", title.endsWith("…"))
        // The character before the ellipsis must not be a space, and the cut must not split a word:
        // every word kept has to be a whole word from the original.
        val kept = title.removeSuffix("…").split(" ")
        assertTrue(kept.all { it in long.split(" ") })
    }

    @Test
    fun bothSidesOfAnExchangeArePersistedInOrder() = runTest {
        val chatId = repository.appendUserMessage(null, "hi", 1_000L)
        repository.appendModelMessage(chatId, "Hello. How can I help?", 2_000L)

        val messages = repository.observeMessages(chatId).first()
        assertEquals(listOf(MessageRole.USER, MessageRole.MODEL), messages.map { it.role })
        assertEquals(listOf("hi", "Hello. How can I help?"), messages.map { it.text })
    }

    @Test
    fun continuingAConversationAppendsToItRatherThanStartingAnother() = runTest {
        val chatId = repository.appendUserMessage(null, "first", 1_000L)
        repository.appendUserMessage(chatId, "second", 2_000L)

        assertEquals(1, repository.observeChats().first().size)
        assertEquals(2, repository.observeMessages(chatId).first().size)
    }

    @Test
    fun deletingAConversationTakesItsMessagesWithIt() = runTest {
        val chatId = repository.appendUserMessage(null, "throwaway", 1_000L)
        repository.appendModelMessage(chatId, "reply", 2_000L)

        repository.deleteChat(chatId)

        assertEquals(emptyList<Long>(), repository.observeChats().first().map { it.id })
        assertEquals(0, repository.observeMessages(chatId).first().size)
    }

    @Test
    fun theMostRecentlyUsedConversationSortsFirst() = runTest {
        val older = repository.appendUserMessage(null, "older chat", 1_000L)
        val newer = repository.appendUserMessage(null, "newer chat", 2_000L)
        // Touching the older one should lift it back to the top of the drawer.
        repository.appendUserMessage(older, "revived", 3_000L)

        assertEquals(listOf(older, newer), repository.observeChats().first().map { it.id })
    }
}
