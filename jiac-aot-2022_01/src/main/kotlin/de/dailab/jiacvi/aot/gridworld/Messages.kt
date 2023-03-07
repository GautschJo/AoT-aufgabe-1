package de.dailab.jiacvi.aot.gridworld

/*
 * GAME MANAGEMENT PROTOCOL (Server <-> Broker)
 */

// Broker -> Server (ask), replied with StartGameResponse
data class StartGameMessage(val brokerId: String, val gridFile: String)
data class StartGameResponse(
    val size: Position,
    val initialWorkers: List<Worker>,
    val obstacles: List<Position>?,
    val orders: List<Order>?
)

// Server -> Broker (tell)
data class EndGameMessage(val totalReward: Int, val rank: Int)

// Broker -> Server (ask) current game turn, replied with Int
data class GameTurnMessage(val brokerId: String)

/*
 * ORDERS PROTOCOL (Server <-> Broker)
 */

// Server -> Broker (tell)
data class OrderMessage(val order: Order)

// Broker -> Server (ask), replied with Boolean
data class TakeOrderMessage(val brokerId: String, val orderId: String)

// Broker -> Server (ask), replied with Boolean
data class CollectOrderMessage(val brokerId: String, val orderId: String)

/*
 * WORKER MOVEMENT PROTOCOL (Worker <-> Server)
 */

// Worker -> Server (ask), replied with Boolean
data class WorkerMessage(val brokerId: String, val workerId: String, val action: WorkerAction)

