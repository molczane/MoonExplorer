package org.jetbrains.moonexplorer.assets

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidStorageDir(context: Context) : StorageDir {
    private val rootDir: File = context.filesDir.also { it.mkdirs() }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(rootDir, name)
        if (file.isFile) file.readBytes() else null
    }

    override suspend fun writeAtomically(name: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val target = File(rootDir, name)
            val tmp = File(rootDir, "$name.tmp")
            tmp.writeBytes(bytes)
            // rename(2) is atomic on ext4 / f2fs — every Android filesystem in 2026.
            if (!tmp.renameTo(target)) {
                tmp.delete()
                error("AndroidStorageDir: rename ${tmp.name} -> ${target.name} failed")
            }
        }
    }

    override fun exists(name: String): Boolean = File(rootDir, name).isFile

    override fun delete(name: String) {
        File(rootDir, name).delete()
    }
}
