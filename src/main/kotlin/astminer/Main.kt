package astminer

import astminer.config.*
import astminer.pipeline.Pipeline
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.github.ajalt.clikt.core.CliktCommand
import io.javalin.Javalin
import mu.KotlinLogging
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText


private val logger = KotlinLogging.logger("Main")

class PipelineRunner : CliktCommand(name = "") {

    override fun run() {
        val app = Javalin.create().start(7070)

        app.post("/run") { ctx ->
            try {
                val contents = ctx.formParams("contents")!!
                val lang = ctx.formParam("lang")!!
                val label = ctx.formParam("label")!!

                val inputDir = createTempDirectory()
                val outputDir = createTempDirectory()

                val ext = when (lang) {
                    "java" -> FileExtension.Java
                    "js" -> FileExtension.JavaScript
                    "py" -> FileExtension.Python
                    "php" -> FileExtension.PHP
                    else -> throw IllegalArgumentException("Invalid language")
                }

                val config = PipelineConfig(
                    inputDir = inputDir.toString(),
                    outputDir = outputDir.toString(),
                    parser = ParserConfig(ParserType.Antlr, listOf(ext)),
                    labelExtractor = when (label) {
                        "file name" -> FileNameExtractorConfig()
                        "function name" -> FunctionNameExtractorConfig()
                        else -> throw IllegalArgumentException("Illegal label")
                    },
                    storage = Code2VecPathStorageConfig(8, 2),
                    numOfThreads = 1
                )

                contents.forEachIndexed { idx, content ->
                    inputDir.resolve("input${idx}.${ext.fileExtension}").writeText(content)
                }

                Pipeline(config).run()

                val result = outputDir.resolve("${lang}/data/path_contexts.c2s").readText()

                ctx.result(result)

                inputDir.toFile().deleteRecursively()
                outputDir.toFile().deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
                ctx.status(500).result(e.message ?: "")
            }
        }
    }

    private fun report(message: String, e: Exception) {
        logger.error(e) { message }
        println("$message:\n$e")
    }

    companion object {
        private const val POLYMORPHISM_PROPERTY_NAME = "name"

        private val yaml = Yaml(
            configuration = YamlConfiguration(
                polymorphismStyle = PolymorphismStyle.Property,
                polymorphismPropertyName = POLYMORPHISM_PROPERTY_NAME
            )
        )
    }
}

fun main(args: Array<String>) = PipelineRunner().main(args)
