package goon.spring.ecommerce.repository;

import goon.spring.ecommerce.model.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {
}
