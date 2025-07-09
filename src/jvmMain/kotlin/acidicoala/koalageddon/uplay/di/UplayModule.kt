package acidicoala.koalageddon.uplay.di

import acidicoala.koalageddon.uplay.ui.UplayScreenModel
import acidicoala.koalageddon.uplay.ui.UplayGameScreenModel
import acidicoala.koalageddon.core.use_case.InstallUplaySleepApiUnlocker
import acidicoala.koalageddon.core.use_case.InstallUplayGameUnlocker
import acidicoala.koalageddon.uplay.model.UplayGamesCache
import acidicoala.koalageddon.uplay.domain.use_case.DetectUplayGames
import acidicoala.koalageddon.uplay.domain.use_case.UninstallUplayGameUnlocker
import org.kodein.di.DI
import org.kodein.di.bindProvider
import org.kodein.di.bindSingleton
 
val uplayModule = DI.Module(name = "Uplay") {
    bindProvider { UplayScreenModel(di) }
    bindSingleton { UplayGameScreenModel(di) }
    bindProvider { InstallUplaySleepApiUnlocker(di) }
    bindProvider { InstallUplayGameUnlocker(di) }
    bindProvider { UninstallUplayGameUnlocker(di) }
    bindProvider { UplayGamesCache(di) }
    bindProvider { DetectUplayGames(di) }
} 