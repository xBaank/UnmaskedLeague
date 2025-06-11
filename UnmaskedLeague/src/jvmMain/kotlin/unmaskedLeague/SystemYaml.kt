package unmaskedLeague

import io.ktor.client.request.*
import io.ktor.client.statement.*
import simpleJson.deserialized
import java.io.File
import java.io.InputStream

fun extractResourceToFile(): File {
    val inputStream: InputStream? = object {}.javaClass.getResourceAsStream("ManifestDownloader.exe")
    requireNotNull(inputStream) { "Manifest downloader not found" }

    val file = (unmaskedLeagueFolder / "ManifestDownloader.exe").toFile()
    file.outputStream().use { output ->
        inputStream.copyTo(output)
    }

    return file
}

suspend fun downloadLatestSystemYaml(region: String) {
    val response =
        client.get("https://clientconfig.rpg.riotgames.com/api/v1/config/public?os=windows&region=$region&app=league_of_legends&version=1&patchline=live")
    val body = response.bodyAsText().deserialized().getOrNull()
}
