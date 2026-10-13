package goon.spring.ecommerce.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Service
public class PedidoEventPublisher {

    // Atributo de classe do Spring usado para publicar mensanges
    // Dentro dele, armazena o tipo da chave e do conteudo
    // Sendo a chave o ID do pedido 
    private final KafkaTemplate<String, PedidoCriadoEvent> kafkaTemplate;

    public PedidoEventPublisher(
            KafkaTemplate<String, PedidoCriadoEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // O Spring so chama apos a confirmacao da transacao
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicarPedidoCriado(PedidoCriadoEvent evento) { // recebe o evento apos o commit
        kafkaTemplate.send(
            "pedido-criado", // nome do topico
            evento.pedidoId().toString(),  // passa apenas o id do pedido
            evento  // passa o objeto completo
        );
    }
}