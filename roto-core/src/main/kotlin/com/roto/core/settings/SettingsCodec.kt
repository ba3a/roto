package com.roto.core.settings

import com.roto.core.json.JsonCodec

/** Reads and writes [Settings] as `settings.json` in the [SettingsStore]'s folder; a bad file is a [SettingsException]. */
object SettingsCodec : JsonCodec<Settings>(Settings.serializer(), "settings.json", ::SettingsException)
