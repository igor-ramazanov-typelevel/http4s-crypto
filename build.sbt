import org.typelevel.sbt.gha.WorkflowStep.Run
import org.typelevel.sbt.gha.WorkflowStep.Sbt

/*
 * Copyright 2021 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.firefox.{FirefoxOptions, FirefoxProfile}
import org.openqa.selenium.remote.server.{DriverFactory, DriverProvider}
import org.scalajs.jsenv.selenium.SeleniumJSEnv

import JSEnv._

name := "http4s-crypto"

ThisBuild / githubOwner := "igor-ramazanov-typelevel"
ThisBuild / githubRepository := "http4s-crypto"

ThisBuild / githubWorkflowPublishPreamble := List.empty
ThisBuild / githubWorkflowUseSbtThinClient := true
ThisBuild / githubWorkflowPublish := List(
  Run(
    commands = List("echo \"$PGP_SECRET\" | gpg --import"),
    id = None,
    name = Some("Import PGP key"),
    env = Map("PGP_SECRET" -> "${{ secrets.PGP_SECRET }}"),
    params = Map(),
    timeoutMinutes = None,
    workingDirectory = None
  ),
  Sbt(
    commands = List("+ publish"),
    id = None,
    name = Some("Publish"),
    cond = None,
    env = Map("GITHUB_TOKEN" -> "${{ secrets.GB_TOKEN }}"),
    params = Map.empty,
    timeoutMinutes = None,
    preamble = true
  )
)
ThisBuild / gpgWarnOnFailure := false

ThisBuild / tlBaseVersion := "0.3"

ThisBuild / developers := List(
  tlGitHubDev("armanbilge", "Arman Bilge")
)
ThisBuild / startYear := Some(2021)

ThisBuild / crossScalaVersions := Seq("3.3.5", "2.13.16")

ThisBuild / githubWorkflowBuildPreamble ++= Seq(
  WorkflowStep.Use(
    UseRef.Public("actions", "setup-node", "v2.4.0"),
    name = Some("Setup NodeJS v18 LTS"),
    params = Map("node-version" -> "18"),
    cond = Some("matrix.project == 'rootJS' && matrix.jsenv == 'NodeJS'")
  )
)

val jsenvs = List(NodeJS, Chrome, Firefox).map(_.toString)
ThisBuild / githubWorkflowBuildMatrixAdditions += "jsenv" -> jsenvs
ThisBuild / githubWorkflowBuildSbtStepPreamble += s"set Global / useJSEnv := JSEnv.$${{ matrix.jsenv }}"
ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  for {
    scala <- List("3")
    jsenv <- jsenvs.tail
  } yield MatrixExclude(Map("scala" -> scala, "jsenv" -> jsenv))
}
ThisBuild / githubWorkflowBuildMatrixExclusions ++= {
  for {
    jsenv <- jsenvs.tail
    project <- List("rootJVM", "rootNative")
  } yield MatrixExclude(Map("project" -> project, "jsenv" -> jsenv))
}

lazy val useJSEnv =
  settingKey[JSEnv]("Use Node.js or a headless browser for running Scala.js tests")
Global / useJSEnv := NodeJS

ThisBuild / Test / jsEnv := {
  val old = (Test / jsEnv).value

  useJSEnv.value match {
    case NodeJS => old
    case Firefox =>
      val options = new FirefoxOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options)
    case Chrome =>
      val options = new ChromeOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options)
  }
}

val catsVersion = "2.13.0"
val catsEffectVersion = "3.7-4972921"
val scodecBitsVersion = "1.2.1"
val munitVersion = "1.1.1"
val munitCEVersion = "2.2.0-M1"
val disciplineMUnitVersion = "2.0.0"

lazy val root = tlCrossRootProject.aggregate(crypto, testRuntime)

lazy val crypto = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("crypto"))
  .settings(
    name := "http4s-crypto",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % catsVersion,
      "org.typelevel" %%% "cats-effect-kernel" % catsEffectVersion,
      "org.scodec" %%% "scodec-bits" % scodecBitsVersion,
      "org.scalameta" %%% "munit" % munitVersion % Test,
      "org.typelevel" %%% "cats-laws" % catsVersion % Test,
      "org.typelevel" %%% "cats-effect" % catsEffectVersion % Test,
      "org.typelevel" %%% "discipline-munit" % disciplineMUnitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect" % munitCEVersion % Test
    ),
    publishTo := githubPublishTo.value,
    publishConfiguration := publishConfiguration.value.withOverwrite(true),
    publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
  )
  .nativeSettings(
    tlVersionIntroduced := List("2.13", "3").map(_ -> "0.2.4").toMap,
    unusedCompileDependenciesTest := {}
  )
  .dependsOn(testRuntime % Test)

lazy val testRuntime = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("test-runtime"))
  .enablePlugins(BuildInfoPlugin, NoPublishPlugin)
  .settings(
    buildInfoPackage := "org.http4s.crypto"
  )
  .jvmSettings(
    buildInfoKeys := Seq(
      BuildInfoKey.sbtbuildinfoConstantEntry("runtime" -> "JVM")
    )
  )
  .jsSettings(
    buildInfoKeys := Seq(
      BuildInfoKey("runtime" -> useJSEnv.value.toString)
    )
  )
  .nativeSettings(
    buildInfoKeys := Seq(
      BuildInfoKey("runtime" -> "Native")
    ),
    unusedCompileDependenciesTest := {}
  )
