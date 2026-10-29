package com.wanderwildwood.soramoyo.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * One plain-text GET, to a weather station on the home network and to nothing else.
 *
 * An Ecowitt gateway or a Davis WeatherLink Live speaks plain http, at an address only its owner
 * knows. Android's network security config can name a host but not "any private address", so
 * the app declares no cleartext traffic at all (the platform refuses plain http everywhere) and
 * this class makes the one exception itself: it resolves the typed address, refuses it unless
 * every address it resolves to is a private one (10/8, 172.16/12, 192.168/16, 169.254/16,
 * fc00::/7, fe80::/10), and then connects to the address it checked, so a name cannot be
 * pointed somewhere else between the check and the connection. Redirects are not followed.
 */
object LocalHttp {

    /** The typed address is not one on a home network, so nothing was sent to it. */
    class NotLocal(message: String) : IOException(message)

    private const val LIMIT = 1024 * 1024

    /** Whether [a] is an address that only a local network uses. */
    fun isLocal(a: ByteArray): Boolean = when (a.size) {
        4 -> {
            val b0 = a[0].toInt() and 0xff
            val b1 = a[1].toInt() and 0xff
            b0 == 10 || (b0 == 172 && b1 in 16..31) || (b0 == 192 && b1 == 168) || (b0 == 169 && b1 == 254)
        }
        16 -> {
            val b0 = a[0].toInt() and 0xff
            val b1 = a[1].toInt() and 0xff
            (b0 and 0xfe) == 0xfc || (b0 == 0xfe && (b1 and 0xc0) == 0x80)
        }
        else -> false
    }

    fun isLocal(a: InetAddress): Boolean = when (a) {
        is Inet4Address, is Inet6Address -> isLocal(a.address)
        else -> false
    }

    /** What the user typed, split into a host and a port (80 unless given). Scheme and path are dropped. */
    data class Target(val host: String, val port: Int)

    fun parse(typed: String): Target {
        var s = typed.trim()
        s = s.removePrefix("http://").removePrefix("https://").removePrefix("HTTP://").removePrefix("HTTPS://")
        s = s.substringBefore('/').substringBefore('?').trim()
        if (s.isEmpty()) throw IOException("no address")
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end < 0) throw IOException("bad address")
            val port = s.substring(end + 1).removePrefix(":").toIntOrNull() ?: 80
            return Target(s.substring(1, end), port)
        }
        if (s.count { it == ':' } == 1) {
            return Target(s.substringBefore(':'), s.substringAfter(':').toIntOrNull() ?: throw IOException("bad port"))
        }
        return Target(s, 80)
    }

    /** The addresses [host] names, all of which must be local; [NotLocal] otherwise. */
    fun resolveLocal(host: String): List<InetAddress> {
        val all = InetAddress.getAllByName(host).toList()
        if (all.isEmpty() || !all.all { isLocal(it) }) {
            throw NotLocal("$host is not an address on a home network")
        }
        return all
    }

    /** GET [path] from the station typed as [typed]; the body of a 200 answer. */
    fun get(typed: String, path: String, connectMs: Int = 4000, readMs: Int = 6000): String {
        val target = parse(typed)
        val addresses = resolveLocal(target.host)
        var last: IOException? = null
        for (ip in addresses) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, target.port), connectMs)
                    socket.soTimeout = readMs
                    val hostHeader = if (target.port == 80) target.host else "${target.host}:${target.port}"
                    val request = "GET $path HTTP/1.1\r\nHost: $hostHeader\r\nUser-Agent: Sky\r\n" +
                        "Accept: application/json\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().apply { write(request.toByteArray(Charsets.ISO_8859_1)); flush() }
                    return parseResponse(socket.getInputStream())
                }
            } catch (e: NotLocal) {
                throw e
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("no answer")
    }

    /** Status line, headers, then a body that is chunked, of a stated length, or runs to the close. */
    internal fun parseResponse(input: InputStream): String {
        val status = readLine(input) ?: throw IOException("no answer")
        val code = status.split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("not http")
        var length = -1
        var chunked = false
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val name = line.substringBefore(':').trim().lowercase()
            val value = line.substringAfter(':', "").trim()
            if (name == "content-length") length = value.toIntOrNull() ?: -1
            if (name == "transfer-encoding" && value.lowercase().contains("chunked")) chunked = true
        }
        if (code != 200) throw IOException("gateway answered $code")
        val body = ByteArrayOutputStream()
        if (chunked) {
            while (true) {
                val size = readLine(input)?.substringBefore(';')?.trim()?.toIntOrNull(16) ?: throw IOException("bad chunk")
                if (size == 0) break
                copy(input, body, size)
                readLine(input)
            }
        } else if (length >= 0) {
            copy(input, body, length)
        } else {
            val buf = ByteArray(4096)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                body.write(buf, 0, n)
                if (body.size() > LIMIT) throw IOException("answer too large")
            }
        }
        return body.toString("UTF-8")
    }

    private fun copy(input: InputStream, out: ByteArrayOutputStream, count: Int) {
        if (count > LIMIT || out.size() + count > LIMIT) throw IOException("answer too large")
        var left = count
        val buf = ByteArray(4096)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size, left))
            if (n < 0) throw IOException("answer cut short")
            out.write(buf, 0, n)
            left -= n
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 8192) throw IOException("line too long")
        }
    }
}
