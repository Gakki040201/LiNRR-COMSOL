# Li-NRR COMSOL × Codex 项目

目标：建立一套可复现、可批处理、可由 Codex 维护，同时仍能在 COMSOL GUI 中人工修改的连续流锂介导合成氨模型体系。

## 推荐主架构

- **COMSOL MPH**：用于人工查看、修改、调试和最终归档。
- **COMSOL Java API**：用于 Codex 生成模型、修改节点、批量运行和导出结果。
- **PowerShell**：连接 Codex、COMSOL 编译器和批处理求解器。
- **Record Method / Copy as Code**：把人工 GUI 修改转换为 Java API 代码，再由 Codex 合并回源码。
- **Git**：保存文本源码、参数、实验数据处理脚本和决策记录。大体积 MPH 仅保留关键里程碑版本。

## 第一次运行

1. 将本文件夹复制到本机，例如：

   `F:\LiNRR_COMSOL`

2. 在 PowerShell 中进入项目：

   ```powershell
   Set-Location F:\LiNRR_COMSOL
   ```

3. 检查 COMSOL 程序入口：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\windows\01_check_environment.ps1
   ```

4. 生成第一个可编辑 MPH：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\windows\02_build_M00_geometry.ps1
   ```

5. 在 COMSOL 中打开：

   `models\generated\LiNRR_M00_geometry.mph`

M00 只验证“Codex写代码 → COMSOL编译 → COMSOL批处理 → 生成可编辑MPH”的自动化链路，不包含物理场。

## 模型路线

- M00：自动化环境与二维几何
- M01：二维电解液层流
- M02：N2/NH3 对流扩散
- M03：二级电流分布
- M04：局部电流—产氨通量耦合
- M05：SSC/PtAu-SSC 多孔 GDE
- M06：气液压差、渗流和润湿窗口
- M07：Li+ 迁移与浓差极化
- M08：SEI 动态与 potential cycling
- M09：参数反演、不确定性与优化
- M10：三维流场与放大

详细规则见 `AGENTS.md`，科学蓝图见 `docs/research_blueprint.md`。
