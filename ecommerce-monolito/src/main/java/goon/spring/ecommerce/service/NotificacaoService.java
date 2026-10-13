package goon.spring.ecommerce.service;

import goon.spring.ecommerce.model.Notificacao;
import goon.spring.ecommerce.model.Pedido;
import goon.spring.ecommerce.repository.NotificacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    private final NotificacaoRepository notificacaoRepository;

    public NotificacaoService(NotificacaoRepository notificacaoRepository) {
        this.notificacaoRepository = notificacaoRepository;
    }

    public void enviarConfirmacao(Pedido pedido) {
        enviar(pedido, "CONFIRMACAO", "Pedido #%d confirmado | Total: R$ %s"
                .formatted(pedido.getId(), pedido.getValorTotal()));
    }

    public void enviarCancelamento(Pedido pedido) {
        enviar(pedido, "CANCELAMENTO", "Pedido #%d cancelado | Estoque devolvido"
                .formatted(pedido.getId()));
    }

    private void enviar(Pedido pedido, String tipo, String corpo) {
        // Acoplamento proposital: o historico grava na mesma base e na mesma
        // transacao do pedido, com FK para PEDIDO. Separar isso e parte do exercicio.
        notificacaoRepository.save(new Notificacao(pedido, tipo));

        log.info("[E-MAIL SIMULADO] Para: {} <{}> | {}",
                pedido.getCliente().getNome(), pedido.getCliente().getEmail(), corpo);
    }
}
