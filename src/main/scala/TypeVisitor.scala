/**
 * This file is part of objc2swift.
 * https://github.com/yahoojapan/objc2swift
 *
 * Copyright (c) 2015 Yahoo Japan Corporation
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

import ObjCParser._
import org.antlr.v4.runtime.tree.TerminalNode
import collection.JavaConversions._

trait TypeVisitor extends Converter {
  self: ObjCBaseVisitor[String] =>

  /**
   * Convert Objective-C type to Swift type.
   *
   * @param ctx the parse tree
   * @return Swift type strings
   *
   * TODO: Implement other types
   *
   */
  override def visitType_specifier(ctx: Type_specifierContext): String = {
    val sb = new StringBuilder()

    for (element <- ctx.children) {
      element match {
        case symbol: TerminalNode => {
          sb.append(
            symbol.getSymbol.getText match {
              case "void"   => "void"
              case "id"     => "AnyObject"
              case "short"  => "Int8"
              case "int"    => "Int32"
              case "long"   => "Int32"
              case "float"  => "Float"
              case "double" => "Double"
              case s if s != "" => s
              case _ => "AnyObject"
            }
          )
        }
        case className: Class_nameContext => {
          sb.append(
            className.getText match {
              case "NSInteger" | "NSUInteger" => "Int"
              case "NSArray" => "[AnyObject]"
              case "NSDictionary" => "[NSObject : AnyObject]"
              case "SEL" => "Selector"
              case "BOOL" => "Bool"
              case s if s != "" => s
              case _ => "AnyObject"
            }
          )
        }
        case pointer: PointerContext => {
          sb.append(visit(pointer))
        }
        case _ => {
          sb.append(element.getText)
        }
      }
    }

    sb.toString()
  }
}