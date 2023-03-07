package de.dailab.jiacvi.aot.gridworld

import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

enum class WorkerAction {
    NORTH,
    SOUTH,
    WEST,
    EAST,
    ORDER
}

data class GridworldGame(
    /** size of the grid */
    val size: Position,
    /** current game turn */
    var turn: Int,
    /** maximum number of turns in this game */
    val maxTurns: Int,
    /** brokers in this game, mapped by ID; will be only one for Solo play */
    val brokers: MutableMap<String, Broker>,
    /** available orders, mapped by ID */
    val orders: MutableMap<String, Order>,
    /** set of cells that are blocked due to obstacles;
     * if a worker happens to spawn on a blocked cell, it can leave it;
     * if an order spawns on a blocked cell, it can not be completed */
    val obstacles: MutableSet<Position>,
    /** relative path to log file from project root */
    var logfile: String
) {
    /**
     * Pretty-print the game state, only for debugging...
     */
    fun prettyPrint(): String {
        val buffer = StringBuffer()
        // some statistics about game turns, open orders, brokers, etc.
        buffer.append(if (turn > maxTurns) "FINAL\n" else String.format("TURN %d/%d\n", turn, maxTurns))

        buffer.append("Brokers\n")

        brokers.values.forEach { buffer.append("$it\n") }
        buffer.append("Orders\n")
        orders.values.sortedBy { it.created }.forEach { buffer.append("$it\n") }

        // what is where?
        val elements = mutableMapOf<Position, Any>()
        orders.values.filter { it.completed == null && it.created <= turn && it.deadline > turn }
            .forEach { elements[it.position] = it }  // associateBy?
        brokers.values.flatMap { it.workers }.forEach { elements[it.position] = it }

        // print the grid
        for (y in 0 until size.y) {
            for (x in 0 until size.x) {
                val p = Position(x, y)
                val at = elements[p]
                buffer.append(when {
                    at is Order    -> "\t${at.id}"
                    at is Worker   -> "\t${at.id}"
                    p in obstacles -> "\t#"
                    else           -> "\t."
                })
            }
            buffer.append("\n")
        }
        return buffer.toString()
    }

    /**
     * Save the Pretty-printed game state, in log file
     */
    fun log() {
        try {
            File(this.logfile).appendText("${this.prettyPrint()}\n")
        } catch (e: IOException){
            System.err.println("Can't open logfile $e")
        }
    }



}

data class Broker (
    val id: String,
    val workers: List<Worker>,
    val takenOrders: MutableSet<String>,
    val completedOrders: MutableSet<String>,
    val failedOrders: MutableSet<String>,
    val collectedOrders: MutableSet<String>
)

data class Worker(
    val id: String,
    val brokerId: String,
    var position: Position,
    var steps: Int,
    var lastTurn: Int,
    var assignedOrders: List<String>
)

data class Order(
    val id: String,
    val position: Position,
    /** value of this Order, will be awarded to successful Broker */
    val value: Int,
    /** turn when the Order was created or announced */
    val created: Int,
    /** penalty to be subtracted from value in each turn before completion */
    val turnPenalty: Int,
    /** turn when the Order will have failed */
    val deadline: Int,
    /** turn when the Order was completed, or -1 */
    var completed: Int?
) {
    /**
     * Get the current reward for the order at given game turn.
     *
     * - not completed? MINUS value if past deadline, otherwise 0
     * - completed? value minus turn penalty times turns, but not less than 0
     */
    fun reward(turn: Int) =
        if (completed == null) {
            if (turn > deadline) { - value } else { 0 }
        } else {
            max(value - (completed!! - created) * turnPenalty, 0)
        }
}


data class Position(val x: Int, val y: Int) {
    companion object {
        /** get new random position within the bounds defined by the size parameter. */
        fun randomPosition(size: Position) = Position(Random.nextInt(size.x), Random.nextInt(size.y))
    }

    /** Manhattan-distance from here to some other position */
    fun distance(other: Position) = abs(other.x - this.x) + abs(other.y - this.y)

    /**
     * Create and return new (optional) position for given move and grid size. The size
     * is optional and used for checking the maximum values, can be null if you know the
     * position will be valid (e.g. after a WorkerConfirm message)
     */
    fun applyMove(action: WorkerAction, size: Position? = null): Position? {
        var x2 = this.x
        var y2 = this.y
        when (action) {
            WorkerAction.NORTH -> y2--
            WorkerAction.SOUTH -> y2++
            WorkerAction.WEST  -> x2--
            WorkerAction.EAST  -> x2++
            else -> {}
        }
        return if (size == null || (0 <= x2 && x2 < size.x && 0 <= y2 && y2 < size.y)) {
            Position(x2, y2)
        } else {
            null
        }
    }
}
