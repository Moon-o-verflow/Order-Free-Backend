package com.example.tableorder.repository;

import com.example.tableorder.entity.store.StoreTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreTableRepository extends JpaRepository<StoreTable, Long> {

    @Query("select t.store.id from StoreTable t where t.id = :tableId")
    Optional<Long> findStoreIdById(@Param("tableId") Long tableId);

    Optional<StoreTable> findByStore_IdAndId(Long store_Id, Long id);


    List<StoreTable> findByStore_IdOrderByTableNumberAsc(Long storeId);
    boolean existsByStore_IdAndTableNumber(Long storeId, Integer tableNumber);
}
