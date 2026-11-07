# MCP 调用指南 · 此地无银三百两（电脑端）

> 适用版本：**10.27.3** 及以后 · 仅 Windows 桌面端  
> 手机端不提供 MCP 接口。

## 1. 是什么

桌面端内置了一个基于 **stdio** 的 MCP（Model Context Protocol）服务。支持 MCP 的 AI 客户端（Trae、Claude Desktop、Cherry Studio 等）可以直接调用账本工具：

- 查总余额、攒着、可用，改总余额或任意调整攒着/可用
- 配置自动攒着：每周/每月/每年从可用转入攒着，可选择是否视作支出计入限额与统计
- 记账、改账、删账（与界面上的操作等价）
- 读取分类、新增/改名/删除分类
- 读取支出统计图表数据（分类占比、每日序列、最大几笔支出）

所有数据只读写本机账本文件 `%APPDATA%\MucageLedger\ledger.json`，不访问网络。

## 2. 使用前准备

1. Windows 桌面端版本 ≥ 10.27.3。
2. **先关闭正在运行的 MucageLedger 窗口**：界面和 MCP 各自在内存里维护账本，同时运行会用其中一方覆盖另一方。
3. 建议先备份 `%APPDATA%\MucageLedger\ledger.json`。

## 3. 启动与客户端配置

### 方式一：直接启动 exe

```bash
MucageLedger.exe --mcp
```

把下面配置加入 MCP 客户端的配置文件（路径按你的实际安装位置修改）：

```json
{
  "mcpServers": {
    "mucage-ledger": {
      "command": "C:\\0created-aigc\\ci_di_wu_yin_san_bai_liang\\MucageLedger\\MucageLedger.exe",
      "args": ["--mcp"]
    }
  }
}
```

> 说明：MCP 客户端通过管道连接 stdin/stdout 即可正常工作。若你用 PowerShell 手工测试 `| MucageLedger.exe --mcp` 看不到输出，这是 PowerShell 对 GUI 程序的限制，可改用 `Start-Process -RedirectStandardInput/-RedirectStandardOutput` 重定向测试。
>
> 服务启动后，Windows 通知区域会出现绿色 **¥** 托盘图标。鼠标悬停可看到 PID 与账本路径；右键菜单可「显示 MCP 状态」或「停止 MCP 服务」。由 Agent 在后台启动的实例同样会显示，不会再无声运行。

### 方式二：用 Python 包装器（客户端只允许解释器命令时）

有些客户端（例如 **AstroBot**）出于安全考虑，只允许 `python` / `node` / `bun` / `uv` 等解释器作为 stdio 命令，直接填 exe 会报：

```
MCP stdio command 'mucaged...' is not allowed.
Allowed commands: bun, bunx, deno, node, npm, npx, pnpm, py, python, python3, uv, uvx, yarn.
```

此时改用安装包里自带的 `mucage_mcp.py`（与本目录的 `MucageLedger.exe` 放在一起，已随压缩包附带）：

```json
{
  "mcpServers": {
    "mucage-ledger": {
      "command": "python",
      "args": ["C:\\0created-aigc\\ci_di_wu_yin_san_bai_liang\\MucageLedger\\mucage_mcp.py"]
    }
  }
}
```

包装器只负责把 stdin/stdout 原样转发给同目录的 `MucageLedger.exe --mcp`，不修改任何数据。如果 exe 不在同一目录，可设置环境变量 `MUCAGE_LEDGER_EXE` 指向完整的 exe 路径。

另一种做法是放开客户端白名单（把 exe 加进允许列表）：

```text
ASTROBOT_MCP_STDIO_ALLOWED_COMMANDS=python,node,MucageLedger.exe
```

### 可选：指定其他账本目录

设置环境变量 `MUCAGE_LEDGER_HOME`（目录路径），服务会读写该目录下的 `ledger.json`：

```powershell
$env:MUCAGE_LEDGER_HOME = "D:\MucageLedgerData"
MucageLedger.exe --mcp
```

不设置时默认 `%APPDATA%\MucageLedger`。

## 4. 工具一览

| 工具 | 作用 | 主要参数 |
| --- | --- | --- |
| `get_balance` | 查总余额、攒着、可用、自动攒着配置、锚点信息、账目数量 | 无 |
| `set_balance` | 设置总余额（以今天 0 点为锚点，保留攒着配置） | `amount` 元 |
| `set_savings` | 调整攒着，总余额不变（自动限制在 0~总余额） | `amount` 元 |
| `set_available` | 调整可用，总余额不变（攒着 = 总余额 - 可用） | `amount` 元 |
| `set_savings_auto` | 配置自动攒着：开关、周期、每期金额、是否视作支出 | `enabled` `period` `amount` `countAsExpense` |
| `list_records` | 查账目（可按时间、类型、分类、备注过滤） | `start` `end` `type` `category` `keyword` `limit` |
| `add_record` | 记一笔 | `amount` `type` `category` `note` `date` `limitExempt` |
| `update_record` | 改一笔（只传要改的字段） | `id` + 任意字段 |
| `delete_record` | 删一笔（软删除，同步时为墓碑） | `id` |
| `list_categories` | 列出分类 | `type`（可选） |
| `add_category` | 新增分类 | `name` `type` `icon` |
| `update_category` | 改分类名称/图标 | `id` `name` `icon` |
| `delete_category` | 删分类（账目归入「其他」，「其他」不可删） | `id` |
| `get_stats` | 支出/收入合计、分类占比、每日序列、前 5 大支出 | `month` 或 `start`+`end` |
| `import_ledger` | 导入另一端的账本文件并合并（源文件不会被修改） | `path` |
| `export_ledger` | 导出本机账本为同步文件（本机账本不会被修改） | `path`（可选） |

## 5. 调用示例

MCP 客户端会自动完成握手；手工调试时按行发送 JSON-RPC：

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"demo","version":"1.0"}}}
{"jsonrpc":"2.0","method":"notifications/initialized"}
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_balance","arguments":{}}}
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"set_savings","arguments":{"amount":300}}}
{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"set_available","arguments":{"amount":700}}}
{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"set_savings_auto","arguments":{"enabled":true,"period":"每月","amount":200,"countAsExpense":true}}}
{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"add_record","arguments":{"amount":35.5,"type":"支出","category":"吃","note":"午饭"}}}
{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"get_stats","arguments":{"month":"2026-11"}}}
{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"export_ledger","arguments":{"path":"D:\\sync\\账本.json"}}}
{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"import_ledger","arguments":{"path":"D:\\sync\\手机账本.json"}}}
```

返回值统一为：

```json
{"content":[{"type":"text","text":"{...工具结果 JSON...}"}],"isError":false}
```

出错时 `isError` 为 `true`，`text` 中是错误原因（例如分类不存在时会列出可用分类）。

## 6. 参数与数据约定

- **金额单位**：一律为「元」，支持两位小数；返回同时给出 `amount`（元）与 `amountCents`（分）。
- **日期**：`YYYY-MM-DD` 或 `YYYY-MM-DD HH:mm`（也接受毫秒时间戳）；省略表示当前时间。查询时的 `end` 若只给日期，会包含当天。
- **类型**：`支出` / `收入`，也接受 `EXPENSE` / `INCOME`。
- **分类**：可用名称或数字 id；省略时归入同类型「其他」。名称不存在时会返回可用分类列表。
- **限额豁免** `limitExempt`：`0` 不豁免、`1` 仅豁免日限额、`2` 全部豁免。
- **删除**：软删除，账目仍以墓碑保留在同步文件中，导出给手机后另一端也会删除。
- **余额拆分**：`总余额 = 攒着 + 可用`。总余额 = 锚点余额 + 锚点之后的收入 − 支出；攒着只是账面预留，不涉及真实资金搬动。`set_savings` / `set_available` 只改划分、不改总余额；`set_balance` 会以今天 0 点为新锚点，并保留攒着与自动攒着配置。
- **自动攒着**：`set_savings_auto` 的 `period` 支持 `每周` / `每月` / `每年`（也接受 `WEEKLY` / `MONTHLY` / `YEARLY`）。首次开启或切换周期从当前周期起点开始，下一周期才转入；错过的周期会逐期补足，可用不足的那一期直接跳过。`countAsExpense=true` 时每次转入生成一笔分类为「攒着」的支出（`savingsTransfer=true`），计入日/月限额与统计；关闭则只改账面金额，不生成记录。
- **导入**：`import_ledger` 只合并、不改源文件；按 `syncId` 去重、`updatedAt` 较新者胜出。返回 `inserted` / `updated` / `skipped` / `categoriesChanged` / `balanceUpdated` / `localOnly`，其中 `localOnly` 表示本机另有账目不在源文件中。攒着金额与自动攒着配置随余额锚点一起同步；两端应升级到 10.27.3 及以上，旧版客户端不认识这些字段。
- **导出**：`export_ledger` 把本机账本写成同步文件，不改本机账本；省略 `path` 时自动写入 `%APPDATA%\MucageLedger\exports\SanBaiLiang-ledger-*.json`。不能把 `path` 指向本机 `ledger.json`，会被拒绝。

## 7. `get_balance` 返回结构

```json
{
  "initialized": true,
  "total": 1000.00,
  "totalCents": 100000,
  "saved": 600.00,
  "savedCents": 60000,
  "available": 400.00,
  "availableCents": 40000,
  "balance": 1000.00,
  "balanceCents": 100000,
  "anchorBalance": 1000.00,
  "anchoredAt": "2026-11-07 00:00",
  "recordCount": 3,
  "autoSave": {
    "enabled": true,
    "period": "MONTHLY",
    "periodLabel": "每月",
    "amount": 200.00,
    "amountCents": 20000,
    "countAsExpense": true,
    "lastAt": "2026-11-01 00:00",
    "nextAt": "2026-12-01 00:00"
  },
  "hint": "总余额 = 锚点余额 + 锚点之后的收入 - 支出；攒着 + 可用 = 总余额，账面划转不影响总余额"
}
```

> `balance` / `balanceCents` 为兼容旧调用方保留，含义与 `total` 相同。自动攒着生成的记录在 `list_records` 中会带 `"savingsTransfer": true`，分类为「攒着」。

## 8. `get_stats` 返回结构

```json
{
  "range": {"start": "2026-11-01 00:00", "end": "2026-11-30 23:59"},
  "expense": {"amount": 1234.50, "count": 12},
  "income": {"amount": 500.00, "count": 2},
  "net": -734.50,
  "byCategory": [
    {"categoryId": 101, "category": "吃", "amount": 600.00, "count": 6, "share": 0.486}
  ],
  "daily": [
    {"date": "2026-11-01", "expense": 35.50, "income": 0.00}
  ],
  "topExpenses": [
    {"id": "…", "date": "2026-11-05 12:30", "amount": 200.00, "category": "吃", "note": "聚餐"}
  ]
}
```

## 9. 常见问题

- **客户端报「stdio command ... is not allowed」**：该客户端只允许解释器命令（AstroBot 等），按「方式二」改用 `python` + `mucage_mcp.py`，或设置 `ASTROBOT_MCP_STDIO_ALLOWED_COMMANDS` 加入 exe。
- **客户端连上但工具列表为空**：确认配置里传了 `--mcp`（包装器方式无需再传）；可先用 `python`/`node` 等脚本以管道方式启动自测。
- **PowerShell 管道收不到输出**：属于 PowerShell 对 GUI 程序的限制，改用真实 MCP 客户端或文件重定向。
- **后台看不到 MCP 服务 / 想结束它**：看 Windows 通知区域的绿色 ¥ 图标，悬停可见 PID，右键可停止；也可以在任务管理器里结束 `MucageLedger.exe`（`--mcp` 参数）。
- **想同时用界面和 MCP**：目前不支持并发写入，请关闭窗口后再让 Agent 操作。
- **改了电脑账本，手机怎么同步**：在桌面端「设置 → 导出给手机」导出新文件，再在手机「设置 → 数据同步」导入。
- **自动攒着会真的转账吗**：不会。攒着/可用只是账面上的两栏展示；开启「视作支出」只是额外生成一笔分类为「攒着」的支出记录，用于限额与统计。
- **为什么可用会变成 0 / 攒着没有继续增加**：可用不足时自动攒着会跳过该期，不会把攒着或可用变成负数。
- **数据安全**：工具拥有与界面相同的写权限；建议在让 Agent 批量操作前备份 `ledger.json`。

## 10. 隐私说明

MCP 服务只在本地通过标准输入输出通信，不上传账本、余额或分类数据；是否把结果发给 AI 模型由你使用的客户端决定。
