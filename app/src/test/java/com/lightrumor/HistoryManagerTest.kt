package com.lightrumor

import org.junit.Assert.*
import org.junit.Test

class HistoryManagerTest {

    @Test
    fun testBranchingDoesNotDeletePreviousHistory() {
        val initialParams = DevelopmentParams(exposureEV = 0.0f)
        val historyManager = HistoryManager(initialParams)

        // 1. Edit A -> B
        val paramsB = DevelopmentParams(exposureEV = 1.0f)
        val nodeB = historyManager.recordState("Edit B (+1.0 EV)", paramsB)
        val nodeBId = nodeB.id

        assertEquals(2, historyManager.getTotalNodeCount()) // Root + B

        // 2. Undo back to Root (A)
        val nodeA = historyManager.undo()
        assertNotNull(nodeA)
        assertEquals(0.0f, nodeA!!.params.exposureEV, 0.001f)

        // 3. Create branch C (+2.0 EV) from A
        val paramsC = DevelopmentParams(exposureEV = 2.0f)
        val nodeC = historyManager.recordState("Edit C (+2.0 EV)", paramsC)
        val nodeCId = nodeC.id

        // 4. Verify that node B was NOT deleted and is still accessible!
        assertEquals(3, historyManager.getTotalNodeCount()) // Root + B + C

        val jumpedB = historyManager.jumpToNode(nodeBId)
        assertNotNull("Node B should still exist in DAG tree after branching!", jumpedB)
        assertEquals(1.0f, jumpedB!!.params.exposureEV, 0.001f)

        // Verify that node C is also accessible
        val jumpedC = historyManager.jumpToNode(nodeCId)
        assertNotNull("Node C should exist in DAG tree!", jumpedC)
        assertEquals(2.0f, jumpedC!!.params.exposureEV, 0.001f)

        // Verify branches
        val branches = historyManager.getBranches()
        assertTrue("Should have multiple branches", branches.size >= 2)
    }

    @Test
    fun testInitialMasksPreservedInRootNode() {
        val mask = MaskLayerState(name = "Initial Subject Mask")
        val historyManager = HistoryManager(DevelopmentParams(), listOf(mask))

        val current = historyManager.getCurrentNode()
        assertNotNull(current)
        assertEquals(1, current!!.masks.size)
        assertEquals("Initial Subject Mask", current.masks[0].name)
    }
}
