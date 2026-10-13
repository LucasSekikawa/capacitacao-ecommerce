package goon.spring.ecommerce.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

/**
 * Historico de notificacoes enviadas.
 *
 * Acoplamento proposital para o exercicio de decomposicao: esta tabela vive no
 * mesmo banco e na mesma transacao do pedido, e ainda tem FK para PEDIDO.
 * Extrair o servico de notificacao exige separar este schema.
 */
@Entity
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Pedido pedido;

    private String destinatario;

    private String tipo;

    private Instant enviadoEm;

    protected Notificacao() {
    }

    public Notificacao(Pedido pedido, String tipo) {
        this.pedido = pedido;
        this.destinatario = pedido.getCliente().getEmail();
        this.tipo = tipo;
        this.enviadoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getTipo() {
        return tipo;
    }

    public Instant getEnviadoEm() {
        return enviadoEm;
    }
}
