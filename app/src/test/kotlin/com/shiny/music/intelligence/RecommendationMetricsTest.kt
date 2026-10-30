package com.shiny.music.intelligence

import com.shiny.music.db.entities.SurfaceListenRow
import com.shiny.music.db.entities.SurfaceMetricRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecommendationMetricsTest {
    private fun listens(
        surface: String = "Radio",
        listens: Int = 0,
        completed: Int = 0,
        meaningful: Int = 0,
        partial: Int = 0,
        skipped: Int = 0,
        accidental: Int = 0,
        interrupted: Int = 0,
        replays: Int = 0,
        firstListens: Int = 0,
        firstPositive: Int = 0,
        artists: Int = 0,
    ) = SurfaceListenRow(surface, listens, completed, meaningful, partial, skipped, accidental, interrupted, replays, firstListens, firstPositive, artists)

    @Test
    fun `mis-taps and interruptions do not count against a surface`() {
        val report = RecommendationMetrics.reports(
            emptyList(),
            listOf(listens(listens = 10, completed = 3, meaningful = 1, skipped = 4, accidental = 1, interrupted = 1)),
        ).single()
        assertEquals(0.5, report.meaningfulRate!!, 1e-9)
        assertEquals(0.5, report.skipRate!!, 1e-9)
        assertEquals(0.375, report.completionRate!!, 1e-9)
    }

    @Test
    fun `shown and heard surfaces are joined, and a missing side stays unknown rather than zero`() {
        val reports = RecommendationMetrics.reports(
            listOf(SurfaceMetricRow("HomeMix", shown = 10, selected = 2, played = 3, meaningful = 2, skipped = 1, distinctSongs = 8)),
            listOf(listens(surface = "Radio", listens = 2, skipped = 2)),
        ).associateBy { it.surface }

        val mix = reports.getValue("HomeMix")
        assertEquals(0.2, mix.selectionRate!!, 1e-9)
        assertEquals(0.2, mix.repeatedRate!!, 1e-9)
        assertNull(mix.meaningfulRate)
        assertEquals(0, mix.listens)

        val radio = reports.getValue("Radio")
        assertNull(radio.selectionRate)
        assertEquals(1.0, radio.skipRate!!, 1e-9)
    }

    @Test
    fun `discovery success is the share of first listens that worked`() {
        val report = RecommendationMetrics.reports(emptyList(), listOf(listens(listens = 8, firstListens = 4, firstPositive = 1))).single()
        assertEquals(0.25, report.discoverySuccess!!, 1e-9)
    }

    @Test
    fun `nothing recorded means no reports`() {
        assertEquals(0, RecommendationMetrics.reports(emptyList(), emptyList()).size)
    }

    @Test
    fun `every interruption leaves the rates unknown instead of dividing by zero`() {
        val report = RecommendationMetrics.reports(emptyList(), listOf(listens(listens = 2, interrupted = 2))).single()
        assertNull(report.meaningfulRate)
        assertNull(report.skipRate)
    }
}
