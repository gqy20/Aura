# Aura Web

Aura 项目网站 - 产品叙事旗舰站。仓库与 Android 项目同 repo（monorepo）。

## 仓库结构

```
web/
  apps/
    web/          ← Next.js 15 主应用
  packages/
    tokens/       ← 设计 token（颜色、字体、间距）
    ui/           ← 共享 UI 组件
  package.json    ← workspace root
  pnpm-workspace.yaml
  turbo.json
```

## 命令

```bash
# 安装依赖
pnpm install

# 启动开发（apps/web）
pnpm dev

# 构建
pnpm build

# 类型检查
pnpm type-check

# Lint
pnpm lint

# 格式化
pnpm format
```

## 部署

Vercel 部署：`apps/web` 子目录为根目录，Framework Preset: Next.js。

详见 [docs/plan/website-proposal.md](../docs/plan/website-proposal.md)。
