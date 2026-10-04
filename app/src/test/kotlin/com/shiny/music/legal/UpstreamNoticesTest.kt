package com.shiny.music.legal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Copyright and licence notices that upstream authors put in Shiny's source files must stay exactly
 * as written (GPL-3.0 section 4; Apache-2.0 section 4(c)). A brand rename once rewrote the first
 * line of the ArchiveTune notice; this test catches that kind of edit.
 *
 * If a file is deleted together with its code, remove it from the list here in the same change.
 * Never edit a notice to make this pass. LICENSE_COMPLIANCE.md lists every notice and why it stays.
 */
class UpstreamNoticesTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "licenses/dependencies.json").exists() }
    private val app = "app/src/main/kotlin/com/shiny/music/"

    private val metrolist = listOf(
        "widget/TurntableWidgetReceiver.kt", "widget/ShinyWidgetManager.kt", "widget/PlaylistWidgetReceiver.kt",
        "widget/PlaylistWidgetManager.kt", "widget/MusicWidgetReceiver.kt", "widget/MusicRecognizerWidgetService.kt",
        "widget/MusicRecognizerWidgetReceiver.kt", "utils/YTPlayerUtils.kt", "utils/Fix403.kt",
    )
    private val vivi = listOf("ui/component/GlassEffect.kt")
    private val archiveTune = listOf(
        "spotify/Spotify.kt", "spotify/SpotifyAuth.kt", "spotify/SpotifyHashProvider.kt", "spotify/SpotifyMapper.kt",
        "spotify/models/SpotifyAlbum.kt", "spotify/models/SpotifyArtist.kt", "spotify/models/SpotifyHomeFeed.kt",
        "spotify/models/SpotifyLibraryItem.kt", "spotify/models/SpotifyPaging.kt", "spotify/models/SpotifyPlaylist.kt",
        "spotify/models/SpotifyRecommendations.kt", "spotify/models/SpotifySearchResult.kt", "spotify/models/SpotifyToken.kt",
        "spotify/models/SpotifyTrack.kt", "spotify/models/SpotifyUser.kt", "spotifyimport/SpotifyImportModels.kt",
        "spotifyimport/SpotifyImportRepository.kt", "spotifyimport/SpotifyImportViewModel.kt",
    )

    private fun text(path: String) = File(root, path).readText().replace("\r\n", "\n")

    private fun filesContaining(marker: String): List<String> =
        File(root, app).walkTopDown()
            .filter { it.isFile && it.extension == "kt" && marker in it.readText() }
            .map { it.relativeTo(File(root, app)).invariantSeparatorsPath }
            .sorted().toList()

    @Test
    fun `Metrolist notices are intact`() {
        val notice = " * Metrolist Project (C) 2026\n * Licensed under GPL-3.0 | See git history for contributors\n"
        assertEquals(metrolist.sorted(), filesContaining("Metrolist Project (C) 2026"))
        metrolist.forEach { assertEquals(it, true, notice in text(app + it)) }
    }

    @Test
    fun `vivi-music notices are intact`() {
        val notice = " * vivimusic Project (C) 2026\n * Licensed under GPL-3.0 | See git history for contributors\n"
        assertEquals(vivi.sorted(), filesContaining("vivimusic Project (C) 2026"))
        vivi.forEach { assertEquals(it, true, notice in text(app + it)) }
    }

    @Test
    fun `ArchiveTune notices are intact and carry their original first line`() {
        val notice = " * ArchiveTune (2026)\n * © Chartreux Westia — github.com/koiverse\n" +
            " * GPL-3.0 License | Contributors: see git history\n" +
            " * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5\n"
        assertEquals(archiveTune.sorted(), filesContaining("© Chartreux Westia"))
        archiveTune.forEach { assertEquals(it, true, notice in text(app + it)) }
    }

    @Test
    fun `Apache notices of the vendored libraries are intact`() {
        val kyant = "https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0"
        assertEquals(30, filesContaining(kyant).size)
        assertEquals(true, "   Copyright 2025 Kyant\n" in text(app + "ui/component/backdrop/internal/Shaders.kt"))
        val tabBar = text(app + "ui/component/floatingtabbar/FloatingTabBar.kt")
        assertEquals(true, " * FloatingTabBar v1.0.1 by Elyes Mansour\n" in tabBar)
        assertEquals(true, " * Licensed under the Apache License, Version 2.0\n" in tabBar)
        assertEquals(true, "SPDX-License-Identifier: Unlicense" in text("app/src/main/assets/solver/yt.solver.core.js"))
    }

    @Test
    fun `no upstream notice is relabelled as Shiny`() {
        assertEquals(emptyList<String>(), filesContaining(" * Shiny (2026)"))
        assertEquals(emptyList<String>(), filesContaining("Shiny Project (C)"))
    }
}
