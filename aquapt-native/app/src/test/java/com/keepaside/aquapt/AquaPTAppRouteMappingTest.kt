package com.keepaside.aquapt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AquaPTAppRouteMappingTest {

    @Test
    fun `maps core tab and modal routes`() {
        assertEquals("tanks", mapExternalRouteToNativeRoute("/"))
        assertEquals("tasks", mapExternalRouteToNativeRoute("/(tabs)/tasks"))
        assertEquals("livestock", mapExternalRouteToNativeRoute("/(tabs)/livestock"))
        assertEquals("insights", mapExternalRouteToNativeRoute("/modal"))
        assertEquals("workflows", mapExternalRouteToNativeRoute("/settings/workflows"))
    }

    @Test
    fun `maps model browser routes with target and selected id`() {
        assertEquals(
            "model-browser/ASSISTANT",
            mapExternalRouteToNativeRoute("/settings/models")
        )

        assertEquals(
            "model-browser/MEMORY?selectedId=openai%2Fgpt-4o-mini",
            mapExternalRouteToNativeRoute("/settings/models/memory?selectedId=openai/gpt-4o-mini")
        )

        assertEquals(
            "model-browser/MEMORY?selectedId=anthropic%2Fclaude-3.7-sonnet",
            mapExternalRouteToNativeRoute("/model-browser?target=memory&selectedId=anthropic/claude-3.7-sonnet")
        )
    }

    @Test
    fun `maps entity detail routes with optional aquarium context`() {
        assertEquals(
            "entity/AQUARIUM/aq-1/_",
            mapExternalRouteToNativeRoute("/entity/aquarium/aq-1")
        )

        assertEquals(
            "entity/PARAMETER_LOG/p-1/a-1",
            mapExternalRouteToNativeRoute("/entity/parameter-log/p-1?aquariumId=a-1")
        )

        assertEquals(
            "entity/ISSUE/i-1/a-2",
            mapExternalRouteToNativeRoute("aquapt://entity/issue/i-1/a-2")
        )
    }

    @Test
    fun `maps entity form routes and applies native fallbacks`() {
        assertEquals(
            "entity-form/MEMO/a-1",
            mapExternalRouteToNativeRoute("/entity-form/memo?aquariumId=a-1")
        )

        assertEquals(
            "entity-form/CONSUMABLE/_?targetId=c-1",
            mapExternalRouteToNativeRoute("/entity-form/consumable?id=c-1")
        )

        assertEquals(
            "tasks",
            mapExternalRouteToNativeRoute("/entity-form/task-execution?taskTemplateId=t-1")
        )

        assertEquals(
            "livestock",
            mapExternalRouteToNativeRoute("/entity-form/livestock")
        )
    }

    @Test
    fun `maps entity edit routes from path or query id`() {
        assertEquals(
            "entity-edit/task-template/t-1",
            mapExternalRouteToNativeRoute("/entity-edit/task-template/t-1")
        )

        assertEquals(
            "entity-edit/task-execution/e-1",
            mapExternalRouteToNativeRoute("aquapt://entity-edit/task-execution?id=e-1")
        )
    }

    @Test
    fun `returns null for unsupported or invalid deep links`() {
        assertNull(mapExternalRouteToNativeRoute("/entity-form/task-template"))
        assertNull(mapExternalRouteToNativeRoute("/entity/unknown-kind/id-1"))
        assertNull(mapExternalRouteToNativeRoute("/entity/aquarium"))
        assertNull(mapExternalRouteToNativeRoute("/entity-edit/task-template"))
        assertNull(mapExternalRouteToNativeRoute("   "))
    }
}
