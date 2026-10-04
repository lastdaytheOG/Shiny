package com.shiny.music.legal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The licence inventory every APK carries (licenses/) must be complete and current: every library has
 * a resolved licence, every notice file it points to exists, and it was regenerated after the last
 * dependency change. LICENSE_COMPLIANCE.md describes the inventory.
 */
class LicenseInventoryTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "licenses/dependencies.json").exists() }

    private fun json(path: String): JsonObject =
        Json.parseToJsonElement(File(root, path).readText()).jsonObject

    @Test
    fun `every library has a resolved licence`() {
        val unknown = json("licenses/dependencies.json")["libraries"]!!.jsonArray
            .map { it.jsonObject }
            .filter { lib -> lib["spdx"]!!.jsonArray.any { it.jsonPrimitive.content == "UNKNOWN" } }
            .map { it["id"]!!.jsonPrimitive.content }
        assertEquals("Resolve these in licenses/overrides.json, with evidence", emptyList<String>(), unknown)
    }

    @Test
    fun `every notice file the inventory names exists`() {
        val bundled = json("licenses/bundled.json")["groups"]!!.jsonArray
            .flatMap { it.jsonObject["components"]!!.jsonArray }
            .flatMap { it.jsonObject["noticeFiles"]?.jsonArray.orEmpty() }
        val libraries = json("licenses/dependencies.json")["libraries"]!!.jsonArray
            .flatMap { it.jsonObject["noticeFiles"]?.jsonArray.orEmpty() }
        val missing = (bundled + libraries).map { it.jsonPrimitive.content }.filterNot { File(root, it).isFile }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `every licence in use has its text or a notice to show`() {
        val texts = File(root, "licenses/texts").list()!!.map { it.removeSuffix(".txt") }.toSet()
        val inventory = json("licenses/dependencies.json")
        val used = inventory["licensesUsed"]!!.jsonArray.map { it.jsonPrimitive.content } +
            json("licenses/bundled.json")["groups"]!!.jsonArray
                .flatMap { it.jsonObject["components"]!!.jsonArray }
                .flatMap { it.jsonObject["spdx"]!!.jsonArray.map { s -> s.jsonPrimitive.content } }
        // GPL-3.0 is the repository's LICENSE; LicenseRef-* and "WITH" expressions are shown from the
        // component's own notice files or its declared licence URL instead of a standard text.
        val withoutText = used.distinct()
            .filterNot { it in texts || it == "GPL-3.0" || it.startsWith("LicenseRef-") || " WITH " in it }
        assertEquals(emptyList<String>(), withoutText)
    }

    @Test
    fun `inventory was regenerated after the last dependency change`() {
        val recorded = json("licenses/dependencies.json")["dependencyFingerprint"]!!.jsonPrimitive.content
        assertEquals(
            "Dependencies changed: run `python scripts/third_party_licenses.py` and commit licenses/",
            recorded,
            dependencyFingerprint(),
        )
    }

    /** The same fingerprint scripts/third_party_licenses.py records (dependency_fingerprint()). */
    private fun dependencyFingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val catalog = File(root, "gradle/libs.versions.toml").readText().replace("\r\n", "\n")
        digest.update(catalog.toByteArray())
        val declaration = Regex("""^"?(\w+)?(implementation|api|runtimeOnly|coreLibraryDesugaring)"?\(""", RegexOption.IGNORE_CASE)
        val builds = root.listFiles()!!
            .filter { File(it, "build.gradle.kts").isFile }
            .sortedBy { it.name.lowercase() }
            .map { File(it, "build.gradle.kts") } + File(root, "build.gradle.kts")
        for (build in builds) {
            for (line in build.readLines()) {
                val s = line.trim()
                if (declaration.containsMatchIn(s)) digest.update("${build.relativeTo(root).invariantSeparatorsPath}:$s\n".toByteArray())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `the bundled notices carry the lines their licences require`() {
        fun notice(name: String) = File(root, "licenses/notices/$name").readText()
        assertTrue("Copyright (c) 2015, David Bonnet" in notice("astring-1.9.0-LICENSE.txt"))
        assertTrue("Permission is hereby granted, free of charge" in notice("astring-1.9.0-LICENSE.txt"))
        assertTrue("KFlash" in notice("meriyah-6.1.4-LICENSE.md"))
        assertTrue("Permission to use, copy, modify, and/or distribute" in notice("meriyah-6.1.4-LICENSE.md"))
        assertTrue("released into the public domain" in notice("yt-dlp-ejs-LICENSE.txt"))
        assertTrue("Apache License" in notice("kyant0-AndroidLiquidGlass-LICENSE.txt"))
        assertTrue("Apache License" in notice("compose-floating-tab-bar-v1.0.1-LICENSE.txt"))
    }

    @Test
    fun `the notice says the work is modified and the licence is the GPL`() {
        // GPL-3.0 section 5(a): the work states that it was modified, and when.
        assertTrue(File(root, "NOTICE").readText().contains("The Shiny Project has modified the code base since"))
        assertTrue(File(root, "LICENSE").readText().contains("GNU GENERAL PUBLIC LICENSE"))
    }
}
