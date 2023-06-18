package com.zipoapps.level.view

enum class DisplayType(val displayFormat: String, val displayBackgroundText: String, val max: Double) {
    ANGLE("00.0", "88.8", 99.9),
    INCLINATION("000.0", "888.8", 999.9),
    ROOF_PITCH("00.00", "88.88", 99.99);
}