package io.eels.component.csv

import com.univocity.parsers.csv.CsvParser
import io.eels.{Part, Row}
import io.eels.schema.Schema
import java.nio.file.Path

import com.sksamuel.exts.Logging
import rx.lang.scala.Observable

import scala.util.control.NonFatal

class CsvPart(val createParser: () => CsvParser,
              val path: Path,
              val header: Header,
              val skipBadRows: Boolean,
              val schema: Schema) extends Part with Logging {

  val rowsToSkip: Int = header match {
    case Header.FirstRow => 1
    case _ => 0
  }

  override def data(): Observable[Row] = {

    val parser = createParser()
    parser.beginParsing(path.toFile())

    val iterator = Iterator.continually(parser.parseNext).takeWhile(_ != null).drop(rowsToSkip)

    Observable.apply { sub =>
      try {
        sub.onStart()
        iterator.foreach { record =>
          try {
            val row = Row(schema, record.toVector)
            sub.onNext(row)
          } catch {
            case NonFatal(e) if skipBadRows =>
              logger.warn(s"Parse error, record=$record")
          }
        }
      } catch {
        case e: Throwable =>
          sub.onError(e)
      } finally {
        if (!sub.isUnsubscribed)
          sub.onCompleted()
      }
    }
  }
}