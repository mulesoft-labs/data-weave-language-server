%dw 2.0
import * from dw::test::Tests
import * from dw::test::Asserts
import tests_0 from testSpec

---
"Multiplication" describedBy
      (tests_0 map ((value, index) -> () -> value.name in do {0 * value.value must equalTo(0)}))
