## Audit Service S3 Object Lock — 本番デプロイランブック(ADR-0008 A4)

`audit-service` が日次で WORM 保管する S3 bucket(Object Lock Compliance + 1 年 retention)+ Athena 経由のクエリ用 Glue table 定義。

### 構成

```
infra/audit-s3/
├── bucket-config/
│   ├── object-lock-configuration.json   # Compliance mode + 365 days
│   └── bucket-policy.json               # delete禁止 + IAM principal固定
├── glue/
│   ├── audit_records_table.sql          # Athena External Table(JSONL gzip)
│   └── audit_anchors_table.sql          # Athena External Table(JSON 単発)
└── README.md
```

### S3 のレイアウト

```
s3://<bucket>/audit-records/tenant=<tenantId>/date=<yyyy-MM-dd>/records.jsonl.gz
s3://<bucket>/audit-anchors/tenant=<tenantId>/date=<yyyy-MM-dd>/anchor.json
```

records は JSON Lines + gzip(1 行 = 1 監査レコード)。 anchor は単発 JSON。 partition は tenant × date で Athena projection に揃える。

### MVP の format について

ADR-0008 では Parquet と書いていたが、 MVP 実装では **JSON Lines + gzip** に変更している(`parquet-avro` の transitive 依存が重く、 1 PR 規模を肥大化させるため)。 Athena は JSON も外部表として読めるため J-SOX 上の要件は満たす。 列指向の効率(Athena scan cost)は後フェーズで Parquet 化したいので、 ADR-0008 の Implementation status に "format: JSON Lines (MVP)" を追記済。

### デプロイ手順(初期構築)

#### Step 1. S3 bucket を Object Lock 有効で作成

Object Lock は **bucket 作成時にしか有効化できない** ので、 既存 bucket を流用してはならない。

```bash
export AUDIT_BUCKET="inventory-audit-prod"   # tenant に依らない単一 bucket
export REGION="ap-northeast-1"

aws s3api create-bucket \
  --bucket "$AUDIT_BUCKET" \
  --region "$REGION" \
  --create-bucket-configuration LocationConstraint="$REGION" \
  --object-lock-enabled-for-bucket
```

#### Step 2. Versioning を有効化(Object Lock の前提)

```bash
aws s3api put-bucket-versioning \
  --bucket "$AUDIT_BUCKET" \
  --versioning-configuration Status=Enabled
```

#### Step 3. Default retention(Compliance mode + 365 days)を適用

```bash
aws s3api put-object-lock-configuration \
  --bucket "$AUDIT_BUCKET" \
  --object-lock-configuration file://bucket-config/object-lock-configuration.json
```

これ以降に PUT されたオブジェクトは **AWS root user でも 365 日間 削除/上書き不可**。

#### Step 4. Bucket policy で delete を deny + IAM principal を固定

`bucket-policy.json` の `PLACEHOLDER_AUDIT_BUCKET` / `PLACEHOLDER_ACCOUNT_ID` / `PLACEHOLDER_AUDIT_SERVICE_IRSA_ROLE` / `PLACEHOLDER_AUDITOR_ROLE` を実値に置換してから:

```bash
aws s3api put-bucket-policy \
  --bucket "$AUDIT_BUCKET" \
  --policy file://bucket-config/bucket-policy.json
```

#### Step 5. Lifecycle: 1 年経過後に自動削除(retention 期限と整合)

```bash
cat > /tmp/audit-lifecycle.json <<'EOF'
{
  "Rules": [
    {
      "ID": "AuditRecordsExpire1Year",
      "Status": "Enabled",
      "Filter": {},
      "Expiration": {
        "Days": 365
      },
      "NoncurrentVersionExpiration": {
        "NoncurrentDays": 365
      }
    }
  ]
}
EOF

aws s3api put-bucket-lifecycle-configuration \
  --bucket "$AUDIT_BUCKET" \
  --lifecycle-configuration file:///tmp/audit-lifecycle.json
```

retention 365 日 + lifecycle 365 日で「1 年経過 → 自動削除」が成立。 期限内は Compliance mode で改竄不可。

#### Step 6. IRSA Role を作成して audit-service Pod に紐付け

`audit-service-irsa` という Role(または既存サービス用 IRSA を流用)に下記 inline policy を付ける:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject"
      ],
      "Resource": "arn:aws:s3:::inventory-audit-prod/*"
    }
  ]
}
```

`audit-service` の K8s ServiceAccount に annotation(`eks.amazonaws.com/role-arn: arn:aws:iam::<account>:role/audit-service-irsa`)を付ける。

#### Step 7. audit-service の環境変数を設定

```yaml
env:
  - name: PLATFORM_AUDIT_ARCHIVE_ENABLED
    value: "true"
  - name: PLATFORM_AUDIT_ARCHIVE_BUCKET
    value: "inventory-audit-prod"
  - name: PLATFORM_AUDIT_ARCHIVE_REGION
    value: "ap-northeast-1"
```

`endpoint-override` は本番では空でよい(AWS 標準 endpoint)。

#### Step 8. Glue Database + Athena External Table を作成

```bash
aws glue create-database \
  --database-input '{"Name": "audit_db", "Description": "Audit service WORM archive"}'
```

`glue/audit_records_table.sql` と `glue/audit_anchors_table.sql` の `PLACEHOLDER_AUDIT_BUCKET` を置換し、 Athena Query Editor で実行(または `aws athena start-query-execution`)。

projection を有効にしているので、 partition の手動追加(`ALTER TABLE ADD PARTITION`)は不要。

#### Step 9. 監査人用の検証クエリ(サンプル)

```sql
-- 特定 tenant の特定日のレコード件数
SELECT count(*) FROM audit_db.audit_records
WHERE tenant = 'tenant-1' AND date = '2026-05-06';

-- レコードと同日の anchor を突き合わせ
SELECT
  r.tenant,
  r.date,
  count(*) AS record_count_in_s3,
  max(a.recordCount) AS record_count_in_anchor,
  max(a.rootHash) AS root_hash
FROM audit_db.audit_records r
LEFT JOIN audit_db.audit_anchors a
  ON a.tenant = r.tenant AND a.date = r.date
WHERE r.tenant = 'tenant-1' AND r.date = '2026-05-06'
GROUP BY r.tenant, r.date;
```

#### Step 10. ローカルでの動作確認(LocalStack)

LocalStack には Object Lock が(MVP では)サポートされないが、 PUT 経路の動作確認はできる:

```yaml
# LocalStack で立てるとき
env:
  - name: PLATFORM_AUDIT_ARCHIVE_ENABLED
    value: "true"
  - name: PLATFORM_AUDIT_ARCHIVE_BUCKET
    value: "audit-bucket"
  - name: PLATFORM_AUDIT_ARCHIVE_ENDPOINT_OVERRIDE
    value: "http://localstack:4566"
  - name: AWS_ACCESS_KEY_ID
    value: "test"
  - name: AWS_SECRET_ACCESS_KEY
    value: "test"
  - name: AWS_REGION
    value: "us-east-1"
```

### 運用ノート

- **bucket は 1 つに統一**(per-tenant bucket は management overhead が肥大化するため非推奨)
- audit-service の S3 export 失敗は warn ログのみ — DB anchor は残るので運用で再投入できる
- 既存 anchor がある日の records 再投入はしない(WORM 上書き不可で必ず失敗、 並行 race の冪等扱いで吸収)
- AWS root user でも保持期限内の削除は不可 — **bucket 削除も Object Lock 有効中はできない**(`aws s3 rb` は Versioning 有効で `--force` 付けても deny される)
- 監査結果の改竄チェックは DB 側 hash chain + Merkle root と S3 側オブジェクトを対比する(将来 ETL ジョブ化の候補)
- bucket policy の `Principal *` deny は強力。 IAM 経由でも上書き不可。 ただし retention 期限が経過したオブジェクトは自然と削除可能になるため、 Compliance mode 自体に補完されてはじめて WORM が成立する
- 1 年保持期限が法令変更等で長期化した場合は、 Default retention の `Days` 値を上げる(短くするのは Compliance mode では不可)
