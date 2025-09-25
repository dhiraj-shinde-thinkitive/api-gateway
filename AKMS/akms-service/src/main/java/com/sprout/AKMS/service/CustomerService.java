package com.sprout.AKMS.service;

import com.sprout.AKMS.core.dto.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerService {
    Customer createCustomer(Customer customer);
    Optional<Customer> getCustomerById(UUID id);
    Optional<Customer> getCustomerByEmail(String email);
    List<Customer> getAllCustomers();
    Page<Customer> getAllCustomers(Pageable pageable);
    Customer updateCustomer(UUID id, Customer customer);
    void deleteCustomer(UUID id);
    boolean existsByEmail(String email);
}