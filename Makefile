# ============================================================
#  Android 智能体应用 — Makefile
#  开发快捷命令（Windows: Git Bash / WSL 运行 make）
# ============================================================

.PHONY: help build clean install run test lint debug \
        release check format devices logcat uninstall

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

check: lint test
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
	adb logcat -s "Companion*" "*:S" | head -100

logcat-clear:
	adb logcat -c

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
