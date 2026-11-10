package com.example.tableorder.repository;

import com.example.tableorder.entity.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder_Id(Long orderId);

    Optional<Payment> findByPgOrderId(String pgOrderId);
}
