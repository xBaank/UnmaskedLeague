package unmaskedLeague

import arrow.core.getOrElse
import arrow.core.raise.either
import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import com.github.pgreze.process.unwrap
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import okio.FileSystem
import simpleJson.asArray
import simpleJson.asString
import simpleJson.deserialized
import simpleJson.get
import java.io.File
import java.security.MessageDigest

fun extractResourceToFile(): File {
    val file = (unmaskedLeagueFolder / "ManifestDownloader.exe").toFile()

    // Load resource into memory
    val bytes = object {}.javaClass.classLoader
        .getResourceAsStream("ManifestDownloader.exe")
        ?.readAllBytes()
        ?: error("Manifest downloader not found")

    // Compute SHA-256 of resource bytes
    val resourceHash = MessageDigest.getInstance("SHA-256").digest(bytes)

    if (file.exists()) {
        // Compute SHA-256 of existing file
        val fileHash = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        if (resourceHash.contentEquals(fileHash)) {
            return file // identical, no need to overwrite
        }
    }

    // Write resource to disk
    file.writeBytes(bytes)
    return file
}


suspend fun downloadLatestSystemYaml(region: String) {
    try {
        extractResourceToFile()
        val response =
            configClient.get("https://clientconfig.rpg.riotgames.com/api/v1/config/public?os=windows&region=$region&app=league_of_legends&version=1&patchline=$patchLine")
        require(response.status.isSuccess())
        val body = response.bodyAsText().deserialized().getOrElse { throw it }
        val configurations =
            body["keystone.products.league_of_legends.patchlines.$patchLine"]["platforms"]["win"]["configurations"]
        val manifestUrl = either {
            configurations.asArray().bind().first { it["id"].asString().bind() == region }["patch_url"].asString()
                .bind()
        }.getOrElse { throw it }

        val process = process(
            (unmaskedLeagueFolder / "ManifestDownloader.exe").toString(),
            manifestUrl,
            "--filter",
            "system.yaml",
            "--no-langs",
            "-o",
            companionPath.toString(),
            stderr = Redirect.CAPTURE,
            stdout = Redirect.CAPTURE
        )

        logger.info { process.output.joinToString("") }
        process.unwrap()
    } catch (exception: Exception) {
        logger.error { exception.message }
        //Fallback
        FileSystem.SYSTEM.copy(systemYamlOriginalPath, systemYamlPatchedPath)
    }
}
