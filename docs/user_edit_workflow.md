# 你如何在 GUI 中修改，同时让 Codex 接管维护

## 原则

MPH 是可视化和人工调试载体；Java源码是可复现自动化载体。两者通过 COMSOL 的 Record Method 或 Copy as Code 同步。

## 人工修改流程

1. 从 `models/generated/` 打开最近一个通过测试的 MPH。
2. 立即另存为 `models/master/日期_说明_manual.mph`，不要覆盖生成文件。
3. 在 Model Builder 中启用节点名称和Tag显示。
4. 打开 Developer 选项卡。
5. 点击 **Record Method**。
6. 只做一个逻辑修改，例如：
   - 修改入口流量；
   - 新增一个边界条件；
   - 修改网格；
   - 新增一个结果导出。
7. 点击 **Stop Recording**。
8. 在 Application Builder 的 Method Editor 中复制生成代码。
9. 保存到：
   `changes/CHG_日期_简短说明.java`
10. 告诉 Codex：
   “把 changes 中的新方法合并到对应模块，重建模型并验证。”

## 更轻量的方式

对单个节点右键，使用：

`Copy as Code to Clipboard`

优先复制：
- Create；
- Set All；
- Get。

## 禁止做法

- 一次录制几十个不相关操作；
- 依赖边界数字且不建立命名选择；
- 人工修改后只保存MPH、不保留代码；
- 直接覆盖master；
- 在未验证前删除旧模型。
