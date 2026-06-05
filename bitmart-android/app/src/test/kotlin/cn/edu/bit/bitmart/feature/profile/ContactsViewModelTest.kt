package cn.edu.bit.bitmart.feature.profile

import cn.edu.bit.bitmart.core.data.FakeContactPrefsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ContactsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test
    fun `add appends contact to store`() = runTest {
        val store = FakeContactPrefsStore()
        val vm = ContactsViewModel(store)

        vm.add("wxid_x")
        dispatcher.scheduler.advanceUntilIdle()

        val saved = store.contactsFlow.first()
        assertEquals(1, saved.size)
        assertEquals("wxid_x", saved.first())
    }

    @Test
    fun `add ignores blank value`() = runTest {
        val store = FakeContactPrefsStore()
        val vm = ContactsViewModel(store)

        vm.add("   ")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, store.contactsFlow.first().size)
    }

    @Test
    fun `add dedupes identical channel and value`() = runTest {
        val store = FakeContactPrefsStore(listOf("wxid_x"))
        val vm = ContactsViewModel(store)

        vm.add("wxid_x")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, store.contactsFlow.first().size)
    }

    @Test
    fun `removeAt deletes contact at index`() = runTest {
        val store = FakeContactPrefsStore(listOf("wxid_a", "qq_b"))
        val vm = ContactsViewModel(store)

        vm.removeAt(0)
        dispatcher.scheduler.advanceUntilIdle()

        val saved = store.contactsFlow.first()
        assertEquals(1, saved.size)
        assertEquals("qq_b", saved.first())
    }
}
