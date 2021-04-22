package astminer.cli

import astminer.common.model.Node
import astminer.common.model.ParseResult
import astminer.common.model.FunctionInfo
import astminer.common.preOrder
import astminer.common.setTechnicalToken
import astminer.parse.antlr.AntlrNode
import astminer.parse.antlr.java.JavaMethodSplitter
import astminer.parse.antlr.javascript.JavaScriptMethodSplitter
import astminer.parse.antlr.python.PythonMethodSplitter
import astminer.parse.fuzzy.cpp.FuzzyMethodSplitter
import astminer.parse.fuzzy.cpp.FuzzyNode
import astminer.parse.gumtree.GumTreeNode
import astminer.parse.gumtree.java.GumTreeJavaMethodSplitter
import astminer.parse.gumtree.python.GumTreePythonMethodSplitter
import java.io.File


/**
 * An AST subtree with a label and the path of the source file.
 * @property root The root of the AST subtree.
 * @property label Any label for this subtree.
 * @property filePath The path to the source file where the AST is from.
 */
data class LabeledResult<T : Node>(val root: T, val label: String, val filePath: String)


interface LabelExtractor {
    fun toLabeledData(parseResult: ParseResult<out Node>): List<LabeledResult<out Node>>
}

abstract class FileLabelExtractor : LabelExtractor {

    override fun toLabeledData(
            parseResult: ParseResult<out Node>
    ): List<LabeledResult<out Node>> {
        val (root, filePath) = parseResult
        return if (root == null) {
            emptyList()
        } else {
            val label = extractLabel(root, filePath) ?: return emptyList()
            listOf(LabeledResult(root, label, parseResult.filePath))
        }
    }

    abstract fun extractLabel(root: Node, filePath: String): String?
}

abstract class MethodLabelExtractor(
        open val filterPredicates: Collection<MethodFilter> = emptyList(),
        open val javaParser: String = "gumtree",
        open val pythonParser: String = "antlr"
) : LabelExtractor {

    override fun toLabeledData(
            parseResult: ParseResult<out Node>
    ): List<LabeledResult<out Node>> {
        val (root, filePath) = parseResult
        if (root == null) {
            return emptyList()
        }
        val fileExtension = File(filePath).extension
        val methodInfos = when (fileExtension) {
            "c", "cpp" -> {
                val methodSplitter = FuzzyMethodSplitter()
                methodSplitter.splitIntoMethods(root as FuzzyNode)
            }
            "java" -> {
                when (javaParser) {
                    "gumtree" -> {
                        val methodSplitter = GumTreeJavaMethodSplitter()
                        methodSplitter.splitIntoMethods(root as GumTreeNode)
                    }
                    "antlr" -> {
                        val methodSplitter = JavaMethodSplitter()
                        methodSplitter.splitIntoMethods(root as AntlrNode)
                    }
                    else -> {
                        throw UnsupportedOperationException("Unsupported parser $javaParser")
                    }
                }
            }
            "py" -> {
                when (pythonParser) {
                    "gumtree" -> {
                        val methodSplitter = GumTreePythonMethodSplitter()
                        methodSplitter.splitIntoMethods(root as GumTreeNode)
                    }
                    "antlr" -> {
                        val methodSplitter = PythonMethodSplitter()
                        methodSplitter.splitIntoMethods(root as AntlrNode)
                    }
                    else -> {
                        throw UnsupportedOperationException("Unsupported parser $pythonParser")
                    }
                }
            }
            "js" -> {
                val methodSplitter = JavaScriptMethodSplitter()
                methodSplitter.splitIntoMethods(root as AntlrNode)
            }
            else -> throw UnsupportedOperationException("Unsupported extension $fileExtension")
        }.filter { methodInfo ->
            filterPredicates.all { predicate ->
                predicate.isFiltered(methodInfo)
            }
        }
        return methodInfos.mapNotNull {
            val label = extractLabel(it, filePath) ?: return@mapNotNull null
            LabeledResult(it.root, label, filePath)
        }
    }

    abstract fun <T : Node> extractLabel(functionInfo: FunctionInfo<T>, filePath: String): String?
}

class FilePathExtractor : FileLabelExtractor() {
    override fun extractLabel(root: Node, filePath: String): String {
        return filePath
    }
}

class FolderExtractor : FileLabelExtractor() {
    override fun extractLabel(root: Node, filePath: String): String? {
        return File(filePath).parentFile.name
    }
}

class MethodNameExtractor(
        override val filterPredicates: Collection<MethodFilter> = emptyList(),
        override val javaParser: String = "gumtree",
        override val pythonParser: String = "antlr"
) : MethodLabelExtractor(filterPredicates, javaParser, pythonParser) {

    override fun <T : Node> extractLabel(functionInfo: FunctionInfo<T>, filePath: String): String? {
        val name = functionInfo.name ?: return null
        functionInfo.root.preOrder().forEach { node ->
            if (node.getToken() == name) {
                node.setTechnicalToken("SELF")
            }
        }
        functionInfo.nameNode?.setTechnicalToken("METHOD_NAME")
        // TODO: for some reason it is not normalized, check if something is wrong. Maybe storages normalize the label
        return name
    }
}
