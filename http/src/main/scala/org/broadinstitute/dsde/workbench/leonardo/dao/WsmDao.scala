package org.broadinstitute.dsde.workbench.leonardo.dao

import java.util.UUID

import ca.mrvisser.sealerate
import cats.mtl.Ask
import org.broadinstitute.dsde.workbench.leonardo.{
  AppContext,
  AzureCloudContext,
  CidrIP,
  DiskSize,
  ManagedResourceGroupName,
  RuntimeImage,
  RuntimeName,
  SubscriptionId,
  TenantId,
  WorkspaceId,
  WsmControlledResourceId
}
import com.azure.resourcemanager.compute.models.VirtualMachineSizeTypes
import _root_.io.circe._
import org.broadinstitute.dsde.workbench.model.{TraceId, WorkbenchEmail}
import org.broadinstitute.dsde.workbench.leonardo.JsonCodec.{
  azureRegionEncoder,
  runtimeNameEncoder,
  wsmControlledResourceIdEncoder
}

trait WsmDao[F[_]] {
  def createIp(request: CreateIpRequest)(implicit ev: Ask[F, AppContext]): F[CreateIpResponse]

  def createDisk(request: CreateDiskRequest)(implicit ev: Ask[F, AppContext]): F[CreateDiskResponse]

  def createNetwork(request: CreateNetworkRequest)(implicit ev: Ask[F, AppContext]): F[CreateNetworkResponse]

  def createVm(request: CreateVmRequest)(implicit ev: Ask[F, AppContext]): F[CreateVmResult]

  def getCreateVmJobResult(request: GetJobResultRequest)(implicit ev: Ask[F, AppContext]): F[CreateVmResult]

  def deleteVm(request: DeleteVmRequest)(implicit ev: Ask[F, AppContext]): F[DeleteVmResult]

  def getWorkspace(workspaceId: WorkspaceId)(implicit ev: Ask[F, AppContext]): F[WorkspaceDescription]
}

final case class WorkspaceDescription(id: WorkspaceId, displayName: String, azureContext: AzureCloudContext)

//Azure Vm Models
final case class CreateVmRequest(workspaceId: WorkspaceId,
                                 common: ControlledResourceCommonFields,
                                 vmData: CreateVmRequestData)

final case class CreateVmRequestData(name: RuntimeName,
                                     region: com.azure.core.management.Region,
                                     vmSize: VirtualMachineSizeTypes,
                                     vmImageUri: RuntimeImage,
                                     ipId: WsmControlledResourceId,
                                     diskId: WsmControlledResourceId,
                                     networkId: WsmControlledResourceId)

final case class AzureVmName(value: String) extends AnyVal
final case class AzureImageUri(value: String) extends AnyVal

final case class WsmVm(resourceId: WsmControlledResourceId)

final case class DeleteVmRequest(workspaceId: WorkspaceId,
                                 resourceId: WsmControlledResourceId,
                                 deleteRequest: DeleteControlledAzureResourceRequest)

final case class CreateVmResult(vm: WsmVm, jobReport: WsmJobReport, errorReport: Option[WsmErrorReport])

final case class GetJobResultRequest(workspaceId: WorkspaceId, jobId: WsmJobId)

// Azure IP models
final case class CreateIpRequest(workspaceId: WorkspaceId,
                                 common: ControlledResourceCommonFields,
                                 ipData: CreateIpRequestData)

final case class CreateIpRequestData(name: AzureIpName, region: com.azure.core.management.Region)

final case class AzureIpName(value: String) extends AnyVal

final case class CreateIpResponse(resourceId: WsmControlledResourceId)

// Azure Disk models
final case class CreateDiskRequest(workspaceId: WorkspaceId,
                                   common: ControlledResourceCommonFields,
                                   diskData: CreateDiskRequestData)

final case class CreateDiskRequestData(name: AzureDiskName, size: DiskSize, region: com.azure.core.management.Region)

//TODO: delete this case class when current pd.diskName is no longer coupled to google2 diskService
final case class AzureDiskName(value: String) extends AnyVal

final case class CreateDiskResponse(resourceId: WsmControlledResourceId)

//Network models
final case class CreateNetworkRequest(workspaceId: WorkspaceId,
                                      common: ControlledResourceCommonFields,
                                      networkData: CreateNetworkRequestData)

final case class CreateNetworkRequestData(networkName: AzureNetworkName,
                                          subnetName: AzureSubnetName,
                                          addressSpaceCidr: CidrIP,
                                          subnetAddressCidr: CidrIP,
                                          region: com.azure.core.management.Region)

final case class AzureNetworkName(value: String) extends AnyVal
final case class AzureSubnetName(value: String) extends AnyVal

final case class CreateNetworkResponse(resourceId: WsmControlledResourceId)

// Common Controlled resource models

final case class ControlledResourceCommonFields(name: ControlledResourceName,
                                                description: ControlledResourceDescription,
                                                cloningInstructions: CloningInstructions,
                                                accessScope: AccessScope,
                                                managedBy: ManagedBy,
                                                privateResourceUser: Option[PrivateResourceUser])

final case class ControlledResourceName(value: String) extends AnyVal
final case class ControlledResourceDescription(value: String) extends AnyVal
final case class PrivateResourceUser(userName: WorkbenchEmail, privateResourceIamRoles: List[ControlledResourceIamRole])

final case class WsmJobId(value: UUID) extends AnyVal
final case class WsmErrorReport(message: String, statusCode: Int, causes: List[String])
final case class WsmJobReport(id: WsmJobId,
                              description: String,
                              status: WsmJobStatus,
                              statusCode: Int,
                              submitted: String,
                              completed: String,
                              resultUrl: String)

final case class WsmJobControl(id: WsmJobId)
final case class DeleteControlledAzureResourceRequest(jobControl: WsmJobControl)

final case class DeleteVmResult(jobReport: WsmJobReport, errorReport: Option[WsmErrorReport])

sealed abstract class WsmJobStatus
object WsmJobStatus {
  case object Running extends WsmJobStatus {
    override def toString: String = "RUNNING"
  }
  case object Succeeded extends WsmJobStatus {
    override def toString: String = "SUCCEEDED"
  }
  case object Failed extends WsmJobStatus {
    override def toString: String = "FAILED"
  }

  def values: Set[WsmJobStatus] = sealerate.values[WsmJobStatus]

  def stringToObject: Map[String, WsmJobStatus] = values.map(v => v.toString -> v).toMap
}

sealed abstract class ControlledResourceIamRole
object ControlledResourceIamRole {
  case object Reader extends ControlledResourceIamRole {
    override def toString: String = "READER"
  }
  case object Writer extends ControlledResourceIamRole {
    override def toString: String = "WRITER"
  }
  case object Editor extends ControlledResourceIamRole {
    override def toString: String = "EDITOR"
  }

  def values: Set[ControlledResourceIamRole] = sealerate.values[ControlledResourceIamRole]

  def stringToObject: Map[String, ControlledResourceIamRole] = values.map(v => v.toString -> v).toMap
}

sealed abstract class CloningInstructions
object CloningInstructions {
  case object Nothing extends CloningInstructions {
    override def toString: String = "COPY_NOTHING"
  }
  case object Definition extends CloningInstructions {
    override def toString: String = "COPY_DEFINITION"
  }
  case object Resource extends CloningInstructions {
    override def toString: String = "COPY_RESOURCE"
  }
  case object Reference extends CloningInstructions {
    override def toString: String = "COPY_REFERENCE"
  }

  def values: Set[CloningInstructions] = sealerate.values[CloningInstructions]

  def stringToObject: Map[String, CloningInstructions] = values.map(v => v.toString -> v).toMap
}

sealed abstract class AccessScope

object AccessScope {
  case object SharedAccess extends AccessScope {
    override def toString: String = "SHARED_ACCESS"
  }

  case object PrivateAccess extends AccessScope {
    override def toString: String = "PRIVATE_ACCESS"
  }

  def values: Set[AccessScope] = sealerate.values[AccessScope]

  def stringToObject: Map[String, AccessScope] = values.map(v => v.toString -> v).toMap
}

sealed abstract class ManagedBy

object ManagedBy {
  case object User extends ManagedBy {
    override def toString: String = "USER"
  }

  case object Application extends ManagedBy {
    override def toString: String = "APPLICATION"
  }

  def values: Set[ManagedBy] = sealerate.values[ManagedBy]

  def stringToObject: Map[String, ManagedBy] = values.map(v => v.toString -> v).toMap
}
// End Common Controlled resource models

object WsmDecoders {
  implicit val createIpResponseDecoder: Decoder[CreateIpResponse] = Decoder.instance { c =>
    for {
      id <- c.downField("resourceId").as[UUID]
    } yield CreateIpResponse(WsmControlledResourceId(id))
  }

  implicit val createDiskResponseDecoder: Decoder[CreateDiskResponse] = Decoder.instance { c =>
    for {
      id <- c.downField("resourceId").as[UUID]
    } yield CreateDiskResponse(WsmControlledResourceId(id))
  }

  implicit val createNetworkResponseDecoder: Decoder[CreateNetworkResponse] = Decoder.instance { c =>
    for {
      id <- c.downField("resourceId").as[UUID]
    } yield CreateNetworkResponse(WsmControlledResourceId(id))
  }

  implicit val createVmResponseDecoder: Decoder[WsmVm] = Decoder.instance { c =>
    for {
      id <- c.downField("resourceId").as[UUID]
    } yield WsmVm(WsmControlledResourceId(id))
  }

  implicit val azureContextDecoder: Decoder[AzureCloudContext] = Decoder.instance { c =>
    for {
      tenantId <- c.downField("tenantId").as[String]
      subscriptionId <- c.downField("subscriptionId").as[String]
      resourceGroupId <- c.downField("resourceGroupId").as[String]
    } yield AzureCloudContext(TenantId(tenantId),
                              SubscriptionId(subscriptionId),
                              ManagedResourceGroupName(resourceGroupId))
  }

  implicit val getWorkspaceResponseDecoder: Decoder[WorkspaceDescription] = Decoder.instance { c =>
    for {
      id <- c.downField("id").as[UUID]
      displayName <- c.downField("displayName").as[String]
      azureContext <- c.downField("azureContext").as[AzureCloudContext]
    } yield WorkspaceDescription(WorkspaceId(id), displayName, azureContext)
  }

  implicit val wsmJobStatusDecoder: Decoder[WsmJobStatus] =
    Decoder.decodeString.emap(s => WsmJobStatus.stringToObject.get(s).toRight(s"Invalid WsmJobStatus found: $s"))

  implicit val wsmJobReportDecoder: Decoder[WsmJobReport] = Decoder.instance { c =>
    for {
      id <- c.downField("id").as[UUID]
      description <- c.downField("description").as[String]
      status <- c.downField("status").as[WsmJobStatus]
      statusCode <- c.downField("statusCode").as[Int]
      submitted <- c.downField("submitted").as[String]
      completed <- c.downField("completed").as[String]
      resultUrl <- c.downField("resultURL").as[String]
    } yield WsmJobReport(WsmJobId(id), description, status, statusCode, submitted, completed, resultUrl)
  }

  implicit val wsmErrorReportDecoder: Decoder[WsmErrorReport] =
    Decoder.forProduct3("message", "statusCode", "causes")(WsmErrorReport.apply)

  implicit val deleteControlledAzureResourceResponseDecoder: Decoder[DeleteVmResult] = Decoder.instance { c =>
    for {
      jobReport <- c.downField("jobReport").as[WsmJobReport]
      errorReport <- c.downField("errorReport").as[Option[WsmErrorReport]]
    } yield DeleteVmResult(jobReport, errorReport)
  }

  implicit val createVmResultDecoder: Decoder[CreateVmResult] =
    Decoder.forProduct3("azureVm", "jobReport", "errorReport")(CreateVmResult.apply)
}

object WsmEncoders {
  implicit val controlledResourceIamRoleEncoder: Encoder[ControlledResourceIamRole] =
    Encoder.encodeString.contramap(x => x.toString)
  implicit val privateResourceUserEncoder: Encoder[PrivateResourceUser] =
    Encoder.forProduct2("userName", "privateResourceIamRoles")(x => (x.userName.value, x.privateResourceIamRoles))
  implicit val wsmCommonFieldsEncoder: Encoder[ControlledResourceCommonFields] =
    Encoder.forProduct6("name",
                        "description",
                        "cloningInstructions",
                        "accessScope",
                        "managedBy",
                        "privateResourceUser")(x =>
      (x.name.value,
       x.description.value,
       x.cloningInstructions.toString,
       x.accessScope.toString,
       x.managedBy.toString,
       x.privateResourceUser)
    )

  implicit val ipRequestDataEncoder: Encoder[CreateIpRequestData] =
    Encoder.forProduct2("name", "region")(x => (x.name.value, x.region.toString))
  implicit val createIpRequestEncoder: Encoder[CreateIpRequest] =
    Encoder.forProduct2("common", "azureIp")(x => (x.common, x.ipData))

  implicit val diskRequestDataEncoder: Encoder[CreateDiskRequestData] =
    Encoder.forProduct3("name", "size", "region")(x => (x.name.value, x.size.gb, x.region.toString))
  implicit val createDiskRequestEncoder: Encoder[CreateDiskRequest] =
    Encoder.forProduct2("common", "azureDisk")(x => (x.common, x.diskData))

  implicit val networkRequestDataEncoder: Encoder[CreateNetworkRequestData] =
    Encoder.forProduct5("networkName", "subnetName", "addressSpaceCidr", "subnetAddressCidr", "region")(x =>
      (x.networkName.value, x.subnetName.value, x.addressSpaceCidr.value, x.subnetAddressCidr.value, x.region.toString)
    )
  implicit val createNetworkRequestEncoder: Encoder[CreateNetworkRequest] =
    Encoder.forProduct2("common", "azureNetwork")(x => (x.common, x.networkData))

  implicit val vmSizeEncoder: Encoder[VirtualMachineSizeTypes] = Encoder.encodeString.contramap(_.toString)

  implicit val vmRequestDataEncoder: Encoder[CreateVmRequestData] =
    Encoder.forProduct7("name", "region", "vmSize", "vmImageUri", "ipId", "diskId", "networkId")(x =>
      (x.name, x.region, x.vmSize, x.vmImageUri.imageUrl, x.ipId, x.diskId, x.networkId)
    )
  implicit val createVmRequestEncoder: Encoder[CreateVmRequest] =
    Encoder.forProduct2("common", "azureVm")(x => (x.common, x.vmData))

  implicit val wsmJobIdEncoder: Encoder[WsmJobId] = Encoder.encodeString.contramap(_.value.toString)
  implicit val wsmJobControlEncoder: Encoder[WsmJobControl] = Encoder.forProduct1("id")(x => x.id)

  implicit val deleteControlledAzureResourceRequestEncoder: Encoder[DeleteControlledAzureResourceRequest] =
    Encoder.forProduct1("jobControl")(x => x.jobControl)
}

final case class WsmException(traceId: TraceId, message: String) extends Exception(message)