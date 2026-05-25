package com.mrpowergamerbr.butterscotchwikigenerator

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import net.lingala.zip4j.io.inputstream.ZipInputStream
import org.bouncycastle.crypto.digests.MD5Digest
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.jsoup.select.Elements
import java.io.ByteArrayInputStream
import java.io.File
import java.util.*

suspend fun main(args: Array<String>) {
    val gameMakerRunnerVersion = System.getenv("GAMEMAKER_RUNNER_VERSION")

    val decompiledLibYoYoFile = File("libyoyo.c")

    // Get all registered functions in the runner
    if (!decompiledLibYoYoFile.exists()) {
        val http = HttpClient {}
        val url = "http://gms.yoyogames.com/Zeus-Runtime.rss"

        val doc = Jsoup.connect(url)
            .parser(Parser.xmlParser())
            .ignoreContentType(true)
            .userAgent("Mozilla/5.0")
            .timeout(10000)
            .get()

        val items: Elements = doc.select("item")
        for (item in items) {
            val title = item.selectFirst("title")!!.text()

            if (title == gameMakerRunnerVersion) {
                val url = item.selectFirst("[name='android']")!!.attr("url")
                println(url)

                val md5Digest = MD5Digest()
                val array = ByteArray(16)
                val bytes = ("MRJA" + url.substringAfterLast("/") + "PHMD").toByteArray(Charsets.UTF_8)
                // Yes this is quirky, we NEED to use BouncyCastle for this because the native Java version can't reproduce the "bug"
                // If we don't, the hash won't match!
                md5Digest.reset()
                md5Digest.update(bytes, 0, bytes.size)
                // Call to finish first to do first padding + compression
                md5Digest.finish()
                // Then we actually finish it
                md5Digest.doFinal(array, 0)
                val password = Base64.getEncoder().encodeToString(array)

                val body = http.get(url).bodyAsBytes()

                val libyoyo = extractFile(
                    body,
                    "android/runner/ProjectFiles/src/main/jniLibs/arm64-v8a/libyoyo.so",
                    password.toCharArray()
                )

                val libyoyoFile = File("libyoyo.so").absoluteFile
                libyoyoFile.writeBytes(libyoyo)

                decompileWithGhidra(libyoyoFile, File("libyoyo.c").absoluteFile)
            }
        }
    }

    val decompiled = decompiledLibYoYoFile.readText()
    val registeredYoYoFunctions = Regex("Function_Add\\(\"(.+)\",")
        .findAll(decompiled)
        .map {
            it.groupValues[1]
        }
        .toList()

    val registeredYoYoBuiltInVariables = Regex("Variable_BuiltIn_Add\\n.+\\(\"(.+)\",")
        .findAll(decompiled)
        .map {
            it.groupValues[1]
        }
        .toList()

    val registeredButterscotchFunctions = Regex("VM_registerBuiltin\\(ctx, \"(.+)\", .+\\);")
        .findAll(File("Butterscotch/src/vm_builtins.c").readText())
        .map {
            it.groupValues[1]
        }
        .toList()

    val registeredButterscotchBuiltInVariables = Regex("\\{ \"(.+)\", BUILTIN_.+ },")
        .findAll(File("Butterscotch/src/vm_builtins.c").readText())
        .map {
            it.groupValues[1]
        }
        .toList()

    val anon = object {}
    fun loadResource(name: String): List<String> = anon::class.java.getResourceAsStream("/$name")!!.readAllBytes().toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }

    val versionTimeline: List<Pair<String, String?>> = listOf(
        "GM:S 1.0.98" to "gms_1_0_98",
        "GM:S 1.0.114" to "gms_1_0_114",
        "GM:S 1.0.129" to "gms_1_0_129",
        "GM:S 1.0.198" to "gms_1_0_198",
        "GM:S 1.1.622" to "gms_1_1_622",
        "GM:S 1.1.690" to "gms_1_1_690",
        "GM:S 1.1.694" to "gms_1_1_694",
        "GM:S 1.1.711" to "gms_1_1_711",
        "GM:S 1.1.734" to "gms_1_1_734",
        "GM:S MIPS 1.1.747" to "gms_mips_1_1_747",
        "GM:S 1.1.750" to "gms_1_1_750",
        "GM:S 1.1.754" to "gms_1_1_754",
        "GM:S 1.1.785" to "gms_1_1_785",
        "GM:S 1.1.805" to "gms_1_1_805",
        "GM:S 1.1.827" to "gms_1_1_827",
        "GM:S 1.1.844" to "gms_1_1_844",
        "GM:S 1.1.867" to "gms_1_1_867",
        "GM:S 1.1.872" to "gms_1_1_872",
        "GM:S 1.1.913" to "gms_1_1_913",
        "GM:S 1.1.917" to "gms_1_1_917",
        "GM:S 1.1.929" to "gms_1_1_929",
        "GM:S 1.1.964" to "gms_1_1_964",
        "GM:S 1.1.1013" to "gms_1_1_1013",
        "GM:S 1.1.1044" to "gms_1_1_1044",
        "GM:S 1.1.1058" to "gms_1_1_1058",
        "GM:S 1.1.1076" to "gms_1_1_1076",
        "GM:S 1.1.1086" to "gms_1_1_1086",
        "GM:S 1.1.1089" to "gms_1_1_1089",
        "GM:S 1.1.1130" to "gms_1_1_1130",
        "GM:S 1.2.1113" to "gms_1_2_1113",
        "GM:S 1.2.1130" to "gms_1_2_1130",
        "GM:S 1.2.1135" to "gms_1_2_1135",
        "GM:S 1.2.1146" to "gms_1_2_1146",
        "GM:S 1.2.1150" to "gms_1_2_1150",
        "GM:S 1.2.1161" to "gms_1_2_1161",
        "GM:S 1.2.1214" to "gms_1_2_1214",
        "GM:S 1.2.1220" to "gms_1_2_1220",
        "GM:S 1.2.1224" to "gms_1_2_1224",
        "GM:S 1.2.1264" to "gms_1_2_1264",
        "GM:S 1.2.1279" to "gms_1_2_1279",
        "GM:S 1.3.1307" to "gms_1_3_1307",
        "GM:S 1.3.1336" to "gms_1_3_1336",
        "GM:S 1.3.1344" to "gms_1_3_1344",
        "GM:S 1.3.1347" to "gms_1_3_1347",
        "GM:S 1.3.1373" to "gms_1_3_1373",
        "GM:S 1.3.1379" to "gms_1_3_1379",
        "GM:S 1.3.1386" to "gms_1_3_1386",
        "GM:S 1.3.1409" to "gms_1_3_1409",
        "GM:S 1.4.1398" to "gms_1_4_1398",
        "WAD Version 14" to "wad14",
        "WAD Version 15" to "wad15",
        "WAD Version 16" to "wad16",
        "GM ${gameMakerRunnerVersion.removePrefix("Version ").trim()}" to null,
    )

    data class Lifespan(val addedIn: String, val removedIn: String?)

    fun lifespans(suffix: String, liveList: List<String>): LinkedHashMap<String, Lifespan> {
        val first = LinkedHashMap<String, String>()
        val last = LinkedHashMap<String, String>()
        for ((label, prefix) in versionTimeline) {
            val list = if (prefix == null) liveList else loadResource("${prefix}_$suffix.txt")
            for (name in list) {
                if (name !in first) first[name] = label
                last[name] = label
            }
        }
        val labels = versionTimeline.map { it.first }
        val liveLabel = labels.last()
        val out = LinkedHashMap<String, Lifespan>()
        for ((name, addedIn) in first) {
            val lastLabel = last[name]!!
            val removedIn = if (lastLabel == liveLabel) null else labels[labels.indexOf(lastLabel) + 1]
            out[name] = Lifespan(addedIn, removedIn)
        }
        return out
    }

    fun generateMergedTable(header: String, lifespans: Map<String, Lifespan>, implemented: List<String>): String {
        val labelOrder = versionTimeline.map { it.first }
        val sorted = lifespans.entries.sortedWith(compareBy({ labelOrder.indexOf(it.value.addedIn) }, { it.key }))
        val total = sorted.size
        val done = sorted.count { it.key in implemented }

        val table = buildString {
            appendLine("| $header | Implemented in Butterscotch? | Added In | Removed In |")
            appendLine("| - | - | - | - |")
            for ((name, lifespan) in sorted) {
                val mark = if (name in implemented) "✅" else "🚫"
                val removed = lifespan.removedIn ?: ""
                appendLine("| `$name` | $mark | ${lifespan.addedIn} | $removed |")
            }
        }

        return buildString {
            appendLine("**Progress:** $done/$total (${(done / total.toDouble()) * 100}%)")
            appendLine()
            appendLine(table)
        }
    }

    val functionLifespans = lifespans("builtin_functions", registeredYoYoFunctions)
    val builtInLifespans = lifespans("builtin_variables", registeredYoYoBuiltInVariables)

    File("Butterscotch.wiki/builtin_functions.md")
        .writeText(generateMergedTable("GML Function", functionLifespans, registeredButterscotchFunctions))
    File("Butterscotch.wiki/builtin_variables.md")
        .writeText(generateMergedTable("GML Variable", builtInLifespans, registeredButterscotchBuiltInVariables))

    val ideToWadVersions = linkedMapOf(
        "GM:S 1.0.98" to 6,
        "GM:S 1.0.114" to 6,
        "GM:S 1.0.129" to 7,
        "GM:S 1.0.198" to 8,
        "GM:S 1.1.622" to 10,
        "GM:S 1.1.690" to 10,
        "GM:S 1.1.694" to 10,
        "GM:S 1.1.711" to 10,
        "GM:S 1.1.734" to 10,
        "GM:S MIPS 1.1.747" to 10,
        "GM:S 1.1.750" to 10,
        "GM:S 1.1.754" to 11,
        "GM:S 1.1.785" to 11,
        "GM:S 1.1.805" to 11,
        "GM:S 1.1.827" to 11,
        "GM:S 1.1.844" to 11,
        "GM:S 1.1.867" to 12,
        "GM:S 1.1.872" to 12,
        "GM:S 1.1.913" to 13,
        "GM:S 1.1.917" to 13,
        "GM:S 1.1.929" to 13,
        "GM:S 1.1.964" to 13,
        "GM:S 1.1.1013" to 13,
        "GM:S 1.1.1044" to 13,
        "GM:S 1.1.1058" to 13,
        "GM:S 1.1.1076" to 13,
        "GM:S 1.1.1086" to 13,
        "GM:S 1.1.1089" to 13,
        "GM:S 1.1.1130" to 13,
        "GM:S 1.2.1113" to 13,
        "GM:S 1.2.1130" to 13,
        "GM:S 1.2.1135" to 13,
        "GM:S 1.2.1146" to 13,
        "GM:S 1.2.1150" to 13,
        "GM:S 1.2.1161" to 13,
        "GM:S 1.2.1214" to 13,
        "GM:S 1.2.1220" to 13,
        "GM:S 1.2.1224" to 13,
        "GM:S 1.2.1264" to 13,
        "GM:S 1.2.1279" to 13,
        "GM:S 1.3.1307" to 13,
        "GM:S 1.3.1336" to 13,
        "GM:S 1.3.1344" to 13,
        "GM:S 1.3.1347" to 13,
        "GM:S 1.3.1373" to 13,
        "GM:S 1.3.1379" to 13,
        "GM:S 1.3.1386" to 13,
        "GM:S 1.3.1409" to 13,
        "GM:S 1.4.1398" to 13,
        "GM:S 1.4.1464" to 14,
        "GM:S 1.4.1499" to 14,
        "GM:S 1.4.1514" to 14,
        "GM:S 1.4.1522" to 14,
        "GM:S 1.4.1542" to 14,
        "GM:S 1.4.1556" to 14,
        "GM:S 1.4.1567" to 14,
        "GM:S 1.4.1598" to 14,
        "GM:S 1.4.1629" to 14,
        "GM:S 1.4.1657" to 14,
        "GM:S 1.4.1675" to 15,
        "GM:S 1.4.1683" to 15,
        "GM:S 1.4.1690" to 15,
        "GM:S 1.4.1711" to 15,
        "GM:S 1.4.1749" to 15,
        "GM:S 1.4.1767" to 16,
        "GM:S 1.4.1773" to 16,
        "GM:S 1.4.1804" to 16,
        "GM:S 1.4.9999" to 16,
    )

    fun generateIdeToWadVersionsTable(): String = buildString {
        appendLine("| IDE Version | WAD Version |")
        appendLine("| - | - |")
        for ((ide, wad) in ideToWadVersions) appendLine("| $ide | $wad |")
    }

    File("Butterscotch.wiki/ide_versions.md").writeText(generateIdeToWadVersionsTable())
}

fun decompileWithGhidra(soFile: File, outC: File) {
    val ghidraHome = System.getenv("GHIDRA_HOME")
    val analyzeHeadless = File(ghidraHome, "support/analyzeHeadless")
    require(analyzeHeadless.canExecute()) { "analyzeHeadless not found/executable at $analyzeHeadless" }

    // Use a per-invocation workdir so concurrent decompiles don't stomp each other's Ghidra
    // project / script directories.
    val workDir = File(System.getProperty("java.io.tmpdir"), "ghidra-workdir-${UUID.randomUUID()}").apply {
        deleteRecursively(); mkdirs()
    }
    val scriptDir = File(workDir, "scripts").apply { mkdirs() }
    val projectDir = File(workDir, "project").apply { mkdirs() }

    File(scriptDir, "ExportToC.java").writeText(
        """
        import ghidra.app.script.GhidraScript;
        import ghidra.app.util.exporter.CppExporter;
        import java.io.File;

        public class ExportToC extends GhidraScript {
            @Override
            public void run() throws Exception {
                String outPath = System.getenv("EXPORT_C_OUT");
                CppExporter exporter = new CppExporter();
                boolean ok = exporter.export(new File(outPath), currentProgram, null, monitor);
                if (!ok) println("CppExporter.export returned false");
                println("Done. Output: " + outPath);
            }
        }
        """.trimIndent()
    )

    println("Decompiling...")
    val pb = ProcessBuilder(
        analyzeHeadless.absolutePath,
        projectDir.absolutePath, "yoyo",
        "-import", soFile.absolutePath,
        "-scriptPath", scriptDir.absolutePath,
        "-postScript", "ExportToC.java",
        "-deleteProject"
    ).redirectErrorStream(true).inheritIO()
    pb.environment()["EXPORT_C_OUT"] = outC.absolutePath

    val exit = pb.start().waitFor()
    check(exit == 0) { "Ghidra headless failed with exit code $exit" }
    println("Decompiled output written to: ${outC.absolutePath}")
}

fun extractFile(
    zipBytes: ByteArray,
    entryName: String,
    password: CharArray
): ByteArray {
    ZipInputStream(ByteArrayInputStream(zipBytes), password).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.fileName == entryName) {
                return zis.readBytes()
            }
            entry = zis.nextEntry
        }
    }
    error("File not found in zip: $entryName")
}
