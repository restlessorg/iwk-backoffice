package org.restless.iwkbackoffice.service;

import lombok.RequiredArgsConstructor;
import org.restless.iwkbackoffice.domain.entity.OrderEntity;
import org.restless.iwkbackoffice.domain.request.OrderCreateRequest;
import org.restless.iwkbackoffice.domain.request.OrdersFilter;
import org.restless.iwkbackoffice.domain.response.FilterLimits;
import org.restless.iwkbackoffice.domain.response.OrderCreateResponse;
import org.restless.iwkbackoffice.domain.response.OrderDetailsResponse;
import org.restless.iwkbackoffice.domain.response.OrderLocation;
import org.restless.iwkbackoffice.domain.response.OrderResponse;
import org.restless.iwkbackoffice.domain.response.PageData;
import org.restless.iwkbackoffice.exception.BadRequestException;
import org.restless.iwkbackoffice.exception.NotFoundException;
import org.restless.iwkbackoffice.repository.OrderJdbcRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderJdbcRepository repository;

    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        validateCoordinates(request.longitude(), request.latitude());
        Long createdOrderId = repository.createOrder(new OrderEntity(
                request.longitude(),
                request.latitude(),
                LocalDateTime.now(),
                request.subTotal()
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

    private void validateCoordinates(Double longitude, Double latitude) {
        if (longitude < -180 || longitude > 180) {
            throw new BadRequestException("longitude must be between -180 and 180");
        }
        if (latitude < -90 || latitude > 90) {
            throw new BadRequestException("latitude must be between -90 and 90");
        }
    }
}
