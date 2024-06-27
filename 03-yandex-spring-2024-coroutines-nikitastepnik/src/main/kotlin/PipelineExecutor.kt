/***********************
Implement the PipelineExecutor class with method execute.
The execute method takes jobs as a parameter and creates channels for them and starts processing
 ***********************/


import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class PipelineExecutor<T> {
    fun execute(vararg jobs: Job<T>) = runBlocking {
        val inputsChannels = mutableListOf(Channel<T>())
        for (i in jobs.indices) {
            val outputChannel = Channel<T>()
            launch {
                jobs[i].run(inputsChannels[i], outputChannel)
                outputChannel.close()
            }
            inputsChannels.add(outputChannel)
        }
    }
}
