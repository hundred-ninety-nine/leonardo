package org.broadinstitute.dsde.workbench.leonardo
package monitor

import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Concurrent, ContextShift, IO, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google.GoogleProjectDAO
import org.broadinstitute.dsde.workbench.google2.{GoogleComputeService, InstanceName}
import org.broadinstitute.dsde.workbench.leonardo.config.ZombieClusterConfig
import org.broadinstitute.dsde.workbench.leonardo.dao.google.GoogleDataprocDAO
import org.broadinstitute.dsde.workbench.leonardo.db._
import org.broadinstitute.dsde.workbench.leonardo.http._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.newrelic.NewRelicMetrics

import scala.concurrent.ExecutionContext

/**
 * This monitor periodically sweeps the Leo database and checks for clusters which no longer exist in Google.
 */
class ZombieClusterMonitor[F[_]: Parallel: ContextShift: Timer](
  config: ZombieClusterConfig,
  gdDAO: GoogleDataprocDAO,
  gce: GoogleComputeService[F],
  googleProjectDAO: GoogleProjectDAO
)(implicit F: Concurrent[F],
  metrics: NewRelicMetrics[F],
  logger: Logger[F],
  dbRef: DbReference[F],
  ec: ExecutionContext,
  cs: ContextShift[IO]) {

  val process: Stream[F, Unit] =
    (Stream.sleep[F](config.zombieCheckPeriod) ++ Stream.eval(zombieCheck)).repeat

  private[monitor] val zombieCheck: F[Unit] =
    for {
      start <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
      uuid <- F.delay(UUID.randomUUID())
      implicit0(traceId: ApplicativeAsk[F, TraceId]) = ApplicativeAsk.const[F, TraceId](TraceId(uuid))
      startInstant = Instant.ofEpochMilli(start)
      semaphore <- Semaphore[F](config.concurrency)

      // Get active clusters from the Leo DB, grouped by project
      clusterMap <- ZombieMonitorQueries.listZombieQuery.transaction

      _ <- logger.info(
        s"Starting zombie detection across ${clusterMap.size} projects with concurrency of ${config.concurrency}"
      )
      zombies <- clusterMap.toList.parFlatTraverse[F, PotentialZombieRuntime] {
        case (project, clusters) =>
          semaphore.withPermit(
            // Check if the project is active
            isProjectActiveInGoogle(project).flatMap {
              case true =>
                // If the project is active, check each individual cluster
                clusters.toList.traverseFilter { cluster =>
                  isRuntimeActiveInGoogle(cluster, startInstant).ifA(F.pure(None), F.pure(Some(cluster)))
                }
              case false =>
                // If the project is inactive, all clusters in the project are zombies
                F.pure(clusters.toList)
            }
          )
      }
      // Error out each detected zombie cluster
      _ <- zombies.parTraverse(zombie => semaphore.withPermit(handleZombieCluster(zombie, startInstant)))
      end <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)
      duration = end - start
      _ <- logger.info(
        s"Detected ${zombies.size} zombie clusters in ${zombies.map(_.googleProject).toSet.size} projects. Elapsed time = ${duration} milli seconds"
      )
    } yield ()

  private def isProjectActiveInGoogle(googleProject: GoogleProject): F[Boolean] = {
    // Check the project and its billing info
    val res = for {
      isBillingActive <- F.liftIO(IO.fromFuture(IO(googleProjectDAO.isBillingActive(googleProject.value))))
      // short circuit
      isProjectActive <- if (!isBillingActive) F.pure(false)
      else F.liftIO(IO.fromFuture(IO(googleProjectDAO.isProjectActive(googleProject.value))))
    } yield isProjectActive

    res.recoverWith {
      case e: GoogleJsonResponseException if e.getStatusCode == 403 =>
        logger
          .info(e)(
            s"Unable to check status of project ${googleProject.value} for zombie cluster detection " +
              s"due to a 403 from google. We are assuming this is a free credits project that has been cleaned up. " +
              s"Marking project as a zombie."
          )
          .as(false)

      case e =>
        logger
          .warn(e)(s"Unable to check status of project ${googleProject.value} for zombie cluster detection")
          .as(true)
    }
  }

  private def isRuntimeActiveInGoogle(runtime: PotentialZombieRuntime,
                                      now: Instant)(implicit traceId: ApplicativeAsk[F, TraceId]): F[Boolean] = {
    val milliSecondsSinceClusterCreation: Long = now.toEpochMilli - runtime.auditInfo.createdDate.toEpochMilli
    // this or'd with the google cluster status gives creating clusters a grace period before they are marked as zombies
    if (runtime.status == RuntimeStatus.Creating && milliSecondsSinceClusterCreation < config.creationHangTolerance.toMillis) {
      F.pure(true)
    } else {
      val runtimeStatus: F[RuntimeStatus] = runtime.cloudService match {
        case CloudService.GCE =>
          gce.getInstance(runtime.googleProject, config.gceZoneName, InstanceName(runtime.runtimeName.asString)).map {
            instance =>
              instance
                .flatMap(s => RuntimeStatus.withNameInsensitiveOption(s.getStatus))
                .getOrElse(RuntimeStatus.Unknown)
          }
        case CloudService.Dataproc =>
          F.liftIO(IO.fromFuture(IO(gdDAO.getClusterStatus(runtime.googleProject, runtime.runtimeName))))
      }

      runtimeStatus
        .map(RuntimeStatus.activeStatuses contains)
        .recoverWith {
          case e =>
            logger
              .warn(e)(
                s"Unable to check status of cluster ${runtime.googleProject} / ${runtime.runtimeName} for zombie cluster detection"
              )
              .as(true)
        }
    }

  }

  private def handleZombieCluster(runtime: PotentialZombieRuntime, now: Instant): F[Unit] =
    for {
      _ <- logger.info(s"Deleting zombie cluster: ${runtime.googleProject} / ${runtime.runtimeName}")
      _ <- metrics.incrementCounter("zombieClusters")
      _ <- dbRef.inTransaction {
        for {
          _ <- clusterQuery.completeDeletion(runtime.id, now)
          error = RuntimeError(
            s"An underlying resource was removed in Google. Runtime(${runtime.runtimeName.asString}) has been marked deleted in Leo.",
            -1,
            now
          )
          _ <- clusterErrorQuery.save(runtime.id, error)
        } yield ()
      }
    } yield ()
}

object ZombieClusterMonitor {
  def apply[F[_]: Parallel: ContextShift: Timer](
    config: ZombieClusterConfig,
    gdDAO: GoogleDataprocDAO,
    gce: GoogleComputeService[F],
    googleProjectDAO: GoogleProjectDAO
  )(implicit F: Concurrent[F],
    metrics: NewRelicMetrics[F],
    logger: Logger[F],
    dbRef: DbReference[F],
    ec: ExecutionContext,
    cs: ContextShift[IO]): ZombieClusterMonitor[F] =
    new ZombieClusterMonitor(config, gdDAO, gce, googleProjectDAO)
}