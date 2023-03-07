package de.dailab.jiacvi.aot.gridworld

import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.behaviour.act
import java.time.Duration

const val serverName = "server"


class ServerAgent(
		private val takeOrderTimeout: Int = 3,
		private val revealObstacles: Boolean = false,
		private val revealOrders: Boolean = false,
		private val logGames: Boolean = false,
		private val logFile: String = ""
): Agent(overrideName = serverName) {

	private val activeGames = mutableMapOf<String, GridworldGame>()

	override fun behaviour() = act {

		respond<StartGameMessage, StartGameResponse> { msg ->
			log.info("Received StartGameMessage $msg")

			// start with turn = -1 so that after increment in execute the first turn is 0
			val game = Util.loadGameFromFile(msg.gridFile, msg.brokerId)
			game.turn = -1
			if (logFile.isNotEmpty()) game.logfile=logFile

			activeGames[msg.brokerId] = game

			val broker = game.brokers[msg.brokerId]!!
			broker.takenOrders.addAll(broker.workers.flatMap { it.assignedOrders })

			// send response
			StartGameResponse(game.size, broker.workers,
					if (revealObstacles) game.obstacles.toList() else null,
					if (revealOrders) game.orders.values.toList() else null
			)
		}

		// manage all active games
		every(Duration.ofSeconds(1)) {
			val finishedGames = mutableListOf<String>()

			for ((brokerId, game) in activeGames.entries) {
				game.turn++
				if (game.turn > game.maxTurns) {
					finishGame(game)
					finishedGames.add(brokerId)
				} else {
					playGame(game)
				}
				if (logGames) game.log()
				println("\n${game.prettyPrint()}\n")
			}

			for (brokerId in finishedGames) {
				activeGames.remove(brokerId)
			}
		}

		respond<GameTurnMessage, Int> { msg ->
			log.info("Received GameTurnMessage $msg")
			if (!hasGameActive(msg.brokerId)) {return@respond -1}

			val game = activeGames[msg.brokerId]!!
			game.turn
		}

		respond<TakeOrderMessage, Boolean> { msg ->
			log.info("Received TakeOrderMessage $msg")
			if (!hasGameActive(msg.brokerId)) {return@respond false}

			val game = activeGames[msg.brokerId]!!
			val order = game.orders[msg.orderId]!!
			if (game.turn <= order.created + takeOrderTimeout) {
				// set order as "taken" for the broker
				game.brokers[msg.brokerId]!!.takenOrders.add(order.id)
				true
			} else {
				// too late...
				false
			}
		}

		respond<CollectOrderMessage, Boolean> { msg ->
			log.info("Received CollectOrderMessage $msg")
			if (!hasGameActive(msg.brokerId)) {return@respond false}

			val game = activeGames[msg.brokerId]!!
			val broker = game.brokers[msg.brokerId]!!
			val order = game.orders[msg.orderId]!!

			if (broker.completedOrders.contains(order.id)) {
				// set order as "collected" for the broker
				broker.collectedOrders.add(order.id)
				true
			} else {
				// order can't be collected
				false
			}
		}

		respond<WorkerMessage, Boolean> { msg ->
			log.info("Received WorkerMessage $msg")
			if (!hasGameActive(msg.brokerId)) {return@respond false}


			val game = activeGames[msg.brokerId]!!
			val broker = game.brokers[msg.brokerId]!!
			val worker = broker.workers.find { it.id == msg.workerId }!!

			// make sure each worker can only do one action per game turn
			if (worker.lastTurn >= game.turn) {
				false
			} else {
				worker.lastTurn = game.turn

				// if action is "order", check if order in list of taken orders
				if (msg.action == WorkerAction.ORDER) {
					// get order on that position, if any
					val order = broker.takenOrders.map { game.orders[it] }.find {it?.position == worker.position}
					// there is an order -> register order as completed
					if (order != null) {
						broker.takenOrders.remove(order.id)
						broker.completedOrders.add(order.id)
						order.completed = game.turn
						// reply with success to worker
						true
					} else {
						// no matching order -> send "fail" to worker
						false
					}
				} else {
					// if action is direction, check if it is valid
					// update worker's position, step; send "okay" or "fail" accordingly
					val newPos = worker.position.applyMove(msg.action, game.size)
					if (newPos != null && newPos !in game.obstacles) {
						worker.position = newPos
						worker.steps++
						true
					} else {
						false
					}
				}
			}
		}
	}


	private fun hasGameActive(brokerId: String): Boolean {
		if (activeGames[brokerId] != null) return true
		log.warn("$brokerId has no active game")
		return false
	}

	private fun playGame(game: GridworldGame) {
		// check for new orders to publish
		val readyOrders = game.orders.values.filter { it.created == game.turn }
		for (order in readyOrders) {
			game.brokers.values.forEach {
				val ref = system.resolve(it.id)
				ref tell OrderMessage(order)
			}
		}


		// check orders past deadline
		for (broker in game.brokers.values) {

			// get this broker's orders that are now past their deadlines
			val doneOrders = broker.takenOrders.map { game.orders[it]!! }.filter { game.turn > it.deadline }
			for (order in doneOrders) {
				broker.takenOrders.remove(order.id)
				broker.failedOrders.add(order.id)
			}
		}
	}

	private fun finishGame(game: GridworldGame) {
		// count still open orders as failed
		for (broker in game.brokers.values) {
			broker.failedOrders.addAll(broker.takenOrders)
			broker.takenOrders.clear()
		}

		// evaluate total scores of all Brokers
		val scores: Map<String, Int> = game.brokers.values.associateBy(
				{ it.id },
				{ (it.collectedOrders + it.failedOrders).sumBy { x -> game.orders[x]!!.reward(game.turn) } }
		)
		// send EndGameMessage to all brokers
		for ((rank, entry) in scores.entries.sortedByDescending { it.value }.withIndex()) {
			val ref = system.resolve(entry.key)
			ref tell EndGameMessage(entry.value, rank)
		}
	}
}
