package com.roto.core.profile

import com.roto.core.template.SafeName
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * The profiles of one project in the library folder: `<library>/<projectId>/<profile id>/`, each a
 * [ProfileFolder]. The library lives outside every repository and is shared by all projects; the
 * project id comes from the project's `roto.json`. Profile ids follow [SafeName], since they become
 * folder names and the `{profile}` value in output paths.
 */
class ProfileRepository(library: Path, projectId: String) {

    /** `<library>/<projectId>`, holding one folder per profile. */
    val projectFolder: Path

    init {
        SafeName.validate(projectId, "Project id")
        projectFolder = library.toAbsolutePath().normalize().resolve(projectId)
    }

    /** The ids of every profile in the project, in sorted order; folders without a profile are skipped. */
    fun ids(): List<String> {
        if (!projectFolder.isDirectory()) return emptyList()
        return projectFolder.listDirectoryEntries()
            .filter { it.isDirectory() && SafeName.isValid(it.name) && folder(it.name).profileFile.isRegularFile() }
            .map { it.name }
            .sorted()
    }

    fun exists(id: String): Boolean = folder(id).profileFile.isRegularFile()

    /** The folder profile [id] lives in, whether or not it exists yet. */
    fun folder(id: String): ProfileFolder {
        SafeName.validate(id, "Profile id")
        return ProfileFolder(projectFolder.resolve(id))
    }

    /**
     * Creates profile [id] with an empty `sources/` and `handoff/`, and returns it. Fails if a profile
     * with that id exists, even one differing only in case, which would share its folder on Windows.
     */
    fun create(id: String): Profile {
        val clash = ids().firstOrNull { it.equals(id, ignoreCase = true) }
        if (clash != null) throw FileAlreadyExistsException(folder(clash).path.toString(), null, "Profile exists")
        val folder = folder(id)
        Files.createDirectories(folder.sourcesFolder)
        Files.createDirectories(folder.handoffFolder)
        val profile = Profile()
        save(id, profile)
        return profile
    }

    /** Reads profile [id]; a missing profile is a `NoSuchFileException`, a bad one a [ProfileException]. */
    fun load(id: String): Profile = ProfileCodec.read(folder(id).profileFile)

    /** Replaces profile [id]'s `profile.json` with [profile], atomically. */
    fun save(id: String, profile: Profile) {
        ProfileCodec.write(profile, folder(id).profileFile)
    }
}
