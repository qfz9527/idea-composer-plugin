package org.psliwa.idea.composerJson.intellij.codeAssist

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.json.JsonLanguage
import com.intellij.json.psi.{JsonArray, JsonFile, JsonObject, JsonProperty}
import com.intellij.patterns.PlatformPatterns._
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.psliwa.idea.composerJson._
import org.psliwa.idea.composerJson.intellij.Patterns._
import org.psliwa.idea.composerJson.json._

import scala.collection.Seq

abstract class AbstractCompletionContributor extends com.intellij.codeInsight.completion.CompletionContributor {

  private lazy val schema = ComposerSchema

  schema.foreach(addCompletionProvidersForSchema)

  import org.psliwa.idea.composerJson.intellij.codeAssist.AbstractCompletionContributor._

  private def addCompletionProvidersForSchema(schema: Schema): Unit = {
    completionProvidersForSchema(schema, rootPsiElementPattern).foreach {
      case (pattern, provider) => extend(CompletionType.BASIC, pattern, provider)
    }
  }

  private def completionProvidersForSchema(s: Schema, parent: Capture): List[(Capture, CompletionProvider[CompletionParameters])] = s match {
    case SObject(properties, _) => {
      propertyCompletionProvider(parent, properties) ++
        properties.flatMap(t => completionProvidersForSchema(t._2.schema, psiElement().and(propertyCapture(parent)).withName(t._1)))
    }
    case SOr(l) => l.flatMap(completionProvidersForSchema(_, parent))
    case SArray(i) => completionProvidersForSchema(i, psiElement(classOf[JsonArray]).withParent(parent))
    case _ => getCompletionProvidersForSchema(s, parent)
  }

  protected def propertyCompletionProvider(parent: Capture, properties: Map[String, Property]): List[(Capture, CompletionProvider[CompletionParameters])] = List()

  protected def insertHandlerFor(schema: Schema): Option[InsertHandler[LookupElement]] = None

  protected def getCompletionProvidersForSchema(s: Schema, parent: Capture): List[(Capture, CompletionProvider[CompletionParameters])]

  protected def propertyCompletionProvider(parent: Capture, es: LookupElements, getInsertHandler: InsertHandlerFinder = _ => None) = {
    List((
      psiElement()
        .withSuperParent(2,
          psiElement().and(propertyCapture(parent))
            .withName(stringContains(EmptyNamePlaceholder))
        ),
      new LookupElementsCompletionProvider(es, getInsertHandler)
    ))
  }

  private def rootPsiElementPattern: PsiElementPattern.Capture[JsonFile] = {
    psiElement(classOf[JsonFile])
      .withLanguage(JsonLanguage.INSTANCE)
      .inFile(psiFile(classOf[JsonFile]).withName(ComposerJson))
  }

  private[intellij] def propertyCapture(parent: Capture): PsiElementPattern.Capture[JsonProperty] = {
    psiElement(classOf[JsonProperty]).withParent(psiElement(classOf[JsonObject]).withParent(parent))
  }
}

object AbstractCompletionContributor {

  class ParametersDependantCompletionProvider(loadElements: CompletionParameters => Seq[BaseLookupElement]) extends AbstractCompletionProvider {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      val es = loadElements(parameters)

      addLookupElementsToResult(es)(parameters, mapResult(result))
    }

    protected def mapResult(result: CompletionResultSet) = result
  }

  abstract class AbstractCompletionProvider extends com.intellij.codeInsight.completion.CompletionProvider[CompletionParameters] {
    protected def addLookupElementsToResult(es: Iterable[BaseLookupElement], getInsertHandler: InsertHandlerFinder = _ => None)
        (parameters: CompletionParameters, result: CompletionResultSet) {

      es.foreach(e => {
        result.addElement(e.withPsiElement(parameters.getPosition).withInsertHandler(insertHandler(parameters.getPosition, e, getInsertHandler)))
      })
    }

    protected def insertHandler(element: PsiElement, le: BaseLookupElement, getInsertHandler: InsertHandlerFinder) = {
      if(!le.quoted) null
      else getInsertHandler(le).getOrElse(QuoteInsertHandler)
    }
  }

  class LookupElementsCompletionProvider(es: LookupElements, getInsertHandler: InsertHandlerFinder = _ => None)
    extends AbstractCompletionProvider {

    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      addLookupElementsToResult(es(), getInsertHandler)(parameters, result)
    }
  }
}