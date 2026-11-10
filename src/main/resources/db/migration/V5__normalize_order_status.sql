-- 주문 상태를 OrderStatus enum 값(ORDERED, PREPARING, SERVED, COMPLETED, CANCELLED)으로 통일한다.
-- 그동안 코드마다 'ORDERED', 'PREPARING', 'Received', 'Done', '완료' 등 서로 다른 값을 써 왔다.

UPDATE `order` SET status = 'ORDERED'
 WHERE UPPER(status) IN ('ORDERED', 'PENDING', 'RECEIVED') OR status = '주문접수';

UPDATE `order` SET status = 'PREPARING'
 WHERE UPPER(status) = 'PREPARING' OR status = '조리중';

UPDATE `order` SET status = 'SERVED'
 WHERE UPPER(status) = 'SERVED' OR status = '서빙완료';

UPDATE `order` SET status = 'COMPLETED'
 WHERE UPPER(status) IN ('COMPLETED', 'DONE') OR status = '완료';

UPDATE `order` SET status = 'CANCELLED'
 WHERE UPPER(status) IN ('CANCELLED', 'CANCELED') OR status = '취소';

-- 위에서 처리하지 못한 값은 이미 지난 주문으로 보고 종료 처리한다.
UPDATE `order` SET status = 'COMPLETED'
 WHERE status NOT IN ('ORDERED', 'PREPARING', 'SERVED', 'COMPLETED', 'CANCELLED');

ALTER TABLE `order`
    MODIFY status VARCHAR(20) NOT NULL DEFAULT 'ORDERED',
    ADD CONSTRAINT chk_order_status
        CHECK (status IN ('ORDERED', 'PREPARING', 'SERVED', 'COMPLETED', 'CANCELLED'));
