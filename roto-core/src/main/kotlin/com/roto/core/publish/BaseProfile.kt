package com.roto.core.publish

import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileFolder

/**
 * The profile a variant recolours, as [Publisher] needs it: the profile and where its handoffs are.
 *
 * @property profile the base profile
 * @property folder the base profile's folder
 */
class BaseProfile(val profile: Profile, val folder: ProfileFolder)
