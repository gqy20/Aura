# Aura LifeScore（人生积分）设计方案

> Archived on 2026-06-15. Kept for historical design context; no longer a current planning entry.
>
> Last updated: 2026-06-15
>
> Scope: 为 Aura 引入"用户自身进步可量化、可视化"的轻量积分系统。让"我也在一点点变好"从感觉变成可触摸的数字、趋势与里程碑。
>
> 关联文档：[`insight-driven-product.md`](../../plan/insight-driven-product.md) · [`dual-mind-architecture.md`](../../plan/dual-mind-architecture.md) · [`roadmap.md`](../../roadmap.md) · [`architecture.md`](../../architecture.md)

---

## 0. 一句话定位

> **LifeScore 是 Aura 给用户的一面"私人进步镜子"。**

Insight 帮用户 **看见自己**（文字归纳），Presence 让用户 **感受 AI**（AI 状态可视化），而 LifeScore 让用户 **量化自己** —— 把"我最近一直在坚持"变成"本周 +23 分"，把"我好像在变好"变成连续 7 天的曲线。

LifeScore 不和 Insight 抢位置，也不是给用户"打卡游戏"。它安静地记录用户每一次有意义的动作，把它折算成可解释的分数；当用户想看的时候，Home 卡片上有一行小字 —— "你今天在坚持"。

---

## 1. 设计原则

### 1.1 必须零干扰

> **LifeScore 不打断主流程，不发通知，不弹横幅。**

90% 的积分来源都复用现有数据流（reminder 完成、memory summary、mood snapshot、chat 启动），无需新增用户操作或系统通知。用户**不需要**为了得分而行动 —— 得分只是他们已经在做的有意义行为的"回声"。

新增埋点必须满足三个条件之一：
- **复用**：从现有 DAO 写入路径钩入，不增加任何 IO
- **极轻**：单一动作、单一按钮（如 Phase C 的"今日打卡"）
- **可关闭**：所有"AI 推断型"积分必须能在设置里关闭

### 1.2 必须可解释

> **每一次加分，用户都能展开看到来源。**

不出现"+5（魔法）"。LifeCard 点击展开后能看到：
- 今天 +5（完成提醒：喝水）
- 今天 +3（生成记忆摘要：项目复盘）
- 本周 +18，对比上周 +14 ↑

**信任 > 数值**。用户对分数失去信任的那一刻，整个系统就死了。

### 1.3 必须渐进

> **数字、趋势、里程碑，三层递进。**

不是一上来就堆满折线图和勋章墙。Phase A 只在 Home 放一个小卡片：总分 + 本周增量 + 趋势箭头。Phase B 才加独立 Life 页 + 折线图。Phase C 才加勋章。

节奏对应用户心理：**先相信**（数字真实）→ **再期待**（看到曲线）→ **最后留恋**（解锁里程碑舍不得卸载）。

### 1.4 必须纯本机

> **LifeScore 不依赖云端 LLM，不上传任何"用户行为画像"。**

参考 Insight 框架的护城河逻辑：行为积分是"我对用户有多了解"的另一种表达，必须 100% 本机可重算。这样用户清除数据时分数清零，迁移设备时分数同迁，**永远没有"被云端打分"的恐惧**。

### 1.5 必须可关闭

> **整套系统在设置里有总开关。**

关闭后：不写入 life_event、不读取 life_event、Home 卡片隐藏。重新打开后，历史数据依然存在（不删除）。这是"克制"的最后一道防线。

---

## 2. 架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│                        feature/life/  (UI 层)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐   │
│  │ LifeHomeCard │  │  LifeScreen  │  │ LifeCardViewModel     │   │
│  │ (Phase A)    │  │  (Phase B)   │  │ (订阅 lifeEvent Flow) │   │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬────────────┘   │
└─────────┼──────────────────┼──────────────────────┼──────────────┘
          │                  │                      │
┌─────────▼──────────────────▼──────────────────────▼──────────────┐
│                       core/life/  (计算层)                       │
│  ┌──────────────────────┐  ┌───────────────────────────────┐    │
│  │ LifeScoreCalculator  │  │ MilestoneDetector             │    │
│  │ (纯函数规则引擎)     │  │ (FIRST_HUNDRED/STREAK_7/...)  │    │
│  └──────────┬───────────┘  └──────────────┬────────────────┘    │
└─────────────┼────────────────────────────┼────────────────────────┘
              │                            │
┌─────────────▼────────────────────────────▼────────────────────────┐
│                       data/life/  (数据层)                        │
│  ┌──────────────────┐  ┌───────────────────────┐  ┌───────────┐  │
│  │ LifeEventEntity  │  │ LifeScoreSnapshotEnt. │  │  DAO x2   │  │
│  │ (事件流)         │  │ (按日聚合)            │  │           │  │
│  └──────────────────┘  └───────────────────────┘  └───────────┘  │
└──────────────────────────────────────────────────────────────────┘
              ▲                            ▲
              │ 埋点                       │ 埋点
              │                            │
┌─────────────┴────────────────────────────┴────────────────────────┐
│  现有触发源（埋点接入点）                                          │
│  · ReminderNotificationWorker · MemorySummaryDao.insert           │
│  · MoodSnapshotDao.insert     · ChatViewModel.init                │
│  · InsightLongPressDialog.ack                                      │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. 数据模型

### 3.1 `LifeEventEntity` — 事件流

> 每次积分写入一条新记录，**不可变**。这是审计日志，也是"重算"的唯一来源。

```kotlin
@Entity(
    tableName = "life_event",
    indices = [
        Index("timestamp"),
        Index("eventType"),
    ],
)
data class LifeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String,          // LifeEventType.name
    val points: Int,                // 该次事件产生的积分
    val category: String,           // BEHAVIOR / EMOTION / GROWTH
    val timestamp: Long,            // epoch millis
    val sourceId: String? = null,   // 关联 reminderId/summaryId/messageId
    val description: String? = null,// 人类可读描述（如"完成提醒：喝水"）
)
```

### 3.2 `LifeEventType` — 枚举（单一事实来源）

```kotlin
enum class LifeEventType(val defaultPoints: Int, val category: LifeCategory) {
    REMINDER_COMPLETED(5,  LifeCategory.BEHAVIOR),
    MEMORY_SUMMARY_CREATED(3, LifeCategory.GROWTH),
    CHAT_STARTED(1,         LifeCategory.BEHAVIOR),
    MOOD_LOGGED(2,          LifeCategory.EMOTION),
    INSIGHT_ACKNOWLEDGED(1, LifeCategory.GROWTH),
    USER_CHECKIN(5,         LifeCategory.BEHAVIOR),  // Phase C 启用
    AI_KEYWORD_HIT(3,       LifeCategory.GROWTH),    // 受控，可关闭
    ;

    companion object {
        fun fromName(name: String): LifeEventType? =
            entries.firstOrNull { it.name == name }
    }
}

enum class LifeCategory { BEHAVIOR, EMOTION, GROWTH }
```

> 枚举是规则的**单一事实来源**。`LifeScoreCalculator` 不硬编码数值，而是从枚举读取，便于调参。

### 3.3 `LifeScoreSnapshotEntity` — 按日聚合

> 视图层读这个，不是直接读 `life_event`。避免每次 Home 加载都 sum 一周的数据。

```kotlin
@Entity(
    tableName = "life_score_snapshot",
    primaryKeys = ["dateKey"],
)
data class LifeScoreSnapshotEntity(
    @ColumnInfo(name = "dateKey") val dateKey: String,  // "2026-06-15"
    val totalPoints: Int,
    val eventCount: Int,
    val behaviorPoints: Int,
    val emotionPoints: Int,
    val growthPoints: Int,
    val updatedAt: Long,
)
```

聚合时机：
- **写时同步**：每次 `LifeEventDao.insert` 后，触发 Repository 层更新当日 snapshot（事务内）
- **历史重建**：首次启用时或 DB 升级后，从 `life_event` 流式重建所有 snapshot

### 3.4 DAO

```kotlin
@Dao
interface LifeEventDao {
    @Insert
    suspend fun insert(event: LifeEventEntity): Long

    @Query("SELECT * FROM life_event WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    suspend fun findInRange(start: Long, end: Long): List<LifeEventEntity>

    @Query("SELECT * FROM life_event ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<LifeEventEntity>>

    @Query("SELECT COUNT(*) FROM life_event")
    suspend fun countAll(): Int
}

@Dao
interface LifeScoreSnapshotDao {
    @Query("SELECT * FROM life_score_snapshot WHERE dateKey = :dateKey")
    suspend fun getByDate(dateKey: String): LifeScoreSnapshotEntity?

    @Query("SELECT * FROM life_score_snapshot WHERE dateKey BETWEEN :start AND :end ORDER BY dateKey DESC")
    fun observeRange(start: String, end: String): Flow<List<LifeScoreSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: LifeScoreSnapshotEntity)

    @Query("DELETE FROM life_score_snapshot")
    suspend fun clearAll()
}
```

---

## 4. 触发规则与埋点接入

### 4.1 规则表

| EventType | Points | Category | 触发点 | 状态 |
|---|---|---|---|---|
| `REMINDER_COMPLETED` | +5 | BEHAVIOR | `ReminderNotificationWorker.doWork()` 成功后 | Phase A |
| `MEMORY_SUMMARY_CREATED` | +3 | GROWTH | `MemorySummaryDao.insert()` 调用方 | Phase A |
| `CHAT_STARTED` | +1 | BEHAVIOR | `ChatViewModel.init`（新会话，非每条消息） | Phase A |
| `MOOD_LOGGED` | +2 | EMOTION | `MoodSnapshotDao.insert()` 调用方 | Phase A |
| `INSIGHT_ACKNOWLEDGED` | +1 | GROWTH | `InsightLongPressDialog` 关闭且非"删除"分支 | Phase A |
| `USER_CHECKIN` | +5 | BEHAVIOR | Home 卡片按钮 | Phase C |
| `AI_KEYWORD_HIT` | +3 | GROWTH | chat 消息流关键词命中（受控） | **Phase A 不做**，可关闭 |

### 4.2 接入策略：埋点 vs 触发器

**两种接入方式，对应不同语义：**

1. **同步埋点（In-Process Hook）**
   - 在调用方代码（Repository / Worker）中显式调用 `LifeEventRecorder.record(type, sourceId)`
   - 适用：Reminder Worker、Mood/Chat/Insight 写入路径
   - 优点：精确控制 sourceId、description；可读性高
   - 缺点：每个调用点都要改

2. **DAO 包装（Wrapper Observer）**
   - 给关键 DAO 加一层 Repository 包装（如 `LifeAwareMoodRepository`），在 DAO.insert 之后自动同步写入 life_event
   - 适用：高频、写入点分散的（如 Mood）
   - 优点：单点改动
   - 缺点：多一层抽象

**Phase A 决策**：默认采用**同步埋点**，原因：
- 现有项目代码规模可控
- 显式埋点可读性 > 自动织入的"魔法"
- 单测容易（不用 mock 拦截器）

### 4.3 各埋点的具体接入点（基于当前代码）

| 埋点 | 当前代码位置 | 改动 |
|---|---|---|
| REMINDER_COMPLETED | `ReminderNotificationWorker.kt:28` 之后 | 注入 `LifeEventRecorder`，在 `reminderDao.markFired` 成功后 record |
| MEMORY_SUMMARY_CREATED | 各处调用 `memorySummaryDao.insert(...)` | 在 `MemoryRepository.upsertSummary()` 集中点 record（如果存在）或新增 |
| CHAT_STARTED | `ChatViewModel` 构造函数或首个消息发送 | 注入 `LifeEventRecorder`，仅在新 sessionId 时 record |
| MOOD_LOGGED | 调用 `moodSnapshotDao.insert` 的位置 | 同上，在 Repository 集中点 record |
| INSIGHT_ACKNOWLEDGED | `InsightLongPressDialog` 关闭回调 | dialog 调用 ViewModel.recordAck() |

> **真实行号在实施 Task #1 时确认。**

---

## 5. 计算模块

### 5.1 `LifeScoreCalculator` — 纯函数规则引擎

```kotlin
// 输入：List<LifeEventEntity> + 时间窗口
// 输出：LifeScoreSummary（总分、分类、趋势、里程碑）
class LifeScoreCalculator {

    fun compute(
        events: List<LifeEventEntity>,
        windowStart: Long,
        windowEnd: Long,
        previousWeekEvents: List<LifeEventEntity>,
        allEvents: List<LifeEventEntity>,  // 用于 milestone 检测
    ): LifeScoreSummary
}

data class LifeScoreSummary(
    val totalPoints: Int,
    val behaviorPoints: Int,
    val emotionPoints: Int,
    val growthPoints: Int,
    val weeklyDelta: Int,          // 本周总分 - 上周总分（可负）
    val weeklyDeltaPercent: Float,  // 百分比变化（无限大时用 Int.MAX_VALUE 标记）
    val trend: Trend,               // UP / DOWN / FLAT
    val eventCount: Int,
)

enum class Trend { UP, DOWN, FLAT }
```

**纯函数 + 不读取时钟**：所有时间窗口由调用方传入，便于测试。

### 5.2 `MilestoneDetector` — 轻量里程碑

```kotlin
class MilestoneDetector {

    /**
     * 检测里程碑。返回该时间窗口内"新达成"的里程碑列表。
     * 历史已解锁过的不能重复触发（除非降级再升级）。
     */
    fun detect(
        allEvents: List<LifeEventEntity>,
        unlockedMilestones: Set<String>,
    ): List<Milestone>
}

data class Milestone(
    val type: MilestoneType,
    val unlockedAt: Long,
    val title: String,
    val message: String,
)

enum class MilestoneType(val key: String) {
    FIRST_HUNDRED("first_hundred"),      // 累计 ≥ 100
    STREAK_7("streak_7"),                // 连续 7 天每天至少 1 个事件
    MONTHLY_HIGH("monthly_high"),        // 本月总分 > 历史所有月份
    ;
}
```

**已解锁状态**存储在 DataStore（key: `life_score.unlocked_milestones`，JSON Set），不是 DB 字段。原因：
- 不污染事件表
- 用户"重置 LifeScore"时可一键清空
- DataStore 读写足够高频

### 5.3 `LifeScoreRepository` — 数据访问门面

```kotlin
@Singleton
class LifeScoreRepository @Inject constructor(
    private val eventDao: LifeEventDao,
    private val snapshotDao: LifeScoreSnapshotDao,
    private val calculator: LifeScoreCalculator,
    private val milestoneDetector: MilestoneDetector,
    private val dataStore: DataStore<Preferences>,
) {
    /** ViewModel 订阅此 Flow。 */
    fun observeWeeklySummary(): Flow<LifeScoreSummary>

    /** ViewModel 订阅此 Flow。 */
    fun observeRecentMilestones(): Flow<List<Milestone>>

    /** 写入一条事件（由埋点调用）。同步更新 snapshot。 */
    suspend fun recordEvent(type: LifeEventType, sourceId: String? = null, description: String? = null)
}
```

---

## 6. 展现层（Phase A）

### 6.1 `LifeHomeCard` — Home 小卡片

```
┌────────────────────────────────────────┐
│  人生积分                    ⚙         │
│                                        │
│   247            ↑ +18 (本周)         │
│  ─────                                │
│   你今天在坚持 · 完成提醒 喝水         │
└────────────────────────────────────────┘
```

设计要点：
- **大数字**：累计总分（次大字号，不是 H1）
- **趋势**：本周 vs 上周；箭头 + 数值 + 颜色（绿/红/灰）
- **副文案**：最近一条事件的 description（"完成提醒：喝水"）
- **零图标迷思**：不画奖杯、不画火焰，克制
- **可点击**：Phase A 暂不跳转（Phase B 加 Life 页），点击可展开"本周事件列表"

### 6.2 `LifeHomeCardViewModel`

```kotlin
@HiltViewModel
class LifeHomeCardViewModel @Inject constructor(
    private val repository: LifeScoreRepository,
) : ViewModel() {
    val uiState: StateFlow<LifeHomeCardUiState>

    fun dismissMilestone(milestone: Milestone)
}

data class LifeHomeCardUiState(
    val isEnabled: Boolean,
    val summary: LifeScoreSummary?,
    val recentEvent: LifeEventEntity?,
    val pendingMilestone: Milestone?,
)
```

### 6.3 接入 `AuraHomeScreen`

插入位置：**现有 InsightCard 之下、Memory Room 入口之上**（如果有的话）。具体位置在 Task #6 时确认。

---

## 7. 数据库迁移（v7 → v8）

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS life_event (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                eventType TEXT NOT NULL,
                points INTEGER NOT NULL,
                category TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                sourceId TEXT,
                description TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_life_event_timestamp ON life_event(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_life_event_eventType ON life_event(eventType)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS life_score_snapshot (
                dateKey TEXT NOT NULL,
                totalPoints INTEGER NOT NULL,
                eventCount INTEGER NOT NULL,
                behaviorPoints INTEGER NOT NULL,
                emotionPoints INTEGER NOT NULL,
                growthPoints INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(dateKey)
            )
        """.trimIndent())
    }
}
```

并在 `DataModule.provideDatabase` 加入 `MIGRATION_7_8`。

> **零破坏性**：新增表，不动任何旧字段。

---

## 8. 测试策略

| 测试 | 文件 | 关注点 |
|---|---|---|
| `LifeScoreCalculatorTest` | `core/life/LifeScoreCalculatorTest.kt` | 纯函数，4-6 个用例覆盖：空列表、单事件、多分类、跨周对比、无限大百分比 |
| `MilestoneDetectorTest` | `core/life/MilestoneDetectorTest.kt` | 边界：恰好 100 / 99 / 101；streak 跨天；monthly high 第一次/第二次 |
| `LifeEventDaoTest` | `data/db/dao/LifeEventDaoTest.kt`（继承 `BaseDaoTest`）| insert、findInRange、observeAll |
| `LifeScoreSnapshotDaoTest` | `data/db/dao/LifeScoreSnapshotDaoTest.kt` | upsert、observeRange |
| `LifeScoreRepositoryTest` | `data/repository/LifeScoreRepositoryTest.kt` | recordEvent 后 snapshot 同步、Flow 触发 |
| `LifeHomeCardViewModelTest` | `feature/life/LifeHomeCardViewModelTest.kt` | StateFlow 行为（参考 CLAUDE.md 模板） |

**最终验证**：`./gradlew testDebugUnitTest`，必须全绿（参考 `test-run.log` 与 XML 报告，**一次运行、一次分析**，避免重复 build）。

---

## 9. 实施路径

### Phase A（本次）—— "看见数字"

1. 数据层：Entity + DAO + v8 Migration
2. 计算层：Calculator + MilestoneDetector + Repository
3. 埋点接入：5 个现有触发点
4. 展现：`LifeHomeCard` + ViewModel + 接入 Home
5. 测试：Calculator / Detector / DAO / Repository / ViewModel
6. **里程碑**：全套单测通过 + Home 卡片可见

### Phase B（下一步）—— "看见曲线"

1. 独立 `LifeScreen`（NavHost 新增路由）
2. 7 日折线图（Compose Canvas 自绘，参考 Android Compose Charts 思路）
3. 事件时间轴（LazyColumn）
4. 分类饼图 / 堆叠柱（Compose Canvas）

### Phase C（未来）—— "主动打卡"

1. Home 卡片按钮 → USER_CHECKIN +5
2. 里程碑解锁弹窗（一行小字 + 简单 vector）
3. 设置 → LifeScore 总开关 + AI_KEYWORD_HIT 子开关
4. 周报自动生成（结合本地 LLM 在 Insight 框架内复用）

---

## 10. 与现有模块的关系

| 模块 | 关系 |
|---|---|
| **Presence Layer** | 完全独立。Presence 是 AI 的"状态可视化"，LifeScore 是用户的"进步可视化"，**绝不混淆** |
| **Insight 框架** | 互补。Insight 是文字归纳，LifeScore 是数字归纳。**未来可在 Insight 详情页加一行"+X 分（来自此 insight）"** |
| **MoodSnapshot** | 作为积分来源之一。情绪稳定记录 = +2 |
| **Reminder** | 作为积分来源之一。完成提醒 = +5。**形成正反馈**：完成 reminder → 涨分 → 想继续完成 |
| **MemorySummary** | 作为积分来源之一。生成 summary = +3。鼓励用户主动沉淀 |
| **Chat** | `CHAT_STARTED` = +1。**注意**：不是每条消息，避免 chat 灌水刷分 |
| **本地 LLM** | Phase A 不依赖。Phase C 可用于周报生成 |

---

## 11. 风险与权衡

| 风险 | 应对 |
|---|---|
| **gamification 反感**：用户觉得"被算计" | 所有规则透明、可解释；总开关在设置；不要做"差一名就解锁"的紧迫感设计 |
| **埋点侵入**：5 个现有文件改动可能引入 bug | 每个埋点一个独立 PR 可回滚；先做 Calculator 单测再接埋点 |
| **DB Migration 失败**：v7→v8 在生产环境崩 | 写 Migration 前本地 5 次真机验证；测试时破坏旧 DB 文件再用 Migration 重建 |
| **Reminders worker 注入新依赖**：Hilt WorkerFactory 是否支持 | 当前 `ReminderNotificationWorker` 已用 `@HiltWorker`，加一个 `LifeEventRecorder` 依赖是 Hilt 标准能力，零风险 |
| **冷启动空数据**：首次启用时 LifeCard 显示什么 | 优雅降级：显示"0 分" + "今天开始记录你的进步"，避免突兀 |
| **Performance**：高频 chat 中每条消息不能写 life_event | `CHAT_STARTED` 仅在**新会话**触发；DAO insert 是单行写，开销 < 1ms |

---

## 12. 关键文件清单（实施时落点）

**新增：**
```
app/src/main/java/com/xiaoqi/companion/data/db/entity/LifeEventEntity.kt
app/src/main/java/com/xiaoqi/companion/data/db/entity/LifeScoreSnapshotEntity.kt
app/src/main/java/com/xiaoqi/companion/data/db/dao/LifeEventDao.kt
app/src/main/java/com/xiaoqi/companion/data/db/dao/LifeScoreSnapshotDao.kt
app/src/main/java/com/xiaoqi/companion/data/life/LifeEventType.kt
app/src/main/java/com/xiaoqi/companion/data/life/LifeCategory.kt
app/src/main/java/com/xiaoqi/companion/data/repository/LifeScoreRepository.kt
app/src/main/java/com/xiaoqi/companion/core/life/LifeScoreCalculator.kt
app/src/main/java/com/xiaoqi/companion/core/life/MilestoneDetector.kt
app/src/main/java/com/xiaoqi/companion/core/life/LifeScoreSummary.kt
app/src/main/java/com/xiaoqi/companion/core/life/Milestone.kt
app/src/main/java/com/xiaoqi/companion/core/life/LifeEventRecorder.kt   # 埋点门面
app/src/main/java/com/xiaoqi/companion/feature/life/LifeHomeCard.kt
app/src/main/java/com/xiaoqi/companion/feature/life/LifeHomeCardViewModel.kt
app/src/main/java/com/xiaoqi/companion/feature/life/LifeHomeCardUiState.kt

app/src/test/java/com/xiaoqi/companion/core/life/LifeScoreCalculatorTest.kt
app/src/test/java/com/xiaoqi/companion/core/life/MilestoneDetectorTest.kt
app/src/test/java/com/xiaoqi/companion/data/db/dao/LifeEventDaoTest.kt
app/src/test/java/com/xiaoqi/companion/data/db/dao/LifeScoreSnapshotDaoTest.kt
app/src/test/java/com/xiaoqi/companion/data/repository/LifeScoreRepositoryTest.kt
app/src/test/java/com/xiaoqi/companion/feature/life/LifeHomeCardViewModelTest.kt
```

**修改：**
```
app/src/main/java/com/xiaoqi/companion/data/db/CompanionDatabase.kt          # entities[], v8, MIGRATION_7_8
app/src/main/java/com/xiaoqi/companion/di/DataModule.kt                       # provideDatabase().addMigrations, provideLifeEventDao, provideLifeScoreSnapshotDao
app/src/main/java/com/xiaoqi/companion/core/reminder/ReminderNotificationWorker.kt  # 注入 LifeEventRecorder
app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt    # summary 写入后埋点（如集中点存在）
app/src/main/java/com/xiaoqi/companion/feature/chat/ChatViewModel.kt         # init 埋点
app/src/main/java/com/xiaoqi/companion/data/repository/MoodRepository.kt      # mood 写入埋点（如存在)
app/src/main/java/com/xiaoqi/companion/feature/insight/InsightLongPressDialog.kt  # ack 埋点
app/src/main/java/com/xiaoqi/companion/feature/chat/AuraHomeScreen.kt        # 插入 LifeHomeCard
```

> 真实修改范围在 Task #1 调研后微调。

---

## 13. 决策记录

| 时间 | 决策 | 取舍 |
|---|---|---|
| 2026-06-15 | 用枚举 `LifeEventType` 作为规则单一来源，而非写死在 Calculator | 便于后续调参与 A/B 测试 |
| 2026-06-15 | 埋点用同步显式调用，不用 DAO 织入 | 当前项目规模下，可读性 > 抽象性 |
| 2026-06-15 | snapshot 表用同步写，不用异步重建 | 写入频率 < 10/天，性能不是瓶颈 |
| 2026-06-15 | 里程碑状态存 DataStore 而非 DB 字段 | 重置体验 + 不污染事件流 |
| 2026-06-15 | `AI_KEYWORD_HIT` 不在 Phase A 做 | 避免"AI 算我"的不信任感，先让用户接受被动积分 |
| 2026-06-15 | `CHAT_STARTED` 只在新会话触发，不按消息数 | 防止 chat 灌水刷分；保护积分的"意义感" |
| 2026-06-15 | Home 卡片可点击但 Phase A 暂不跳页 | 避免空路由；Phase B 再补独立 Life 页 |

---

## 14. 代码量与时间预估

> 制定于 2026-06-15 文档评审后。

### 14.1 总览

| 维度 | 数值 |
|---|---|
| **生产代码** | ~880 行 |
| **测试代码** | ~530 行（占总量 34%） |
| **修改现有代码** | ~130 行 |
| **新增文件** | 17 个 |
| **修改文件** | 8 个 |
| **Phase A 总行数（含测试）** | **~1540 行** |

测试数预估：当前基线 41 个 → 完成后 **约 50-55 个**（6 个测试文件 × 4-6 个用例）。

### 14.2 按模块拆分（生产代码）

| 模块 | 文件数 | 行数 | 占比 |
|---|---|---|---|
| 数据层（Entity + DAO + 枚举） | 5 | ~170 | 19% |
| 计算层（Calculator + Detector + 2 data class + Recorder） | 5 | ~290 | 33% |
| Repository | 1 | ~120 | 14% |
| UI 层（Card + VM + State） | 3 | ~300 | 34% |
| **合计** | **14** | **~880** | **100%** |

### 14.3 按文件清单（新增 + 行数）

```
data/db/entity/
  LifeEventEntity.kt                       30 行
  LifeScoreSnapshotEntity.kt               30 行
data/db/dao/
  LifeEventDao.kt                          30 行
  LifeScoreSnapshotDao.kt                  30 行
data/life/
  LifeEventType.kt                         50 行   ← 枚举 + Category
data/repository/
  LifeScoreRepository.kt                  120 行
core/life/
  LifeScoreCalculator.kt                   80 行
  MilestoneDetector.kt                    100 行
  LifeScoreSummary.kt                      40 行   ← data class
  Milestone.kt                             30 行
  LifeEventRecorder.kt                     40 行
feature/life/
  LifeHomeCard.kt                         180 行
  LifeHomeCardViewModel.kt                 80 行
  LifeHomeCardUiState.kt                   40 行
———————————————————————————————————————————
生产代码小计                              880 行

test/...
  LifeScoreCalculatorTest.kt              100 行
  MilestoneDetectorTest.kt                100 行
  LifeEventDaoTest.kt                      70 行
  LifeScoreSnapshotDaoTest.kt              60 行
  LifeScoreRepositoryTest.kt              100 行
  LifeHomeCardViewModelTest.kt            100 行
———————————————————————————————————————————
测试小计                                  530 行

新增合计                                1410 行
```

### 14.4 修改现有文件（8 个，~130 行）

| 文件 | 改动行数 | 改动内容 |
|---|---|---|
| `data/db/CompanionDatabase.kt` | +20 | entities[] 加入 2 个新 Entity + MIGRATION_7_8 |
| `di/DataModule.kt` | +25 | addMigrations(MIGRATION_7_8) + 2 个 provideDao |
| `core/reminder/ReminderNotificationWorker.kt` | +10 | 注入 LifeEventRecorder + record 调用 |
| `data/repository/MemoryRepository.kt` | +10 | summary 写入后 record（需确认集中点） |
| `feature/chat/ChatViewModel.kt` | +15 | init 时 record（带新会话去重判断） |
| `data/repository/MoodRepository.kt` | +10 | mood 写入后 record（需确认是否存在集中点） |
| `feature/insight/InsightLongPressDialog.kt` | +15 | ack 分支 record（区分"删除"路径） |
| `feature/chat/AuraHomeScreen.kt` | +25 | 插入 LifeHomeCard（位置在 InsightCard 下） |
| **修改合计** | **+130 行** | — |

### 14.5 时间预估（参考类似规模 Kotlin 项目）

| 阶段 | 工作量 | 关键活动 |
|---|---|---|
| 数据层 + Migration | 0.5 天 | 模板化代码 + BaseDaoTest 复用 |
| 计算层 + 单测 | 1 天 | 纯函数 + 边界条件，最考验细节 |
| 5 个埋点接入 | 0.5 天 | 关键是精准找到 Mood/Memory 写入的集中点 |
| UI + Home 集成 | 0.5-1 天 | Compose 卡片实现 + ViewModel 订阅 Flow |
| 联调 + `testDebugUnitTest` 全绿 | 0.5 天 | 一次 build 验证，避免重复构建（参考 CLAUDE.md） |
| **Phase A 总时间** | **3-4 天** | — |

### 14.6 风险点（可能拉长周期）

- ⚠️ **Mood/MemoryRepository 集中写入点未知** — Task #1 调研时确认；若无集中点，需要从 DAO 层织入（增加 0.5 天）
- ⚠️ **`ChatViewModel` "新会话"判断** — 需要设计 sessionId 去重逻辑，可能需要小幅重构（+0.5 天）
- ⚠️ **DB v8 Migration 真机验证** — 每次升级都要在本机做"破坏-重建"验证，预留 0.5 天

### 14.7 PR 拆分建议（如果想分批 review）

```
PR1（数据 + 计算，含单测）       ~700 行，1 天
PR2（5 个埋点接入）              ~130 行，0.5 天
PR3（UI + Home 集成）            ~300 行，0.5-1 天
PR4（联调 + 测试全绿）            已在 PR1-3 覆盖，0.5 天
```

PR1 可独立 review（纯新模块、不动现有代码）；PR2 是侵入式改动，建议独立 PR 便于回滚；PR3 是 UI 改动，影响面小。

### 14.8 与 CLAUDE.md 基线对齐

- **测试规范**：遵循 `ViewModel + StateFlow 测试模式`（`Dispatchers.setMain(UnconfinedTestDispatcher())` + `viewModel.uiState.value` 断言）
- **DAO 测试基类**：继承 `BaseDaoTest`，不跨 test class 共享 DB
- **测试运行**：一次 `./gradlew testDebugUnitTest` + tee 日志分析，避免重复 build
- **不使用 runBlocking**：所有 ViewModel 测试遵循 Koog 线程规则
