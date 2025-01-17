package stryker4s.log

import org.apache.maven.plugin.logging.Log
import org.apache.maven.shared.utils.logging.MessageUtils

class MavenMojoLogger(mavenLogger: Log) extends Logger {

  def log(level: Level, msg: => String): Unit = doLog(level)(
    mavenLogger.debug(msg),
    mavenLogger.info(msg),
    mavenLogger.warn(msg),
    mavenLogger.error(msg)
  )

  def log(level: Level, msg: => String, e: => Throwable): Unit = doLog(level)(
    mavenLogger.debug(msg, e),
    mavenLogger.info(msg, e),
    mavenLogger.warn(msg, e),
    mavenLogger.error(msg, e)
  )

  def log(level: Level, e: Throwable): Unit = doLog(level)(
    mavenLogger.debug(e),
    mavenLogger.info(e),
    mavenLogger.warn(e),
    mavenLogger.error(e)
  )

  private def doLog(level: Level)(
      onDebug: => Unit,
      onInfo: => Unit,
      onWarn: => Unit,
      onError: => Unit
  ): Unit = level match {
    case Debug => if (mavenLogger.isDebugEnabled()) onDebug
    case Info  => if (mavenLogger.isInfoEnabled()) onInfo
    case Warn  => if (mavenLogger.isWarnEnabled()) onWarn
    case Error => if (mavenLogger.isErrorEnabled()) onError
  }

  override val colorEnabled = MessageUtils.isColorEnabled() && !sys.env.contains("NO_COLOR")
}
