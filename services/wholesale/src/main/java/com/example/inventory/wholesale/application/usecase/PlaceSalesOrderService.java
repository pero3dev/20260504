package com.example.inventory.wholesale.application.usecase;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.inventory.commons.audit.Auditable;
import com.example.inventory.commons.persistence.SnowflakeIdGenerator;
import com.example.inventory.wholesale.application.port.in.DuplicateOrderCodeException;
import com.example.inventory.wholesale.application.port.in.PartnerPriceNotFoundException;
import com.example.inventory.wholesale.application.port.in.PlaceSalesOrderUseCase;
import com.example.inventory.wholesale.application.port.out.PartnerPriceRepository;
import com.example.inventory.wholesale.application.port.out.SalesOrderRepository;
import com.example.inventory.wholesale.domain.model.Order;
import com.example.inventory.wholesale.domain.model.OrderCode;
import com.example.inventory.wholesale.domain.model.OrderId;
import com.example.inventory.wholesale.domain.model.OrderItem;
import com.example.inventory.wholesale.domain.model.PartnerCode;
import com.example.inventory.wholesale.domain.model.PartnerPrice;

/**
 * 受注確定サービス。
 *
 * <p>業態固有のポイント: クライアントから unit_price を受け取らず、 (partnerCode, skuCode) で {@link PartnerPriceRepository}
 * を引いてサーバ側で値を埋める。 これにより契約価格マスタを単一の真実の源とし、価格上書きの抜け道を塞ぐ。
 *
 * <p>currency は Command と PartnerPrice が一致しなければエラーで弾く想定だが、 MVP では Command の currency をそのまま採用し
 * PartnerPrice 側は表示用情報として無視する (master-data の Partner と契約マスタは原則同 currency になる前提)。
 */
@Service
public class PlaceSalesOrderService implements PlaceSalesOrderUseCase {

    private final SalesOrderRepository orderRepository;
    private final PartnerPriceRepository priceRepository;
    private final SnowflakeIdGenerator idGenerator;

    public PlaceSalesOrderService(
            SalesOrderRepository orderRepository,
            PartnerPriceRepository priceRepository,
            SnowflakeIdGenerator idGenerator) {
        this.orderRepository = orderRepository;
        this.priceRepository = priceRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    @Auditable(
            action = "SALES_ORDER_PLACE",
            targetType = "SalesOrder",
            targetIdExpression = "#command.code")
    public Order place(Command command) {
        OrderCode code = new OrderCode(command.code());
        if (orderRepository.existsByCode(code)) {
            throw new DuplicateOrderCodeException(command.code());
        }
        PartnerCode partner = new PartnerCode(command.partnerCode());

        List<OrderItem> items = new ArrayList<>();
        int lineNo = 1;
        for (Command.Line l : command.items()) {
            PartnerPrice price =
                    priceRepository
                            .findCurrent(partner, l.skuCode())
                            .orElseThrow(
                                    () ->
                                            new PartnerPriceNotFoundException(
                                                    partner.value(), l.skuCode()));
            items.add(
                    new OrderItem(
                            lineNo++,
                            l.skuCode(),
                            l.locationId(),
                            l.quantity(),
                            price.unitPrice()));
        }

        OrderId id = new OrderId(idGenerator.nextId());
        Order order =
                Order.place(
                        id,
                        code,
                        partner,
                        command.currency(),
                        items,
                        command.requestedDeliveryDate());
        return orderRepository.save(order);
    }
}
