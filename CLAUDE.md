# Android 项目 - 开发环境

## 工具路径

### ADB_Cli — Android 调试工具集
- **路径**: `D:\tools\ADB_Cli`
- **版本**: ADB 1.0.41
- **用途**: 与 Android 设备/模拟器交互（调试、刷机、安装应用）

### Android Studio — IDE
- **路径**: `D:\tools\Android_Studio`
- **入口**: `D:\tools\Android_Studio\bin\studio64.exe`

### Android_Studio_Cli — 命令行工具
- **路径**: `D:\tools\Android_Studio_Cli`
- **核心内容**: `cmdline-tools/bin` — sdkmanager、avdmanager 等

## 运行时环境

| 项目 | 版本/路径 |
|------|-----------|
| JDK | **21.0.6** (Oracle LTS) |
| Kotlin | 由 Gradle plugin 管理（目标 2.0+） |
| AGP | 8.6.1 |
| SDK Root | `C:\Users\gqy17\AppData\Local\Android\Sdk` |
| SDK Platform | android-36.1 |
| Build Tools | 37.0.0 / 36.1.0 |
| Git | 2.50.0 |
| Node.js | v22.14.0 |
| Python | 3.12.8 |

> **注意**: `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 需设置为 `C:\Users\gqy17\AppData\Local\Android\Sdk`

## 测试规范

### ViewModel + StateFlow 测试模式（官方推荐）

ViewModel 在 `@Before` 中创建（`runTest` 外），其 `viewModelScope` 的 Dispatcher 必须手动替换：

```kotlin
private val testDispatcher = UnconfinedTestDispatcher()

@Before fun setUp() {
    Dispatchers.setMain(testDispatcher)   // 1. 先设测试 dispatcher
    viewModel = MyViewModel(fakeDep)      // 2. 再创建 ViewModel（viewModelScope 绑定到测试 dispatcher）
}
@After fun tearDown() { Dispatchers.resetMain() }
```

**断言方式：** 优先用 `viewModel.uiState.value` 直接断言当前状态，避免 StateFlow conflation 导致的中间值丢失问题。需要验证状态序列时才用 Turbine `test {}`。

**参考资料：**
- [Android 官方：测试 Kotlin 数据流](https://developer.android.com/kotlin/flow/test)
- [kotlinx.coroutines#3143](https://github.com/Kotlin/kotlinx.coroutines/issues/3143)

### TDD 流程

- Red-Green-Refactor 循环
- DAO 测试：Robolectric + Room inMemory + Turbine
- ViewModel 测试：MockK + runTest + Dispatchers.setMain(UnconfinedTestDispatcher())
- 所有测试通过后才提交

### DAO 测试基类

所有 DAO 测试继承 `BaseDaoTest`（位于 `data/db/BaseDaoTest.kt`），遵循 Google Now in Android 官方模式：

- `@Before` 创建 Room in-memory DB + 调用 `initDaos()` 初始化 DAO
- `@After` 关闭 DB
- 子类只需声明 DAO 字段并实现 `initDaos()`
- **不要尝试跨 test class 共享 DB 实例** — Robolectric 的 `ShadowLegacySQLiteConnection` 不支持，会导致连接状态冲突

### 测试运行规范

**一次运行，一次分析。** 不要反复重跑测试：

```bash
./gradlew testDebugUnitTest 2>&1 | tee build/test-run.log
grep -E "FAILED|^e:" build/test-run.log                    # 编译/测试错误
grep -oh 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/TEST-*.xml | awk -F'"' '{s+=$2} END {print s}'
```

- 用 `tee` 保存完整日志，从日志和 XML 报告中提取所有信息
- 首次构建较慢（~40s），缓存命中后仅需 ~6s（已开启 configuration-cache）
