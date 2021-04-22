package astminer.examples

import astminer.cli.LabeledResult
import astminer.parse.antlr.java.JavaMethodSplitter
import astminer.parse.antlr.java.JavaParser
import astminer.storage.path.Code2VecPathStorage
import astminer.storage.path.PathBasedStorageConfig
import java.io.File

//Retrieve paths from Java files, using a generated parser.
fun allJavaFiles() {
    val inputDir = "src/test/resources/examples/"

    val outputDir = "out_examples/allJavaFilesAntlr"
    val storage = Code2VecPathStorage(outputDir, PathBasedStorageConfig(5, 5))

    File(inputDir).forFilesWithSuffix("11.java") { file ->
        val node = JavaParser().parseInputStream(file.inputStream()) ?: return@forFilesWithSuffix
        node.prettyPrint()
        JavaMethodSplitter().splitIntoMethods(node).forEach {
            println(it.name)
            println(it.returnType)
            println(it.className)
            it.parameters.forEach { parameter ->
                println("${parameter.name} ${parameter.type}")
            }
        }
        storage.store(LabeledResult(node, file.path, file.path))
    }

    storage.close()
}
