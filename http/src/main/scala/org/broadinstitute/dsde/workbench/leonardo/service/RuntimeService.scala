package org.broadinstitute.dsde.workbench.leonardo
package http
package service

import cats.mtl.ApplicativeAsk
import org.broadinstitute.dsde.workbench.leonardo.http.api.{CreateRuntime2Request, RuntimeServiceContext}
import org.broadinstitute.dsde.workbench.model.UserInfo
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

trait RuntimeService[F[_]] {
  def createRuntime(userInfo: UserInfo,
                    googleProject: GoogleProject,
                    runtimeName: RuntimeName,
                    req: CreateRuntime2Request)(implicit as: ApplicativeAsk[F, RuntimeServiceContext]): F[Unit]

  def getRuntime(userInfo: UserInfo, googleProject: GoogleProject, runtimeName: RuntimeName)(
    implicit as: ApplicativeAsk[F, RuntimeServiceContext]
  ): F[GetRuntimeResponse]

  def listRuntimes(userInfo: UserInfo, googleProject: Option[GoogleProject], params: Map[String, String])(
    implicit as: ApplicativeAsk[F, RuntimeServiceContext]
  ): F[Vector[ListRuntimeResponse]]

  def deleteRuntime(userInfo: UserInfo, googleProject: GoogleProject, runtimeName: RuntimeName)(
    implicit as: ApplicativeAsk[F, RuntimeServiceContext]
  ): F[Unit]

  def stopRuntime(userInfo: UserInfo, googleProject: GoogleProject, runtimeName: RuntimeName)(
    implicit as: ApplicativeAsk[F, RuntimeServiceContext]
  ): F[Unit]

  def startRuntime(userInfo: UserInfo, googleProject: GoogleProject, runtimeName: RuntimeName)(
    implicit as: ApplicativeAsk[F, RuntimeServiceContext]
  ): F[Unit]
}