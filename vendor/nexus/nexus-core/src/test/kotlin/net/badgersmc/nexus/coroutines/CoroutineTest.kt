package net.badgersmc.nexus.coroutines

import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.badgersmc.nexus.annotations.PostConstruct
import net.badgersmc.nexus.annotations.PreDestroy
import net.badgersmc.nexus.annotations.Service
import net.badgersmc.nexus.core.NexusContext
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class CoroutineTest {

    @Test
    fun `should create context with coroutine scope`() {
        val context = NexusContext.create(
            classLoader = this::class.java.classLoader,
            contextName = "test"
        )

        assertNotNull(context.scope)
        assertNotNull(context.dispatchers)
        assertTrue(context.scope!!.isActive)

        context.close()
    }

    @Test
    fun `scope should be cancelled on close`() {
        val context = NexusContext.create(
            classLoader = this::class.java.classLoader,
            contextName = "test"
        )

        val scope = context.scope!!
        assertTrue(scope.isActive)

        context.close()
        assertFalse(scope.isActive)
    }

    @Test
    fun `should propagate classloader to virtual threads`() {
        val expectedClassLoader = this::class.java.classLoader
        val latch = CountDownLatch(1)
        var capturedClassLoader: ClassLoader? = null

        val context = NexusContext.create(
            classLoader = expectedClassLoader,
            contextName = "test"
        )

        context.scope!!.launch {
            capturedClassLoader = Thread.currentThread().contextClassLoader
            latch.countDown()
        }

        latch.await(5, TimeUnit.SECONDS)
        assertEquals(expectedClassLoader, capturedClassLoader)

        context.close()
    }

    @Test
    fun `should run on virtual threads`() {
        val latch = CountDownLatch(1)
        var isVirtual = false

        val context = NexusContext.create(
            classLoader = this::class.java.classLoader,
            contextName = "test"
        )

        context.scope!!.launch {
            isVirtual = Thread.currentThread().isVirtual
            latch.countDown()
        }

        latch.await(5, TimeUnit.SECONDS)
        assertTrue(isVirtual)

        context.close()
    }

    @Test
    fun `scope and dispatchers should be injectable as beans`() {
        val context = NexusContext.create(
            classLoader = this::class.java.classLoader,
            contextName = "test"
        )

        assertTrue(context.containsBean("nexusScope"))
        assertTrue(context.containsBean("nexusDispatchers"))

        context.close()
    }

    @Test
    fun `context without classloader should have null scope`() {
        val context = NexusContext.create()

        assertNull(context.scope)
        assertNull(context.dispatchers)

        context.close()
    }

    @Test
    fun `should handle suspend PostConstruct and PreDestroy`() {
        val context = NexusContext.create(
            "net.badgersmc.nexus.coroutines",
            this::class.java.classLoader,
            contextName = "test"
        )

        val bean = context.getBean<SuspendLifecycleBean>()
        assertTrue(bean.postConstructCalled)

        context.close()
        assertTrue(bean.preDestroyCalled)
    }
}

@Service
class SuspendLifecycleBean {
    var postConstructCalled = false
    var preDestroyCalled = false

    @PostConstruct
    suspend fun init() {
        kotlinx.coroutines.delay(10)
        postConstructCalled = true
    }

    @PreDestroy
    suspend fun cleanup() {
        kotlinx.coroutines.delay(10)
        preDestroyCalled = true
    }
}
