package org.restless.iwkbackoffice.service;

import lombok.RequiredArgsConstructor;
import org.restless.iwkbackoffice.domain.entity.OrderEntity;
import org.restless.iwkbackoffice.domain.request.OrderCreateRequest;
import org.restless.iwkbackoffice.domain.response.FilterLimits;
import org.restless.iwkbackoffice.domain.response.OrderCreateResponse;
import org.restless.iwkbackoffice.repository.OrderJdbcRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderJdbcRepository repository;

    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        Long createdOrderId = repository.createOrder(new OrderEntity(
                request.longitude(),
                request.latitude(),
                LocalDateTime.now(),
                BigDecimal.valueOf(request.subTotal())
        ));
        return new OrderCreateResponse(createdOrderId);
    }

    public FilterLimits getFilterLimits() {
        return repository.getFilterLimits();
    }
}
