package goon.spring.ecommerce.service;

import goon.spring.ecommerce.model.ItemPedido;
import goon.spring.ecommerce.model.Produto;
import goon.spring.ecommerce.repository.ProdutoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class EstoqueService {

    private final ProdutoRepository produtoRepository;

    public EstoqueService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    /**
     * Valida o estoque de cada produto e ja aplica a baixa.
     * Retorna os itens do pedido, com o preco unitario congelado no momento da compra.
     */
    public List<ItemPedido> reservar(Map<Long, Integer> quantidadePorProduto) {
        return quantidadePorProduto.entrySet().stream().map(entrada -> {
            Produto produto = produtoRepository.findById(entrada.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Produto nao encontrado: " + entrada.getKey()));
            int quantidade = entrada.getValue();
            if (quantidade <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Quantidade invalida para o produto " + produto.getNome() + ": " + quantidade);
            }
            if (produto.getQuantidadeEmEstoque() < quantidade) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Estoque insuficiente para o produto " + produto.getNome()
                                + " (disponivel: " + produto.getQuantidadeEmEstoque() + ", pedido: " + quantidade + ")");
            }
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() - quantidade);
            return new ItemPedido(produto, quantidade);
        }).toList();
    }

    /** Devolve ao estoque os itens de um pedido cancelado. */
    public void devolver(List<ItemPedido> itens) {
        itens.forEach(item -> {
            Produto produto = item.getProduto();
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque() + item.getQuantidade());
        });
    }
}
