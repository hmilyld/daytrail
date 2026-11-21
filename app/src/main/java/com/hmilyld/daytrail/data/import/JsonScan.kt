package com.hmilyld.daytrail.data.import

/**
 * 在**不构建语法树**的前提下，从 JSON 文本里取出数组的顶层元素。
 *
 * 为什么要自己写：`Json.parseToJsonElement` 会把整个文件变成对象树，
 * 一份几万点的轨迹导出在内存里放大到上百 MB，导入直接 OOM。
 * 这里只做一件事——把 `"features": [ ... ]` 按顶层逗号切开，
 * 之后逐个 Feature 解析，内存占用与「单个 Feature」成正比。
 *
 * 只处理结构扫描，不校验 JSON 语义：真正的语法错误交给
 * `parseToJsonElement` 在单个元素上报错。
 */
internal object JsonScan {
    /**
     * 取出 `"key": [ ... ]` 中每个顶层元素的原始文本（已 trim）。
     * 找不到这个键或数组不完整时返回 null。
     */
    fun elementsOfArray(
        raw: String,
        key: String,
    ): List<String>? {
        val marker = "\"$key\""
        var searchFrom = 0
        while (searchFrom < raw.length) {
            val keyAt = raw.indexOf(marker, searchFrom)
            if (keyAt < 0) return null
            var i = keyAt + marker.length
            // 键后面必须是冒号：同名字符串出现在别的值里（比如 notes）时必须跳过
            while (i < raw.length && raw[i].isWhitespace()) i++
            if (i < raw.length && raw[i] == ':') {
                i++
                while (i < raw.length && raw[i].isWhitespace()) i++
                if (i < raw.length && raw[i] == '[') return splitArray(raw, i)
            }
            searchFrom = keyAt + marker.length
        }
        return null
    }

    /** 读取 `"key": 123` 形式的整数值。找不到返回 null。 */
    fun intValueOf(
        raw: String,
        key: String,
    ): Int? {
        val marker = "\"$key\""
        val keyAt = raw.indexOf(marker)
        if (keyAt < 0) return null
        var i = keyAt + marker.length
        while (i < raw.length && raw[i].isWhitespace()) i++
        if (i >= raw.length || raw[i] != ':') return null
        i++
        while (i < raw.length && raw[i].isWhitespace()) i++
        val start = i
        while (i < raw.length && (raw[i].isDigit() || raw[i] == '-')) i++
        if (i == start) return null
        return raw.substring(start, i).toIntOrNull()
    }

    /**
     * 从 [openAt] 处的 `[` 开始，按顶层逗号切开元素。
     *
     * 字符串内的括号与逗号不参与计数，`\` 转义要正确跳过——
     * 这两点是切分器唯一容易写错的地方，单测里各有一条用例。
     */
    private fun splitArray(
        raw: String,
        openAt: Int,
    ): List<String> {
        val elements = mutableListOf<String>()
        var i = openAt + 1
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false

        while (i < raw.length) {
            val c = raw[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> {
                        if (start < 0) start = i
                        inString = true
                    }
                    '{', '[' -> {
                        if (start < 0) start = i
                        depth++
                    }
                    '}', ']' -> {
                        if (c == ']' && depth == 0) {
                            if (start >= 0) elements += raw.substring(start, i).trim()
                            return elements.filter { it.isNotEmpty() }
                        }
                        depth--
                    }
                    ',' -> {
                        if (depth == 0) {
                            if (start >= 0) elements += raw.substring(start, i).trim()
                            start = -1
                        }
                    }
                    else -> if (start < 0 && !c.isWhitespace()) start = i
                }
            }
            i++
        }
        // 数组没闭合：交给调用方按「文件损坏」处理
        return elements.filter { it.isNotEmpty() }
    }
}
