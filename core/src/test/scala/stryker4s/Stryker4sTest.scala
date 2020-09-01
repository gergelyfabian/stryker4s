package stryker4s

import better.files.File
import org.mockito.captor.ArgCaptor
import org.scalatest.Inside
import stryker4s.config.Config
import stryker4s.extension.mutationtype.LesserThan
import stryker4s.model.{Killed, Mutant, MutantRunResult}
import stryker4s.mutants.Mutator
import stryker4s.mutants.applymutants.{ActiveMutationContext, MatchBuilder, StatementTransformer}
import stryker4s.mutants.findmutants.{FileCollector, MutantFinder, MutantMatcher, SourceCollector}
import stryker4s.report.{AggregateReporter, Reporter}
import stryker4s.run.MutantRunner
import stryker4s.run.threshold.SuccessStatus
import stryker4s.scalatest.{FileUtil, LogMatchers}
import stryker4s.testutil.stubs.{TestProcessRunner, TestSourceCollector}
import stryker4s.testutil.{MockitoSuite, Stryker4sSuite}

import scala.meta._
import scala.util.Success
import stryker4s.report.FinishedRunReport
import stryker4s.model.TestRunnerContext
import cats.effect.IO
import cats.effect.Resource

class Stryker4sTest extends Stryker4sSuite with MockitoSuite with Inside with LogMatchers {
  case class TestTestRunnerContext() extends TestRunnerContext
  class TestMutantRunner(sourceCollector: SourceCollector, reporter: Reporter)(implicit config: Config)
      extends MutantRunner(sourceCollector, reporter) {
    private[this] val stream = Iterator.from(0)
    type Context = TestTestRunnerContext
    override def runMutant(mutant: Mutant, context: Context): IO[MutantRunResult] =
      IO.pure(Killed(Mutant(stream.next(), q">", q"<", LesserThan)))
    override def runInitialTest(context: Context): IO[Boolean] = IO.pure(true)
    override def initializeTestContext(tmpDir: File): Resource[IO, Context] =
      Resource.pure[IO, Context](TestTestRunnerContext())
  }

  describe("run") {
    val file = FileUtil.getResource("scalaFiles/simpleFile.scala")
    val testFiles = Seq(file)
    val testSourceCollector = new TestSourceCollector(testFiles)
    val testProcessRunner = TestProcessRunner(Success(1), Success(1), Success(1), Success(1))
    val reporterMock = mock[AggregateReporter]
    when(reporterMock.reportRunFinished(any[FinishedRunReport])).thenReturn(IO.unit)
    when(reporterMock.reportMutationComplete(any[MutantRunResult], anyInt)).thenReturn(IO.unit)
    when(reporterMock.reportMutationStart(any[Mutant])).thenReturn(IO.unit)

    it("should call mutate files and report the results") {
      implicit val conf: Config = Config(baseDir = FileUtil.getResource("scalaFiles"))

      val testMutantRunner = new TestMutantRunner(new FileCollector(testProcessRunner), reporterMock)

      val sut = new Stryker4s(
        testSourceCollector,
        new Mutator(
          new MutantFinder(new MutantMatcher),
          new StatementTransformer,
          new MatchBuilder(ActiveMutationContext.sysProps)
        ),
        testMutantRunner
      )

      val result = sut.run().unsafeRunSync()

      val startCaptor = ArgCaptor[Mutant]
      verify(reporterMock, times(4)).reportMutationStart(startCaptor)
      startCaptor.values should matchPattern {
        case List(Mutant(0, _, _, _), Mutant(1, _, _, _), Mutant(2, _, _, _), Mutant(3, _, _, _)) =>
      }
      val runReportMock = ArgCaptor[FinishedRunReport]
      verify(reporterMock).reportRunFinished(runReportMock)
      val FinishedRunReport(reportedResults, _) = runReportMock.value

      result shouldBe SuccessStatus
      reportedResults.files.flatMap(_._2.mutants) should have size 4
      reportedResults.files.foreach({
        case (path, _) => path shouldBe "simpleFile.scala"
      })
    }
  }
}
