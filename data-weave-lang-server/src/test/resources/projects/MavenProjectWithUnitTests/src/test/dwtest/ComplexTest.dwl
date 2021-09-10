%dw 2.0
import * from dw::test::Tests
import * from dw::test::Asserts
---
"Multiplication" describedBy
      ([
         {name: "zero", value: 0},
         {name: "two", value: 2},
         {name: "minus Ten", value: -10},
      ] map ((value, index) -> () -> value.name in do {0 * value.value must equalTo(0)}))
