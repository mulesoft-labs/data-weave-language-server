%dw 2.0
import * from dw::test::Tests
import * from dw::test::Asserts
---
"Test MyTest" describedBy [
    "It should ..." in do {
        {} must beObject()
    }
]
