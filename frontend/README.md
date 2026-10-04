# ApiPilot 前端

Vue 3 / Vite / TailwindCSS 桌面界面，入口为 src/main.js，API 请求统一经过 src/api/client.js。界面直接使用现有后端数据，不提供登录或团队模块。预览数据有明确标记，不执行任务或读取真实模型配置。

## 开发

在仓库根目录启动后端，再进入前端目录：

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

默认访问 http://127.0.0.1:5173/，/api 和 /actuator 代理到 http://127.0.0.1:18081。IDEA 后端使用 8080 时在启动 Vite 前设置：

```powershell
$env:DOCHELPER_BACKEND_URL = 'http://127.0.0.1:8080'
npm.cmd run dev
```

Ctrl+C 停止当前开发服务。Linux/macOS 使用 npm 与相应环境变量语法。

## 构建与验证

```bash
npm ci
npm test
npm run build
```

构建输出 dist，Maven 打包时复制到 jar 静态资源；日常使用 app.ps1 或 Docker，不需要 Vite。源码、锁文件、测试与 STYLE.md 保留，node_modules/dist 不提交。

[接口字段与异常](../docs/api.md)、[维护步骤](../docs/personal-operations.md)、[界面风格](STYLE.md)、[合成联调](tests/integration/README.md)。
