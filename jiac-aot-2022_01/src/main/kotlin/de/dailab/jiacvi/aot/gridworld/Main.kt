package de.dailab.jiacvi.aot.gridworld

import de.dailab.jiacvi.communication.LocalBroker
import de.dailab.jiacvi.dsl.agentSystem

fun main() {


    agentSystem("Gridworld") {
        enable(LocalBroker)
        agents {
            // you can set logGames=true, logFile="logs/<name>.log" here
            add(ServerAgent())

            // TODO change the amount and type of Agents here. You can change the Id's how you want.
            add(BrokerAgent("broker"))
            add(WorkerAgentRandom("w11"))
            add(WorkerAgentRandom("w12"))
            add(WorkerAgentRandom("w13"))
            add(WorkerAgentRandom("w14"))
            add(WorkerAgentRandom("w15"))

            //add ...



        }

    }.start()
}
