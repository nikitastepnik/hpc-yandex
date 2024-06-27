/***********************
Implement the functions/jobs for Pipeline:
SingleHash[job] = crc32(data)+"~"+crc32(md5(data))
MultiHash[job] = join(crc32(th+data), ""); th = 0..5
CombineResults[job] = join(sort(results), "_")
crc32[fun] = implement with use DataSignerCrc32
md5[fun] = implement with use DataSignerMd5
 ***********************/
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList


val singleHash = Job { input: Channel<String>, output: Channel<String> ->
    coroutineScope {
        val tasks = mutableListOf<Pair<Deferred<String>, Deferred<String>>>()
        for (data in input) {
            val md5 = dataSignerMd5(data)
            val crcDataDeferred = async { dataSignerCrc32(data) }
            val crcMd5Deferred = async { dataSignerCrc32(md5) }
            tasks.add(crcDataDeferred to crcMd5Deferred)
        }
        for (task in tasks) {
            output.send("${task.first.await()}~${task.second.await()}")
        }
    }
}

val multiHash = Job { input: Channel<String>, output: Channel<String> ->
    coroutineScope {
        val resultsDeferred = mutableListOf<List<Deferred<String>>>()
        for (data in input) {
            val totalHash = mutableListOf<Deferred<String>>()
            for (th in 0..5) {
                val hashPartDeferred = async { dataSignerCrc32("$th$data") }
                totalHash.add(hashPartDeferred)
            }
            resultsDeferred.add(totalHash)
        }
        for (resDeffer in resultsDeferred) {
            output.send(resDeffer.awaitAll().joinToString(separator = ""))
        }

    }
}


val combineResults = Job { input: Channel<String>, output: Channel<String> ->
    val list = arrayListOf<String>()
    input.consumeEach { element ->
        list.add(element)
    }
    output.send(list.sorted().joinToString(separator = "_"))
}
