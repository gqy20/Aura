# ============================================================
#  Android 智能体应用 — Makefile
#  开发快捷命令（Windows: Git Bash / WSL 运行 make）
# ============================================================

.PHONY: help build clean install run test lint debug \
        release check format devices logcat uninstall \
        benchmark-mnn benchmark-aura

# --- 默认目标 ---
help:
	@echo ""
	@echo "  Aura (奥拉) - Makefile Commands"
	@echo "  =================================="
	@echo ""
	@echo "  Build:"
	@echo "    make build       Debug APK"
	@echo "    make release     Release APK (signed, minified)"
	@echo "    make clean       Clean build outputs"
	@echo "    make check       Full CI locally (lint + test + build)"
	@echo ""
	@echo "  Install & Run:"
	@echo "    make install     Install debug APK to device"
	@echo "    make run         Build + install + launch"
	@echo "    make uninstall   Uninstall from device"
	@echo ""
	@echo "  Test & Quality:"
	@echo "    make test        Run unit tests"
	@echo "    make atest       Run instrumented (Android) tests"
	@echo "    make lint        Run Kotlin Lint + Android Lint"
	@echo "    make format      Auto-format code (ktlint)"
	@echo ""
	@echo "  Device:"
	@echo "    make devices     List connected devices/emulators"
	@echo "    make logcat      Show device log (app filter)"
	@echo "    make benchmark-mnn   Run benchmark using scripts/mnn_benchmark.yml"
	@echo "    make benchmark-aura  Parse Aura runtime metrics from logcat"
	@echo ""
	@echo "  Project:"
	@echo "    make deps        Download dependencies (first time)"
	@echo "    make tree        Show project dependency tree"
	@echo ""

# --- 构建 ---

build:
	./gradlew assembleDebug

release:
	./gradlew assembleRelease

clean:
	./gradlew clean

check: lint test build
	@echo "=== All checks passed ==="

# --- 安装运行 ---

install: build
	./gradlew installDebug

run: install
	adb shell am start -n com.xiaoqi.companion/.MainActivity

uninstall:
	adb uninstall com.xiaoqi.companion

# --- 测试 ---

test:
	./gradlew testDebugUnitTest --stacktrace

# 快速跑 —— 跳过 Robolectric 类(DAO/UI/Downloader),只跑纯 JVM 测试
# 场景:开发期迭代“修改逻辑 → 快速验证”,不做 DB/UI/集成验证
test-fast:
	./gradlew testDebugUnitTest --stacktrace --tests "com.xiaoqi.companion.core.*" --tests "com.xiaoqi.companion.feature.chat.ChatViewModelTest" --tests "com.xiaoqi.companion.feature.chat.usecase.*" --tests "com.xiaoqi.companion.feature.onboarding.*" --tests "com.xiaoqi.companion.data.datastore.*" --tests "com.xiaoqi.companion.data.repository.ConfigRepositoryTest" --tests "com.xiaoqi.companion.data.repository.ConversationRepositoryTest" --tests "com.xiaoqi.companion.data.repository.McpServerListRepositoryTest" --tests "com.xiaoqi.companion.data.repository.MessageRepositoryTest" --tests "com.xiaoqi.companion.data.repository.ToolCallRepositoryTest"

# 只跑 DAO/Repo(集成性) —— 使用 Robolectric + Room
# 场景:修改 schema / DAO 查询后验证
test-db:
	./gradlew testDebugUnitTest --stacktrace --tests "com.xiaoqi.companion.data.db.*" --tests "com.xiaoqi.companion.data.repository.MemoryRepositoryTest"

# 只跑单一类 —— 调用: make test-one T=CompanionRuntimeTest
test-one:
	./gradlew testDebugUnitTest --stacktrace --tests "*$(T)*"

atest:
	./gradlew connectedDebugAndroidTest

test-coverage:
	./gradlew testDebugUnitTestCoverageVerification

# --- 代码质量 ---

lint:
	./gradlew :app:lint

format:
	./gradlew ktlintFormat

detekt:
	./gradlew detekt

# --- 设备操作 ---

devices:
	adb devices -l

logcat:
	adb logcat | grep -E "Companion\.(App|Chat|Runtime|LLM|Prompt|Parser|Repo|Config|Emotion|Relation|DB)" | head -100

logcat-clear:
	adb logcat -c

benchmark-mnn:
	python scripts/mnn_benchmark.py --mode app

benchmark-aura:
	python scripts/mnn_benchmark.py --mode aura

screenshot:
	adb exec-out screencap -p > screenshot_$(shell date +%Y%m%d_%H%M%S).png

# --- 项目信息 ---

deps:
	./gradlew dependencies --configuration implementation

tree:
	./gradlew :app:dependencies

tasks:
	./gradlew tasks --group="build"

# --- Gradle Wrapper ---
wrapper:
	./gradlew wrapper --gradle-version=8.10.2
