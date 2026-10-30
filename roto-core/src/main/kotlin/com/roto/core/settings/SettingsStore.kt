package com.roto.core.settings

import java.nio.file.Files
import java.nio.file.Path

/**
 * Where the artist's [Settings] are kept: [file], by default `settings.json` in the operating
 * system's per-user configuration folder, so the desktop app and the command line share one library
 * choice. A missing file is the empty settings; a damaged one is a [SettingsException].
 */
class SettingsStore(val file: Path) {

    fun load(): Settings = if (Files.isRegularFile(file)) SettingsCodec.read(file) else Settings()

    fun save(settings: Settings) {
        SettingsCodec.write(settings, file)
    }

    companion object {
        private const val APP_FOLDER = "Roto"
        private const val UNIX_APP_FOLDER = "roto"

        /** The store in this user's configuration folder on this machine. */
        fun default(): SettingsStore = SettingsStore(
            folder(System.getProperty("os.name"), System.getenv(), Path.of(System.getProperty("user.home")))
                .resolve(SettingsCodec.fileName),
        )

        /**
         * The per-user configuration folder Roto's settings live in, for an operating system called
         * [osName] with [environment] and the user's [home]: `%APPDATA%\Roto` on Windows,
         * `~/Library/Application Support/Roto` on macOS, otherwise `$XDG_CONFIG_HOME/roto`, which
         * defaults to `~/.config/roto`.
         */
        fun folder(osName: String, environment: Map<String, String>, home: Path): Path {
            val os = osName.lowercase()
            return when {
                os.startsWith("windows") ->
                    (environment["APPDATA"]?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
                        ?: home.resolve("AppData").resolve("Roaming")).resolve(APP_FOLDER)
                os.startsWith("mac") -> home.resolve("Library").resolve("Application Support").resolve(APP_FOLDER)
                else ->
                    (environment["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
                        ?: home.resolve(".config")).resolve(UNIX_APP_FOLDER)
            }
        }
    }
}
