package com.example.smstopc

object CodeExtractor {

    private val keywords = listOf("验证码", "校验码", "动态码", "验证代码")
    private val codeRegex = Regex("""\d{4,8}""")

    fun extract(text: String): String? {
        val cleaned = text.trim()

        // Find which keyword is present and its position
        val keywordIndex = keywords.minOfOrNull { kw ->
            val idx = cleaned.indexOf(kw)
            if (idx >= 0) idx else Int.MAX_VALUE
        }?.takeIf { it < Int.MAX_VALUE } ?: return null

        // Find all 4-8 digit numbers with their positions, pick closest to keyword
        return codeRegex.findAll(cleaned).minByOrNull { match ->
            kotlin.math.abs(match.range.first - keywordIndex)
        }?.value
    }

    fun extractOrDefault(text: String): String {
        return extract(text) ?: "未识别"
    }
}
