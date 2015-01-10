package org.psliwa.idea.composerJson.inspection

import org.psliwa.idea.composerJson.composer
import org.psliwa.idea.composerJson.fixtures.ComposerFixtures._

class NotInstalledPackageInspectionTest extends InspectionTest {

  override def setUp() = {
    super.setUp()

    myFixture.enableInspections(classOf[NotInstalledPackageInspection])
  }

  def testGivenUninstalledPackage_thatShouldBeReported() = {
    checkInspection(
      """
        |{
        |  "require": {
        |    <weak_warning>"vendor/pkg": "1.0.2"</weak_warning>
        |  }
        |}
      """.stripMargin)
  }

  def testGivenVirtualPackage_thatShouldNotBeReported() = {
    checkInspection(
      """
        |{
        |  "require": {
        |    "php": ">=5.3"
        |  }
        |}
      """.stripMargin)
  }

  def testGivenInstalledPackage_thatShouldNotBeReported() = {
    createComposerLock(myFixture, composer.Packages(composer.Package("vendor/pkg", "1.0.2")))

    checkInspection(
      """
        |{
        |  "require": {
        |    "vendor/pkg": "1.0.2"
        |  }
        |}
      """.stripMargin)
  }

  def testGivenUninstalledPackage_packageHasNotVersionYet_thatShouldNotBeReported() = {
    checkInspection(
      """
        |{
        |  "require": {
        |    "vendor/pkg": ""
        |  }
        |}
      """.stripMargin)
  }
}
