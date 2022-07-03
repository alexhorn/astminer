package astminer.storage.path

import astminer.common.model.*
import astminer.common.storage.*
import java.io.File

class Code2VecPathStorage(
    outputDirectoryPath: String,
    private val config: PathBasedStorageConfig,
) :
    PathBasedStorage(outputDirectoryPath, config) {

    private val tokensMap: RankedIncrementalIdStorage<String> = RankedIncrementalIdStorage()
    private val pathsMap: RankedIncrementalIdStorage<Int> = RankedIncrementalIdStorage()

    private fun dumpPathContexts(labeledPathContextIds: LabeledPathContextIds<String>): String {
        val pathContextIdsString = labeledPathContextIds.pathContexts.filter {
            val isNumberOfTokensValid = config.maxTokens == null ||
                tokensMap.getKeyRank(it.startTokenId) <= config.maxTokens &&
                tokensMap.getKeyRank(it.endTokenId) <= config.maxTokens
            val isNumberOfPathsValid = config.maxPaths == null || pathsMap.getKeyRank(it.pathId) <= config.maxPaths

            isNumberOfTokensValid && isNumberOfPathsValid
        }

        return pathContextIdsToString(pathContextIdsString, labeledPathContextIds.label)
    }

    private fun storePathContext(pathContext: PathContext): PathContextId {
        val startTokenId = pathContext.startToken
        tokensMap.record(startTokenId)
        val endTokenId = pathContext.endToken
        tokensMap.record(endTokenId)
        val pathId = pathContext.orientedNodeTypes.sumOf { "${it.typeLabel} ${it.direction}".hashCode() }
        pathsMap.record(pathId)
        return PathContextId(startTokenId, pathId, endTokenId)
    }

    override fun labeledPathContextsToString(labeledPathContexts: LabeledPathContexts<String>): String {
        val labeledPathContextIds = LabeledPathContextIds(
            labeledPathContexts.label,
            labeledPathContexts.pathContexts.map { storePathContext(it) }
        )
        return dumpPathContexts(labeledPathContextIds)
    }

    private fun pathContextIdsToString(pathContextIds: List<PathContextId>, label: String): String {
        val joinedPathContexts = pathContextIds.joinToString(" ") { pathContextId ->
            "${pathContextId.startTokenId},${pathContextId.pathId},${pathContextId.endTokenId}"
        }
        return "$label $joinedPathContexts"
    }

}
