package org.broadinstitute.dsde.workbench.leonardo.runtimes

import cats.effect.unsafe.implicits.global
import org.broadinstitute.dsde.workbench.leonardo.TestUser.{getAuthTokenAndAuthorization, Ron}
import org.broadinstitute.dsde.workbench.leonardo.{
  BillingProjectFixtureSpec,
  ClusterStatus,
  Leonardo,
  LeonardoApiClient,
  LeonardoTestUtils
}
import org.scalatest.time.{Minutes, Seconds, Span}
import scala.concurrent.duration._
import org.scalatest.{DoNotDiscover, ParallelTestExecution}

@DoNotDiscover
class RuntimeAutopauseSpec extends BillingProjectFixtureSpec with ParallelTestExecution with LeonardoTestUtils {
  implicit val (ronAuthToken, ronAuthorization) = getAuthTokenAndAuthorization(Ron)
  implicit val rat = ronAuthToken.unsafeRunSync()
  implicit val ra = ronAuthorization.unsafeRunSync()

  "autopause should work" in { billingProject =>
    val runtimeName = randomClusterName
    val runtimeRequest =
      LeonardoApiClient.defaultCreateRuntime2Request.copy(autopause = Some(true), autopauseThreshold = Some(1 minute))

    withNewRuntime(billingProject, runtimeName, runtimeRequest) { runtime =>
      Leonardo.cluster
        .getRuntime(runtime.googleProject, runtime.clusterName)
        .autopauseThreshold shouldBe 1

      // the autopause check interval is 1 minute at the time of creation, but it can be flaky with a tighter window.
      eventually(timeout(Span(3, Minutes)), interval(Span(10, Seconds))) {
        val dbCluster = Leonardo.cluster.getRuntime(runtime.googleProject, runtime.clusterName)
        dbCluster.status shouldBe ClusterStatus.Stopping
      }
    }
  }
}
