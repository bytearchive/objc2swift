/**
 * This file is part of objc2swift.
 * https://github.com/yahoojapan/objc2swift
 *
 * Copyright (c) 2015 Yahoo Japan Corporation
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

package org.objc2swift.converter

import org.objc2swift.converter.ObjCParser._
import org.objc2swift.util.antlr._

import scala.collection.JavaConversions._

/**
 * Implements visit methods for method-contexts.
 */
protected trait MethodVisitor {
  this: ObjC2SwiftConverter =>

  /**
   * Returns translated text of instance method declaration context.
   *
   * @param ctx the parse tree
   * @return Strings of Swift's instance method code
   */
  override def visitInstance_method_declaration(ctx: Instance_method_declarationContext): String =
    Option(ctx.method_declaration()).map { c =>
      s"${indent(ctx)}${optional(ctx)}${visit(c)}".stripSuffix(" ")
    }.getOrElse("")

  /**
   * Returns translated text of class method declaration context.
   *
   * @param ctx the parse tree
   **/
  override def visitClass_method_declaration(ctx: Class_method_declarationContext): String =
    Option(ctx.method_declaration()).map { c =>
      s"${indent(ctx)}${optional(ctx)}class ${visit(c)}".stripSuffix(" ")
    }.getOrElse("")

  /**
   * Returns translated text of method definition context.
   *
   * @param ctx the parse tree
   * @return Strings of Swift code
   */
  override def visitInstance_method_definition(ctx: Instance_method_definitionContext): String =
    ctx.method_definition() match {
      case c if !isVisited(c) => s"${indent(ctx)}${visit(c)}".stripSuffix(" ")
      case _ => "" // Already printed
    }

  /**
   * Returns translated text of class method definition context.
   *
   * @param ctx the parse tree
   **/
  override def visitClass_method_definition(ctx: Class_method_definitionContext): String =
    ctx.method_definition() match {
      case c if !isVisited(c) => s"${indent(ctx)}class ${visit(c)}".stripSuffix(" ")
      case _ => "" // Already printed
    }

  /**
   * Returns translated text of method declaration context.
   *
   * @param ctx the parse tree
   * @return Strings of Swift's method code
   */
  override def visitMethod_declaration(ctx: Method_declarationContext): String =
    findCorrespondingMethodDefinition(ctx) match {
      case Some(impl: Method_definitionContext) => visit(impl)
      case _ =>
        // Has no definition
        val slct = ctx.method_selector()
        val tp = Option(ctx.method_type())
        val hd = createMethodHeader(slct, tp)

        // Check ancestor is protocol or not
        ctx.parent.parent.parent match {
          case _: Protocol_declarationContext => hd
          case _ => s"$hd {\n${indent(ctx)}}"
        }
    }

  /**
   * Returns translated text of method definition context.
   *
   * @param ctx the parse tree
   **/
  override def visitMethod_definition(ctx: Method_definitionContext): String = {
    val slct = ctx.method_selector()
    val tp = Option(ctx.method_type())
    val hd = createMethodHeader(slct, tp)

    s"""|$hd {
        |${visit(ctx.compound_statement())}${indent(ctx)}}""".stripMargin
  }

  /**
   * Returns translated text of method selector context.
   * @param ctx the parse tree
   **/
  override def visitMethod_selector(ctx: Method_selectorContext): String =
    Option(ctx.selector()) match {
      case Some(s) => s"${visit(s)}()" // No parameters
      case None =>
        // Method name(selector)
        val selector = Option(ctx.keyword_declarator(0).selector()).map(visit).getOrElse("")

        // First parameter
        val head = visitKeyword_declarator(ctx.keyword_declarator(0), isHead = true)

        // Other parameters
        val tail =
          ctx.keyword_declarator().tail
            .map(c => ", " + visitKeyword_declarator(c))
            .mkString

        s"$selector($head$tail)"
    }

  /**
   * Returns translated text of keyword declarator
   *
   * @param ctx the parse tree
   **/
  override def visitKeyword_declarator(ctx: Keyword_declaratorContext): String =
    visitKeyword_declarator(ctx, isHead = false)

  /**
   * Returns translated text of keyword declarator
   *
   * @param ctx the parse tree
   * @param isHead node index in branches
   * @return parameter code
   */
  private def visitKeyword_declarator(ctx: Keyword_declaratorContext, isHead: Boolean): String = {
    // Parameter's Internal name
    val paramName = ctx.IDENTIFIER().getText

    // Method name(idx = 0) or Parameter's External name
    val selector = Option(ctx.selector()).map(visit).getOrElse("")

    // Parameter's Type
    val it = ctx.method_type().toIterator
    val paramType = it.map(visit).find(_.nonEmpty).getOrElse("")

    selector match {
      case ""           => s"$paramName: $paramType" // No external name
      case _ if isHead  => s"$paramName: $paramType" // head param has no external name
      case `paramName`  => s"$paramName: $paramType" // external name equals internal one
      case _            => s"$selector $paramName: $paramType"
    }
  }

  /**
   * Return method/parameter type on Swift rule.
   *
   * @param ctx the parse tree
   * @return Swift method type
   */
  override def visitMethod_type(ctx: Method_typeContext): String = {
    val retType = {
      for {
        x <- Option(ctx.type_name().specifier_qualifier_list())
        y <- Option(x.type_specifier())
      } yield y
    }.map(concatType(_)).getOrElse("AnyObject")

    if (retType == "void") "" else retType
  }

  /**
   * Returns method header text.
   * @param sctx method_selector_context tree
   * @param tctx method_type_context tree (Optional)
   * @return Translated text of method header contexts.
   */
  private def createMethodHeader(sctx: Method_selectorContext, tctx: Option[Method_typeContext]): String =
    tctx.map(visit).map {
      case "IBAction" => s"@IBAction func ${visit(sctx)}" // IBAction
      case ""         => s"func ${visit(sctx)}" // void
      case s          => s"func ${visit(sctx)} -> $s"
    }.getOrElse(s"func ${visit(sctx)} -> AnyObject") // Default

  def findCorrespondingMethodDefinition(declCtx: Method_declarationContext): Option[Method_definitionContext] = {
    val selector = declCtx.method_selector.getText

    {
      declCtx.parent.parent.parent match {
        case classCtx: Class_interfaceContext =>
          classCtx.correspondingClassImplementation(root)
            .map(_.implementation_definition_list)
        case catCtx: Category_interfaceContext =>
          findCorrespondingCategoryImplementation(catCtx)
            .map(_.implementation_definition_list)
        case _ => None
      }
    }.flatMap { implDefList =>
      declCtx.parent match {
        case _: Instance_method_declarationContext =>
          implDefList.instance_method_definition.map(_.method_definition())
            .find(_.method_selector.getText == selector)
        case _: Class_method_declarationContext =>
          implDefList.class_method_definition.map(_.method_definition())
            .find(_.method_selector.getText == selector)
        case _ => None
      }
    }
  }
}
