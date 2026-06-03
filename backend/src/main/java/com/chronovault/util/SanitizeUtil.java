package com.chronovault.util;

/**
 * XSS 过滤工具类 — 对用户输入的文本进行 HTML 特殊字符转义。
 * 适用于 name、title、note、description 等展示到前端的字段。
 */
public final class SanitizeUtil {

    private SanitizeUtil() {}

    /**
     * 转义 HTML 特殊字符，防止 XSS 注入。
     * 转义: & < > " ' /
     */
    public static String escapeHtml(String input) {
        if (input == null || input.isBlank()) return input;
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }

    /**
     * 去除所有 HTML 标签（保留纯文本内容）。
     */
    public static String stripHtmlTags(String input) {
        if (input == null || input.isBlank()) return input;
        return input.replaceAll("<[^>]*>", "");
    }

    /**
     * 清洗用户输入：去除 HTML 标签 + 转义特殊字符。
     */
    public static String sanitize(String input) {
        if (input == null || input.isBlank()) return input;
        return escapeHtml(stripHtmlTags(input));
    }
}
