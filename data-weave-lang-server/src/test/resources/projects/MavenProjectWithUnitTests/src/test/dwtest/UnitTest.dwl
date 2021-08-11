%dw 2.0
import * from dw::test::Tests
import * from dw::test::Asserts
---
"FirstTestSuite" describedBy [
    "FirstTestSuiteFirstTest" in do {
        {} must beObject()
    }
]
