# Aura Web — Vercel 部署指引

> 适用 P0 阶段（占位首页）+ 后续 P1-P2 阶段。

## 一次性配置

### 1. 创建 Vercel 项目

1. 打开 https://vercel.com/new
2. 选择 `Import Git Repository` → 选本仓库（`gqy20/android` 或你 fork 后的仓库）
3. **Project Name**：`aura-web`（或你喜欢的名字）
4. **Framework Preset**：自动识别为 `Next.js`（无需改）
5. **Root Directory**：点 `Edit` → 选 `web/apps/web` ⚠️
6. **Build & Output Settings**：自动填充，**不要改**：
   - Build Command: `cd web && pnpm install --frozen-lockfile && pnpm turbo build --filter=web`
   - Output Directory: `web/apps/web/.next`
   - Install Command: `cd web && pnpm install --frozen-lockfile`
7. **Environment Variables**：本阶段**无**
8. 点击 `Deploy`

> ⚠️ 根目录必须是 `web/apps/web`，不是 `web/` 也不是仓库根。Vercel 不知道 monorepo 的 workspace 边界。

### 2. 首次部署后

- 默认域名：`aura-web.vercel.app`（或你设置的 name）
- 部署日志：Vercel → Project → Deployments → 选最新
- 每次 push 到 `master` 自动部署生产环境
- 每个 PR 自动生成 preview URL（`aura-web-git-<branch>-<user>.vercel.app`）

### 3. 验证部署成功

- [ ] 访问 `https://aura-web.vercel.app/` → 看到 "The AI companion that lives with you."
- [ ] 标题："Aura — AI companion that lives with you"
- [ ] 浏览器 DevTools → Network → HTML 200，CSS 加载，深色背景 `#08090A`
- [ ] Lighthouse Score ≥ 95（Vercel 自动跑）

## 后续

### 接入自定义域名

Vercel → Project → Settings → Domains → 添加 `aura.yourdomain.com`，按提示配 DNS。

### 预览环境

每个 PR 自动生成 `https://aura-web-git-<branch>-<user>.vercel.app`，可分享给团队 review。

### 回滚

Vercel → Deployments → 选历史版本 → `Promote to Production`。

## 故障排查

| 现象 | 原因 | 解决 |
|------|------|------|
| Build 报 "Lockfile is incompatible" | `pnpm-lock.yaml` 与 `package.json` 不一致 | 本地跑 `pnpm install` 重生成 lockfile 后 push |
| 部署成功但页面 404 | Root Directory 配错 | Settings → General → Root Directory = `web/apps/web` |
| 字体加载失败 | next/font 拉取 Google Fonts 受限 | 检查 `Output: "standalone"` 是否启用，或换 self-host |
| 慢 | Free 层冷启动 | 升级 Pro 或加 `vercel.json` warmup |

## 监控

- **Analytics**（隐私友好）：Vercel → Project → Analytics → 启用
- **Speed Insights**：同上，Lighthouse 数据
- **Logs**：Deployments → 选版本 → Logs

---

部署完成后告诉本助手，进入 P1 阶段（Hero + 滚动叙事 + 3D 主视觉）。
