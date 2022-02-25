package org.broadinstitute.dsde.workbench.leonardo.dao

import java.util.UUID

import cats.effect.IO
import cats.mtl.Ask
import org.broadinstitute.dsde.workbench.leonardo.{
  AppContext,
  AzureCloudContext,
  ManagedResourceGroupName,
  SubscriptionId,
  TenantId,
  WorkspaceId,
  WsmControlledResourceId
}

class MockWsmDAO(jobStatus: WsmJobStatus = WsmJobStatus.Succeeded) extends WsmDao[IO] {
  override def createIp(request: CreateIpRequest)(implicit ev: Ask[IO, AppContext]): IO[CreateIpResponse] =
    IO.pure(
      CreateIpResponse(
        WsmControlledResourceId(UUID.randomUUID())
      )
    )

  override def createDisk(request: CreateDiskRequest)(implicit ev: Ask[IO, AppContext]): IO[CreateDiskResponse] =
    IO.pure(
      CreateDiskResponse(
        WsmControlledResourceId(UUID.randomUUID())
      )
    )

  override def createNetwork(
    request: CreateNetworkRequest
  )(implicit ev: Ask[IO, AppContext]): IO[CreateNetworkResponse] =
    IO.pure(
      CreateNetworkResponse(
        WsmControlledResourceId(UUID.randomUUID())
      )
    )

  override def createVm(request: CreateVmRequest)(implicit ev: Ask[IO, AppContext]): IO[CreateVmResult] =
    IO.pure(
      CreateVmResult(
        WsmVm(WsmControlledResourceId(UUID.randomUUID())),
        WsmJobReport(
          WsmJobId(UUID.randomUUID()),
          "desc",
          jobStatus,
          200,
          "submittedTimestamp",
          "completedTimestamp",
          "resultUrl"
        ),
        if (jobStatus.equals(WsmJobStatus.Failed))
          Some(
            WsmErrorReport(
              "error",
              500,
              List.empty
            )
          )
        else None
      )
    )

  override def getCreateVmJobResult(
    request: GetJobResultRequest
  )(implicit ev: Ask[IO, AppContext]): IO[CreateVmResult] =
    IO.pure(println("in createVmJobResult")) >> IO.pure(
      CreateVmResult(
        WsmVm(WsmControlledResourceId(UUID.randomUUID())),
        WsmJobReport(
          request.jobId,
          "desc",
          jobStatus,
          200,
          "submittedTimestamp",
          "completedTimestamp",
          "resultUrl"
        ),
        if (jobStatus.equals(WsmJobStatus.Failed))
          Some(
            WsmErrorReport(
              "error",
              500,
              List.empty
            )
          )
        else None
      )
    )

  override def deleteVm(request: DeleteVmRequest)(implicit ev: Ask[IO, AppContext]): IO[DeleteVmResult] =
    IO.pure(
      DeleteVmResult(
        WsmJobReport(
          request.deleteRequest.jobControl.id,
          "desc",
          jobStatus,
          200,
          "submittedTimestamp",
          "completedTimestamp",
          "resultUrl"
        ),
        if (jobStatus.equals(WsmJobStatus.Failed))
          Some(
            WsmErrorReport(
              "error",
              500,
              List.empty
            )
          )
        else None
      )
    )

  override def getWorkspace(workspaceId: WorkspaceId)(implicit ev: Ask[IO, AppContext]): IO[WorkspaceDescription] =
    IO.pure(
      WorkspaceDescription(
        WorkspaceId(UUID.randomUUID()),
        "workspaceName",
        AzureCloudContext(
          TenantId("testTenantId"),
          SubscriptionId("testSubscriptionId"),
          ManagedResourceGroupName("testResourceGroup")
        )
      )
    )

}