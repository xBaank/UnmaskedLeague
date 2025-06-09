package unmaskedLeague

import java.nio.file.Paths


fun isLockfileTaken(): Boolean {
    val userProfile = System.getenv("USERPROFILE") ?: error("USERPROFILE not found")
    val lockfilePath = Paths.get(userProfile,"UnmaskedLeague" ,"lockfile").toFile()

    if (lockfilePath.exists()) {
        val existingPid = lockfilePath.readText().trim().toLongOrNull()
        if (existingPid != null) {
            val processHandle = ProcessHandle.of(existingPid)
            if (processHandle.isPresent && processHandle.get().isAlive) {
                logger.info {"Another instance is already running with PID $existingPid. Exiting." }
                return true
            } else {
                logger.info {"Stale lockfile found. Overwriting..."}
            }
        }
    }

    val currentPid = ProcessHandle.current().pid()
    lockfilePath.writeText(currentPid.toString())
    logger.info {"Lock acquired. Running with PID $currentPid."}

    // Add shutdown hook to remove lockfile on exit
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info {"Cleaning up lockfile..."}
        lockfilePath.delete()
    })

    return false
}