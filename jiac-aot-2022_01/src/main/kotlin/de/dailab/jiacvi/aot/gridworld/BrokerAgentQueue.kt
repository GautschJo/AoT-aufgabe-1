package de.dailab.jiacvi.aot.gridworld;

import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.behaviour.act

class BrokerAgentQueue(private val brokerId: String): Agent(overrideName=brokerId) {

    private var workers = emptyList<Worker>()
    private var count = 0
    override fun preStart() {
        val serverRef = system.resolve(serverName)
        val game = StartGameMessage(brokerId, "/main/resources/grids/example.grid")
        serverRef invoke ask<StartGameResponse>(game) {
            workers = it.initialWorkers
            for (worker in workers){
                val workerRef = system.resolve(worker.id)
                workerRef tell SetInitial(brokerId, worker.position)
            }
        }
    }
    override fun behaviour() = act {
        /* TODO most of your broker logic belongs in here. See the README.md on how to use the behavior in JIACVI
        * - you will need to find a way talk to your worker agents
        * - you need to start a game from here (the server id is saved in "serverName")
        */
        on<OrderMessage> { it ->
            val order = it.order



            // give order to the next worker in the queue
            val serverRef = system.resolve(serverName)
            val workerRef = system.resolve(workers[count].id)
            count = (count + 1) % workers.size

            serverRef invoke ask<Boolean>(TakeOrderMessage(brokerId, order.id)) {
                if (it) {
                    workerRef tell WorkerOrder(order)
                }
            }
        }

        on<OrderCompleted> {
            val orderId = it.order.id
            val workerRef = system.resolve(it.workerId)
            val serverRef = system.resolve(serverName)
            serverRef invoke ask<Boolean>(CollectOrderMessage(brokerId, orderId)) { accepted ->
                if (accepted) {
                    workerRef tell ConfirmCompleted(it.order)
                }
            }
        }
        on<EndGameMessage>{
            println("reward: ${it.totalReward},\nrank: ${it.rank}")
        }
    }
}


