package com.evolutiongaming.sbt.sonar

import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicReference
import sbt._
import sbt.AutoPlugin
import sbt.Keys._
import sbt.testing.Event
import sbt.testing.NestedTestSelector
import sbt.testing.OptionalThrowable
import sbt.testing.Status
import sbt.testing.TestSelector
import scala.collection.mutable.ListBuffer
import scala.util.DynamicVariable
import scala.xml.Elem
import scala.xml.XML


object SonarReporterPlugin extends AutoPlugin {
  
  override def trigger = allRequirements
  
  override def projectSettings = Seq(
    testListeners += new SonarReporter(
      (Test / scalaSource).value,
      target.value / "sonar-reports"
    )
  )
  
  // shamelessly inspired by JUnitXmlTestsListener
  class SonarReporter(source: File, target: File) extends TestsListener {
    
    val allSuites = new AtomicReference(List.empty[TestSuite])
  
    class TestSuite(val file: String) {
      
      val events: ListBuffer[Event] = new ListBuffer()
      def count(status: Status) = events.count(_.status == status)
  
      def stop(): Elem = {
        val duration = events.map(_.duration()).sum
        val (errors, failures, tests) = (count(Status.Error), count(Status.Failure), events.size)
        val ignoredSkippedPending = count(Status.Ignored) + count(Status.Skipped) + count(Status.Pending)
  
        val result =
          <file path={file}>
          {
            events map { e =>
              <testCase name={
                 e.selector match {
                   case selector: TestSelector => selector.testName.split('.').last
                   case nested: NestedTestSelector => nested.suiteId().split('.').last + "." + nested.testName()
                   case other => s"(It is not a test it is a ${other.getClass.getCanonicalName})"
                 }
               } duration={e.duration.toString}>
                 {
                   val trace = if (e.throwable.isEmpty) "" else {
                     val stringWriter = new StringWriter()
                     val writer = new PrintWriter(stringWriter)
                     e.throwable.get.printStackTrace(writer)
                     writer.flush()
                     stringWriter.toString
                   }
                   e.status match {
                     case Status.Error if e.throwable.isDefined =>
                       <error message={e.throwable.get.getMessage}>{trace}</error>
                     case Status.Error=>
                       <error message="No Exception or message provided"/>
                     case Status.Failure if e.throwable.isDefined =>
                       <failure message={e.throwable.get.getMessage}>{ trace }</failure>
                     case Status.Failure =>
                       <failure message="No Exception or message provided"/>
                     case Status.Ignored | Status.Skipped | Status.Pending=>
                       <skipped/>
                     case _    => {}
                   }
                 }
               </testCase>
            }
          }
          </file>

        result
      }
    }

    val testSuite = new DynamicVariable(null: TestSuite)
    
    override def doInit() = target.mkdirs()
    override def startGroup(name: String): Unit = {
      testSuite.value = new TestSuite(guessFile(name))
    }
    override def testEvent(event: TestEvent): Unit =
      event.detail foreach (testSuite.value.events += _)
    override def endGroup(name: String, t: Throwable) = {
      val event = new Event {
        def fullyQualifiedName = name
        def duration = -1
        def status = Status.Error
        def fingerprint = null
        def selector = null
        def throwable = new OptionalThrowable(t)
      }
      testSuite.value.events += event
      allSuites.updateAndGet(testSuite.value :: _)      
    }
    override def endGroup(name: String, result: TestResult) = {
      allSuites.updateAndGet(testSuite.value :: _)
    }
    private[this] def guessFile(className: String) = {
      val path = className replace ('.', '/')
      s"${source}/${path}.scala"
    }
      
    override def doComplete(finalResult: TestResult): Unit = {
      val file = new File(target, "generic.xml").getAbsolutePath
      val nodes = allSuites.get map (_.stop())
      val xml = <testExecutions version="1">{nodes}</testExecutions>
      XML.save(file, xml, "UTF-8")
    }
    override def contentLogger(test: TestDefinition): Option[ContentLogger] = None
  }
  
}
