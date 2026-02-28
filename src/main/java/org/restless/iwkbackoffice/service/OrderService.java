package org.restless.iwkbackoffice.service;

import lombok.RequiredArgsConstructor;
import org.restless.iwkbackoffice.domain.entity.OrderEntity;
import org.restless.iwkbackoffice.domain.request.OrderCreateRequest;
import org.restless.iwkbackoffice.domain.request.OrdersFilter;
import org.restless.iwkbackoffice.domain.response.*;
import org.restless.iwkbackoffice.exception.NotFoundException;
import org.restless.iwkbackoffice.repository.OrderJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

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

    public PageData<OrderResponse> findOrders(OrdersFilter ordersFilter, int page, int perPage) {
        return repository.findOrders(ordersFilter, page, perPage);
    }

    public OrderDetailsResponse getById(Long id) {
        OrderDetailsResponse order = repository.getById(id);
        if (order == null) {
            throw new NotFoundException("Order %d not found".formatted(id));
        }
        return order;
    }

    public List<OrderLocation> getOrdersLocations() {
        return repository.getOrdersLocations();
    }
}
