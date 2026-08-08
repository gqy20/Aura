# Aura 组件一致性审计

## 范围

审计聊天页工具入口、对话与提醒弹层、MCP 列表、记忆列表与记忆详情，重点检查按钮、弹层标题、关闭操作、危险操作及点击尺寸。

## 步骤与结论

### 1. 对话弹层 — 改善中

![](visual-audit-assets/audit-components-01-conversation.png)

- `＋` 与 `×` 已统一为轻量图标操作。
- 截图仍是已安装旧包，源码已把 `0 个对话` 改为标题同行的数字 `0`。

### 2. 提醒弹层 — 需要同步标题规则

![](visual-audit-assets/audit-components-02-reminders.png)

- 与对话弹层共用 `AuraDialogPanel` / `AuraDialogHeader`，关闭按钮已经一致。
- `0 个待生效` 仍单独占一行，建议改成标题同行的 `提醒 0`。

### 3. MCP 列表 — 基本健康

![](visual-audit-assets/audit-components-03-mcp.png)

- 使用 Material 3 `TopAppBar`、`FilledTonalButton`、`OutlinedButton`，组件来源一致。
- `添加 MCP` 和 `测试连接` 宽度差异较大；层级能够理解，但底部操作栏视觉不平衡。

### 4. 记忆列表 — 点击尺寸与层级风险

![](visual-audit-assets/audit-components-04-memory-room.png)

- 统计胶囊、筛选按钮和卡片分别使用不同圆角体系，但整体尚可辨认。
- 删除图标实际布局为 32dp，低于推荐的 48dp 点击区域；图标颜色也偏淡。

### 5. 记忆详情 — 不一致最明显

![](visual-audit-assets/audit-components-05-memory-detail.png)

- 同时提供右上角 `×` 和底部“关闭”，属于重复操作。
- “删除”仍使用普通主色文本，没有表达危险操作。
- 该页面使用 Material `AlertDialog` 的淡紫表面，而对话/提醒使用自定义白色 `AuraDialogPanel`，弹层视觉分裂。

## 优先级

1. 统一弹层原语：对话、提醒、记忆详情、Insight 详情共用标题、关闭和表面规则。
2. 删除重复关闭操作；危险操作统一使用 error 色并保留确认步骤。
3. 所有图标按钮使用至少 48dp 点击区域，图标本身保持 20–24dp。
4. 提醒数量改为标题同行数字；MCP 底部按钮统一高度和布局节奏。
5. 首页快捷按钮仍使用 `Surface + IconButton` 的带底圆形写法，可在下一轮统一成一套 `AuraIconAction`。

## 证据限制

- 截图可确认视觉层级、目标大小风险和操作重复，不能据此声明完整无障碍合规。
- 最新 `对话 0` 源码尚未生成新 APK，因为工作区另一组 LLM 客户端改动存在 Kotlin 可见性编译错误。
