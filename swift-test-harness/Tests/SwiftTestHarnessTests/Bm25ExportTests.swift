#if canImport(Testing)
import Testing
import Bm25

@Suite("Bm25 Swift Export Tests")
struct Bm25ExportTests {
    @Test("Swift module loads")
    func testSwiftModuleLoads() {
        #expect(Bool(true), "Bm25 swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import Bm25

final class Bm25ExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "Bm25 swift module imported cleanly")
    }
}
#endif
