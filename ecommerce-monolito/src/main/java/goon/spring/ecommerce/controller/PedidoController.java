package goon.spring.ecommerce.controller;

import goon.spring.ecommerce.model.Pedido;
import goon.spring.ecommerce.service.PedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    public record ItemRequest(Long produtoId, Integer quantidade) {
    }

    public record PedidoRequest(Long clienteId, List<ItemRequest> itens) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido criar(@RequestBody PedidoRequest request) {
        if (request.clienteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clienteId e obrigatorio");
        }
        if (request.itens() == null || request.itens().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itens e obrigatorio");
        }
        if (request.itens().stream().anyMatch(i -> i.produtoId() == null || i.quantidade() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "produtoId e quantidade sao obrigatorios em cada item");
        }

        Map<Long, Integer> quantidadePorProduto = request.itens().stream()
                .collect(Collectors.toMap(ItemRequest::produtoId, ItemRequest::quantidade, Integer::sum));

        return pedidoService.criar(request.clienteId(), quantidadePorProduto);
    }

    @PatchMapping("/{id}/cancelar")
    public Pedido cancelar(@PathVariable Long id) {
        return pedidoService.cancelar(id);
    }
}
