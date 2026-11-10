-- 결제 모듈 재설계 (PRD: 중복 결제 방지)
--
-- 기존 payment 테이블은 Flyway 마이그레이션에 없었고(예전 수동 스키마에만 존재),
-- transaction_id PK 구조라 승인 전 행을 만들 수 없었다. 결제 API가 없어 실데이터도 없으므로
-- 있으면 지우고 새 구조로 만든다.
DROP TABLE IF EXISTS payment_webhook_event;
DROP TABLE IF EXISTS payment;

CREATE TABLE payment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT       NOT NULL,
    pg_order_id     VARCHAR(64)  NOT NULL,   -- 서버 발급, PG에 넘기는 주문번호(재시도마다 새로 발급)
    payment_key     VARCHAR(200) NULL,       -- PG 승인 후 채움
    amount          INT          NOT NULL,   -- 서버가 계산한 결제 금액
    status          VARCHAR(20)  NOT NULL,
    method          VARCHAR(50)  NULL,
    approved_at     DATETIME     NULL,
    failure_code    VARCHAR(100) NULL,
    failure_message VARCHAR(500) NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    -- 방어선 1: 주문 1건에 결제 행 1개
    CONSTRAINT uk_payment_order    UNIQUE (order_id),
    -- 방어선 2: PG 주문번호는 하나의 결제에만
    CONSTRAINT uk_payment_pg_order UNIQUE (pg_order_id),
    -- 방어선 3: 같은 PG 승인이 두 행에 기록되지 않음 (NULL은 중복 허용)
    CONSTRAINT uk_payment_key      UNIQUE (payment_key),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES `order`(id),
    CONSTRAINT chk_payment_status
        CHECK (status IN ('READY', 'IN_PROGRESS', 'DONE', 'FAILED', 'UNKNOWN', 'CANCELED')),
    CONSTRAINT chk_payment_amount CHECK (amount > 0)
);

-- 복구 배치가 오래 멈춘 결제를 찾을 때 사용
CREATE INDEX idx_payment_status_updated ON payment (status, updated_at);

-- 웹훅 중복 수신 방지: 같은 이벤트 키는 한 번만 처리
CREATE TABLE payment_webhook_event (
    event_key   VARCHAR(200) PRIMARY KEY,
    received_at DATETIME NOT NULL
);
