package mesosphere.marathon.state

// Accord validation.
import com.wix.accord.dsl._
import mesosphere.marathon.api.v2.ModelValidation

import mesosphere.marathon.plugin

import scala.language.implicitConversions

case class PathId(path: List[String], absolute: Boolean = true) extends Ordered[PathId] with plugin.PathId {

  def root: String = path.headOption.getOrElse("")

  def rootPath: PathId = PathId(path.headOption.map(_ :: Nil).getOrElse(Nil), absolute)

  def tail: List[String] = path.tail

  def isEmpty: Boolean = path.isEmpty

  def isRoot: Boolean = path.isEmpty

  def parent: PathId = path match {
    case Nil          => this
    case head :: Nil  => PathId(Nil, absolute)
    case head :: rest => PathId(path.reverse.tail.reverse, absolute)
  }

  def allParents: List[PathId] = if (isRoot) Nil else {
    val p = parent
    p :: p.allParents
  }

  def child: PathId = PathId(tail)

  def append(id: PathId): PathId = PathId(path ::: id.path, absolute)

  def append(id: String): PathId = append(PathId(id))

  def /(id: String): PathId = append(id)

  def restOf(parent: PathId): PathId = {
    def in(currentPath: List[String], parentPath: List[String]): List[String] = {
      if (currentPath.isEmpty) Nil
      else if (parentPath.isEmpty || currentPath.head != parentPath.head) currentPath
      else in(currentPath.tail, parentPath.tail)
    }
    PathId(in(path, parent.path), absolute)
  }

  def canonicalPath(base: PathId = PathId(Nil, absolute = true)): PathId = {
    require(base.absolute, "Base path is not absolute, canonical path can not be computed!")
    def in(remaining: List[String], result: List[String] = Nil): List[String] = remaining match {
      case head :: tail if head == "."  => in(tail, result)
      case head :: tail if head == ".." => in(tail, if (result.nonEmpty) result.tail else Nil)
      case head :: tail                 => in(tail, head :: result)
      case Nil                          => result.reverse
    }
    if (absolute) PathId(in(path)) else PathId(in(base.path ::: path))
  }

  def safePath: String = {
    require(absolute, "Path is not absolute. Can not create safe path.")
    path.mkString("_")
  }

  def toHostname: String = path.reverse.mkString(".")

  def includes(definition: plugin.PathId): Boolean = {
    //scalastyle:off return
    if (path.size < definition.path.size) return false
    path.zip(definition.path).forall { case (left, right) => left == right }
  }

  override def toString: String = toString("/")
  private def toString(delimiter: String): String = path.mkString(if (absolute) delimiter else "", delimiter, "")

  override def compare(that: PathId): Int = {
    import Ordering.Implicits._
    val seqOrder = implicitly(Ordering[List[String]])
    seqOrder.compare(canonicalPath().path, that.canonicalPath().path)
  }
}

object PathId {
  def fromSafePath(in: String): PathId = PathId(in.split("_").toList, absolute = true)
  def apply(in: String): PathId =
    PathId(in.replaceAll("""(^/+)|(/+$)""", "").split("/").filter(_.nonEmpty).toList, in.startsWith("/"))
  def empty: PathId = PathId(Nil)

  implicit class StringPathId(val stringPath: String) extends AnyVal {
    def toPath: PathId = PathId(stringPath)
    def toRootPath: PathId = PathId(stringPath).canonicalPath()
  }

  /**
    * This regular expression is used to validate each path segment of an ID.
    *
    * If you change this, please also change "pathType" in AppDefinition.json and
    * notify the maintainers of the DCOS CLI.
    */
  private[this] val ID_PATH_SEGMENT_PATTERN =
    "^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])|(\\.|\\.\\.)$".r

  implicit val pathIdValidator = validator[PathId] { pathId =>
    pathId.path.each should matchRegex(ID_PATH_SEGMENT_PATTERN.pattern)
    // pathId.path.forall(.matcher(_).matches()) s"""path contains invalid characters (allowed: lowercase letters, digits, hyphens, ".", "..")""" is true
    /*
    if (!pathId.path.forall(ID_PATH_SEGMENT_PATTERN.pattern.matcher(_).matches()))
      ModelValidation.failureWithRuleViolation(pathId,
        s"""path contains invalid characters (allowed: lowercase letters, digits, hyphens, ".", "..")""",
        Some(pathId.toString))
    else ModelValidation.failureWithRuleViolation(pathId,
      s"""path contains invalid characters (allowed: lowercase letters, digits, hyphens, ".", "..")""",
      Some(pathId.toString))
      */
  }

/*  implicit val pathSetIdValidator = validator[Set[PathId]] { setPathId =>
    setPathId.zipWithIndex.each is validWithPosition
    // pathId.path.forall(.matcher(_).matches()) s"""path contains invalid characters (allowed: lowercase letters, digits, hyphens, ".", "..")""" is true
    /*
    if (!pathId.path.forall(ID_PATH_SEGMENT_PATTERN.pattern.matcher(_).matches()))
      ModelValidation.failureWithRuleViolation(pathId,
        s"""path contains invalid characters (allowed: lowercase letters, digits, hyphens, ".", "..")""",
        Some(pathId.toString))
    else ModelValidation.failureWithRuleViolation(pathId,
      s"""path contains invalid characters (allowed: lowercase letters, digits, hyphens, ".", "..")""",
      Some(pathId.toString))
      */
  }*/



/*
  def validWithPosition[T](implicit validator: Validator[T]): Validator[(T, Int)] =
    new Validator[(T, Int)] {
      def apply(value: (T, Int)) = {
        validate(value._1) match {
          case f: Failure => Failure(Set(GroupViolation(value._1, s"at position ${value._2}", None, f.violations)))
          case Success => Success
        }

        /*
        val violations = values.zipWithIndex.flatMap({ case (item, pos) =>
            validate(item) match {
              case f: Failure => Some(RuleViolation(item, s"at position $pos", None))
              case Success => None
            }
        })
        if(violations.nonEmpty)
          Failure(violations.toSet)
        else
          Success*/
      }
    }*/
}
