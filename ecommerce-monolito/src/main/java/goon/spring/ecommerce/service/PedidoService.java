package goon.spring.ecommerce.service;

import goon.spring.ecommerce.model.Cliente;
import goon.spring.ecommerce.model.ItemPedido;
import goon.spring.ecommerce.model.Pedido;
import goon.spring.ecommerce.repository.ClienteRepository;
import goon.spring.ecommerce.repository.PedidoRepository;
import goon.spring.ecommerce.messaging.PedidoCriadoEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final EstoqueService estoqueService;
    private final NotificacaoService notificacaoService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public PedidoService(PedidoRepository pedidoRepository,
                         ClienteRepository clienteRepository,
                         EstoqueService estoqueService,
                         NotificacaoService notificacaoService,
                         ApplicationEventPublisher applicationEventPublisher) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.estoqueService = estoqueService;
        this.notificacaoService = notificacaoService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional 
    public Pedido criar(Long clienteId, Map<Long, Integer> quantidadePorProduto) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cliente nao encontrado: " + clienteId));
        List<ItemPedido> itens = estoqueService.reservar(quantidadePorProduto);
        Pedido pedido = pedidoRepository.save(new Pedido(cliente, itens));

        // Cria o evento da notificacao apos o pedido ser salvo
        PedidoCriadoEvent evento = new PedidoCriadoEvent(
            pedido.getId(),
            pedido.getCliente().getNome(),
            pedido.getCliente().getEmail(),
            pedido.getValorTotal()
        );

        // Anuncia o evento para o Spring
        // o listener vai enviar ao Kafka depois da confirmação da transação
        applicationEventPublisher.publishEvent(evento);

        // Acoplamento proposital: chamada direta e sincrona.
        // Este e o ponto de corte do exercicio de decomposicao (vira um evento Kafka).
        // notificacaoService.enviarConfirmacao(pedido);

        return pedido;
    }

    @Transactional
    public Pedido cancelar(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Pedido nao encontrado: " + pedidoId));
        if (!Pedido.CRIADO.equals(pedido.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pedido #" + pedidoId + " nao pode ser cancelado (status: " + pedido.getStatus() + ")");
        }

        estoqueService.devolver(pedido.getItens());
        pedido.cancelar();

        // Segundo caller sincrono: mais um evento a modelar na migracao.
        notificacaoService.enviarCancelamento(pedido);

        return pedido;
    }
}
