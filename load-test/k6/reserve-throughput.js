// Reserve API throughput baseline (Phase A)
//
// Inventory Core の `POST /v1/inventories/{id}/reservations` をランダムな inventory id に
// 連続で叩いて、 sustained throughput と latency を測る。
//
// 結果の判定基準(load-test/README.md 参照):
//   p95 < 200ms / error rate < 0.1% / sustained RPS / Pod ≥ 1000 → ✅ Phase 2 進行
//   超えるなら ボトルネック分析(commons-event の Outbox publisher、 DB I/O 等)が
//   Phase 2 の優先項目に上がる。
//
// 実行例:
//   docker run --rm -i --network host \
//     -e TOKEN=$TOKEN \
//     -e BASE_URL=http://localhost:8080 \
//     -v $PWD/k6:/scripts \
//     grafana/k6 run /scripts/reserve-throughput.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// シナリオパラメータ
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const TENANT_ID = __ENV.TENANT_ID || 'dev';

// 投入対象: seed.sql で 1000 件作っているので 1〜1000
const INVENTORY_ID_MIN = 1;
const INVENTORY_ID_MAX = 1000;

// 引当数量: 1〜5 のランダム(Inventory には available=1,000,000 あるので枯渇しない)
const QUANTITY_MIN = 1;
const QUANTITY_MAX = 5;

// カスタムメトリクス
const reserveLatency = new Trend('reserve_latency_ms', true);
const reserveSuccessRate = new Rate('reserve_success_rate');

export const options = {
    // ramp-up: 30s で 100 VU まで上げる。 sustained: 90s 100 VU で安定計測。 ramp-down: 10s。
    stages: [
        { duration: '30s', target: 100 },
        { duration: '90s', target: 100 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        // Reserve API endpoint レスポンスタイム
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        // 失敗率
        http_req_failed: ['rate<0.01'],
        // checks() アサーション通過率
        checks: ['rate>0.99'],
        // カスタムメトリクスでも別途閾値
        reserve_success_rate: ['rate>0.99'],
    },
};

export function setup() {
    if (!TOKEN) {
        throw new Error(
            'TOKEN environment variable is required. ' +
                'Get one via load-test/scripts/get-token.sh and pass with -e TOKEN=...',
        );
    }
    console.log(`BASE_URL=${BASE_URL}, TENANT_ID=${TENANT_ID}, target Inventory id range=[${INVENTORY_ID_MIN}, ${INVENTORY_ID_MAX}]`);
}

export default function () {
    const inventoryId = randomInt(INVENTORY_ID_MIN, INVENTORY_ID_MAX);
    const quantity = randomInt(QUANTITY_MIN, QUANTITY_MAX);

    const url = `${BASE_URL}/v1/inventories/${inventoryId}/reservations`;
    const payload = JSON.stringify({ quantity });
    const params = {
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${TOKEN}`,
        },
        tags: { name: 'reserve' }, // k6 集計タグ
    };

    const res = http.post(url, payload, params);

    reserveLatency.add(res.timings.duration);
    const ok = check(res, {
        '201 Created': (r) => r.status === 201,
    });
    reserveSuccessRate.add(ok);

    // VU pacing: ペイロードが軽すぎて CPU 100% になるのを避ける(本物のクライアントを模擬)
    sleep(0.05);
}

function randomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}
