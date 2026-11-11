# workspace-service

Serviço do **Correcta** responsável pelo espaço de trabalho de código dos estudantes durante uma avaliação.
Ele guarda os rascunhos de código, controla as submissões e pede a avaliação do código ao
`evaluation-orchestrator`.

## Estado atual

| Item | Situação |
|---|---|
| Workspace por estudante, avaliação e questão | Pronto |
| Rascunhos imutáveis com `ETag` / `If-Match` | Pronto |
| Execução parcial (`run`) | Pronto |
| Entrega da avaliação e fim de sessão por fila | Pronto |
| Outbox transacional para RabbitMQ | Pronto |
| Docente ver o workspace submetido de um estudante | Não implementado |
| Estudante ver o próprio workspace submetido | Não implementado |
| Listagens paginadas e filtros (`PaginationExt`, `*Specification`) | Código pronto e testado, sem endpoint |
| Cobertura de testes | 100% de linhas e de branches (JaCoCo) |

As pendências abertas ficam em [KNOWN-ISSUES.md](KNOWN-ISSUES.md).

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
| `CustomExceptionHandler` | Converte exceções em respostas JSON com mensagens em português. |

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

Corpo do `PUT /drafts`:

```json
{ "sourceCode": "int main() { return 0; }", "language": "C" }
```

`sourceCode` e `language` são obrigatórios. O código aceita até 65.536 caracteres.

Códigos de erro:

| Código | Quando |
|---|---|
| `400` | `If-Match` com formato inválido, corpo inválido ou cabeçalho obrigatório ausente. |
| `401` | Sem autenticação. |
| `403` | Perfil não é estudante, ou a sessão não aceita mais submissões (inclui prazo vencido). |
| `404` | Workspace não existe ou não pertence ao estudante. |
| `405` | Método HTTP não suportado. A resposta traz o cabeçalho `Allow`. |
| `412` | `If-Match` desatualizado: outro salvamento aconteceu antes. |
| `415` | `Content-Type` não suportado. |
| `428` | Cabeçalho `If-Match` ausente. |
| `500` | Erro interno, ou falha ao submeter alguma questão na entrega. |

Os erros têm o corpo `{ "message": "..." }`. Erros de validação têm também `errors`, com uma mensagem por campo.

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

## Banco de dados

O Flyway gerencia o schema (`src/main/resources/db/migration`). O Hibernate apenas valida.

| Migração | Conteúdo |
|---|---|
| `V1__initial_schema.sql` | Workspaces, rascunhos e submissões. |
| `V2__add_unique_active_submission_per_workspace.sql` | Índice único parcial: uma submissão `ACTIVE` por workspace. |
| `V3__submission_references_draft_revision.sql` | A submissão passa a referenciar a revisão do rascunho. |
| `V4__create_outbox_messages.sql` | Tabela do outbox. |

## Tecnologias

Kotlin 1.9, Java 17, Spring Boot 3.4 (Web, Data JPA, Security, AMQP, AOP, Validation, Actuator),
Spring Cloud OpenFeign, PostgreSQL, Flyway, RabbitMQ e SpringDoc OpenAPI.
Testes com JUnit 5, Mockito, H2, Testcontainers e JaCoCo. Formatação com Spotless (ktlint).

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

Perfil `local`: use `SPRING_PROFILES_ACTIVE=local` para valores padrão de desenvolvimento (PostgreSQL em
`localhost:5432`, RabbitMQ em `localhost:5672`, `assessment-service` em `http://127.0.0.1:8086`) e para
habilitar o Swagger em `/swagger-ui.html`.

## Como rodar

```bash
# testes + relatório de cobertura
./gradlew test

# testes, formatação e verificação de 100% de cobertura
./gradlew check

# formatar o código
./gradlew spotlessApply

# aplicação com perfil local
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun

# imagem Docker (porta 8080)
docker build -t workspace-service .
```

O endpoint de saúde é `/actuator/health`. Ele é o único endpoint do Actuator exposto.

O hook de pre-commit (`.githooks/pre-commit`) roda o `spotlessApply` nos arquivos Kotlin do commit.
Ele é instalado automaticamente na primeira compilação.

## Testes

```
src/test/kotlin/io/correcta/workspace
├── service/          Regras de negócio com mocks (DraftService, SubmissionService, WorkspaceService...)
├── controllers/      Contrato HTTP com MockMvc: status, ETag, If-Match e validação
├── handlers/         Mapeamento de cada exceção para o código HTTP
├── security/         Filtro de cabeçalhos, ScopeEvaluator e SecurityAspect
├── messaging/        Outbox, OutboxRelay (com mocks do RabbitMQ) e SessionEndedListener
├── repositories/     Specifications executadas no H2
├── integration/      PostgreSQL real (Testcontainers) com migrações e testes de concorrência
└── ...               Modelos, filtros, paginação e exceções
```

- Os testes de controller e de repositório usam H2, com o schema gerado pelas entidades.
- O teste de integração (`WorkspaceConcurrencyIntegrationTest`) precisa de Docker. Sem Docker, ele é ignorado.
- O JaCoCo gera o relatório em `build/reports/jacoco/test/html/index.html`.
- O `./gradlew check` falha se a cobertura de linhas ou de branches ficar abaixo de 100%.

## Estrutura do código

```
src/main/kotlin/io/correcta/workspace
├── controllers/      API HTTP (interface com anotações + implementação)
├── service/          Regras de negócio
├── entities/         Entidades JPA
├── repositories/     Repositórios Spring Data e specifications
├── messaging/        RabbitMQ: sessão, avaliação e outbox
├── clients/          Cliente Feign do assessment-service
├── security/         Filtro de cabeçalhos, anotações e aspecto de autorização
├── handlers/         Tratamento de exceções
├── exceptions/       Exceções de domínio com o código HTTP de cada uma
├── components/       Filtros HTTP auxiliares
├── config/           JPA, agendamento, relógio e OpenAPI
├── utils/            Paginação
└── models/           Requests e responses
```

## Deploy

O workflow `.github/workflows/release.yml` roda a cada push na `main`. Ele gera a imagem
`docker.correcta.staily.dev/correcta/workspace-service:sha-<commit>`, publica no registry e atualiza a tag no
repositório `CorrectaIFBA/infra` (overlay de produção). Não há ambiente de staging.
