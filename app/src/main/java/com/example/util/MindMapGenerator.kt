package com.example.util

data class MindMapNode(
    val id: String,
    val text: String,
    val subtext: String = "",
    val type: MindMapNodeType,
    val textOffsetInContent: Int = 0,
    var x: Float = 0f,
    var y: Float = 0f,
    val parentId: String? = null,
    val children: MutableList<MindMapNode> = mutableListOf()
)

enum class MindMapNodeType {
    ROOT,
    SECTION,
    DEFINITION,
    ACTION_ITEM,
    QUESTION,
    KEY_POINT
}

data class MindMapGraph(
    val root: MindMapNode,
    val allNodes: List<MindMapNode>,
    val connections: List<Pair<MindMapNode, MindMapNode>>
)

object MindMapGenerator {

    fun generate(title: String, content: String): MindMapGraph {
        val rootTitle = if (title.isNotBlank()) title.trim() else "Конспект"
        val root = MindMapNode(
            id = "root",
            text = rootTitle,
            subtext = "Главная тема",
            type = MindMapNodeType.ROOT
        )

        val allNodes = mutableListOf(root)
        val connections = mutableListOf<Pair<MindMapNode, MindMapNode>>()

        val analysis = LectureSummaryExtractor.analyze(title, content)
        val toc = TableOfContentsExtractor.extract(content)

        // 1. If we have TOC headings, use them as primary branches
        val sectionNodes = mutableListOf<MindMapNode>()
        if (toc.isNotEmpty()) {
            toc.take(6).forEachIndexed { index, item ->
                val secNode = MindMapNode(
                    id = "sec_$index",
                    text = item.title,
                    subtext = "Раздел",
                    type = MindMapNodeType.SECTION,
                    textOffsetInContent = item.characterOffset,
                    parentId = root.id
                )
                root.children.add(secNode)
                allNodes.add(secNode)
                connections.add(root to secNode)
                sectionNodes.add(secNode)
            }
        }

        // 2. Definitions
        analysis.definitions.take(8).forEachIndexed { index, def ->
            // Distribute under sections if possible, otherwise attach to root
            val targetParent = if (sectionNodes.isNotEmpty()) {
                sectionNodes[index % sectionNodes.size]
            } else {
                root
            }
            val defNode = MindMapNode(
                id = "def_$index",
                text = def.term,
                subtext = def.definition,
                type = MindMapNodeType.DEFINITION,
                parentId = targetParent.id
            )
            targetParent.children.add(defNode)
            allNodes.add(defNode)
            connections.add(targetParent to defNode)
        }

        // 3. Review Questions
        analysis.reviewQuestions.take(4).forEachIndexed { index, q ->
            val qNode = MindMapNode(
                id = "q_$index",
                text = q,
                subtext = "Вопрос для проверки",
                type = MindMapNodeType.QUESTION,
                parentId = root.id
            )
            root.children.add(qNode)
            allNodes.add(qNode)
            connections.add(root to qNode)
        }

        // 4. Action Items (Tasks)
        analysis.actionItems.take(4).forEachIndexed { index, item ->
            val taskNode = MindMapNode(
                id = "task_$index",
                text = item.text,
                subtext = "Задание / дедлайн",
                type = MindMapNodeType.ACTION_ITEM,
                parentId = root.id
            )
            root.children.add(taskNode)
            allNodes.add(taskNode)
            connections.add(root to taskNode)
        }

        // 5. If note is brief and has key points, add them
        if (allNodes.size <= 2 && analysis.keyPoints.isNotEmpty()) {
            analysis.keyPoints.take(5).forEachIndexed { index, pt ->
                val ptNode = MindMapNode(
                    id = "pt_$index",
                    text = pt,
                    subtext = "Ключевой тезис",
                    type = MindMapNodeType.KEY_POINT,
                    parentId = root.id
                )
                root.children.add(ptNode)
                allNodes.add(ptNode)
                connections.add(root to ptNode)
            }
        }

        // Compute layout coordinates (radial tree layout)
        layoutGraph(root)

        return MindMapGraph(
            root = root,
            allNodes = allNodes,
            connections = connections
        )
    }

    private fun layoutGraph(root: MindMapNode) {
        root.x = 0f
        root.y = 0f

        val branches = root.children
        val branchCount = branches.size
        if (branchCount == 0) return

        val angleStep = (2 * Math.PI) / branchCount
        val radiusPrimary = 320f

        branches.forEachIndexed { i, branch ->
            val angle = i * angleStep - Math.PI / 2
            branch.x = (Math.cos(angle) * radiusPrimary).toFloat()
            branch.y = (Math.sin(angle) * radiusPrimary).toFloat()

            // Layout leaf children around branch
            val leaves = branch.children
            val leafCount = leaves.size
            if (leafCount > 0) {
                val leafRadius = 220f
                val leafSpread = Math.PI / 2.5
                val startAngle = angle - leafSpread / 2
                val leafAngleStep = if (leafCount > 1) leafSpread / (leafCount - 1) else 0.0

                leaves.forEachIndexed { j, leaf ->
                    val curLeafAngle = if (leafCount > 1) startAngle + j * leafAngleStep else angle
                    leaf.x = branch.x + (Math.cos(curLeafAngle) * leafRadius).toFloat()
                    leaf.y = branch.y + (Math.sin(curLeafAngle) * leafRadius).toFloat()
                }
            }
        }
    }
}
