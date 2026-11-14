package com.biteandbyte.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.biteandbyte.restaurant.model.Address;
import com.biteandbyte.restaurant.model.CartItem;
import com.biteandbyte.restaurant.model.CustomerOrder;
import com.biteandbyte.restaurant.model.Gender;
import com.biteandbyte.restaurant.model.MenuItem;
import com.biteandbyte.restaurant.model.OrderItem;
import com.biteandbyte.restaurant.model.OrderStatus;
import com.biteandbyte.restaurant.model.Payment;
import com.biteandbyte.restaurant.model.PaymentMethod;
import com.biteandbyte.restaurant.model.PaymentStatus;
import com.biteandbyte.restaurant.model.Role;
import com.biteandbyte.restaurant.model.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** Schema and mapping rules from the gap analysis, checked against the real Flyway schema. */
@SpringBootTest
@Transactional
class PersistenceTest {

    @Autowired private EntityManager em;
    @Autowired private UserRepository users;
    @Autowired private AddressRepository addresses;
    @Autowired private MenuItemRepository menuItems;
    @Autowired private CartItemRepository cartItems;
    @Autowired private CustomerOrderRepository orders;
    @Autowired private PaymentRepository payments;

    private User customer;
    private Address home;
    private MenuItem biryani;
    private MenuItem lassi;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setUsername("ravi");
        customer.setEmail("ravi@example.com");
        customer.setPhone("9876543210");
        customer.setFullName("Ravi Kumar");
        customer.setPasswordHash("not-a-real-hash");
        customer.setAge(28);
        customer.setGender(Gender.MALE);
        customer.setRole(Role.CUSTOMER);
        users.save(customer);

        home = new Address();
        home.setUser(customer);
        home.setLabel("Home");
        home.setStreet("12 Road No. 3, Banjara Hills");
        home.setCity("Hyderabad");
        home.setState("Telangana");
        home.setPincode("500034");
        home.setDefaultAddress(true);
        addresses.save(home);

        biryani = menuItems.findAll().stream().filter(i -> i.getName().contains("Chicken Dum Biryani")).findFirst().orElseThrow();
        lassi = menuItems.findAll().stream().filter(i -> i.getName().equals("Sweet Lassi")).findFirst().orElseThrow();
    }

    @Test
    void cartAllowsOneRowPerUserAndItem() {
        cartItems.saveAndFlush(cartLine(biryani, 1));

        assertThatThrownBy(() -> cartItems.saveAndFlush(cartLine(biryani, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orderKeepsAddressAndItemSnapshotsAfterSourcesChange() {
        CustomerOrder order = newOrder("BB-20261114-00001");
        order.addItem(OrderItem.of(biryani, 2));
        order.addItem(OrderItem.of(lassi, 1));
        orders.saveAndFlush(order);

        home.setStreet("New street after the order");
        biryani.setName("Renamed Biryani");
        biryani.setPrice(new BigDecimal("999.00"));
        em.flush();
        em.clear();

        CustomerOrder reloaded = orders.findByOrderNumber("BB-20261114-00001").orElseThrow();
        assertThat(reloaded.getDeliveryStreet()).isEqualTo("12 Road No. 3, Banjara Hills");
        assertThat(reloaded.getItems()).hasSize(2);
        OrderItem first = reloaded.getItems().get(0);
        assertThat(first.getItemName()).isEqualTo("Hyderabadi Chicken Dum Biryani");
        assertThat(first.getUnitPrice()).isEqualByComparingTo("299.00");
        assertThat(first.getLineTotal()).isEqualByComparingTo("598.00");
    }

    @Test
    void ownershipLookupsHideOtherUsersRows() {
        CustomerOrder order = newOrder("BB-20261114-00002");
        order.addItem(OrderItem.of(lassi, 1));
        orders.saveAndFlush(order);

        Long someoneElse = customer.getId() + 1000;
        assertThat(orders.findByIdAndUserId(order.getId(), customer.getId())).isPresent();
        assertThat(orders.findByIdAndUserId(order.getId(), someoneElse)).isEmpty();
        assertThat(addresses.findByIdAndUserId(home.getId(), someoneElse)).isEmpty();
    }

    @Test
    void softDeletedItemsAreHiddenFromFinders() {
        lassi.setDeleted(true);
        menuItems.saveAndFlush(lassi);

        assertThat(menuItems.findByIdAndDeletedFalse(lassi.getId())).isEmpty();
        assertThat(menuItems.findById(lassi.getId())).isPresent();
        assertThat(menuItems.countByCategoryIdAndDeletedFalse(lassi.getCategory().getId())).isEqualTo(2);
    }

    @Test
    void paymentsStoreRazorpayReferences() {
        CustomerOrder order = newOrder("BB-20261114-00003");
        order.addItem(OrderItem.of(lassi, 1));
        orders.saveAndFlush(order);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.RAZORPAY);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(order.getGrandTotal());
        payment.setRazorpayOrderId("order_TEST123");
        payments.saveAndFlush(payment);

        assertThat(payments.findByRazorpayOrderId("order_TEST123")).isPresent();
        assertThat(payments.findByOrderIdOrderByIdAsc(order.getId())).hasSize(1);
    }

    private CartItem cartLine(MenuItem item, int quantity) {
        CartItem line = new CartItem();
        line.setUser(customer);
        line.setMenuItem(item);
        line.setQuantity(quantity);
        return line;
    }

    private CustomerOrder newOrder(String number) {
        CustomerOrder order = new CustomerOrder();
        order.setUser(customer);
        order.setOrderNumber(number);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setSubtotal(new BigDecimal("687.00"));
        order.setTaxAmount(new BigDecimal("34.35"));
        order.setDeliveryCharge(BigDecimal.ZERO);
        order.setGrandTotal(new BigDecimal("721.35"));
        order.setOrderedAt(Instant.now());
        order.snapshotAddress(home, customer.getFullName(), customer.getPhone());
        return order;
    }
}
