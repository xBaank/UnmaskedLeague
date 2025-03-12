package unmaskedLeague

import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process

suspend fun isRiotClientRunning(): Boolean {
    val process = process(
        "tasklist",
        stdout = Redirect.CAPTURE
    )
    return process.output.any { it.contains("RiotClientServices.exe") }
}

suspend fun killRiotClient() {
    process("taskkill", "/F", "/IM", "RiotClientServices.exe")
}