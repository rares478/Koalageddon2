package acidicoala.koalageddon.core.use_case

import com.sun.jna.Memory
import com.sun.jna.platform.win32.Version
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import org.kodein.di.DI
import org.kodein.di.DIAware
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

class SmokeApiDetector(override val di: DI) : DIAware {
    fun isSmokeApiDll(file: File): Boolean {
        if (!file.exists() || !file.extension.equals("dll", ignoreCase = true)) return false
        return getProductName(file) == "SmokeAPI"
    }

    fun getProductName(file: File): String? {
        val path = Path(file.absolutePath)
        Version.INSTANCE.apply {
            val absolutePath = path.absolutePathString()
            val bufferSize = GetFileVersionInfoSize(absolutePath, null)
            if (bufferSize == 0) return null
            val buffer = Memory(bufferSize.toLong())
            if (!GetFileVersionInfo(absolutePath, 0, bufferSize, buffer)) return null
            val stringPointer = PointerByReference()
            val stringSize = IntByReference()
            val subBlock = "\\StringFileInfo\\040904E4\\ProductName"
            if (!Version.INSTANCE.VerQueryValue(buffer, subBlock, stringPointer, stringSize)) return null
            val pointer = stringPointer.value ?: return null
            return pointer.getWideString(0)
        }
    }
} 