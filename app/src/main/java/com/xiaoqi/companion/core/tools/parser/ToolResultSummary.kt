package com.xiaoqi.companion.core.tools.parser

/**
 * 工具结果的结构化摘要,供 UI 层 detail panel 渲染。
 *
 * **设计原则**
 * - **不绑死单一格式**:同时覆盖 envelope(ok/error)、legacy raw JSON、无 JSON 字符串三种输入,
 *   由 [ToolCallResultParser] 统一分派。UI 只看 summary,不关心原始字符串长什么样。
 * - **最小可携带信息**:每条 summary 给出 `(title, detail, severity, count)`,UI 用这些字段渲染面板。
 * - **失败/空 也算 summary**:`Empty`/`Failed`/`Unknown` 不是异常,是合法状态。
 *
 * **与 envelope 的关系**:`ToolEnvelope` 是 **协议层** —— LLM 看到的格式;`ToolResultSummary`
 * 是 **展示层** —— 人看到的结构。两者字段不同(后者会提取 `data.count` 这类语义),
 * 但触发条件一致(`isError` → Error)。
 */
sealed interface ToolResultSummary {

    /** 列表型:有 count + 标题列表(搜索/历史/记忆)。 */
    data class ListHits(
        val title: String,
        val count: Int,
        val items: List<String>,
    ) : ToolResultSummary

    /** 单条保存/写入/创建。 */
    data class SavedOne(
        val title: String,
        val subject: String,
    ) : ToolResultSummary

    /** 状态报告:键值对设备/天气/时间。 */
    data class KeyValueReport(
        val title: String,
        val pairs: List<Pair<String, String>>,
    ) : ToolResultSummary

    /** 提醒/定时/调度成功。 */
    data class Scheduled(
        val title: String,
        val subject: String,
        val triggerAtMillis: Long?,
        val exact: Boolean,
    ) : ToolResultSummary

    /** 业务失败:envelope 错误,reason/hint 已知。 */
    data class Failed(
        val title: String,
        val reason: String,
        val hint: String?,
    ) : ToolResultSummary

    /** 解析失败或工具返回了非 JSON 字符串:只能原文展示。 */
    data class Unknown(
        val raw: String,
    ) : ToolResultSummary

    /** 空结果 —— count=0 / 空列表 / 空对象。 */
    data class Empty(
        val title: String,
    ) : ToolResultSummary
}
