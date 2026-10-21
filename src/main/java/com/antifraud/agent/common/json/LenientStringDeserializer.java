package com.antifraud.agent.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * 宽松的字符串反序列化器: 用于 {@code List<String>} 等集合的元素。
 * <p>
 * 大模型返回结构化结果时, 声明为"字符串数组"的字段经常返回对象, 例如:
 * <pre>
 * "matchedCases": [ {"phone": "13800001111"} ]
 * </pre>
 * 而 DTO 中是 {@code List<String>}, Jackson 默认会抛
 * {@code MismatchedInputException: Cannot deserialize value of type java.lang.String from Object value}。
 * <p>
 * 本实现: 字符串原样返回; 对象/数组则序列化为紧凑 JSON 文本, 保证不因模型输出格式漂移而失败。
 */
public class LenientStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.readValueAsTree();
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }
}
