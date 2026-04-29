package org.jetbrains.moonexplorer.assets

/**
 * In-memory [StorageDir] for tests. Atomic-write semantics are trivially atomic since the
 * map's `set` is a single operation and tests aren't concurrent. Tracks call counts so tests
 * can assert "wrote N times" / "read N times" behaviour where it matters.
 */
class FakeStorageDir : StorageDir {
    val files: MutableMap<String, ByteArray> = mutableMapOf()
    var readCount: Int = 0
    var writeCount: Int = 0
    var deleteCount: Int = 0

    override suspend fun read(name: String): ByteArray? {
        readCount++
        return files[name]?.copyOf()
    }

    override suspend fun writeAtomically(name: String, bytes: ByteArray) {
        writeCount++
        files[name] = bytes.copyOf()
    }

    override fun exists(name: String): Boolean = files.containsKey(name)

    override fun delete(name: String) {
        deleteCount++
        files.remove(name)
    }
}
