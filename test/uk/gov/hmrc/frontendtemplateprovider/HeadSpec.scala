/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.frontendtemplateprovider

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.frontendtemplateprovider.controllers.GovUkTemplateRendererController
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.ws.WSGet
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.renderer.MustacheRendererTrait

import scala.concurrent.Future

/**
  * Created by mo on 31/05/2017.
  */
class HeadSpec extends UnitSpec with Results with WithFakeApplication {

  implicit val hc = HeaderCarrier()

  override lazy val fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(Map("assets.url" -> "www.example.com/"))
    .bindings(bindModules:_*)
    .build()

  val fakeRequest = FakeRequest("GET", "/")

  "Mustache Template" should {
    "contain IE links" in new Setup {
      bodyText should include("<!--[if IE 6]><link href=\"/template/assets/stylesheets/govuk-template-ie6.css\" media=\"screen\" rel=\"stylesheet\" type=\"text/css\" /><![endif]-->")
      bodyText should include("<!--[if IE 7]><link href=\"/template/assets/stylesheets/govuk-template-ie7.css\" media=\"screen\" rel=\"stylesheet\" type=\"text/css\" /><![endif]-->")
      bodyText should include("<!--[if IE 8]><link href=\"/template/assets/stylesheets/govuk-template-ie8.css\" media=\"screen\" rel=\"stylesheet\" type=\"text/css\" /><![endif]-->")
      bodyText should include("<!--[if gt IE 8]><!--><link href=\"/template/assets/stylesheets/govuk-template.css\" media=\"screen\" rel=\"stylesheet\" type=\"text/css\" /><!--<![endif]-->")
    }

    "contain link to assets-frontend application links" in new Setup {
      bodyText should include("<link rel='stylesheet' href='www.example.com/stylesheets/application-ie7.min.css' />")
      bodyText should include("<link rel='stylesheet' href='www.example.com/stylesheets/application-ie.min.css' />")
      bodyText should include("<link rel='stylesheet' href='www.example.com/stylesheets/application.min.css' />")
    }

    "contain a body opening tag that does not contain a class SDT-470" in new Setup {
      bodyText should include("<body>")
    }

    "contains no optimizely script SDT-471" in new Setup {
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map()).body
      renderedHtml should not include("optimizely")
      renderedHtml should not include("<script src=''") // should not include a script with no src
    }

    "contain no optimizely script if only baseUrl is provided SDT-471" in new Setup {
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map(
        "optimizelyBaseUrl" -> "cdn.optimizely.com"
      )).body
      renderedHtml should not include("cdn.optimizely.com")
    }

    "contain optimizely script if only projectId is provided SDT-471" in new Setup {
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map(
        "optimizelyProjectId" -> "id123"
      )).body
      renderedHtml should include("<script src='//cdn.optimizely.com/js/id123.js' type='text/javascript'></script>")
    }

    "contain optimizely script if both url and projectId is provided SDT-470" in new Setup {
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map(
        "optimizelyBaseUrl" -> "cdn.optimizely.com/",
        "optimizelyProjectId" -> "id123"
      )).body
      renderedHtml should include("<script src='cdn.optimizely.com/id123.js' type='text/javascript'></script>")
    }

    "contain default title if not specified SDT 480" in new Setup {
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map()).body
      renderedHtml should include("GOV.UK - The best place to find government services and information")
    }

    "contain default title if not specified in SDT 480" in new Setup {
      val myTitle = "My very own title."
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map(
        "pageTitle" -> myTitle
      )).body
      renderedHtml should include(myTitle)
    }

    "contain no headScripts even if you specify them SDT 472" in new Setup {
      val headScript = "<script src='www.example.com/script.js' type='text/javascript'></script>"
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map(
        "headScript" -> headScript
      )).body
      renderedHtml should not include(headScript)
    }

    "contain link elem if url is passed through in a list of maps SDT-552" in new Setup {
      val link = "www.example.com/some.css"
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map(
        "linkElems" -> Map("href" -> link)
      )).body
      renderedHtml should include(s"""<link rel="stylesheet" type="text/css" href="$link" />""")
    }

    "support specifying print media type for linkElems SDT-552" in new Setup {
      val link = "www.example.com/some.css"
      val media = "print"
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map(
        "linkElems" -> Map("href" -> link, "print" -> true)
      )).body
      renderedHtml should include(s"""<link rel="stylesheet" type="text/css" href="$link" media="print"/>""")
    }

    "contain multiple link elems multiple elements in the list SDT-552" in new Setup {
      val link1 = "www.example.com/some.css"
      val link2 = "www.example.com/other.css"
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map(
        "linkElems" -> List(
          Map("href" -> link1),
          Map("href" -> link2, "print" -> true)
        )
      )).body
      renderedHtml should include(s"""<link rel="stylesheet" type="text/css" href="$link1" />""")
      renderedHtml should include(s"""<link rel="stylesheet" type="text/css" href="$link2" media="print"/>""")
    }

    "not contain navigation element if there is no navTitle specified SDT-474" in new Setup {
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map()).body
      renderedHtml should include("""<div class="header-proposition">""")
      renderedHtml should not include("""<a href="#proposition-links" class="js-header-toggle menu">Menu</a>""")
      renderedHtml should not include("""<ul id="proposition-links" class="header__menu__proposition-links">""")
    }

    "contain correct navigation header when only navTitle is specified SDT-474" in new Setup {
      val navTitle = "My service"
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map(
        "navTitle" -> navTitle
      )).body
      renderedHtml should include("""<div class="header-proposition">""")
      renderedHtml should include(s"""<span class="header__menu__proposition-name">$navTitle</span>""")
      renderedHtml should not include(s"""<a href="#proposition-links" class="js-header-toggle menu">Menu</a>""")
      renderedHtml should not include(s"""<ul id="proposition-links" class="header__menu__proposition-links">""")
    }

    "contain correct navigation when navTitle and navTitleLink is specified SDT-474" in new Setup {
      val navTitle = "My service"
      val navTitleLink = "www.example.com/my-service"
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map(
        "navTitle" -> navTitle,
        "navTitleLink" -> navTitleLink
      )).body
      renderedHtml should include("""<div class="header-proposition">""")
      renderedHtml should not include(s"""<span class="header__menu__proposition-name">$navTitle</span>""")
      renderedHtml should not include(s"""<a href="#proposition-links" class="js-header-toggle menu">Menu</a>""")
      renderedHtml should include(s"""<a href="$navTitleLink" class="header__menu__proposition-name">$navTitle</a>""")
    }

    "contain correct navigation header when navTitle and navLinks is specified SDT-474" in new Setup {
      val navTitle = "My service"
      val navLink1 = "www.example.com/my-service"
      val text1 = "My service"
      val navLink2 = "www.example.com/another-service"
      val text2 = "Another service"
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map(
        "navTitle" -> navTitle,
        "hasNavLinks" -> true,
        "navLinks" -> Seq(
          Map("navLink" -> navLink1, "text" -> text1),
          Map("navLink" -> navLink2, "text" -> text2)
        )
      )).body
      renderedHtml should include("""<div class="header-proposition">""")
      renderedHtml should include(s"""<span class="header__menu__proposition-name">$navTitle</span>""")
      renderedHtml should include(s"""<a href="#proposition-links" class="js-header-toggle menu">Menu</a>""")
      renderedHtml should include(s"""<ul id="proposition-links" class="header__menu__proposition-links">""")
      renderedHtml should include(s"""<li><a href="$navLink1">$text1</a></li>""")
      renderedHtml should include(s"""<li><a href="$navLink2">$text2</a></li>""")
      renderedHtml should include(navLink2)
    }

    "not add a main class for main tag if non specified SDT 571" in new Setup {
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map()).body
      val mainTagRegex = "<main\\b[^>]*>".r
      val main = mainTagRegex.findFirstIn(renderedHtml).get
      main should not include("class")
    }

    "allow main tag to have it's mainClass SDT 571" in new Setup {
      val renderedHtml: String = localTemplateRenderer.parseTemplate(Html(""), Map(
        "mainClass" -> "clazz"
      )).body
      val mainTagRegex = "<main\\b[^>]*>".r
      val main = mainTagRegex.findFirstIn(renderedHtml).get
      main should include("""class="clazz"""")
    }
  }

  trait Setup {

    val result: Future[Result] = GovUkTemplateRendererController.serveMustacheTemplate()(fakeRequest)
    val bodyText: String = contentAsString(result)
    status(result) shouldBe OK


    val localTemplateRenderer = new MustacheRendererTrait {
      override lazy val templateServiceAddress: String = ???
      override lazy val connection: WSGet = ???
      override lazy val mustacheTemplateString: String = bodyText
    }
  }
}