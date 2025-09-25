package com.sprout.AKMS.service.serviceImpl;

import com.sprout.AKMS.core.dto.Customer;
import com.sprout.AKMS.core.entity.CustomerEntity;
import com.sprout.AKMS.repository.CustomerRepository;
import com.sprout.AKMS.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    @Override
    public Customer createCustomer(Customer customer) {
        log.info("Creating new customer with email: {}", customer.getEmail());
        
        if (existsByEmail(customer.getEmail())) {
            throw new RuntimeException("Customer with email " + customer.getEmail() + " already exists");
        }

        CustomerEntity entity = CustomerEntity.builder()
                .name(customer.getName())
                .email(customer.getEmail())
                .build();

        CustomerEntity savedEntity = customerRepository.save(entity);
        log.info("Customer created successfully with ID: {}", savedEntity.getId());
        
        return mapToDto(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerById(UUID id) {
        log.info("Fetching customer by ID: {}", id);
        return customerRepository.findById(id)
                .map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerByEmail(String email) {
        log.info("Fetching customer by email: {}", email);
        return customerRepository.findByEmail(email)
                .map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        log.info("Fetching all customers");
        return customerRepository.findAll().stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Customer> getAllCustomers(Pageable pageable) {
        log.info("Fetching customers with pagination: page={}, size={}", 
                pageable.getPageNumber(), pageable.getPageSize());
        return customerRepository.findAll(pageable)
                .map(this::mapToDto);
    }

    @Override
    public Customer updateCustomer(UUID id, Customer customer) {
        log.info("Updating customer with ID: {}", id);
        
        CustomerEntity existingEntity = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with ID: " + id));

        // Check if email is being changed and if new email already exists
        if (!existingEntity.getEmail().equals(customer.getEmail()) && 
            existsByEmail(customer.getEmail())) {
            throw new RuntimeException("Customer with email " + customer.getEmail() + " already exists");
        }

        existingEntity.setName(customer.getName());
        existingEntity.setEmail(customer.getEmail());
        existingEntity.setUpdatedAt(LocalDateTime.now());

        CustomerEntity updatedEntity = customerRepository.save(existingEntity);
        log.info("Customer updated successfully with ID: {}", updatedEntity.getId());
        
        return mapToDto(updatedEntity);
    }

    @Override
    public void deleteCustomer(UUID id) {
        log.info("Deleting customer with ID: {}", id);
        
        if (!customerRepository.existsById(id)) {
            throw new RuntimeException("Customer not found with ID: " + id);
        }

        customerRepository.deleteById(id);
        log.info("Customer deleted successfully with ID: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return customerRepository.findByEmail(email).isPresent();
    }

    private Customer mapToDto(CustomerEntity entity) {
        return Customer.builder()
                .id(entity.getId())
                .name(entity.getName())
                .email(entity.getEmail())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}