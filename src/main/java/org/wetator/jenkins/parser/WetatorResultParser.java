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


package org.wetator.jenkins.parser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.wetator.jenkins.Messages;
import org.wetator.jenkins.result.BrowserResult;
import org.wetator.jenkins.result.StepError;
import org.wetator.jenkins.result.StepError.CauseType;
import org.wetator.jenkins.result.TestError;
import org.wetator.jenkins.result.TestFileResult;
import org.wetator.jenkins.result.TestResult;
import org.wetator.jenkins.result.TestResults;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Util;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

/**
 * This parser parses some Wetator XML result files and generates a {@link TestResults} containing all results of all
 * files parsed.
 *
 * @author frank.danek
 */
public class WetatorResultParser {

  private static final String PATH_ROOT = "/wet";
  private static final String PATH_TEST_CASE = PATH_ROOT + "/testcase";
  private static final String PATH_TEST_RUN = PATH_TEST_CASE + "/testrun";
  private static final String PATH_TEST_FILE = PATH_TEST_RUN + "/testfile";

  private boolean allowEmptyResults;

  /**
   * @param anAllowEmptyResults if true empty results are allowed
   */
  public WetatorResultParser(boolean anAllowEmptyResults) {
    allowEmptyResults = anAllowEmptyResults;
  }

  /**
   * Starts a process on the <b>slave</b> parsing the test results.
   *
   * @param aTestResultLocations the pattern relative to the {@code workspace} that specifies the locations of the test
   *        result files; never null
   * @param aTestReportLocations the pattern relative to the {@code workspace} that specifies the locations of the test
   *        reports; may be null
   * @param aWorkspace the workspace in which tests can be found
   * @return a {@link TestResults} containing all results of all files parsed
   * @throws InterruptedException if the user cancels the build, it will be received as a thread interruption; do not
   *         catch it, and instead just forward that through the call stack
   * @throws IOException if you don't care about handling exceptions gracefully, you can just throw IOException and let
   *         the default exception handling in Jenkins takes care of it
   * @throws AbortException if you encounter an error that you handled gracefully, throw this exception and Jenkins will
   *         not show a stack trace
   */
  public TestResults parseResult(String aTestResultLocations, String aTestReportLocations, @Nonnull FilePath aWorkspace)
      throws InterruptedException, IOException {
    return aWorkspace.act(new ParseResultCallable(aTestResultLocations, aTestReportLocations, allowEmptyResults));
  }

  /**
   * <b>INTERNAL API</b>
   *
   * @param anInputStream a stream containing the Wetator result file to parse
   * @return the {@link TestResults}
   * @throws XMLStreamException if an error occurs during parsing the file
   */
  public static TestResults parse(InputStream anInputStream) throws XMLStreamException {
    XMLInputFactory tmpFactory = XMLInputFactory.newInstance();
    XMLStreamReader tmpReader = tmpFactory.createXMLStreamReader(anInputStream);

    try {
      TestResults tmpTestResults = new TestResults(UUID.randomUUID().toString());
      TestResult tmpTestResult = null;
      TestFileResult tmpTestFileResult = null;
      List<BrowserResult> tmpBrowserResults = null;
      BrowserResult tmpBrowserResult = null;
      long tmpDuration = 0;
      int tmpLine = 0;
      String tmpCommand = null;
      String tmpParam0 = null;
      String tmpParam1 = null;
      String tmpParam2 = null;
      String tmpParam3 = null;
      TestError tmpTestError = null;
      StepError tmpStepError = null;

      Path tmpPath = new Path();
      Deque<String> tmpTestFileStack = new ArrayDeque<>();

      while (tmpReader.hasNext()) {
        int tmpEvent = tmpReader.next();
        if (tmpEvent == XMLStreamConstants.START_ELEMENT) {
          tmpPath.push(tmpReader.getLocalName());

          if (tmpPath.matches(PATH_ROOT)) {
            tmpTestResult = new TestResult();
          } else if (tmpPath.matches(PATH_ROOT + "/startTime")) {
            tmpTestResult.setName(tmpReader.getElementText());
            tmpPath.pop();
          } else if (tmpPath.matches(PATH_ROOT + "/executionTime")) {
            tmpTestResult.setDuration(Long.parseLong(tmpReader.getElementText()));
            tmpPath.pop();
          } else if (tmpPath.matches(PATH_TEST_CASE)) {
            tmpTestFileResult = new TestFileResult();
            String tmpTestCaseName = tmpReader.getAttributeValue(null, "name");
            String tmpTestFileName = tmpReader.getAttributeValue(null, "file");
            tmpTestFileResult.setName(tmpTestCaseName);
            if (tmpTestFileName != null && !"".equals(tmpTestFileName)) {
              tmpTestFileResult.setFullName(tmpTestFileName);
            } else {
              tmpTestFileResult.setFullName(tmpTestCaseName);
            }
            tmpBrowserResults = new ArrayList<>();
          } else if (tmpPath.matches(PATH_TEST_RUN)) {
            tmpBrowserResult = new BrowserResult();
            String tmpTestRunBrowser = tmpReader.getAttributeValue(null, "browser");
            tmpBrowserResult.setName(tmpTestRunBrowser);
            tmpBrowserResult.setFullName(tmpTestFileResult.getName() + "[" + tmpTestRunBrowser + "]");
            tmpTestError = null;
            tmpStepError = null;
            tmpDuration = 0;
          } else if (tmpPath.matches(PATH_TEST_RUN + "/error/message")) {
            tmpTestError = new TestError();
            tmpTestError.setFile(tmpTestFileResult.getFullName());
            tmpTestError.setError(tmpReader.getElementText());
            tmpBrowserResult.setError(tmpTestError);
            tmpPath.pop();
          } else if (tmpPath.matches(PATH_TEST_RUN + "/ignored")) {
            tmpBrowserResult.setSkipped(true);
          } else if (tmpPath.endsWith("/testfile")) {
            String tmpCurrentTestFile = tmpReader.getAttributeValue(null, "file");
            tmpTestFileStack.push(tmpCurrentTestFile);
            if (tmpPath.matches(PATH_TEST_FILE)) {
              tmpTestFileResult.setFullName(tmpCurrentTestFile);
            }
          } else if (tmpPath.startsWith(PATH_TEST_RUN) && tmpPath.endsWith("/testfile/error/message")) {
            tmpTestError = new TestError();
            tmpTestError.setFile(tmpTestFileStack.peek());
            tmpTestError.setError(tmpReader.getElementText());
            tmpBrowserResult.setError(tmpTestError);
            tmpPath.pop();
          } else if (tmpPath.startsWith(PATH_TEST_FILE) && tmpPath.endsWith("/command")) {
            tmpLine = Integer.parseInt(tmpReader.getAttributeValue(null, "line"));
            tmpCommand = tmpReader.getAttributeValue(null, "name");
            tmpParam0 = null;
            tmpParam1 = null;
            tmpParam2 = null;
            tmpParam3 = null;
          } else if (tmpPath.startsWith(PATH_TEST_FILE) && tmpPath.endsWith("/command/param0")) {
            tmpParam0 = tmpReader.getElementText();
            tmpPath.pop();
          } else if (tmpPath.startsWith(PATH_TEST_FILE) && tmpPath.endsWith("/command/param1")) {
            tmpParam1 = tmpReader.getElementText();
            tmpPath.pop();
          } else if (tmpPath.startsWith(PATH_TEST_FILE) && tmpPath.endsWith("/command/param2")) {
            tmpParam2 = tmpReader.getElementText();
            tmpPath.pop();
          } else if (tmpPath.startsWith(PATH_TEST_FILE) && tmpPath.endsWith("/command/param3")) {
            tmpParam3 = tmpReader.getElementText();
            tmpPath.pop();
          } else if (tmpPath.startsWith(PATH_TEST_FILE) && tmpPath.endsWith("/command/executionTime")) {
            tmpDuration += Long.parseLong(tmpReader.getElementText());
            tmpPath.pop();
          } else if (tmpPath.startsWith(PATH_TEST_FILE)
              && (tmpPath.endsWith("/command/error/message") || tmpPath.endsWith("/command/failure/message"))
              && tmpStepError == null) {
            // only save the first error or failure per browser run
            tmpStepError = new StepError();
            tmpStepError.setFile(tmpTestFileStack.peek());
            tmpStepError.setLine(tmpLine);
            if (tmpPath.endsWith("/command/failure/message")) {
              tmpStepError.setCauseType(CauseType.FAILURE);
            } else {
              tmpStepError.setCauseType(CauseType.ERROR);
            }
            tmpStepError.setCommand(tmpCommand);
            List<String> tmpParameters = new ArrayList<>();
            if (tmpParam0 != null && !"".equals(tmpParam0)) {
              tmpParameters.add(tmpParam0);
            }
            if (tmpParam1 != null && !"".equals(tmpParam1)) {
              tmpParameters.add(tmpParam1);
            }
            if (tmpParam2 != null && !"".equals(tmpParam2)) {
              tmpParameters.add(tmpParam2);
            }
            if (tmpParam3 != null && !"".equals(tmpParam3)) {
              tmpParameters.add(tmpParam3);
            }
            tmpStepError.setParameters(tmpParameters);
            tmpStepError.setError(tmpReader.getElementText());
            tmpBrowserResult.setError(tmpStepError);
            tmpPath.pop();
          }
        } else if (tmpEvent == XMLStreamConstants.END_ELEMENT) {
          if (tmpPath.endsWith("/testfile")) {
            tmpTestFileStack.pop();
          } else if (tmpPath.matches(PATH_TEST_RUN)) {
            tmpBrowserResult.setDuration(tmpDuration);
            tmpBrowserResults.add(tmpBrowserResult);
            if (tmpTestError == null && tmpStepError == null) {
              tmpTestResults.getPassedTests().add(tmpBrowserResult);
            } else {
              tmpTestResults.getFailedTests().add(tmpBrowserResult);
            }
            tmpBrowserResult = null;
          } else if (tmpPath.matches(PATH_TEST_CASE)) {
            tmpTestFileResult.setBrowserResults(tmpBrowserResults);
            tmpTestResult.getTestFileResults().add(tmpTestFileResult);
            tmpBrowserResults = null;
            tmpTestFileResult = null;
          } else if (tmpPath.matches(PATH_ROOT)) {
            tmpTestResults.getTestResults().add(tmpTestResult);
            tmpTestResult = null;
          }

          tmpPath.pop();
        }
      }

      tmpTestResults.tally();

      return tmpTestResults;
    } finally {
      tmpReader.close();
    }
  }

  /**
   * The worker really parsing the results. It runs on the slave.
   *
   * @author frank.danek
   */
  private static final class ParseResultCallable extends MasterToSlaveFileCallable<TestResults> {

    private static final long serialVersionUID = -876970965386374113L;

    private String testResultLocations;
    private String testReportLocations;
    private boolean allowEmptyResults;

    /**
     * The constructor.
     *
     * @param aTestResultLocations the pattern relative to the {@code workspace} that specifies the locations of the
     *        test result files; never null
     * @param aTestReportLocations the pattern relative to the {@code workspace} that specifies the locations of the
     *        test reports; may be null
     * @param anAllowEmptyResults if true empty results are allowed
     */
    private ParseResultCallable(String aTestResultLocations, String aTestReportLocations, boolean anAllowEmptyResults) {
      testResultLocations = aTestResultLocations;
      testReportLocations = aTestReportLocations;
      allowEmptyResults = anAllowEmptyResults;
    }

    @Override
    public TestResults invoke(File aWorkspace, VirtualChannel aChannel) throws IOException {
      // compared to the JUnit parser we do not check the last modified of the result against the build time
      FileSet tmpResultFileSet = Util.createFileSet(aWorkspace, testResultLocations);
      DirectoryScanner tmpResultScanner = tmpResultFileSet.getDirectoryScanner();

      String[] tmpResultFiles = tmpResultScanner.getIncludedFiles();

      if (tmpResultFiles.length == 0 && !allowEmptyResults) {
        // no test result. Most likely a configuration error or fatal problem
        throw new AbortException(Messages.WetatorRecorder_NoTestReportFound());
      }

      TestResults tmpAllResults = new TestResults(UUID.randomUUID().toString());

      if (StringUtils.isNotBlank(testReportLocations)) {
        FileSet tmpReportFileSet = Util.createFileSet(aWorkspace, testReportLocations);
        DirectoryScanner tmpReportScanner = tmpReportFileSet.getDirectoryScanner();

        String[] tmpReportFiles = tmpReportScanner.getIncludedFiles();

        if (tmpReportFiles.length > 0) {
          List<String> tmpNormalizedReportFiles = new ArrayList<>();
          for (String tmpReportFile : tmpReportFiles) {
            tmpNormalizedReportFiles.add(tmpReportFile.replace('\\', '/'));
          }
          tmpAllResults.setReportFiles(tmpNormalizedReportFiles);
        }
      }

      File tmpBaseDir = tmpResultScanner.getBasedir();
      for (String tmpFile : tmpResultFiles) {
        File tmpReportFile = new File(tmpBaseDir, tmpFile);
        try (InputStream tmpInputStream = Files.newInputStream(tmpReportFile.toPath())) {
          TestResults tmpTestResults = parse(tmpInputStream);
          if (tmpTestResults != null) {
            tmpAllResults.add(tmpTestResults);
          }
        } catch (XMLStreamException e) {
          throw new IOException("Failed to read " + tmpReportFile, e);
        }
      }
      tmpAllResults.tally();

      return tmpAllResults;
    }
  }
}
