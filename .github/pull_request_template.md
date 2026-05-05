<!--
F5 PR テンプレート(設計・実装方針より)。
PR は 600 行以下推奨。超える場合は分割を検討してください。
-->

## What
<!-- 何を変えたか、1〜2行で。 -->

## Why
<!-- 動機。Issue / Linear / Slack のリンクがあれば貼る。 -->

## How tested
<!-- 動作確認手順。新規/変更したテストの種別(unit/integration/E2E/手動) -->

- [ ] Unit tests
- [ ] Integration tests (Testcontainers)
- [ ] ArchUnit
- [ ] Manual verification

## ADR / RFC link
<!-- 関連する ADR / RFC があればリンク。新規ADRが伴う変更なら必須。 -->

## Migration / breaking change
<!-- DBマイグレーション / 既存APIの破壊的変更 / 環境変数追加 などがあれば記載。
     無ければ "なし" でOK。 -->

## Checklist

- [ ] PR 行数が 600 行以内、または分割不可能な理由を明記
- [ ] Conventional Commits 形式の commit message
- [ ] ローカルで `mvn spotless:apply` 適用済み
- [ ] ローカルで `mvn verify` が成功
- [ ] 監査必要な操作には `@Auditable` 付与済み(該当する場合)
- [ ] Feature flag(Unleash)で機能を gating(未完成機能を main にマージする場合)
