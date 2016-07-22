/*
 * Copyright 2015 Dennis Vriend
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.dnvriend

import scala.xml.XML

class ProcessLargeXmlUsingScalaXmlTest extends TestSpec {

  "Loading a big XML file with the Scala XML loader" should "consume a lot of resources" ignore {
    Given("A very large XML file (44MB)")
    withInputStream("lot-of-orders.xml") { is =>
      val threeHundredMegaBytes: Long = 300 * 1024 * 1024
      val memBefore = allocatedMemory
      When("The XML file is converted to a Seq of Elem using the strict loader of Scala XML")
      val xx = XML.load(is)
      val memAfter = allocatedMemory
      Then("It consumes a lot of memory, more than 300MB")
      memAfter - memBefore should be > threeHundredMegaBytes
    }
  }
}
