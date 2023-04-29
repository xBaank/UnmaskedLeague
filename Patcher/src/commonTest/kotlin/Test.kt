import patcher.manifest.ReleaseManifest
import java.io.FileInputStream
import kotlin.test.Test

class Test {
    @Test
    fun should_read(): Unit {
        val manifest =
            FileInputStream("""C:\ProgramData\Riot Games\Metadata\league_of_legends.live\league_of_legends.live.manifest""")
        val releaseManifestFile = ReleaseManifest(manifest)
        println(releaseManifestFile.body)
    }
}
