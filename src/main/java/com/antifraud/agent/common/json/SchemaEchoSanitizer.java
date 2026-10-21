package com.antifraud.agent.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 清理大模型返回的 "JSON Schema 回显"。
 * <p>
 * 背景: {@code BeanOutputConverter.getFormat()} 会把 JSON Schema 拼进提示词用来约束输出。
 * 能力较弱的模型有时不去"按 schema 填数据", 而是把 schema 本身原样返回, 例如:
 * <pre>
 * {"followUp": {"type":"string"}, "intent":"QUERY", "missingInfo": {"type":"boolean"}}
 * </pre>
 * 这会让 Jackson 抛
 * {@code MismatchedInputException: Cannot deserialize value of type java.lang.String/boolean from Object value}。
 * <p>
 * 处理: 把 schema 片段(形如 {"type":"string"} / {"type":"string","description":"..."})替换为 null,
 * 使解析成功并落到"信息缺失 -> 追问 / 转人工"的降级分支, 而不是整体失败。
 */
public final class SchemaEchoSanitizer {

    /** JSON Schema 中的类型名, 用于识别 {"type":"string"} 这类片段 */
    private static final Set<String> JSON_TYPE_NAMES =
            Set.of("string", "boolean", "number", "integer", "object", "array", "null");

    /** 文本形态的占位符回显: 模型常把字段值直接写成类型名或 "null" 字符串 */
    private static final Set<String> PLACEHOLDER_VALUES =
            Set.of("string", "boolean", "number", "integer", "object", "array", "null", "N/A", "n/a", "-");

    /** JSON Schema 合法关键字; 若对象只含这些键, 才判定为 schema 片段 */
    private static final Set<String> SCHEMA_KEYS = Set.of(
            "type", "description", "properties", "items", "enum", "format", "required",
            "additionalProperties", "$schema", "title", "default", "pattern",
            "minimum", "maximum", "$ref", "definitions", "anyOf", "oneOf", "allOf");

    private SchemaEchoSanitizer() {
    }

    /**
     * 清洗 JSON 文本中的 schema 回显。
     *
     * @param json   模型原始输出(建议先经 {@code extractJson} 去围栏)
     * @param mapper ObjectMapper
     * @return 清洗后的 JSON 文本; 解析异常时原样返回
     */
    public static String sanitize(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank() || mapper == null) {
            return "{}";
        }
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode cleaned = clean(root);
            return cleaned == null ? "{}" : mapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            return json;
        }
    }

    private static JsonNode clean(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        // 文本形态的 schema/占位符回显: 如 "fraudType":"string"、"reason":"null"
        if (node.isTextual() && PLACEHOLDER_VALUES.contains(node.asText().trim())) {
            return NullNode.getInstance();
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            if (isSchemaFragment(obj)) {
                return NullNode.getInstance();
            }
            List<String> keys = new ArrayList<>();
            obj.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                JsonNode value = obj.get(key);
                if (value != null && (value.isObject() || value.isArray())) {
                    obj.set(key, clean(value));
                }
            }
            return obj;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode value = arr.get(i);
                if (value != null && (value.isObject() || value.isArray())) {
                    arr.set(i, clean(value));
                }
            }
            return arr;
        }
        return node;
    }

    /** 判定是否为 schema 片段: 含 "type" 且取值为 JSON 类型名, 且所有键都属于 schema 关键字 */
    private static boolean isSchemaFragment(ObjectNode obj) {
        JsonNode type = obj.get("type");
        if (type == null || !type.isTextual() || !JSON_TYPE_NAMES.contains(type.asText())) {
            return false;
        }
        for (Iterator<String> it = obj.fieldNames(); it.hasNext(); ) {
            if (!SCHEMA_KEYS.contains(it.next())) {
                return false;
            }
        }
        return true;
    }
}
