package goon.spring.notificacao_service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component // Permite o spring encontrar e gerenciar essa classe
public class NotificacaoListener {

    private static final Logger log =
        LoggerFactory.getLogger(NotificacaoListener.class);

    @KafkaListener(topics = "pedido-criado") // configura para receber mensagens do tipo pedido-criado
    public void receberPedidoCriado(PedidoCriadoEvent evento) {
        log.info(
            "[E-MAIL SIMULADO] Para: {} <{}> | Pedido #{} confirmado | Total: R$ {}",
            evento.nomeCliente(),
            evento.emailCliente(),
            evento.pedidoId(),
            evento.valorTotal()
        );
    }
}