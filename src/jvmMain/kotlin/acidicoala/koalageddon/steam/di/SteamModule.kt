package acidicoala.koalageddon.steam.di

import acidicoala.koalageddon.core.use_case.*
import acidicoala.koalageddon.steam.domain.use_case.ReloadSteamConfig
import acidicoala.koalageddon.steam.ui.SteamGameScreenModel
import acidicoala.koalageddon.steam.ui.SteamScreenModel
import org.kodein.di.*

val steamModule = DI.Module(name = "Steam") {
    bindProvider { ReloadSteamConfig(di) }
    bindSingleton { SteamScreenModel(di) }
    bindSingleton { SteamGameScreenModel(di) }
}