# E-commerce — notificação com Spring Boot e Kafka

## O que mudou

No `PedidoService`, no modelo antigo de monolito, ele chamava o método `notificacaoService.enviarConfirmacao(pedido)`. Ou seja, após solicitar a gravação do pedido, o fluxo continuava para simular o envio do e-mail na mesma aplicação, ainda dentro da transação. Isso gerava uma dependência direta, de maneira que:

- O pedido era salvo dentro da transação.
- Em seguida, a notificação era registrada e o e-mail era simulado no log.
- Depois, o método retornava o pedido e o Spring solicitava a confirmação da transação.

Porém, após as alterações, a chamada de `enviarConfirmacao` foi retirada da criação do pedido. Foi criado um pacote chamado `messaging`, com dois arquivos:

**PedidoCriadoEvent:** É um record que define a estrutura dos dados necessários para simular o e-mail de confirmação do pedido.

**PedidoEventPublisher:** Nessa classe ocorre a interação com o Kafka. O que acontece agora é:

1. O pedido é salvo dentro da transação, que ainda não foi confirmada.
2. É criado um objeto `PedidoCriadoEvent`, com o ID do pedido, nome e e-mail do cliente e valor total.
3. O evento é anunciado ao Spring.
4. O método `criar` chega ao `return pedido`.
5. O Spring solicita ao banco a confirmação da transação.
6. Após o commit bem-sucedido, o Spring chama `PedidoEventPublisher.publicarPedidoCriado(evento)`.
7. Esse método usa `KafkaTemplate.send()` para iniciar a publicação no tópico `pedido-criado`.
8. O consumidor do outro serviço lê a mensagem do Kafka e converte o JSON em um `PedidoCriadoEvent` da aplicação de notificação.
9. O Spring chama `NotificacaoListener.receberPedidoCriado(evento)`, que escreve o e-mail simulado no log.

Como consequência dessa mudança, a criação do pedido deixa de depender diretamente da execução da notificação. O serviço de notificação roda separadamente e processa o evento de forma assíncrona, sem o monolito precisar esperar pelo e-mail simulado.

Por exemplo, caso o serviço de notificação esteja desligado, o monolito pode criar o pedido e publicar o evento. Se a publicação for bem-sucedida, a mensagem fica armazenada no Kafka e pode ser processada quando o serviço voltar a funcionar, desde que ainda esteja dentro do prazo de retenção.

Como custo dessa mudança, o ambiente ficou mais complexo. Antes, bastava executar o monolito; agora, precisamos executar também o Kafka e o serviço de notificação. Caso uma notificação não apareça, precisamos verificar se o evento foi publicado, se o Kafka está disponível e se o consumidor está funcionando. Além disso, a criação do pedido não garante que a notificação já foi processada, pois isso acontece de forma assíncrona.

Outra limitação é que, como a publicação acontece após o commit, pode ocorrer de o pedido ser confirmado no banco e o envio do evento ao Kafka falhar. Nesse caso, o pedido continua salvo, mas a notificação pode não acontecer.

A alteração contempla apenas `PedidoCriado`. O cancelamento mantém o comportamento original no monolito. O novo serviço simula o e-mail no log, sem persistir histórico em banco.

## Como executar

### Pré-requisitos

- JDK 17 instalado.
- Docker Desktop instalado e em execução.
- Os projetos do monolito e do serviço de notificação disponíveis.

Não é necessário instalar Gradle separadamente: cada projeto possui seu próprio Gradle Wrapper. Os comandos abaixo são para PowerShell no Windows e devem ser executados na raiz do projeto indicado, onde fica o `build.gradle`. No Linux ou macOS, use `./gradlew bootRun` no lugar de `.\gradlew.bat bootRun`.

Confira se o JDK 17 está disponível no terminal:

```powershell
java -version
javac -version
```

Se necessário, configure `JAVA_HOME` com o caminho do JDK 17 instalado na sua máquina e adicione sua pasta `bin` ao `PATH`.

### 1. Iniciar o Kafka

Na raiz deste repositório, onde está o `compose.yaml`, execute:

```powershell
docker compose up -d
```

Confira o estado do contêiner:

```powershell
docker compose ps
```

O serviço `kafka` deve apresentar o status `Up`. Confira também a inicialização:

```powershell
docker compose logs --tail 30 kafka
```

Aguarde `Kafka Server started` e crie o tópico usado pela aplicação:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic pedido-criado --partitions 1 --replication-factor 1
```

O Kafka ficará rodando em segundo plano, acessível em `localhost:9092`.

### 2. Iniciar o monolito

Abra um PowerShell na pasta `ecommerce-monolito`, onde estão o `build.gradle` e o `gradlew.bat`, e execute:

```powershell
.\gradlew.bat bootRun
```

Aguarde aparecer `Started EcommerceApplication` e mantenha o terminal aberto. A API estará disponível em `http://localhost:8080`.

### 3. Iniciar o serviço de notificação

Abra outro PowerShell na raiz do projeto separado `notificacao-service`, onde estão seu `build.gradle` e seu `gradlew.bat`. Confira que o JDK 17 também está disponível nesse terminal e execute:

```powershell
.\gradlew.bat bootRun
```

Aguarde a inicialização e a atribuição da partição:

```text
notificacao-service: partitions assigned: [pedido-criado-0]
```

Mantenha esse terminal aberto. O serviço não possui uma API HTTP; ele aguarda mensagens do Kafka. São dois terminais executando aplicações Java, enquanto o Kafka roda em segundo plano no Docker.

### 4. Criar um pedido

No Insomnia ou Postman, envie:

```text
POST http://localhost:8080/pedidos
Content-Type: application/json
```

Use este corpo JSON:

```json
{
  "clienteId": 1,
  "itens": [
    { "produtoId": 1, "quantidade": 2 },
    { "produtoId": 2, "quantidade": 1 }
  ]
}
```

A resposta esperada é `201 Created`. No terminal do serviço de notificação, deve aparecer uma mensagem semelhante a:

```text
[E-MAIL SIMULADO] Para: Ana Souza <ana@example.com> | Pedido #1 confirmado | Total: R$ 820.00
```

O identificador pode variar conforme os pedidos já criados. O banco do monolito fica em memória e é recriado a cada inicialização, com os clientes e produtos definidos em `src/main/resources/data.sql`. Compras repetidas reduzem o estoque e podem resultar em recusa por estoque insuficiente.

A coleção [ecommerce-monolito.postman_collection.json](ecommerce-monolito/docs/ecommerce-monolito.postman_collection.json) também pode ser importada no Insomnia ou Postman.

### 5. Testar com a notificação desligada

1. Encerre apenas o serviço de notificação com **Ctrl+C**, depois de ele já ter conectado e processado o primeiro pedido.
2. Mantenha o monolito e o Kafka rodando.
3. Crie outro pedido pelo Insomnia ou Postman.
4. Inicie novamente a notificação com `.\gradlew.bat bootRun`.

O serviço deverá consumir o evento pendente e escrever o e-mail simulado no log, desde que a publicação tenha sido bem-sucedida e o evento ainda esteja disponível no Kafka. Mantenha o mesmo grupo de consumidores, `notificacao-service`, para retomar a leitura da posição registrada.

### Encerrar o ambiente

Pressione **Ctrl+C** em cada terminal das aplicações Java.

Na raiz deste repositório, pare o Kafka:

```powershell
docker compose stop
```

Para iniciá-lo novamente, use:

```powershell
docker compose up -d
```
