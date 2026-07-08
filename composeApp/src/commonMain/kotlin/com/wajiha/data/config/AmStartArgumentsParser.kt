package com.wajiha.data.config

/**
 * Parses Daijishō / iiSU "am start"-style argument strings into a structured
 * launch spec. Handles:
 *
 * - `-n pkg/Activity` component, or a bare leading `pkg/Activity` token (iiSU)
 * - `-a ACTION`, `-d DATA`
 * - `-e KEY VALUE` string extras and typed `--es/--ei/--el/--ez/--ef`
 * - `--activity-clear-top` style flags
 * - single-quoted values containing spaces
 * - `%PACKAGE%` in the component position (resolved by caller per package)
 *
 * Placeholders such as `{file.path}` / `%ROM%` are preserved verbatim in the
 * output; the launcher substitutes them at launch time.
 */
object AmStartArgumentsParser {

    data class ParsedCommand(
        val packageName: String?,
        val activityName: String?,
        val action: String?,
        val dataUri: String?,
        val mimeType: String?,
        val extras: List<IntentExtra>,
        val activityFlags: List<String>
    )

    fun parse(arguments: String): ParsedCommand {
        val tokens = tokenize(arguments)
        var pkg: String? = null
        var activity: String? = null
        var action: String? = null
        var data: String? = null
        var mime: String? = null
        val extras = mutableListOf<IntentExtra>()
        val flags = mutableListOf<String>()

        var i = 0
        // iiSU style: first token is `pkg/Activity` with no -n switch
        if (tokens.isNotEmpty() && !tokens[0].startsWith("-") && tokens[0].contains('/')) {
            val (p, a) = splitComponent(tokens[0])
            pkg = p
            activity = a
            i = 1
        }

        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token == "-n" -> {
                    tokens.getOrNull(i + 1)?.let {
                        val (p, a) = splitComponent(it)
                        pkg = p
                        activity = a
                    }
                    i += 2
                }
                token == "-a" -> {
                    action = tokens.getOrNull(i + 1); i += 2
                }
                token == "-d" -> {
                    data = tokens.getOrNull(i + 1); i += 2
                }
                token == "-t" -> {
                    mime = tokens.getOrNull(i + 1); i += 2
                }
                token == "-e" || token == "--es" -> {
                    val key = tokens.getOrNull(i + 1)
                    val value = tokens.getOrNull(i + 2)
                    if (key != null && value != null) extras += IntentExtra(key, value, "string")
                    i += 3
                }
                token == "--ei" -> {
                    val key = tokens.getOrNull(i + 1)
                    val value = tokens.getOrNull(i + 2)
                    if (key != null && value != null) extras += IntentExtra(key, value, "int")
                    i += 3
                }
                token == "--el" -> {
                    val key = tokens.getOrNull(i + 1)
                    val value = tokens.getOrNull(i + 2)
                    if (key != null && value != null) extras += IntentExtra(key, value, "long")
                    i += 3
                }
                token == "--ez" -> {
                    val key = tokens.getOrNull(i + 1)
                    val value = tokens.getOrNull(i + 2)
                    if (key != null && value != null) extras += IntentExtra(key, value, "boolean")
                    i += 3
                }
                token == "--ef" -> {
                    val key = tokens.getOrNull(i + 1)
                    val value = tokens.getOrNull(i + 2)
                    if (key != null && value != null) extras += IntentExtra(key, value, "float")
                    i += 3
                }
                token.startsWith("--activity-") -> {
                    flags += token.removePrefix("--activity-")
                    i += 1
                }
                token == "-f" -> {
                    // raw numeric flags — keep as flag token for the launcher
                    tokens.getOrNull(i + 1)?.let { flags += "raw:$it" }
                    i += 2
                }
                token == "-W" || token == "-D" || token == "--turn-screen-on" -> i += 1
                else -> {
                    // Unknown switch with likely value — skip conservatively
                    i += if (token.startsWith("-")) 2 else 1
                }
            }
        }

        return ParsedCommand(
            packageName = pkg,
            activityName = activity?.let { expandActivity(pkg, it) },
            action = action,
            dataUri = data,
            mimeType = mime,
            extras = extras,
            activityFlags = flags
        )
    }

    private fun splitComponent(component: String): Pair<String?, String?> {
        val idx = component.indexOf('/')
        if (idx <= 0) return component to null
        val pkg = component.substring(0, idx)
        val activity = component.substring(idx + 1)
        return pkg to activity
    }

    private fun expandActivity(pkg: String?, activity: String): String =
        if (activity.startsWith(".") && pkg != null && !pkg.contains('%')) pkg + activity
        else activity

    /** Splits on whitespace/newlines, honoring single and double quotes. */
    internal fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        for (c in input) {
            when {
                quote != null -> {
                    if (c == quote) quote = null else current.append(c)
                }
                c == '\'' || c == '"' -> quote = c
                c.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) tokens += current.toString()
        return tokens
    }
}
