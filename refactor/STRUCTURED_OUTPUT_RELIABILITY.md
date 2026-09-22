# GenChat-X 结构化输出校验与迁移

> **状态：已实现的迁移验证记录。** Foundation API、schema/repair 规则和 provider 限制以 [`agent-foundation/docs/structured-output-reliability.md`](../../agent-foundation/docs/structured-output-reliability.md) 为准；本文只记录 GenChat-X 业务接入和数据契约。

## 已接入的业务

PPT 意图、模板选择、PPT Schema 生成/修改、DeepResearch 规划/评审、推荐问题统一使用 Foundation `StructuredOutputSpec` 与无工具的 `StructuredOutputSupport.generate`。

这些步骤已有完整业务上下文，不需要执行工具。默认采用 schema 提示、完整 JSON Schema 校验和最多 2 次带错误反馈的修复；不全局开启 provider-native 输出。真正的研究任务执行和聊天仍使用原有 ReAct 工具流程。

- 意图识别失败明确终止，不默认创建新的 PPT。
- 模板编码必须属于本次候选集合。
- PPT 校验成功前不写入新 schema、不生图、不渲染。修改校验失败保留原实例的成功状态和 schema，不冒充普通生成流程恢复。
- Schema/模板选择失败直接结束 SSE，不再启动可执行工具的失败解释 Agent。
- 研究计划输出为 `{"tasks":[...]}`；任务 ID 唯一、指令非空、order 分组从 1 连续。支持空 tasks 或单个 id=null/order=0 的停止哨兵，禁止混合停止与执行任务。
- 评审要求真正的布尔 passed；未通过必须有反馈。评审通过停止迭代，但仍生成报告。
- 推荐生成输出为 `{"questions":[...]}`，严格要求三个不同的非空字符串；保存及 SSE 对外仍使用原有数组，历史 payload 解析规则不变。

## PPT 模板数据契约（部署前检查）

仓库没有数据库中实际模板内容的 fixture，不能推断任意历史格式。为防止错误产物，新校验器在调用模型前拒绝空、非法或无法识别的模板定义，不再以模型自行声明的字段/字号作为模板依据。

受支持的模板定义是合法 JSON，其中每页具有：

- 唯一、正整数 `templatePageIndex`，与实际 PPT 的 1-based 页码一致。
- `data` 对象：键为真实模板 shape 名称，值为字段定义。
- 字段 `type` 为 `text`、`image` 或 `background`。
- 文本字段必须有非负整数 `fontLimit`。`pageType` 如有声明，生成结果必须一致。

页定义可放在 `slides` 数组或其他 JSON 容器中；校验器按显式页码识别，不假定数据库中的数组位置就是实际模板页码。示意：

```json
{
  "slides": [
    {
      "templatePageIndex": 1,
      "pageType": "COVER",
      "data": {
        "title": {"type": "text", "fontLimit": 20},
        "photo": {"type": "image"}
      }
    }
  ]
}
```

这是新契约示例，不是生产模板导出。部署时应核对 `ai_ppt_template.template_schema` 与 `file_path` 对应的真实 PPT；旧格式应显式转换或增加经真实样本验证的解析器，不能再次退化成提示词约束。`slide_count` 如存在，必须与页定义相容。

输出可重复引用同一模板页。字段名称/类型与模板一致，文本字号上限不能被模型放大，字符数按 Unicode code point 计算。图片/背景的 URL 可以缺省、null 或空串：有生成描述则生图，无描述则保留模板原图。修改必须保持页数、顺序、模板页码、字段和字号限制；未改图片描述时不静默清空已有 URL。

## 验证

- Foundation 测试覆盖混合 reasoning/text chunk、跨 chunk 标签、字面标签保真、订阅隔离、工具 schema、参数片段、严格 schema 校验、有界修复、不重复工具、轮次耗尽和流式输出后禁止重播。
- GenChat-X 测试覆盖模板候选、PPT 字段/页码/长度/可选 URL、意图失败、修改状态保护、任务/评审契约、推荐元素类型及数量、通过后停止研究。
- 本地 HTTP 假服务使用真实 Spring AI OpenAI 模型适配器，检查原始工具 schema、默认工具隔离、provider options 保留及原生 response_format。
- 测试不访问外部模型，也不代表实际模型支持所有原生 JSON Schema 特性。

