package acidicoala.koalageddon.uplay.model

import acidicoala.koalageddon.core.model.ISA
import acidicoala.koalageddon.core.use_case.GameInstallationStatus

data class UplayGame(
    val executablePath: String,
    val gameName: String,
    val architecture: ISA,
    val installationStatus: GameInstallationStatus
)