package com.roto.core.profile

import com.roto.core.json.JsonCodec

/**
 * Reads and writes [Profile] as `profile.json`, which sits in the profile's library folder; a bad file
 * is a [ProfileException].
 */
object ProfileCodec : JsonCodec<Profile>(Profile.serializer(), "profile.json", ::ProfileException)
