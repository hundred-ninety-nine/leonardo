package org.broadinstitute.dsde.workbench.leonardo
package monitor

import akka.actor.{ActorRef, Props}
import akka.testkit.TestKit
import cats.effect.{ContextShift, IO}
import org.broadinstitute.dsde.workbench.google.GoogleStorageDAO
import org.broadinstitute.dsde.workbench.google2.GoogleStorageService
import org.broadinstitute.dsde.workbench.leonardo.config.{
  AutoFreezeConfig,
  ClusterBucketConfig,
  DataprocConfig,
  ImageConfig,
  MonitorConfig
}
import org.broadinstitute.dsde.workbench.leonardo.dao.google.{GoogleComputeDAO, GoogleDataprocDAO}
import org.broadinstitute.dsde.workbench.leonardo.dao.{JupyterDAO, RStudioDAO, ToolDAO, WelderDAO}
import org.broadinstitute.dsde.workbench.leonardo.db.DbReference
import org.broadinstitute.dsde.workbench.leonardo.model.{Cluster, LeoAuthProvider}
import org.broadinstitute.dsde.workbench.leonardo.util.ClusterHelper
import org.broadinstitute.dsde.workbench.newrelic.mock.FakeNewRelicMetricsInterpreter

object TestClusterSupervisorActor {
  def props(monitorConfig: MonitorConfig,
            dataprocConfig: DataprocConfig,
            imageConfig: ImageConfig,
            clusterBucketConfig: ClusterBucketConfig,
            gdDAO: GoogleDataprocDAO,
            googleComputeDAO: GoogleComputeDAO,
            googleStorageDAO: GoogleStorageDAO,
            google2StorageDAO: GoogleStorageService[IO],
            dbRef: DbReference,
            testKit: TestKit,
            authProvider: LeoAuthProvider[IO],
            autoFreezeConfig: AutoFreezeConfig,
            jupyterProxyDAO: JupyterDAO,
            rstudioProxyDAO: RStudioDAO,
            welderDAO: WelderDAO[IO],
            clusterHelper: ClusterHelper)(implicit cs: ContextShift[IO]): Props =
    Props(
      new TestClusterSupervisorActor(monitorConfig,
                                     dataprocConfig,
                                     imageConfig,
                                     clusterBucketConfig,
                                     gdDAO,
                                     googleComputeDAO,
                                     googleStorageDAO,
                                     google2StorageDAO,
                                     dbRef,
                                     testKit,
                                     authProvider,
                                     autoFreezeConfig,
                                     jupyterProxyDAO,
                                     rstudioProxyDAO,
                                     welderDAO,
                                     clusterHelper)
    )
}

object TearDown

/**
 * Extends ClusterMonitorSupervisor so the akka TestKit can watch the child ClusterMonitorActors.
 */
class TestClusterSupervisorActor(monitorConfig: MonitorConfig,
                                 dataprocConfig: DataprocConfig,
                                 imageConfig: ImageConfig,
                                 clusterBucketConfig: ClusterBucketConfig,
                                 gdDAO: GoogleDataprocDAO,
                                 googleComputeDAO: GoogleComputeDAO,
                                 googleStorageDAO: GoogleStorageDAO,
                                 google2StorageDAO: GoogleStorageService[IO],
                                 dbRef: DbReference,
                                 testKit: TestKit,
                                 authProvider: LeoAuthProvider[IO],
                                 autoFreezeConfig: AutoFreezeConfig,
                                 jupyterProxyDAO: JupyterDAO,
                                 rstudioProxyDAO: RStudioDAO,
                                 welderDAO: WelderDAO[IO],
                                 clusterHelper: ClusterHelper)(implicit cs: ContextShift[IO])
    extends ClusterMonitorSupervisor(
      monitorConfig,
      dataprocConfig,
      imageConfig,
      clusterBucketConfig,
      gdDAO,
      googleComputeDAO,
      googleStorageDAO,
      google2StorageDAO,
      dbRef,
      authProvider,
      autoFreezeConfig,
      jupyterProxyDAO,
      rstudioProxyDAO,
      welderDAO,
      clusterHelper
    )(FakeNewRelicMetricsInterpreter, ToolDAO.clusterToolToToolDao(jupyterProxyDAO, welderDAO, rstudioProxyDAO), cs) {

  // Keep track of spawned child actors so we can shut them down when this actor is stopped
  var childActors: Seq[ActorRef] = Seq.empty

  override def createChildActor(cluster: Cluster): ActorRef = {
    val child = super.createChildActor(cluster)
    childActors = child +: childActors
    testKit watch child
    child
  }

  def tearDown: PartialFunction[Any, Unit] = {
    case TearDown =>
      childActors.foreach { context.stop }
      context.stop(self)
  }

  override def receive: Receive = tearDown orElse super.receive
}
