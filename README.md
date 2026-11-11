# workspace-service

Serviço do **Correcta** responsável pelo espaço de trabalho de código dos estudantes durante uma avaliação.
Ele guarda os rascunhos de código, controla as submissões e pede a avaliação do código ao
`evaluation-orchestrator`.

## O que o serviço faz

- **Workspace**: cada estudante tem um workspace por avaliação e questão. Ele é criado no primeiro acesso.
- **Rascunhos**: cada salvamento cria uma nova revisão imutável do código (`1`, `2`, `3`...). A revisão `0`
  significa "ainda sem rascunho".
- **Controle de concorrência otimista**: o cliente envia a última revisão que viu no cabeçalho `If-Match`.
  Se outra aba ou réplica salvou antes, a resposta é `412`.
- **Executar (run)**: avalia o último rascunho sem criar submissão (avaliação `PARTIAL`).
- **Entrega da avaliação**: submete o último rascunho de todos os workspaces do estudante e avisa o
  `assessment-service` que a sessão foi entregue.
- **Fim de sessão**: quando a sessão termina sem entrega (prazo, arquivamento ou encerramento pelo docente),
  o serviço submete os últimos rascunhos automaticamente.
- **Prazo**: antes de qualquer escrita, o serviço pergunta ao `assessment-service` se a sessão ainda aceita
  submissões. Caso contrário, responde `403`.

## Arquitetura

```
Frontend → Gateway → workspace-service ──HTTP──▶ assessment-service (status da sessão)
                          │
                          ├── PostgreSQL (workspaces, rascunhos, submissões, outbox)
                          └── RabbitMQ ◀──▶ assessment-service / evaluation-orchestrator
```

### Camadas e serviços

| Classe | Responsabilidade |
|---|---|
| `WorkspaceControllerImpl` | Camada HTTP. Descobre o perfil autenticado e define o `ETag` das respostas. |
| `WorkspaceService` | Busca ou cria o workspace de uma questão. |
| `DraftService` | Salva revisões de rascunho com `If-Match` e compare-and-swap no banco. |
| `SubmissionService` | Cria submissões numeradas, substitui a anterior e dispara a avaliação final. |
| `WorkspaceEvaluationService` | Pede avaliações (parcial ou final) gravando-as no outbox. |
| `SubmissionWindowGuard` | Regra única de prazo: consulta o `assessment-service`. |
| `WorkspaceReader` | Leituras compartilhadas: workspace do dono e último rascunho. |
| `Outbox` / `OutboxRelay` | Padrão transactional outbox para publicar mensagens no RabbitMQ. |
| `SessionEndedListener` | Consome `session.ended` e submete os rascunhos do estudante. |

A autenticação fica só no controller. Os serviços recebem `profileId` como parâmetro, então também podem ser
usados por chamadores do sistema, como o listener de fila.

## API

Caminho base: `/workspaces`. Todos os endpoints exigem usuário logado com perfil `STUDENT`.

| Método | Caminho | Descrição |
|---|---|---|
| `GET` | `/workspaces/assessment/{assessmentId}/questions/{questionId}` | Busca ou cria o workspace. Retorna o rascunho atual e o `ETag`. |
| `PUT` | `/workspaces/{workspaceId}/drafts` | Salva uma nova revisão. Exige o cabeçalho `If-Match`, por exemplo `"3"`. |
| `POST` | `/workspaces/{workspaceId}/runs` | Executa o último rascunho salvo (avaliação parcial). |
| `POST` | `/workspaces/assessment/{assessmentId}/submissions` | Entrega a avaliação inteira. Responde `202`. |

Códigos de erro principais:

| Código | Quando |
|---|---|
| `400` | `If-Match` com formato inválido ou corpo inválido. |
| `401` | Sem autenticação. |
| `403` | Perfil não é estudante, ou a sessão não aceita mais submissões (inclui prazo vencido). |
| `404` | Workspace não existe ou não pertence ao estudante. |
| `412` | `If-Match` desatualizado: outro salvamento aconteceu antes. |
| `428` | Cabeçalho `If-Match` ausente. |

Os erros têm o corpo `{ "message": "..." }`. Para o fluxo completo do frontend, veja
[docs/frontend-integration-guide.md](docs/frontend-integration-guide.md).

## Autenticação

O serviço **não valida tokens**. Ele confia nos cabeçalhos de identidade que o gateway injeta
(`X-User-Id`, `X-User-Person-Id`, `X-User-Profile-Type`, `X-User-Login`, `X-User-Email`).
Por isso ele só pode ser acessível pelo gateway. A permissão de cada endpoint é verificada pelas anotações
`@IsLoggedUser` e `@IsProfileType`, aplicadas pelo `SecurityAspect`.

## Mensageria

Exchanges e filas são declaradas pelo serviço (a declaração é idempotente).

| Mensagem | Exchange / chave | Sentido |
|---|---|---|
| `session.ended` | `correcta.assessment-sessions` / `session.ended` | `assessment-service` → este serviço |
| `session.delivered` | `correcta.assessment-sessions` / `session.delivered` | este serviço → `assessment-service` |
| Disparo de avaliação | `correcta.evaluations` / `evaluation.trigger` | este serviço → `evaluation-orchestrator` |

Mensagens que falham depois das tentativas do listener vão para a fila `<fila>.dlq`.

### Outbox

As mensagens de saída são gravadas na tabela `outbox_messages` **na mesma transação** da mudança que elas
anunciam. O `OutboxRelay` lê a tabela periodicamente, publica no RabbitMQ e só marca como publicada depois da
confirmação do broker. Falhas são repetidas com espera exponencial (até 5 minutos). A entrega é *at least once*,
então quem consome precisa ser idempotente.

## Submissões

- Cada submissão aponta para uma revisão de rascunho e tem um número por workspace.
- Uma nova submissão passa a anterior de `ACTIVE` para `SUPERSEDED`. Um índice único parcial garante no máximo
  uma `ACTIVE` por workspace.
- Submeter de novo a mesma revisão não faz nada (idempotente).
- Na entrega, cada workspace é submetido em sua própria transação. Se algum falhar, os outros continuam e o
  serviço devolve erro no final. Repetir a chamada é seguro.

## Tecnologias

Kotlin, Java 17, Spring Boot (Web, Data JPA, Security, AMQP, AOP, Validation, Actuator), Spring Cloud OpenFeign,
PostgreSQL, Flyway, RabbitMQ e SpringDoc OpenAPI.

## Configuração

Variáveis de ambiente:

| Variável | Descrição | Padrão |
|---|---|---|
| `ENV_DATASOURCE_POSTGRES_URL` | URL JDBC do PostgreSQL | obrigatória |
| `ENV_DATASOURCE_POSTGRES_USERNAME` | Usuário do banco | obrigatória |
| `ENV_DATASOURCE_POSTGRES_PASSWORD` | Senha do banco | obrigatória |
| `ENV_RABBITMQ_URL` | URL AMQP do RabbitMQ | obrigatória |
| `ENV_ASSESSMENT_SERVICE_URL` | URL base do `assessment-service` | obrigatória |
| `ENV_ASSESSMENT_SERVICE_CONNECT_TIMEOUT_MS` | Timeout de conexão com o `assessment-service` | `2000` |
| `ENV_ASSESSMENT_SERVICE_READ_TIMEOUT_MS` | Timeout de leitura | `5000` |
| `ENV_JPA_DDL_AUTO` | Modo do Hibernate | `validate` |
| `ENV_SPRINGDOC_ENABLED` | Habilita Swagger/OpenAPI | `false` |
| `ENV_RABBITMQ_LISTENER_MAX_ATTEMPTS` | Tentativas do listener antes da DLQ | `5` |
| `ENV_OUTBOX_RELAY_INTERVAL_MS` | Intervalo do relay do outbox | `1000` |

O schema do banco é gerenciado pelo Flyway (`src/main/resources/db/migration`). O Hibernate apenas valida.

Perfil `local`: use `SPRING_PROFILES_ACTIVE=local` para valores padrão de desenvolvimento (PostgreSQL em
`localhost:5432`, RabbitMQ em `localhost:5672`, `assessment-service` em `http://127.0.0.1:8086`) e para
habilitar o Swagger em `/swagger-ui.html`.

## Como rodar

```bash
# testes
./gradlew test

# aplicação com perfil local
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun

# imagem Docker (porta 8080)
docker build -t workspace-service .
```

O endpoint de saúde é `/actuator/health`. Ele é o único endpoint do Actuator exposto.

## Estrutura do código

```
src/main/kotlin/io/correcta/workspace
├── controllers/      API HTTP (interface com anotações + implementação)
├── service/          Regras de negócio
├── entities/         Entidades JPA
├── repositories/     Repositórios Spring Data
├── messaging/        RabbitMQ: sessão, avaliação e outbox
├── clients/          Cliente Feign do assessment-service
├── security/         Filtro de cabeçalhos, anotações e aspecto de autorização
├── handlers/         Tratamento de exceções
└── models/           Requests e responses
```

## Testes

Os testes unitários ficam em `src/test/kotlin`, divididos por serviço (`DraftServiceTest`, `SubmissionServiceTest`,
`WorkspaceServiceTest`, `WorkspaceEvaluationServiceTest`, `SubmissionWindowGuardTest`, `WorkspaceReaderTest`).
Há também testes de controller e um teste de integração de concorrência.
