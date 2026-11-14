package com.biteandbyte.restaurant.repository;

import com.biteandbyte.restaurant.model.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByDefaultAddressDescIdAsc(Long userId);

    /** Ownership-safe lookup: returns empty when the address belongs to someone else. */
    Optional<Address> findByIdAndUserId(Long id, Long userId);

    Optional<Address> findFirstByUserIdAndDefaultAddressTrue(Long userId);

    long countByUserId(Long userId);
}
