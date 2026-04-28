import Foundation
import Shared

/// Bridges Kotlin/Native `ByteArray` (exposed to Swift as `KotlinByteArray`,
/// a class with `.size` and `.get(index:)` returning `Int8`) into a Swift
/// `Data` value the ObjC++ MoonRenderer can consume as `NSData`.
///
/// The K/N runtime exposes ByteArray's contents element-by-element through
/// `get(index:) -> KotlinByte` (an `Int8`). There is no public bulk-copy
/// API today (a future Kotlin/Native release may add one), so we read each
/// byte and reinterpret its bit pattern as `UInt8`. For the spike's three
/// assets (~1.4 MB albedo + ~1.4 MB normal + ~734 KB material) the per-byte
/// loop runs in well under a second on the main thread, which is fine for
/// app-startup asset push.
extension KotlinByteArray {
    func toData() -> Data {
        let count = Int(self.size)
        var data = Data(count: count)
        data.withUnsafeMutableBytes { (rawPtr: UnsafeMutableRawBufferPointer) in
            guard let base = rawPtr.baseAddress else { return }
            let dst = base.assumingMemoryBound(to: UInt8.self)
            for i in 0..<count {
                dst[i] = UInt8(bitPattern: self.get(index: Int32(i)))
            }
        }
        return data
    }
}
