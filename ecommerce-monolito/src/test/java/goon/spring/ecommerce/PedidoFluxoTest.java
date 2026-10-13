package goon.spring.ecommerce;

import com.jayway.jsonpath.JsonPath;
import goon.spring.ecommerce.repository.NotificacaoRepository;
import goon.spring.ecommerce.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PedidoFluxoTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ProdutoRepository produtoRepository;

    @Autowired
    NotificacaoRepository notificacaoRepository;

    private int estoqueDo(long produtoId) {
        return produtoRepository.findById(produtoId).orElseThrow().getQuantidadeEmEstoque();
    }

    private String pedidoDe(long produtoId, int quantidade) {
        return """
                {"clienteId":1,"itens":[{"produtoId":%d,"quantidade":%d}]}"""
                .formatted(produtoId, quantidade);
    }

    @Test
    void criaPedidoBaixaEstoqueERegistraNotificacao() throws Exception {
        int estoqueAntes = estoqueDo(1L);
        long notificacoesAntes = notificacaoRepository.count();

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":1,"itens":[{"produtoId":1,"quantidade":2},
                                                        {"produtoId":2,"quantidade":1}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CRIADO"))
                .andExpect(jsonPath("$.cliente.email").value("ana@example.com"))
                // 2 x 350.00 + 1 x 120.00
                .andExpect(jsonPath("$.valorTotal").value(820.00));

        assertThat(estoqueDo(1L)).isEqualTo(estoqueAntes - 2);
        assertThat(notificacaoRepository.count()).isEqualTo(notificacoesAntes + 1);
    }

    @Test
    void cancelaPedidoDevolveEstoqueEEnviaSegundaNotificacao() throws Exception {
        int estoqueAntes = estoqueDo(3L);

        String criado = mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoDe(3L, 2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(estoqueDo(3L)).isEqualTo(estoqueAntes - 2);

        long pedidoId = ((Number) JsonPath.read(criado, "$.id")).longValue();
        long notificacoesAntes = notificacaoRepository.count();

        mockMvc.perform(patch("/pedidos/{id}/cancelar", pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        assertThat(estoqueDo(3L)).isEqualTo(estoqueAntes);
        assertThat(notificacaoRepository.count()).isEqualTo(notificacoesAntes + 1);

        // segundo cancelamento nao devolve estoque de novo
        mockMvc.perform(patch("/pedidos/{id}/cancelar", pedidoId))
                .andExpect(status().isBadRequest());
        assertThat(estoqueDo(3L)).isEqualTo(estoqueAntes);
    }

    @Test
    void recusaPedidoSemEstoqueSuficiente() throws Exception {
        // produto 4 (Headset) tem estoque 0 no data.sql
        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoDe(4L, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recusaPedidoDeClienteInexistente() throws Exception {
        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":999,"itens":[{"produtoId":1,"quantidade":1}]}"""))
                .andExpect(status().isBadRequest());
    }
}
