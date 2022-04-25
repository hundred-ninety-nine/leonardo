package org.broadinstitute.dsde.workbench.leonardo.util

import cats.effect.IO
import cats.mtl.Ask
import com.google.api.gax.longrunning.OperationFuture
import com.google.cloud.compute.v1.Operation
import org.broadinstitute.dsde.workbench.google2.mock.{
  FakeGoogleComputeService,
  FakeGoogleResourceService,
  MockGoogleDiskService
}
import org.broadinstitute.dsde.workbench.google2.{GoogleComputeService, InstanceName, ZoneName}
import org.broadinstitute.dsde.workbench.leonardo.CommonTestData._
import org.broadinstitute.dsde.workbench.leonardo.TestUtils.appContext
import org.broadinstitute.dsde.workbench.leonardo.config.Config
import org.broadinstitute.dsde.workbench.leonardo.dao.MockWelderDAO
import org.broadinstitute.dsde.workbench.leonardo.db.{clusterQuery, TestComponent, UpdateAsyncClusterCreationFields}
import org.broadinstitute.dsde.workbench.leonardo.{
  CommonTestData,
  FakeGoogleStorageService,
  LeonardoTestSuite,
  RuntimeAndRuntimeConfig,
  RuntimeStatus
}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.scalatest.flatspec.AnyFlatSpecLike

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class GceInterpreterSpec extends AnyFlatSpecLike with TestComponent with LeonardoTestSuite {
  val bucketHelperConfig =
    BucketHelperConfig(imageConfig, welderConfig, proxyConfig, clusterFilesConfig)
  val bucketHelper =
    new BucketHelper[IO](bucketHelperConfig, FakeGoogleStorageService, serviceAccountProvider)

  val mockGoogleResourceService = new FakeGoogleResourceService {
    override def getProjectNumber(project: GoogleProject)(implicit ev: Ask[IO, TraceId]): IO[Option[Long]] =
      IO(Some(1L))
  }

  val vpcInterp =
    new VPCInterpreter[IO](Config.vpcInterpreterConfig, mockGoogleResourceService, FakeGoogleComputeService)

  def gceInterp(computeService: GoogleComputeService[IO] = FakeGoogleComputeService) =
    new GceInterpreter[IO](Config.gceInterpreterConfig,
                           bucketHelper,
                           vpcInterp,
                           computeService,
                           MockGoogleDiskService,
                           MockWelderDAO)
  it should "don't error if runtime is already deleted" in isolatedDbTest {
    val computeService = new FakeGoogleComputeService {
      override def modifyInstanceMetadata(
        project: GoogleProject,
        zone: ZoneName,
        instanceName: InstanceName,
        metadataToAdd: Map[String, String],
        metadataToRemove: Set[String]
      )(implicit ev: Ask[IO, TraceId]): IO[Option[OperationFuture[Operation, Operation]]] =
        IO.raiseError(new org.broadinstitute.dsde.workbench.model.WorkbenchException("Instance not found: "))
    }

    val gce = gceInterp(computeService)
    val res = for {
      runtime <- IO(
        makeCluster(1)
          .copy(status = RuntimeStatus.Deleting)
          .saveWithRuntimeConfig(CommonTestData.defaultGceRuntimeConfig)
      )
      _ <- IO(
        dbFutureValue(
          clusterQuery.updateAsyncClusterCreationFields(UpdateAsyncClusterCreationFields(None, 1, None, Instant.now))
        )
      )
      updatedRuntme <- IO(dbFutureValue(clusterQuery.getClusterById(runtime.id)))
      runtimeAndRuntimeConfig = RuntimeAndRuntimeConfig(updatedRuntme.get, CommonTestData.defaultGceRuntimeConfig)
      res <- gce.deleteRuntime(DeleteRuntimeParams(runtimeAndRuntimeConfig, None))
    } yield (res shouldBe (None))
    res.unsafeRunSync()(cats.effect.unsafe.implicits.global)
  }
}