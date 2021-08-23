%dw 2.0
import * from dw::test::Tests
import * from dw::test::Asserts
---
"SecondTestSuite" describedBy [
    "SecondTestSuiteFirstTest" in do {
        {} must beObject()
    }
]
