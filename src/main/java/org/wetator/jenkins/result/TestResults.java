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


package org.wetator.jenkins.result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.wetator.jenkins.Messages;

import hudson.model.AbstractBuild;

/**
 * @author frank.danek
 */
public class TestResults extends AbstractBaseResult {

  private static final long serialVersionUID = 5332974371295204003L;

  private List<TestResult> testResults = new ArrayList<>(); // NOPMD
  private List<String> reportFiles = new ArrayList<>();
  private transient List<BrowserResult> passedTests = new ArrayList<>();
  private transient List<BrowserResult> skippedTests = new ArrayList<>();
  private transient List<BrowserResult> failedTests = new ArrayList<>();
  private transient int totalCount;
  private transient int passCount;
  private transient int skipCount;
  private transient int failCount;
  private transient Map<String, TestFileResult> testFileMap = new LinkedHashMap<>();
  private transient Map<String, TestFileResult> testFileUrlMap = new LinkedHashMap<>();

  /**
   * The constructor.
   *
   * @param name the name to set
   */
  public TestResults(String name) { // NOPMD
    this.name = name;
  }

  @Override
  public String getDisplayName() {
    return Messages.WetatorBuildReport_DisplayName();
  }

  @Override
  public String getUrl() {
    return "";
  }

  /**
   * @return the reportFiles
   */
  public List<String> getReportFiles() {
    return reportFiles;
  }

  public void setReportFiles(List<String> reportFiles) { // NOPMD
    this.reportFiles = reportFiles;
  }

  /**
   * @return the failedTests
   */
  public List<BrowserResult> getFailedTests() {
    return failedTests;
  }

  public void setFailedTests(List<BrowserResult> failedTests) { // NOPMD
    this.failedTests = failedTests;
  }

  /**
   * @return the skippedTests
   */
  public List<BrowserResult> getSkippedTests() {
    return skippedTests;
  }

  public void setSkippedTests(List<BrowserResult> skippedTests) { // NOPMD
    this.skippedTests = skippedTests;
  }

  /**
   * @return the passedTests
   */
  public List<BrowserResult> getPassedTests() {
    return passedTests;
  }

  public void setPassedTests(List<BrowserResult> passedTests) { // NOPMD
    this.passedTests = passedTests;
  }

  /**
   * @return the testResults
   */
  public List<TestResult> getTestResults() {
    return testResults;
  }

  public void setTestResults(List<TestResult> testResults) { // NOPMD
    this.testResults = testResults;
  }

  /**
   * @return the totalCount
   */
  public int getTotalCount() {
    return totalCount;
  }

  @Override
  public int getPassCount() {
    return passCount;
  }

  @Override
  public int getSkipCount() {
    return skipCount;
  }

  @Override
  public int getFailCount() {
    return failCount;
  }

  /**
   * @return the testFileMap
   */
  public Map<String, TestFileResult> getTestFileMap() {
    return testFileMap;
  }

  /**
   * @return the testFileNames
   */
  public Set<String> getTestFileNames() {
    return testFileMap.keySet();
  }

  /**
   * @param testFileMap the testFileMap to set
   */
  public void setTestFileMap(Map<String, TestFileResult> testFileMap) { // NOPMD
    this.testFileMap = testFileMap;
  }

  @Override
  public void setOwner(AbstractBuild<?, ?> owner) { // NOPMD
    super.setOwner(owner);
    for (TestResult tmpTest : testResults) {
      tmpTest.setOwner(owner);
    }
    for (TestFileResult tmpTestFileResult : testFileMap.values()) {
      tmpTestFileResult.setOwner(owner);
    }
  }

  /**
   * Finds the result for the given name.
   *
   * @param aName the name of the result
   * @return the result
   */
  public AbstractBaseResult findCorrespondingResult(String aName) {
    if (aName == null || getName().equals(aName)) {
      return this;
    }

    int tmpBraket = aName.indexOf('[');
    String tmpTestFileName = aName;
    String tmpBrowserName = null;
    if (tmpBraket >= 0) {
      // it's a browser
      tmpTestFileName = aName.substring(0, tmpBraket);
      tmpBrowserName = aName.substring(tmpBraket + 1, aName.length() - 1);
    }

    TestFileResult tmpTestFileResult = testFileMap.get(tmpTestFileName);
    if (tmpTestFileResult == null || tmpBrowserName == null) {
      return tmpTestFileResult;
    }

    return tmpTestFileResult.getBrowserResult(tmpBrowserName);
  }

  @Override
  public boolean equals(Object anOther) {
    if (this == anOther) {
      return true;
    }
    if (anOther == null || getClass() != anOther.getClass()) {
      return false;
    }
    TestResults tmpOther = (TestResults) anOther;
    return name.equals(tmpOther.name) && !(owner != null ? !owner.equals(tmpOther.owner) : tmpOther.owner != null);
  }

  @Override
  public int hashCode() {
    int tmpResult = owner != null ? owner.hashCode() : 0;
    tmpResult = 31 * tmpResult + name.hashCode();
    return tmpResult;
  }

  @Override
  public String toString() {
    return "TestResults{" + "name='" + name + '\'' + ", totalCount=" + totalCount + ", skipCount=" + skipCount
        + ", failCount=" + failCount + '}';
  }

  private void add(TestResults r, boolean tally) { // NOPMD
    testResults.addAll(r.getTestResults());
    failedTests.addAll(r.getFailedTests());
    skippedTests.addAll(r.getSkippedTests());
    passedTests.addAll(r.getPassedTests());
    if (tally) {
      // save cycles while getting total results
      tally();
    }
  }

  /**
   * @param aTestResults the {@link TestResults} to add and tally (automatically)
   */
  public void add(TestResults aTestResults) {
    add(aTestResults, true);
  }

  @Override
  public void tally() {
    duration = 0;
    passedTests = new ArrayList<>();
    skippedTests = new ArrayList<>();
    failedTests = new ArrayList<>();
    testFileMap = new LinkedHashMap<>();
    testFileUrlMap = new LinkedHashMap<>();
    for (TestResult tmpTestResult : testResults) {
      tmpTestResult.tally();
      duration += tmpTestResult.getDuration();

      for (TestFileResult tmpTestFileResult : tmpTestResult.getTestFileResults()) {
        String tmpFileName = tmpTestFileResult.getName();
        testFileMap.put(tmpFileName, tmpTestFileResult);
        testFileUrlMap.put(safe(tmpFileName), tmpTestFileResult);
      }
    }
    for (TestFileResult tmpTestFileResult : testFileMap.values()) {
      passedTests.addAll(tmpTestFileResult.getPassedTests());
      skippedTests.addAll(tmpTestFileResult.getSkippedTests());
      failedTests.addAll(tmpTestFileResult.getFailedTests());
    }
    failCount = failedTests.size();
    skipCount = skippedTests.size();
    passCount = passedTests.size();
    totalCount = passCount + skipCount + failCount;
  }

  /**
   * Used by stapler.
   *
   * @param token the token to get
   * @param request the request
   * @param response the response
   * @return the {@link TestFileResult} if the token matches or null
   */
  public Object getDynamic(String token, StaplerRequest request, StaplerResponse response) { // NOPMD
    // the method parameters must be raw (without leading a) to make stapler work
    return testFileUrlMap.get(token);
  }
}
