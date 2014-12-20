package org.psliwa.idea.composer.idea

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.json.JsonLanguage
import com.intellij.json.psi._
import com.intellij.patterns.PlatformPatterns._
import com.intellij.patterns.StandardPatterns._
import com.intellij.patterns.{PsiElementPattern, PatternCondition}
import com.intellij.util.ProcessingContext
import org.psliwa.idea.composer.packagist.Packagist
import org.psliwa.idea.composer.schema._
import org.psliwa.idea.composer.util.Funcs._
import org.psliwa.idea.composer._
import org.psliwa.idea.composer.version._

import scala.annotation.tailrec

class CompletionContributor extends com.intellij.codeInsight.completion.CompletionContributor {

  private lazy val schema = SchemaLoader.load()

  //vars only for testability
  private var packagesLoader: () => Seq[Keyword] = () => PackagesLoader.loadPackages
  private var versionsLoader: (String) => Seq[String] = memorize(30)(Packagist.loadVersions(_).right.getOrElse(List()))

  schema.foreach(addCompletionProvidersForSchema)

  private def addCompletionProvidersForSchema(schema: Schema): Unit = {
    completionProvidersForSchema(schema, rootPsiElementPattern).foreach {
      case (pattern, provider) => extend(CompletionType.BASIC, pattern, provider)
    }
  }

  private def completionProvidersForSchema(s: Schema, parent: Capture): List[(Capture, CompletionProvider[CompletionParameters])] = s match {
    case SObject(m) => {
      propertyCompletionProvider(parent, () => m.keys.map(Keyword(_)), (k) => insertHandlerFor(m.get(k.text).get)) ++
        m.flatMap(t => completionProvidersForSchema(t._2, psiElement().and(propertyCapture(parent)).withName(t._1)))
    }
    case SStringChoice(m) => List((psiElement().withSuperParent(2, parent), KeywordsCompletionProvider(() => m.map(Keyword(_)))))
    case SOr(l) => l.flatMap(completionProvidersForSchema(_, parent))
    case SArray(i) => completionProvidersForSchema(i, psiElement(classOf[JsonArray]).withParent(parent))
    case SBoolean => List((psiElement().withSuperParent(2, parent).afterLeaf(":"), KeywordsCompletionProvider(() => List("true", "false").map(Keyword(_, quoted = false)))))
    case SPackages => {
      propertyCompletionProvider(parent, loadPackages, _ => Some(StringPropertyValueInsertHandler)) ++
        List((
          psiElement().withSuperParent(2, psiElement().and(propertyCapture(parent))),
          new ContextAwareCompletionProvider(c => loadVersions(c.propertyName).flatMap(Version.alternativesForPrefix(c.typedQuery)))
        ))
    }
    case _ => List()
  }

  private def rootPsiElementPattern: PsiElementPattern.Capture[JsonFile] = {
    psiElement(classOf[JsonFile])
      .withLanguage(JsonLanguage.INSTANCE)
      .inFile(psiFile(classOf[JsonFile]).withName(ComposerJson))
  }

  private def propertyCompletionProvider(parent: Capture, keywords: Keywords, getInsertHandler: InsertHandlerFinder = _ => None) = {
    List((
      psiElement()
        .withSuperParent(2,
          psiElement().and(propertyCapture(parent))
            .withName(stringContains(emptyNamePlaceholder))
        ),
      KeywordsCompletionProvider(keywords, getInsertHandler)
    ))
  }

  @tailrec
  private def insertHandlerFor(schema: Schema): Option[InsertHandler[LookupElement]] = schema match {
    case SString | SStringChoice(_) => Some(StringPropertyValueInsertHandler)
    case SObject(_) | SPackages => Some(ObjectPropertyValueInsertHandler)
    case SArray(_) => Some(ArrayPropertyValueInsertHandler)
    case SBoolean | SNumber => Some(EmptyPropertyValueInsertHandler)
    case SOr(h::_) => insertHandlerFor(h)
    case _ => None
  }

  private def propertyCapture(parent: Capture): PsiElementPattern.Capture[JsonProperty] = {
    psiElement(classOf[JsonProperty]).withParent(psiElement(classOf[JsonObject]).withParent(parent))
  }

  protected[idea] def setPackagesLoader(l: () => Seq[Keyword]): Unit = {
    packagesLoader = l
  }

  protected[idea] def setVersionsLoader(l: (String) => Seq[String]): Unit = {
    versionsLoader = l
  }

  private def loadPackages() = packagesLoader()
  private def loadVersions(s: String) = versionsLoader(s)

  private def stringContains(s: String) = {
    string().`with`(new PatternCondition[String]("contains") {
      override def accepts(t: String, context: ProcessingContext): Boolean = t.contains(s)
    })
  }
}