package unmaskedLeague

import kotlin.jvm.optionals.getOrNull


fun isLockfileTaken(): Boolean {
    val lockfilePath = (unmaskedLeagueFolder / "lockfile").toFile()

    if (lockfilePath.exists()) {
        val existingPid = lockfilePath.readText().trim().toLongOrNull()
        if (existingPid != null) {
            val processHandle = ProcessHandle.of(existingPid)
            if (processHandle.isPresent && processHandle.getOrNull()?.isAlive == true) {
                logger.info { "Another instance is already running with PID $existingPid. Exiting." }
                return true
            } else {
                logger.info { "Stale lockfile found. Overwriting..." }
            }
        }
    }

    val currentPid = ProcessHandle.current().pid()
    lockfilePath.writeText(currentPid.toString())
    logger.info { "Lock acquired. Running with PID $currentPid." }

    // Add shutdown hook to remove lockfile on exit
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Cleaning up lockfile..." }
        lockfilePath.delete()
    })

    return false
}