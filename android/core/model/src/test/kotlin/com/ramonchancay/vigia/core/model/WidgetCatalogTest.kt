package com.ramonchancay.vigia.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetCatalogTest {
    @Test
    fun `the catalog lists exactly the widget types this version renders`() {
        val declared = WidgetTypes::class.java.declaredFields
            .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.get(null) as String }
            .toSet()

        assertEquals(declared, WidgetCatalog.types.map { it.type }.toSet())
    }
}
