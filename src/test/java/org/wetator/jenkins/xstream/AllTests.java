/*
 * Copyright (c) 2008-2021 wetator.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.wetator.jenkins.xstream;

import org.junit.runner.JUnitCore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * @author frank.danek
 */
@RunWith(Suite.class)
@SuiteClasses({
// @formatter:off
  XStreamDeserializationTest.class,
  XStreamLegacyDeserializationTest.class,
  XStreamSerializationTest.class
  // @formatter:on
})
public final class AllTests {

  /**
   * @param anArgsArray ignored
   */
  public static void main(String[] anArgsArray) {
    JUnitCore.main(AllTests.class.getName());
  }

  /**
   * The constructor.
   */
  private AllTests() {
    // nothing
  }
}
