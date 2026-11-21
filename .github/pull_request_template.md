## 这个 PR 做了什么

<!-- 一句话说清。一个 PR 只做一件事。 -->

## 为什么

<!-- 关联的 Issue：Fixes #123。以及背景/动机。 -->

## 怎么验证的

- [ ] `.\scripts\verify.ps1` 通过
- [ ] 单测覆盖了新增的纯逻辑
- [ ] 真机验证（说明机型与系统版本）
- [ ] 只跑了模拟器（说明是哪一项没法在模拟器上验）

## 影响面

- [ ] 碰了记录链路（采样 / 休眠 / 写库）
- [ ] 碰了数据库 schema（需要写 Migration 并导出 schema）
- [ ] 碰了导出 / 导入格式（需要更新 docs/import-format.md）
- [ ] 碰了更新逻辑（需要说明校验路径）
- [ ] 只是 UI

## 截图 / 录屏

<!-- UI 改动必填。涉及真实位置请打码或用虚构数据。 -->

## 提交前自查

- [ ] 没有提交密钥、`local.properties`、`keystore.properties`、release keystore
- [ ] 界面文案进了 `strings.xml`，ViewModel 里用 `UiText`
- [ ] 颜色 / 圆角 / 间距走主题与 `TrailSpacing`
- [ ] 注释解释的是「为什么」，不是复述代码
