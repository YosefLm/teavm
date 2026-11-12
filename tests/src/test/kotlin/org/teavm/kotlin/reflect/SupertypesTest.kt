package org.teavm.kotlin.reflect

import kotlin.jvm.KotlinReflectionNotSupportedError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.teavm.junit.SkipJVM
import org.teavm.junit.TeaVMTest

@TeaVMTest
@SkipJVM
class SupertypesTest {
    open class Parent
    interface Listener
    class Child : Parent(), Listener
    interface Generic<T>
    class Parameterized : Generic<String>

    @Test
    fun findsDirectSupertypes() {
        assertEquals(listOf(Parent::class, Listener::class), Child::class.supertypes.map { it.classifier })
        assertEquals(listOf(Any::class), Listener::class.supertypes.map { it.classifier })
        assertEquals(emptyList<Any>(), Any::class.supertypes)
    }

    @Test
    fun keepsGenericSupertypesUnsupported() {
        assertThrows<KotlinReflectionNotSupportedError> { Parameterized::class.supertypes }
    }
}
