package com.aiyu.findex.core.link

import java.net.URLDecoder

/**
 * The pairing QR code Findex for desktop shows:
 * `findex://pair?v=1&id=<fingerprint>&h=<ip>,<ip>&p=<port>&s=<secret>&n=<name>`.
 * Same format as `findex-link/src/qr.rs`. The fingerprint pins the PC from the first connection;
 * the one-time secret lets the PC accept without a prompt.
 */
data class LinkQr(val id: String, val hosts: List<String>, val port: Int, val secret: String, val name: String) {
    companion object {
        fun parse(text: String): LinkQr? {
            val query = text.trim().removePrefix("findex://pair?").takeIf { it != text.trim() } ?: return null
            val params = query.split('&').mapNotNull { part ->
                val key = part.substringBefore('=')
                val value = runCatching { URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrNull() ?: return@mapNotNull null
                key to value
            }.toMap()
            if (params["v"] != "1") return null
            val id = params["id"]?.lowercase()?.takeIf { LinkCrypto.unhex(it)?.size == 32 } ?: return null
            val secret = params["s"]?.lowercase()?.takeIf { (LinkCrypto.unhex(it)?.size ?: 0) >= 16 } ?: return null
            val hosts = params["h"]?.split(',')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: return null
            val port = params["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            return LinkQr(id, hosts, port, secret, params["n"].orEmpty())
        }
    }
}
