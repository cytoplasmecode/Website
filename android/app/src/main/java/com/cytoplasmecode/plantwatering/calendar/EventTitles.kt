package com.cytoplasmecode.plantwatering.calendar

internal fun pendingEventTitle(plantName: String) = "Water $plantName"

internal fun doneEventTitle(userName: String, plantName: String) = "DONE by $userName - $plantName"
