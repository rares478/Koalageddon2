package acidicoala.koalageddon.core.use_case

import acidicoala.koalageddon.core.model.ISA
import java.io.File

class DetectGameArchitecture {
    operator fun invoke(gameFile: File): ISA {
        return try {
            // Read PE header to determine architecture
            gameFile.inputStream().use { input ->
                input.skip(0x3C) // Skip to PE header offset
                val peOffset = (input.read() or (input.read() shl 8) or (input.read() shl 16) or (input.read() shl 24)).toLong()
                input.skip(peOffset - 0x40) // Skip to PE signature
                // Read PE signature
                val peSignature = ByteArray(4)
                input.read(peSignature)
                if (peSignature.contentEquals(byteArrayOf(0x50, 0x45, 0x00, 0x00))) {
                    // Read machine type
                    val machineType = input.read() or (input.read() shl 8)
                    return when (machineType) {
                        0x8664 -> ISA.X86_64
                        0x014C -> ISA.X86
                        else -> ISA.X86 // Default to X86 for unknown types
                    }
                }
            }
            ISA.X86 // Default fallback
        } catch (e: Exception) {
            // Fallback to filename-based detection
            if (gameFile.name.contains("64") || gameFile.name.contains("x64")) {
                ISA.X86_64
            } else {
                ISA.X86
            }
        }
    }
} 