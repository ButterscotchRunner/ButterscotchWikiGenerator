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

    // Cross check
    run {
        var total = 0
        var implemented = 0

        val table = buildString {
            appendLine("| GML Function | Implemented? |")
            appendLine("| - | - |")

            for (yoyo in registeredYoYoFunctions) {
                if (!registeredButterscotchFunctions.contains(yoyo)) {
                    appendLine("| `${yoyo}` | \uD83D\uDEAB |")
                } else {
                    appendLine("| `${yoyo}` | ✅ |")
                    implemented++
                }
                total++
            }
        }

        File("Butterscotch.wiki/Implemented Functions.md")
            .writeText(
                buildString {
                    appendLine("**Progress:** $implemented/$total (${(implemented / total.toDouble()) * 100}%)")
                    appendLine()
                    appendLine("Tested against GameMaker $gameMakerRunnerVersion")
                    appendLine()
                    appendLine(table)
                }
            )
    }

    run {
        var total = 0
        var implemented = 0

        val table = buildString {
            appendLine("| GML Built-In Variable | Implemented? |")
            appendLine("| - | - |")

            for (yoyo in registeredYoYoBuiltInVariables) {
                if (!registeredButterscotchBuiltInVariables.contains(yoyo)) {
                    appendLine("| `${yoyo}` | \uD83D\uDEAB |")
                } else {
                    appendLine("| `${yoyo}` | ✅ |")
                    implemented++
                }
                total++
            }
        }

        File("Butterscotch.wiki/Implemented Built-In Variables.md")
            .writeText(
                buildString {
                    appendLine("**Progress:** $implemented/$total (${(implemented / total.toDouble()) * 100}%)")
                    appendLine()
                    appendLine("Tested against GameMaker $gameMakerRunnerVersion")
                    appendLine()
                    appendLine(table)
                }
            )
    }
}

fun decompileWithGhidra(soFile: File, outC: File) {
    val ghidraHome = System.getenv("GHIDRA_HOME")
    val analyzeHeadless = File(ghidraHome, "support/analyzeHeadless")
    require(analyzeHeadless.canExecute()) { "analyzeHeadless not found/executable at $analyzeHeadless" }

    val workDir = File(System.getProperty("java.io.tmpdir"), "butterscotch-ghidra").apply {
        deleteRecursively(); mkdirs()
    }
    val scriptDir = File(workDir, "scripts").apply { mkdirs() }
    val projectDir = File(workDir, "project").apply { mkdirs() }

    File(scriptDir, "ExportToC.java").writeText(
        """
        import ghidra.app.script.GhidraScript;
        import ghidra.app.decompiler.DecompInterface;
        import ghidra.app.decompiler.DecompileResults;
        import ghidra.program.model.listing.Function;
        import java.io.PrintWriter;

        public class ExportToC extends GhidraScript {
            @Override
            public void run() throws Exception {
                String outPath = System.getenv("EXPORT_C_OUT");
                DecompInterface ifc = new DecompInterface();
                ifc.openProgram(currentProgram);
                int count = 0, failed = 0;
                try (PrintWriter w = new PrintWriter(outPath)) {
                    for (Function f : currentProgram.getFunctionManager().getFunctions(true)) {
                        if (monitor.isCancelled()) break;
                        try {
                            DecompileResults r = ifc.decompileFunction(f, 60, monitor);
                            if (r != null && r.getDecompiledFunction() != null) {
                                w.println("// " + f.getName() + " @ " + f.getEntryPoint());
                                w.println(r.getDecompiledFunction().getC());
                                count++;
                            } else {
                                failed++;
                            }
                        } catch (Exception e) {
                            failed++;
                        }
                        if (count > 0 && count % 100 == 0) println("Decompiled " + count + " functions...");
                    }
                }
                println("Done. Decompiled " + count + " functions (" + failed + " failed). Output: " + outPath);
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