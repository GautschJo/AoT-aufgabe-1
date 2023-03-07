package de.dailab.jiacvi.aot.gridworld


/*
 * WORKER <-> BROKER
 */

// Broker -> Worker
data class WorkerOrder(val order: Order)

// Broker -> Worker
// replied with Int (distance to order)
data class SetInitial(val broker: String, val position: Position) //Der Rest kommt noch

// Broker -> Worker
// confirm completion to worker after server accepts it
data class ConfirmCompleted(val order: Order)

data class MakeMove(val turn: Int)

// Worker -> Broker
data class OrderCompleted(val brokerId: String, val workerId: String, val order: Order)