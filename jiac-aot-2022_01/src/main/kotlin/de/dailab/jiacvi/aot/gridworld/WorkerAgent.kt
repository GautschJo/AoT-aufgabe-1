package de.dailab.jiacvi.aot.gridworld

import de.dailab.jiacvi.Agent
import de.dailab.jiacvi.behaviour.act
import java.time.Duration


/**
 * stub for your worker agent
 */

class WorkerAgent(private val workerId: String): Agent(overrideName=workerId) {
    // TODO you might need to some save some stuff here
    private var obstacle = intArrayOf(0,0,0,0) // NORTH, EAST, SOUTH, WEST
    private var brokerId = ""
    private var currentOrder: Order? = null
    private var currentPosition = Position(-1,-1)

    override fun behaviour() = act {
        /* TODO most of your worker logic belongs in here. See the README.md on how to use the behavior in JIACVI
        * - you need some type of connection to your broker
        * - you need some way to use different variations of your agent
        */

        on<SetInitial> { // every worker must get his own position and broker id from the broker
            brokerId= it.broker
            currentPosition = it.position
        }

        on<WorkerOrder>{
            currentOrder = it.order // set order
        }

        on<ConfirmCompleted>{
            currentOrder = null // become a free worker
        }
        every(Duration.ofSeconds(1)){
            val ref = system.resolve(serverName)
            val orderPosition = currentOrder?.position
            if (orderPosition != null && currentPosition != Position(-1, -1)){ // moving logic
                if (orderPosition.x == currentPosition.x && orderPosition.y == currentPosition.y){
                    // ask server to complete, then tell broker to collect the order
                    ref invoke ask<Boolean>(WorkerMessage(brokerId, workerId, WorkerAction.ORDER)) {
                        if (it) {
                            val brokerRef = system.resolve(brokerId)
                            brokerRef tell OrderCompleted(brokerId, workerId, currentOrder!!)
                        }
                    }
                }
                else if (orderPosition.x > currentPosition.x && obstacle[1] != 1){
                    ref invoke ask<Boolean>(WorkerMessage(brokerId, workerId, WorkerAction.EAST)) {
                        if (it){
                            currentPosition=Position(currentPosition.x+1,currentPosition.y)
                            obstacle = intArrayOf(0,0,0,0)
                        } // position update after a move
                        else {
                            obstacle[1] = 1
                            println("problem:\n $workerId cant move east")
                        }
                    }
                }
                else if (orderPosition.x < currentPosition.x && obstacle[3] != 1) {
                    ref invoke ask<Boolean>(WorkerMessage(brokerId, workerId, WorkerAction.WEST)) {
                        if (it){
                            currentPosition=Position(currentPosition.x-1,currentPosition.y)
                            obstacle = intArrayOf(0,0,0,0)
                        }
                        else{
                            obstacle[3] = 1
                            println("problem:\n $workerId cant move west")
                        }
                    }
                }
                else if (orderPosition.y > currentPosition.y && obstacle[2] != 1) {
                    ref invoke ask<Boolean>(WorkerMessage(brokerId, workerId, WorkerAction.SOUTH)) {
                        if (it){
                            obstacle = intArrayOf(0,0,0,0)
                            currentPosition=Position(currentPosition.x,currentPosition.y+1)
                        }
                        else {
                            obstacle[2] = 1
                            println("problem:\n $workerId cant move south $currentPosition")
                        }
                    }
                }
                else if (obstacle[0] != 1){
                    ref invoke ask<Boolean>(WorkerMessage(brokerId, workerId, WorkerAction.NORTH)) {
                        if (it){
                            obstacle = intArrayOf(0,0,0,0)
                            currentPosition=Position(currentPosition.x,currentPosition.y-1)
                        }
                        else{
                            obstacle[0] = 1
                            println("problem:\n $workerId cant move north")
                        }
                    }
                }
            }
        }
    }
}
