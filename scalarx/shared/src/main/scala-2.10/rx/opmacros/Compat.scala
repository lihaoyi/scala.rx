package rx.opmacros
object Compat{
  type Context = scala.reflect.macros.Context
  def enclosingName(c: Context) = {
    val res =
      c.asInstanceOf[scala.reflect.macros.runtime.Context]
        .callsiteTyper
        .context
        .owner
        .asInstanceOf[c.Symbol]
    res
  }
}
