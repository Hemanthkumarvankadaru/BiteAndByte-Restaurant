package com.biteandbyte.restaurant.service;

import com.biteandbyte.restaurant.dto.address.AddressRequest;
import com.biteandbyte.restaurant.dto.address.AddressResponse;
import com.biteandbyte.restaurant.exception.ApiException;
import com.biteandbyte.restaurant.exception.ResourceNotFoundException;
import com.biteandbyte.restaurant.model.Address;
import com.biteandbyte.restaurant.repository.AddressRepository;
import com.biteandbyte.restaurant.repository.UserRepository;
import com.biteandbyte.restaurant.security.CurrentUser;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The logged-in customer's saved addresses. Someone else's address id answers 404, the same as
 * an id that does not exist, so ids of other customers cannot be probed.
 *
 * <p>While a customer has addresses, exactly one is the default.
 */
@Service
public class AddressService {

    public static final int MAX_ADDRESSES = 10;

    private final AddressRepository addresses;
    private final UserRepository users;
    private final CurrentUser currentUser;

    public AddressService(AddressRepository addresses, UserRepository users, CurrentUser currentUser) {
        this.addresses = addresses;
        this.users = users;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list() {
        return addresses.findByUserIdOrderByDefaultAddressDescIdAsc(currentUser.id()).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse create(AddressRequest request) {
        Long userId = currentUser.id();
        long existing = addresses.countByUserId(userId);
        if (existing >= MAX_ADDRESSES) {
            throw ApiException.badRequest("ADDRESS_LIMIT", "You can save up to " + MAX_ADDRESSES + " addresses");
        }
        Address address = new Address();
        address.setUser(users.getReferenceById(userId));
        apply(address, request);
        addresses.save(address);
        if (existing == 0 || Boolean.TRUE.equals(request.defaultAddress())) {
            makeDefault(userId, address);
        }
        return AddressResponse.from(addresses.saveAndFlush(address));
    }

    @Transactional
    public AddressResponse update(Long id, AddressRequest request) {
        Long userId = currentUser.id();
        Address address = findOwn(id, userId);
        apply(address, request);
        if (Boolean.TRUE.equals(request.defaultAddress())) {
            makeDefault(userId, address);
        }
        return AddressResponse.from(addresses.saveAndFlush(address));
    }

    /**
     * Deletes a saved address. Orders keep their own copy of the address, so history is not
     * affected. If it was the default, the oldest remaining address becomes the default.
     */
    @Transactional
    public void delete(Long id) {
        Long userId = currentUser.id();
        Address address = findOwn(id, userId);
        boolean wasDefault = address.isDefaultAddress();
        addresses.delete(address);
        addresses.flush();
        if (wasDefault) {
            addresses.findByUserIdOrderByDefaultAddressDescIdAsc(userId).stream()
                    .findFirst()
                    .ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    private void makeDefault(Long userId, Address chosen) {
        for (Address a : addresses.findByUserIdOrderByDefaultAddressDescIdAsc(userId)) {
            a.setDefaultAddress(a.getId().equals(chosen.getId()));
        }
        chosen.setDefaultAddress(true);
    }

    private Address findOwn(Long id, Long userId) {
        return addresses.findByIdAndUserId(id, userId).orElseThrow(() -> new ResourceNotFoundException("Address", id));
    }

    private static void apply(Address address, AddressRequest request) {
        address.setLabel(request.label() == null || request.label().isBlank() ? null : request.label().trim());
        address.setStreet(request.street().trim());
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPincode(request.pincode());
        address.setLandmark(request.landmark() == null || request.landmark().isBlank() ? null : request.landmark().trim());
    }
}
