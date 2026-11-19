package com.aiyu.findex.core.link

/** Wire paths are `/<root>/<relative path>`; these helpers are shared by the client and the server. */
object LinkPaths {
    /** Components of [path], or null when any is `.`, `..`, too long or holds `\`, `:` or NUL. Empty parts are dropped. */
    fun split(path: String): List<String>? {
        val parts = path.split('/').filter { it.isNotEmpty() }
        return parts.takeIf { it.all(::validName) }
    }

    fun validName(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && name.length <= 255 && name.none { it == '/' || it == '\\' || it == ':' || it == '\u0000' }

    /** `"Home/a"` (a path inside a network server, as SmbRepository passes it) to `"/Home/a"`. */
    fun wire(inner: String): String = "/" + inner.trim('/')

    fun join(dir: String, name: String): String = if (dir.trim('/').isEmpty()) name else "${dir.trim('/')}/$name"
}
