package goon.spring.ecommerce.messaging;

import java.math.BigDecimal;

public record PedidoCriadoEvent(
    Long pedidoId,
    String nomeCliente,
    String emailCliente,
    BigDecimal valorTotal
) {
}